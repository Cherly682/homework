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

public class RecorderService {
    private static final Logger log = LoggerFactory.getLogger(RecorderService.class);
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean playback = new AtomicBoolean(false);

    private Thread recordThread;
    private Thread playbackThread;

    //接收指令
    public synchronized void startRecording() {
        //忽略重复指令
        if (recording.get()) {
            log.warn("Recorder already recording, ignoring start");
            return;
        }

        int fileNo;
        //jedis连接完自动归还
        try (Jedis jedis = RedisProvider.get()) {
            //hincrBy原子地增值
            fileNo = jedis.hincrBy(Keys.SAVE, "file_num", 1).intValue();

            //在Save hash中初始化
            jedis.hset(Keys.SAVE, "order_file_num", String.valueOf(fileNo));
            jedis.hset(Keys.SAVE, "start_view", "0");
            jedis.hset(Keys.SAVE, "last_view", "0");
            jedis.hset(Keys.SAVE, "order_view", "-1");      //-1=还没开始
            jedis.hset(Keys.SAVE, "created_at_" + fileNo,   //时间戳
                    String.valueOf(System.currentTimeMillis()));

            //清理旧帧
            deleteOldFrames(jedis, fileNo);
        }

        //启动录制线程
        recording.set(true);
        int currentFileNo = fileNo;
        recordThread = new Thread(() -> recordLoop(currentFileNo), "recorder-record");
        recordThread.setDaemon(false);  //非守护线程
        recordThread.start();
        log.info("Recorder started file {}", currentFileNo);
    }


    public synchronized void stopRecording() {
        recording.set(false);
        if (recordThread != null) {
            recordThread.interrupt();  //唤醒录制线程
        }
        log.info("Recorder stopped");
    }


    public synchronized void startPlayback() {
        stopPlayback();  // 先停旧回放

        if (playback.get()) {
            log.warn("Recorder already playing back, ignoring start");
            return;
        }

        //重置当前帧为-1（回放独立字段，不影响录制）
        try (Jedis jedis = RedisProvider.get()) {
            jedis.hset(Keys.SAVE, "playback_order_view", "-1");
        }

        playback.set(true);
        playbackThread = new Thread(this::playbackLoop, "recorder-playback");
        playbackThread.setDaemon(false);//非守护线程
        playbackThread.start();
        log.info("Playback started");
    }


    public synchronized void stopPlayback() {
        playback.set(false);
        if (playbackThread != null) {
            playbackThread.interrupt();  //唤醒回放线程
        }
    }

    private void recordLoop(int fileNo) {
        int frame = 0;
        log.info("Record loop started for file {}", fileNo);

        while (recording.get()) {  //检查是否还需要录制
            try (Jedis jedis = RedisProvider.get()) {
                long t0 = System.nanoTime();

                //拍照ing...
                FrameSnapshot snapshot = capture(jedis);

                //序列化为JSON
                jedis.hset(Keys.recorderFrameKey(fileNo, frame),
                        "snapshot", JsonSupport.toJson(snapshot));

                //更新最后一帧编号
                jedis.hset(Keys.SAVE, "last_view", String.valueOf(frame));

                log.debug("Recorder saved frame {}/{} in {}ms",
                        fileNo, frame, (System.nanoTime() - t0) / 1_000_000L);

                frame++;

                //等待500ms再拍
                Thread.sleep(500L);

            } catch (InterruptedException e) {
                //被唤醒，恢复中断标记
                Thread.currentThread().interrupt();
                recording.set(false);
            } catch (Exception e) {
                log.error("Recording frame {} failed: {}", frame, e.getMessage(), e);
                //不退出，继续录
            }
        }
        log.info("Record loop ended: {} frames saved", frame);
    }

