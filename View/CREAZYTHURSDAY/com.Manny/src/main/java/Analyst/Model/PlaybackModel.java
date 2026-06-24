package Analyst.Model;
import Utils.RedisConnect;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.text.SimpleDateFormat;
import java.util.*;


public class PlaybackModel {
    private static final Logger log = LoggerFactory.getLogger(PlaybackModel.class);
    private Jedis jedis;

    /** 分析员回放专用前缀，隔离配置员的实时地图数据 */
    public static final String PLAYBACK_PREFIX = "playback:";

    private final String BLOCKVIEW = "blockview";
    private final String CARS_KEY = "Cars:";
    private int mapSize;
    private int tileSize;
    private int area;
    private boolean[][] blocks;   // 本地障碍物缓存
    private boolean playbackMode = false; // 播放模式守卫：true=正在回放，false=不读取实时 Redis 数据
    private final String SAVE_KEY = "Save";
    private final String ORDER_FILE_NUM_KEY = "order_file_num";
    private final String SPEED_KEY = "triple_speed";
    private final ObjectMapper objectMapper = new ObjectMapper();  // JSON 解析器

    /** 获取当前应使用的 Redis key（回放模式下自动加前缀） */
    private String pk(String baseKey) {
        return playbackMode ? PLAYBACK_PREFIX + baseKey : baseKey;
    }


    public PlaybackModel(Jedis jedis) {
        this.jedis = jedis;
        // 从 Redis 加载当前地图尺寸（只读，不写入任何数据）
        // 分析员使用独立的 playback: 命名空间，不会影响配置员的实时数据
        initFromRedis();
    }

    /**
     * 确保 Redis 连接可用。如果当前连接已断开，自动从连接池获取新连接。
     */
    private void ensureConnection() {
        try {
            if (jedis == null || !jedis.isConnected() || !"PONG".equals(jedis.ping())) {
                renewConnection();
            }
        } catch (Exception e) {
            log.warn("PlaybackModel Redis connection check failed, renewing: {}", e.getMessage());
            renewConnection();
        }
    }

