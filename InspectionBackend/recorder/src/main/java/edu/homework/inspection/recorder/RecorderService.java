package edu.homework.inspection.recorder;

import edu.homework.inspection.common.Blackboard;
import edu.homework.inspection.common.JsonSupport;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.RedisProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ============================================================
 * 【录制回放服务 —— RecorderService】
 * ============================================================
 *
 * 【这个类的角色】
 * 这是整个 recorder 模块的"心脏"——包含录制和回放两条核心循环。
 *
 * 【两条核心循环】
 *
 * 1. recordLoop（录制循环）
 *    每 500ms 拍一张快照，保存到 Redis：
 *
 *    巡检开始（前端发 "1"）
 *       │
 *       ▼
 *    ┌─────────────────────────────────────────────┐
 *    │  recordLoop 循环（每 500ms 一帧）           │
 *    │                                             │
 *    │  1. capture(jedis)                          │
 *    │     ├─ 读取地图尺寸（width, height）         │
 *    │     ├─ 读取 MapView bitmap → Base64 编码    │
 *    │     ├─ 读取 blockview bitmap → Base64 编码  │
 *    │     └─ 读取所有小车状态 → List<CarSnapshot> │
 *    │                                             │
 *    │  2. 序列化为 JSON → 存入 Redis              │
 *    │     Key: Record:1:0  snapshot: "{...}"      │
 *    │     Key: Record:1:1  snapshot: "{...}"      │
 *    │     Key: Record:1:2  snapshot: "{...}"      │
 *    │     ...                                     │
 *    │                                             │
 *    │  3. Thread.sleep(500) → 等待 500ms          │
 *    └─────────────────────────────────────────────┘
 *
 * 2. playbackLoop（回放循环）
 *    从存储中逐帧读取快照，恢复到 Redis 的"当前状态"键：
 *
 *    用户点击"开始回放"（前端发 "2"）
 *       │
 *       ▼
 *    ┌─────────────────────────────────────────────┐
 *    │  playbackLoop 循环（速度可调）              │
 *    │                                             │
 *    │  1. 读取当前帧号 → 计算下一帧号             │
 *    │  2. 从 Redis 读取快照 JSON                  │
 *    │     Key: Record:1:42  snapshot: "{...}"     │
 *    │  3. 解析 JSON → FrameSnapshot              │
 *    │  4. 恢复到 Redis 当前状态：                 │
 *    │     ├─ 写入 map_width, map_height           │
 *    │     ├─ 写入 MapView bitmap                  │
 *    │     ├─ 写入 blockview bitmap                │
 *    │     └─ 写入所有小车数据（Cars:*）           │
 *    │  5. 更新 order_view（当前帧号）             │
 *    │  6. 根据速度延迟（1.0x=500ms, 2.0x=250ms） │
 *    │  7. 播放到最后一帧 → 自动停止               │
 *    └─────────────────────────────────────────────┘
 *
 * 【线程安全设计】
 * - recording 和 playback 这两个标记用 AtomicBoolean（原子布尔值）
 *   → 保证多线程环境下的读写安全，不需要 synchronized
 * - startRecording/stopRecording/startPlayback/stopPlayback 用
 *   synchronized 修饰 → 同一时刻只有一个线程能执行这些方法
 *   防止"同时收到开始和停止指令"导致的竞态条件
 *
 * 【录制和回放互斥】
 * 开始录制时会自动停止回放，反之亦然。用 boolean 标记来控制循环的启停。
 *
 * 【类比理解】
 * RecorderService 就像一台"硬盘录像机"：
 * - recordLoop = 录制模式：把电视节目录到硬盘上
 * - playbackLoop = 播放模式：把硬盘上的录像播出来
 * - capture() = 按下快门拍照
 * - restore() = 把照片放回相框里展示
 */
public class RecorderService {
    private static final Logger log = LoggerFactory.getLogger(RecorderService.class);

    /**
     * 【AtomicBoolean —— 原子布尔值】
     *
     * 为什么不用普通的 boolean？
     *
     * 普通 boolean 在多线程下有"可见性"问题：
     * 线程 A 修改了 recording = false，但线程 B 可能看不到这个变化
     * （因为每个线程有自己的 CPU 缓存）。
     *
     * AtomicBoolean 内部使用 volatile 和 CAS（比较并交换）操作，
     * 保证一个线程修改后，其他线程能立刻看到最新值。
     *
     * 类比：普通 boolean = 便利贴（别人可能看不到你写的字）
     *       AtomicBoolean = 电子公告牌（所有人看到的内容永远一致）
     */
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean playback = new AtomicBoolean(false);