    private void playbackLoop() {
        log.info("Playback loop started");

        while (playback.get()) {
            try (Jedis jedis = RedisProvider.get()) {
                if (!playback.get()) break;

                //获取当前选中记录的文件编号（回放独立字段）
                int fileNo = parseInt(
                        jedis.hget(Keys.SAVE, "playback_order_file_num"),
                        parseInt(jedis.hget(Keys.SAVE, "file_num"), 1));

                //读取总帧数（回放独立字段）
                int last = parseInt(jedis.hget(Keys.SAVE, "playback_last_view"), -1);
                if (last < 0) {
                    log.warn("Playback: 记录{}没有帧数据（last_view={}），停止回放", fileNo, last);
                    playback.set(false);
                    break;
                }

                //计算下一帧号（回放独立字段）
                int currentFrame = parseInt(jedis.hget(Keys.SAVE, "playback_order_view"), -1);
                int nextFrame = currentFrame + 1;

                //播放完自动停止
                if (nextFrame > last) {
                    log.info("Playback: 记录{}已播放到最后一帧（{}），自动停止", fileNo, last);
                    playback.set(false);
                    break;
                }

                //恢复该帧状态
                long t0 = System.nanoTime();
                boolean restored = restore(jedis, fileNo, nextFrame);
                if (!restored) {
                    log.warn("Playback: 记录{}的帧{}不存在，跳过", fileNo, nextFrame);
                }

                //再次确认回放状态
                if (!playback.get()) {
                    log.info("Playback interrupted before writing playback_order_view, " +
                            "discarding frame {}", nextFrame);
                    break;
                }

                //更新当前帧号（回放独立字段）
                jedis.hset(Keys.SAVE, "playback_order_view", String.valueOf(nextFrame));

                //根据速度计算延迟时间
                long delay = delayMillis(jedis.hget(Keys.SAVE, "triple_speed"));

                log.info("Playback restored frame {}/{} in {}ms, delay={}ms",
                        fileNo, nextFrame,
                        (System.nanoTime() - t0) / 1_000_000L, delay);

                Thread.sleep(delay);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                playback.set(false);
            } catch (Exception e) {
                log.error("Playback failed: {}", e.getMessage(), e);
                sleepQuietly(500L);  //出错后冷静500ms重试
            }
        }
        log.info("Playback loop ended");
    }
    //拍照
    private FrameSnapshot capture(Jedis jedis) {
        FrameSnapshot snapshot = new FrameSnapshot();

        // 记录地图尺寸
        snapshot.setWidth(Blackboard.mapWidth(jedis));
        snapshot.setHeight(Blackboard.mapHeight(jedis));

        //jedis.get()返回字节数组，Base64.getEncoder()编码为字符串
        snapshot.setMapViewBase64(base64(jedis.get(Keys.MAP_VIEW.getBytes())));

        //读取障碍物位图
        snapshot.setBlockViewBase64(base64(jedis.get(Keys.BLOCK_VIEW.getBytes())));

        //读取所有小车状态
        List<CarSnapshot> cars = new ArrayList<>();
        for (Integer id : Blackboard.existingCarIds(jedis)) {
            String key = Keys.carKey(id);           // "Cars:1", "Cars:2", ...
            cars.add(new CarSnapshot(key, jedis.hgetAll(key)));  // 读取小车所有字段
        }
        snapshot.setCars(cars);

        return snapshot;
    }
//恢复快照
    private boolean restore(Jedis jedis, int fileNo, int frame) {
        //读取快照JSON
        String json = jedis.hget(Keys.recorderFrameKey(fileNo, frame), "snapshot");
        if (json == null) {
            return false;  //该帧不存在
        }

        //反序列化
        FrameSnapshot snapshot = JsonSupport.fromJson(json, FrameSnapshot.class);

        //恢复地图尺寸
        jedis.set(Keys.PLAYBACK_PREFIX + Keys.MAP_WIDTH,
                String.valueOf(snapshot.getWidth()));
        jedis.set(Keys.PLAYBACK_PREFIX + Keys.MAP_HEIGHT,
                String.valueOf(snapshot.getHeight()));

        //恢复探索位图
        restoreBytes(jedis, Keys.PLAYBACK_PREFIX + Keys.MAP_VIEW,
                snapshot.getMapViewBase64());

        //恢复障碍物位图
        restoreBytes(jedis, Keys.PLAYBACK_PREFIX + Keys.BLOCK_VIEW,
                snapshot.getBlockViewBase64());

        //恢复小车数据
        for (String carKey : scan(jedis, Keys.PLAYBACK_PREFIX + Keys.CARS_PREFIX + "*")) {
            jedis.del(carKey);
        }
        for (CarSnapshot car : snapshot.getCars()) {
            if (car.getFields() != null && !car.getFields().isEmpty()) {
                jedis.hmset(Keys.PLAYBACK_PREFIX + car.getKey(), car.getFields());
            }
        }

        return true;
    }


    private void deleteOldFrames(Jedis jedis, int fileNo) {
        for (String key : scan(jedis, Keys.RECORDER_FRAME_PREFIX + fileNo + ":*")) {
            jedis.del(key);
        }
    }

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

    private String base64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void restoreBytes(Jedis jedis, String key, String base64) {
        if (base64 == null || base64.isEmpty()) {
            jedis.del(key);
            return;
        }
        jedis.set(key.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(base64));
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private long delayMillis(String speedValue) {
        double speed = 1.0d;
        if (speedValue != null && !speedValue.trim().isEmpty()) {
            speed = Double.parseDouble(speedValue.trim());
        }
        if (speed <= 0.0d) {
            speed = 1.0d;  //速度为0或负数时用默认值
        }
        return Math.max(100L, Math.round(500.0d / speed));
    }


    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            playback.set(false);
        }
    }
}