    private void renewConnection() {
        try {
            if (jedis != null) {
                try { jedis.close(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        jedis = RedisConnect.getConnected();
        log.info("PlaybackModel: Redis connection renewed");
    }


    private void initFromRedis() {
        // 只读：不向共享 Redis 写入默认值，避免覆盖配置员的地图设置
        String widthStr = jedis.get("map_width");
        if (widthStr == null || widthStr.trim().isEmpty()) {
            widthStr = "20";
        }
        this.mapSize = Integer.parseInt(widthStr.trim());
        this.area = mapSize * mapSize;
        this.tileSize = 700 / mapSize;
        blocks = new boolean[mapSize][mapSize];
        // 初始化时从共享 Redis 加载障碍物（回放开始后会切换到 playback: 命名空间）
        getBlocks();
        log.info("PlaybackModel初始化: {}x{} area={} (只读加载)", mapSize, mapSize, area);
    }


    public void resetMap() {
        ensureConnection();
        // 退出播放模式，PlaybackMapView 停止读取 Redis
        playbackMode = false;
        // 清理回放命名空间的残留数据（不影响配置员的实时数据）
        jedis.del(PLAYBACK_PREFIX + "MapView");
        jedis.del(PLAYBACK_PREFIX + "blockview");
        jedis.del(PLAYBACK_PREFIX + "map_width");
        jedis.del(PLAYBACK_PREFIX + "map_height");
        for (String key : scanKeys(PLAYBACK_PREFIX + CARS_KEY + "*")) {
            jedis.del(key);
        }
        // 重置回放元数据
        jedis.hdel(SAVE_KEY, "playback_order_file_num", "playback_order_view", "start_view");
        initFromRedis();
        log.info("PlaybackModel: 地图已重置（已清理回放命名空间）");
    }

    public int getMapSize() { return mapSize; }
    public int getTileSize() { return tileSize; }
    public int getArea() { return area; }

    public boolean syncMapSizeFromRedis() {
        ensureConnection();
        // 回放模式下读取独立的 playback: 命名空间，避免受配置员改地图影响
        String widthStr = jedis.get(PLAYBACK_PREFIX + "map_width");
        if (widthStr == null || widthStr.trim().isEmpty()) {
            return false;
        }
        int newSize = Integer.parseInt(widthStr.trim());
        if (newSize != this.mapSize) {
            log.info("PlaybackModel: 地图尺寸从 {} 变更为 {} (回放命名空间)", this.mapSize, newSize);
            this.mapSize = newSize;
            this.area = mapSize * mapSize;
            this.tileSize = 700 / mapSize;
            this.blocks = new boolean[mapSize][mapSize];
            getBlocks();
            return true;
        }
        return false;
    }


    private void getBlocks() {
        String blockKey = pk(BLOCKVIEW);
        for (int i = 0; i < mapSize; i++) {
            for (int j = 0; j < mapSize; j++) {
                long index = (long) i * mapSize + j;
                blocks[i][j] = jedis.getbit(blockKey, index);
            }
        }
    }


    private void resetCars() {
        // 回放时只清 playback: 命名空间的小车，不影响配置员的实时小车
        String carPattern = pk(CARS_KEY) + "*";
        for (String key : scanKeys(carPattern)) {
            jedis.del(key);
        }
    }


    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        redis.clients.jedis.ScanParams params =
                new redis.clients.jedis.ScanParams().match(pattern).count(100);
        do {
            redis.clients.jedis.ScanResult<String> result = jedis.scan(cursor, params);
            keys.addAll(result.getResult());
            cursor = result.getCursor();
        } while (!"0".equals(cursor));
        return keys;
    }

    // ==================== 回放控制 ====================

    public void setExplorationChoice(String choice) {
        jedis.hset(SAVE_KEY, "playback_order_file_num", choice);
        log.debug("PlaybackModel: exploration choice set to {}", choice);
    }

    public int getCurrentFileNo() {
        String val = jedis.hget(SAVE_KEY, "playback_order_file_num");
        if (val != null && !val.trim().isEmpty()) {
            return Integer.parseInt(val.trim());
        }
        val = jedis.hget(SAVE_KEY, "file_num");
        return val != null ? Integer.parseInt(val.trim()) : 0;
    }

    public void setSpeedChoice(String choice) {
        jedis.hset(SAVE_KEY, SPEED_KEY, choice);
        log.debug("PlaybackModel: speed set to {}", choice);
    }

    public void setOrderView(int index) {
        jedis.hset(SAVE_KEY, "playback_order_view", String.valueOf(index));
        log.debug("PlaybackModel: playback_order_view set to {}", index);
    }

    public int getOrderView() {
        String val = jedis.hget(SAVE_KEY, "playback_order_view");
        return val != null ? Integer.parseInt(val) : 0;
    }

    public int getLastView() {
        String val = jedis.hget(SAVE_KEY, "playback_last_view");
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 扫描 Redis 中指定记录的所有帧，更新 last_view 为该记录的总帧数 - 1。
     * 帧键格式：Record:<fileNo>:<frameNo>
     * 切换记录或刷新时调用，确保进度条范围正确。
     */
    public void updateLastViewForFile(int fileNo) {
        int maxFrame = -1;
        String pattern = "Record:" + fileNo + ":*";
        for (String key : scanKeys(pattern)) {
            try {
                String[] parts = key.split(":");
                if (parts.length >= 3) {
                    int frame = Integer.parseInt(parts[2]);
                    if (frame > maxFrame) maxFrame = frame;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (maxFrame >= 0) {
            jedis.hset(SAVE_KEY, "playback_last_view", String.valueOf(maxFrame));
            log.info("PlaybackModel: 记录{} 总帧数已更新 -> playback_last_view={}", fileNo, maxFrame);
        } else {
            jedis.hset(SAVE_KEY, "playback_last_view", "0");
            log.warn("PlaybackModel: 记录{} 无帧数据，playback_last_view 设为 0", fileNo);
        }
    }

    public double getSpeed() {
        String val = jedis.hget(SAVE_KEY, SPEED_KEY);
        if (val == null || val.trim().isEmpty()) return 1.0;
        return Double.parseDouble(val.trim());
    }

    public String getRecordTimestamp(int fileNo) {
        String ts = jedis.hget(SAVE_KEY, "created_at_" + fileNo);
        if (ts == null || ts.trim().isEmpty()) return "未知时间";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(Long.parseLong(ts)));
    }


    public boolean restoreFrame(int fileNo, int frame) {
        ensureConnection();
        // 进入播放模式：告诉 PlaybackMapView 可以开始从 Redis 读取帧数据
        playbackMode = true;
        try {
            //读取帧数据
            String frameKey = "Record:" + fileNo + ":" + frame;
            Map<String, String> frameData = jedis.hgetAll(frameKey);
            if (frameData == null || frameData.isEmpty() || !frameData.containsKey("snapshot")) {
                log.debug("PlaybackModel: 帧 {} 不存在", frameKey);
                return false;
            }

            // 解析快照 JSON
            String json = frameData.get("snapshot");
            Map<String, Object> snapshot = objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() {});
            if (snapshot == null) {
                return false;
            }

            //读取录制时的地图尺寸
            Object widthObj = snapshot.get("width");
            Object heightObj = snapshot.get("height");
            int recordedWidth = widthObj instanceof Number
                    ? ((Number) widthObj).intValue()
                    : Integer.parseInt(String.valueOf(widthObj));
            int recordedHeight = heightObj instanceof Number
                    ? ((Number) heightObj).intValue()
                    : Integer.parseInt(String.valueOf(heightObj));

            //更新 Redis 地图尺寸（写入回放独立命名空间，不影响配置员）
            jedis.set(PLAYBACK_PREFIX + "map_width", String.valueOf(recordedWidth));
            jedis.set(PLAYBACK_PREFIX + "map_height", String.valueOf(recordedHeight));

            // 同步本地缓存（尺寸变化时重新分配数组）
            boolean sizeChanged = (recordedWidth != this.mapSize || recordedHeight != this.mapSize);
            if (sizeChanged || blocks == null || blocks.length != recordedWidth) {
                this.mapSize = Math.max(recordedWidth, recordedHeight);
                this.area = this.mapSize * this.mapSize;
                this.tileSize = 700 / this.mapSize;
                blocks = new boolean[this.mapSize][this.mapSize];
            }

            //写入探索位图（Base64 编码 → 解码 → 二进制写入 Redis 回放命名空间）
            String mapViewB64 = (String) snapshot.get("mapViewBase64");
            if (mapViewB64 != null && !mapViewB64.isEmpty()) {
                jedis.set((PLAYBACK_PREFIX + "MapView").getBytes(),
                        Base64.getDecoder().decode(mapViewB64));
            }

            // 写入障碍物位图（回放命名空间）
            String blockViewB64 = (String) snapshot.get("blockViewBase64");
            if (blockViewB64 != null && !blockViewB64.isEmpty()) {
                jedis.set((PLAYBACK_PREFIX + "blockview").getBytes(),
                        Base64.getDecoder().decode(blockViewB64));
            }

            // 恢复小车数据（写入回放命名空间，原键名 Cars:N 改为 playback:Cars:N）
            resetCars();
            List<Map<String, Object>> cars = (List<Map<String, Object>>) snapshot.get("cars");
            if (cars != null) {
                for (Map<String, Object> car : cars) {
                    String key = (String) car.get("key");
                    Map<String, Object> fields = (Map<String, Object>) car.get("fields");
                    if (key != null && fields != null && !fields.isEmpty()) {
                        Map<String, String> strFields = new HashMap<>();
                        for (Map.Entry<String, Object> entry : fields.entrySet()) {
                            strFields.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                        // 加前缀写入：Cars:1 → playback:Cars:1
                        jedis.hmset(PLAYBACK_PREFIX + key, strFields);
                    }
                }
            }

            //更新当前帧标记（回放独立字段）
            jedis.hset(SAVE_KEY, "playback_order_view", String.valueOf(frame));

            //刷新本地障碍物缓存
            getBlocks();

            log.debug("PlaybackModel: 已恢复记录{}的帧{} ({}x{})",
                    fileNo, frame, recordedWidth, recordedHeight);
            return true;
        } catch (Exception e) {
            log.error("PlaybackModel: 恢复记录{}的帧{}失败: {}",
                    fileNo, frame, e.getMessage(), e);
            return false;
        }
    }


    public void loadFirstFrame(int fileNo) {
        ensureConnection();
        restoreFrame(fileNo, 0);
    }

    public boolean isPositionBlocked(int x, int y) {
        if (x < 0 || x >= mapSize || y < 0 || y >= mapSize) return true;
        return blocks[x][y];
    }

    /** 分析员是否处于播放模式。非播放模式时 PlaybackMapView 不读取 Redis 实时数据。 */
    public boolean isPlaybackMode() { return playbackMode; }
}