    /** 录制线程和回放线程的引用 */
    private Thread recordThread;
    private Thread playbackThread;

    // ================================================================
    // 公开方法：接收 RabbitMQ 指令 → 启动/停止录制/回放
    // ================================================================

    /**
     * 【开始录制】
     *
     * 执行流程：
     * 1. 先停止回放（录制和回放互斥）
     * 2. 检查是否已在录制中（防止重复启动）
     * 3. 在 Redis 中创建新的录制记录：
     *    a. file_num 自增 → 得到新记录编号
     *    b. 设置 order_file_num（当前操作的文件编号）
     *    c. 初始化 start_view=0, last_view=0, order_view=-1
     *    d. 记录创建时间戳
     *    e. 删除该文件编号的旧帧数据（如果有的话，覆盖写入）
     * 4. 创建后台录制线程并启动
     *
     * synchronized 的作用：防止多线程同时调用导致状态混乱。
     * 例如：前端快速点了两次"开始巡检"，没有 synchronized 的话
     * 可能启动两个录制线程。
     */
    public synchronized void startRecording() {
        stopPlayback();  // 录制前先停回放

        // 已在录制中？忽略重复指令
        if (recording.get()) {
            log.warn("Recorder already recording, ignoring start");
            return;
        }

        int fileNo;
        // try-with-resources：确保 Jedis 连接用完自动归还
        try (Jedis jedis = RedisProvider.get()) {
            // HINCRBY：原子地增加 hash 字段的值 → 线程安全的自增
            // file_num 记录了总共录制了多少条记录
            fileNo = jedis.hincrBy(Keys.SAVE, "file_num", 1).intValue();

            // 在 Save hash 中初始化这条记录的元数据
            jedis.hset(Keys.SAVE, "order_file_num", String.valueOf(fileNo));
            jedis.hset(Keys.SAVE, "start_view", "0");       // 起始帧
            jedis.hset(Keys.SAVE, "last_view", "0");        // 最后一帧编号
            jedis.hset(Keys.SAVE, "order_view", "-1");      // 当前帧（-1=还没开始）
            jedis.hset(Keys.SAVE, "created_at_" + fileNo,   // 创建时间戳
                    String.valueOf(System.currentTimeMillis()));

            // 清理该文件编号的旧帧（支持覆盖重录）
            deleteOldFrames(jedis, fileNo);
        }

        // 设置录制标记 → 启动录制线程
        recording.set(true);
        int currentFileNo = fileNo;  // 捕获到局部变量（Lambda 中需要 effectively final）
        recordThread = new Thread(() -> recordLoop(currentFileNo), "recorder-record");
        recordThread.setDaemon(false);  // 非守护线程：即使主线程结束，录制线程也要跑完
        recordThread.start();
        log.info("Recorder started file {}", currentFileNo);
    }

    /**
     * 【停止录制】
     *
     * 设置 recording = false → 录制循环检测到后自动退出。
     * 同时 interrupt 录制线程 → 如果它正在 sleep(500)，立即唤醒。
     */
    public synchronized void stopRecording() {
        recording.set(false);
        if (recordThread != null) {
            recordThread.interrupt();  // 唤醒正在 sleep 的录制线程
        }
        log.info("Recorder stopped");
    }

    /**
     * 【开始回放】
     *
     * 执行流程：
     * 1. 停止当前回放（如果有的话）
     * 2. 重置 order_view = -1（从第 0 帧开始播放）
     * 3. 创建后台回放线程并启动
     */
    public synchronized void startPlayback() {
        stopPlayback();  // 先停旧回放

        if (playback.get()) {
            log.warn("Recorder already playing back, ignoring start");
            return;
        }

        // 重置当前帧为 -1，下一帧就是 0
        try (Jedis jedis = RedisProvider.get()) {
            jedis.hset(Keys.SAVE, "order_view", "-1");
        }

        playback.set(true);
        playbackThread = new Thread(this::playbackLoop, "recorder-playback");
        playbackThread.setDaemon(false);
        playbackThread.start();
        log.info("Playback started");
    }

    /**
     * 【停止回放】
     */
    public synchronized void stopPlayback() {
        playback.set(false);
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
    }

    // ================================================================
    // 核心循环：录制
    // ================================================================

    /**
     * 【录制循环 —— 整个 recorder 最核心的方法之一】
     *
     * 这是一个后台线程的主循环，每 500ms 执行一帧：
     *
     * while (还在录制) {
     *     1. 拍快照（capture）
     *     2. 序列化为 JSON 存入 Redis
     *     3. 更新 last_view
     *     4. 睡 500ms
     * }
     *
     * 【帧号管理】
     * frame 从 0 开始递增，每拍一张 +1。
     * last_view 实时更新为当前帧号，前端用这个值显示总帧数。
     *
     * 【异常处理】
     * - InterruptedException：线程被 interrupt() 唤醒 → 设置中断标记并退出
     * - 其他异常：记录错误日志 → 继续下一帧（不让一个错误帧毁掉整个录制）
     *
     * @param fileNo 当前录制的文件编号
     */
    private void recordLoop(int fileNo) {
        int frame = 0;
        log.info("Record loop started for file {}", fileNo);

        while (recording.get()) {  // 每轮循环前检查是否还需要录制
            try (Jedis jedis = RedisProvider.get()) {
                long t0 = System.nanoTime();

                // 第1步：拍快照 —— 读取 Redis 当前状态
                FrameSnapshot snapshot = capture(jedis);

                // 第2步：序列化为 JSON → 存入 Redis
                // Key 格式：Record:1:0, Record:1:1, Record:1:2, ...
                jedis.hset(Keys.recorderFrameKey(fileNo, frame),
                        "snapshot", JsonSupport.toJson(snapshot));

                // 第3步：更新最后一帧编号（前端用这个判断总共有多少帧）
                jedis.hset(Keys.SAVE, "last_view", String.valueOf(frame));

                log.debug("Recorder saved frame {}/{} in {}ms",
                        fileNo, frame, (System.nanoTime() - t0) / 1_000_000L);

                frame++;  // 帧号递增

                // 第4步：等待 500ms 再拍下一帧
                // 500ms 是录制间隔，权衡了存储空间和回放流畅度
                Thread.sleep(500L);

            } catch (InterruptedException e) {
                // 被 interrupt() 唤醒（用户点了停止）
                // Thread.currentThread().interrupt() 恢复中断标记
                // （这是 Java 的最佳实践——不要吞掉中断状态）
                Thread.currentThread().interrupt();
                recording.set(false);
            } catch (Exception e) {
                log.error("Recording frame {} failed: {}", frame, e.getMessage(), e);
                // 出错不退出，继续录下一帧
            }
        }
        log.info("Record loop ended: {} frames saved", frame);
    }

    // ================================================================
    // 核心循环：回放
    // ================================================================

    /**
     * 【回放循环 —— 整个 recorder 最核心的方法之二】
     *
     * 这是一个后台线程的主循环，根据速度逐帧恢复历史数据：
     *
     * while (还在回放) {
     *     1. 读取当前选中记录的文件编号
     *     2. 计算下一帧号 = 当前帧 + 1
     *     3. 如果超出最后一帧 → 自动停止（不回绕）
     *     4. 从 Record:{fileNo}:{nextFrame} 读取快照 JSON
     *     5. 解析 JSON → 恢复到 Redis 当前状态键
     *     6. 更新 order_view（前端读取它来更新进度条）
     *     7. 根据速度延迟（1.0x=500ms, 2.0x=250ms, 0.5x=1000ms）
     * }
     *
     * 【为什么播放到最后一帧就停止，不回绕到第 0 帧？】
     * 代码中有详细注释解释：前端的 explorationMonitor 每 500ms 轮询
     * 一次 isFallExplored() 来判断是否弹出"地图已完全探索"的提示。
     * 如果回绕到第 0 帧，在 2 倍速（250ms 间隔）下，前端来不及捕获
     * 最后一帧的完全探索状态，弹窗就不会出现。
     * 所以设计为：播完就停，让前端有足够时间检测到完成状态。
     *
     * 【防御性检查】
     * 在每次写入 Redis 前，再次确认 playback 标记仍为 true。
     * 防止用户点了"重置"清空 Redis 后，回放线程还继续写入旧数据。
     * 这种"race condition"防御在实际系统中非常重要。
     */
    private void playbackLoop() {
        log.info("Playback loop started");

        while (playback.get()) {
            try (Jedis jedis = RedisProvider.get()) {
                // 防御性检查：再次确认回放状态
                if (!playback.get()) break;

                // 第1步：获取当前选中记录的文件编号
                int fileNo = parseInt(
                        jedis.hget(Keys.SAVE, "order_file_num"),  // 优先用 order_file_num
                        parseInt(jedis.hget(Keys.SAVE, "file_num"), 1));  // 回退到 file_num

                // 第2步：读取该记录的总帧数
                int last = parseInt(jedis.hget(Keys.SAVE, "last_view"), -1);
                if (last < 0) {
                    log.warn("Playback: 记录{}没有帧数据（last_view={}），停止回放", fileNo, last);
                    playback.set(false);
                    break;
                }

                // 第3步：计算下一帧号 = 当前帧 + 1
                int currentFrame = parseInt(jedis.hget(Keys.SAVE, "order_view"), -1);
                int nextFrame = currentFrame + 1;

                // 第4步：播放到最后一帧后自动停止（不回绕）
                // nextFrame > last 说明所有帧都已播放完毕
                if (nextFrame > last) {
                    log.info("Playback: 记录{}已播放到最后一帧（{}），自动停止", fileNo, last);
                    playback.set(false);
                    break;
                }

                // 第5步：从存储中恢复该帧到 Redis 当前状态
                long t0 = System.nanoTime();
                boolean restored = restore(jedis, fileNo, nextFrame);
                if (!restored) {
                    log.warn("Playback: 记录{}的帧{}不存在，跳过", fileNo, nextFrame);
                }

                // 第6步：再次确认回放状态（防止重置已清空 Redis）
                if (!playback.get()) {
                    log.info("Playback interrupted before writing order_view, " +
                            "discarding frame {}", nextFrame);
                    break;
                }

                // 第7步：更新当前帧号（前端读取此值来更新进度条和滑块）
                jedis.hset(Keys.SAVE, "order_view", String.valueOf(nextFrame));

                // 第8步：根据速度计算延迟时间
                long delay = delayMillis(jedis.hget(Keys.SAVE, "triple_speed"));

                log.info("Playback restored frame {}/{} in {}ms, delay={}ms",
                        fileNo, nextFrame,
                        (System.nanoTime() - t0) / 1_000_000L, delay);

                // 第9步：等待指定时间再播下一帧
                Thread.sleep(delay);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                playback.set(false);
            } catch (Exception e) {
                log.error("Playback failed: {}", e.getMessage(), e);
                sleepQuietly(500L);  // 出错后冷静 500ms 再重试
            }
        }
        log.info("Playback loop ended");
    }

    // ================================================================
    // 快照的"拍"和"恢复"
    // ================================================================

    /**
     * 【拍快照 —— capture】
     *
     * 从 Redis 中读取当前地图的完整状态，打包成一个 FrameSnapshot 对象。
     *
     * 读取的内容：
     * 1. 地图尺寸（width, height）
     * 2. MapView bitmap → Base64 编码（哪些格子被探索过）
     * 3. blockview bitmap → Base64 编码（哪些格子是障碍物）
     * 4. 所有小车的数据 → List<CarSnapshot>
     *
     * Base64 编码使得二进制位图可以安全地存储在 JSON 字符串中。
     *
     * @param jedis Redis 连接
     * @return 包含当前完整地图状态的快照对象
     */
    private FrameSnapshot capture(Jedis jedis) {
        FrameSnapshot snapshot = new FrameSnapshot();

        // 1. 记录地图尺寸（重要！不同记录可能有不同尺寸）
        snapshot.setWidth(Blackboard.mapWidth(jedis));
        snapshot.setHeight(Blackboard.mapHeight(jedis));

        // 2. 读取探索位图 → Base64 编码
        // jedis.get() 返回字节数组 → Base64.getEncoder() 编码为字符串
        snapshot.setMapViewBase64(base64(jedis.get(Keys.MAP_VIEW.getBytes())));

        // 3. 读取障碍物位图 → Base64 编码
        snapshot.setBlockViewBase64(base64(jedis.get(Keys.BLOCK_VIEW.getBytes())));

        // 4. 读取所有小车状态
        List<CarSnapshot> cars = new ArrayList<>();
        for (Integer id : Blackboard.existingCarIds(jedis)) {
            String key = Keys.carKey(id);           // "Cars:1", "Cars:2", ...
            cars.add(new CarSnapshot(key, jedis.hgetAll(key)));  // 读取小车所有字段
        }
        snapshot.setCars(cars);

        return snapshot;
    }

    /**
     * 【恢复快照 —— restore】
     *
     * 从存储中读取一帧快照 JSON，解析后恢复到 Redis 的"当前状态"键中。
     *
     * 恢复过程实际上是"覆盖"Redis 中以下键的值：
     * - map_width / map_height（地图尺寸可能与当前不同）
     * - MapView bitmap（探索位图）
     * - blockview bitmap（障碍物位图）
     * - Cars:*（所有小车数据——先清空旧的，再写入帧中的）
     *
     * 恢复后，前端渲染器（PlaybackMapView）的下一次定时器触发
     * 就会读到这些新写入的数据，从而显示这一帧的画面。
     *
     * @param jedis   Redis 连接
     * @param fileNo  记录文件编号
     * @param frame   帧号
     * @return true = 成功恢复，false = 该帧不存在
     */
    private boolean restore(Jedis jedis, int fileNo, int frame) {
        // 1. 从 Redis 读取快照 JSON
        String json = jedis.hget(Keys.recorderFrameKey(fileNo, frame), "snapshot");
        if (json == null) {
            return false;  // 该帧不存在（可能录制时出错跳过了）
        }

        // 2. JSON → FrameSnapshot 对象
        FrameSnapshot snapshot = JsonSupport.fromJson(json, FrameSnapshot.class);

        // 3. 恢复地图尺寸
        jedis.set(Keys.MAP_WIDTH, String.valueOf(snapshot.getWidth()));
        jedis.set(Keys.MAP_HEIGHT, String.valueOf(snapshot.getHeight()));

        // 4. 恢复探索位图（Base64 解码 → 二进制写入）
        restoreBytes(jedis, Keys.MAP_VIEW, snapshot.getMapViewBase64());

        // 5. 恢复障碍物位图
        restoreBytes(jedis, Keys.BLOCK_VIEW, snapshot.getBlockViewBase64());

        // 6. 恢复小车数据：先清空所有现有小车，再写入快照中的小车
        for (String carKey : scan(jedis, Keys.CARS_PREFIX + "*")) {
            jedis.del(carKey);
        }
        for (CarSnapshot car : snapshot.getCars()) {
            if (car.getFields() != null && !car.getFields().isEmpty()) {
                jedis.hmset(car.getKey(), car.getFields());
            }
        }

        return true;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 删除指定文件编号的所有旧帧数据
     * 在开始新录制前调用，避免新旧数据混合。
     * 例如：如果上次 fileNo=1 录了 100 帧，这次重新录 fileNo=1，
     * 先清空 Record:1:* 的所有旧数据。
     */
    private void deleteOldFrames(Jedis jedis, int fileNo) {
        for (String key : scan(jedis, Keys.RECORDER_FRAME_PREFIX + fileNo + ":*")) {
            jedis.del(key);
        }
    }

    /**
     * Redis SCAN 命令封装
     * 分批遍历匹配的键，不阻塞 Redis。
     */
    private List<String> scan(Jedis jedis, String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        ScanParams params = new ScanParams().match(pattern).count(100);
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            keys.addAll(result.getResult());
            cursor = result.getCursor();
        } while (!"0".equals(cursor));
        return keys;
    }

    /**
     * 字节数组 → Base64 编码字符串
     * null 或空数组返回空字符串
     */
    private String base64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64 字符串 → 解码 → 写入 Redis
     * 空值处理：如果 Base64 为空，删除对应的 Redis 键（而不是写入空数据）
     */
    private void restoreBytes(Jedis jedis, String key, String base64) {
        if (base64 == null || base64.isEmpty()) {
            jedis.del(key);
            return;
        }
        jedis.set(key.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(base64));
    }

    /**
     * 安全的字符串转整数
     * null 或空字符串 → 返回默认值（不会抛出异常）
     */
    private int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    /**
     * 【根据速度值计算帧间延迟】
     *
     * 公式：delay = 500ms / speed
     * - 1.0x → 500ms（每 0.5 秒一帧，正常速度）
     * - 2.0x → 250ms（两倍速，画面更快）
     * - 0.5x → 1000ms（半速，慢动作）
     *
     * 最小延迟 100ms：防止速度设置过高导致 CPU 空转。
     */
    private long delayMillis(String speedValue) {
        double speed = 1.0d;
        if (speedValue != null && !speedValue.trim().isEmpty()) {
            speed = Double.parseDouble(speedValue.trim());
        }
        if (speed <= 0.0d) {
            speed = 1.0d;  // 防御：速度为 0 或负数时用默认值
        }
        return Math.max(100L, Math.round(500.0d / speed));
    }

    /**
     * 安静的 sleep——出错时默默等待，不抛异常。
     * InterruptedException 被正确处理（恢复中断标记 + 停止回放）。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            playback.set(false);
        }
    }
}
