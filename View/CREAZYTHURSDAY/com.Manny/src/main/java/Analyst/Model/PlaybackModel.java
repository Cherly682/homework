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


    public PlaybackModel(Jedis jedis) {
        this.jedis = jedis;
        // 从 Redis 加载当前地图状态（只读，不删除任何数据）
        // 这样分析员登录时不会破坏配置员正在进行的巡检

        // 确保地图尺寸存在（没有则设默认值 20）
        String widthStr = jedis.get("map_width");
        if (widthStr == null || widthStr.trim().isEmpty()) {
            jedis.set("map_width", "20");
            jedis.set("map_height", "20");
        }
        initFromRedis();
    }


    private void initFromRedis() {
        String widthStr = jedis.get("map_width");
        if (widthStr == null || widthStr.trim().isEmpty()) {
            widthStr = "20";
            jedis.set("map_width", "20");
            jedis.set("map_height", "20");
        }
        this.mapSize = Integer.parseInt(widthStr);
        this.area = mapSize * mapSize;
        this.tileSize = 700 / mapSize;
        blocks = new boolean[mapSize][mapSize];
        // 只从 Redis 加载障碍物，不删除小车和地图数据
        getBlocks();
        log.info("PlaybackModel初始化: {}x{} area={} (从Redis加载已有数据)", mapSize, mapSize, area);
    }


    public void resetMap() {
        // 退出播放模式，PlaybackMapView 停止读取 Redis
        playbackMode = false;
        jedis.del("MapView");
        jedis.del("blockview");
        resetCars();
        // 重置当前帧标记，但保留 last_view
        jedis.hdel(SAVE_KEY, ORDER_FILE_NUM_KEY, "order_view", "start_view");
        initFromRedis();
        log.info("PlaybackModel: 地图已重置");
    }

    public int getMapSize() { return mapSize; }
    public int getTileSize() { return tileSize; }
    public int getArea() { return area; }

    /**
     * 从 Redis 同步最新地图尺寸。
     * 当配置员在其他客户端修改地图大小时，
     * 调用此方法可自动更新本地缓存的 mapSize/tileSize/area/blocks。
     * 返回 true 表示尺寸发生了变化。
     */
    public boolean syncMapSizeFromRedis() {
        String widthStr = jedis.get("map_width");
        if (widthStr != null && !widthStr.trim().isEmpty()) {
            int newSize = Integer.parseInt(widthStr.trim());
            if (newSize != this.mapSize) {
                log.info("PlaybackModel: 地图尺寸从 {} 变更为 {}", this.mapSize, newSize);
                this.mapSize = newSize;
                this.area = mapSize * mapSize;
                this.tileSize = 700 / mapSize;
                this.blocks = new boolean[mapSize][mapSize];
                getBlocks();
                return true;
            }
        }
        return false;
    }


    private void getBlocks() {
        for (int i = 0; i < mapSize; i++) {
            for (int j = 0; j < mapSize; j++) {
                long index = (long) i * mapSize + j;
                blocks[i][j] = jedis.getbit(BLOCKVIEW, index);
            }
        }
    }


    private void resetCars() {
        for (String key : scanKeys(CARS_KEY + "*")) {
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
        jedis.hset(SAVE_KEY, ORDER_FILE_NUM_KEY, choice);
        log.debug("PlaybackModel: exploration choice set to {}", choice);
    }

    public int getCurrentFileNo() {
        String val = jedis.hget(SAVE_KEY, ORDER_FILE_NUM_KEY);
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
        jedis.hset(SAVE_KEY, "order_view", String.valueOf(index));
        log.debug("PlaybackModel: order_view set to {}", index);
    }

    public int getOrderView() {
        String val = jedis.hget(SAVE_KEY, "order_view");
        return val != null ? Integer.parseInt(val) : 0;
    }

    public int getLastView() {
        String val = jedis.hget(SAVE_KEY, "last_view");
        return val != null ? Integer.parseInt(val) : 0;
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

            //更新 Redis 地图尺寸
            jedis.set("map_width", String.valueOf(recordedWidth));
            jedis.set("map_height", String.valueOf(recordedHeight));

            // 同步本地缓存（尺寸变化时重新分配数组）
            boolean sizeChanged = (recordedWidth != this.mapSize || recordedHeight != this.mapSize);
            if (sizeChanged || blocks == null || blocks.length != recordedWidth) {
                this.mapSize = Math.max(recordedWidth, recordedHeight);
                this.area = this.mapSize * this.mapSize;
                this.tileSize = 700 / this.mapSize;
                blocks = new boolean[this.mapSize][this.mapSize];
            }

            //写入探索位图（Base64 编码 → 解码 → 二进制写入 Redis）
            String mapViewB64 = (String) snapshot.get("mapViewBase64");
            if (mapViewB64 != null && !mapViewB64.isEmpty()) {
                jedis.set("MapView".getBytes(), Base64.getDecoder().decode(mapViewB64));
            }

            // 写入障碍物位图
            String blockViewB64 = (String) snapshot.get("blockViewBase64");
            if (blockViewB64 != null && !blockViewB64.isEmpty()) {
                jedis.set("blockview".getBytes(), Base64.getDecoder().decode(blockViewB64));
            }

            // 恢复小车数据
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
                        jedis.hmset(key, strFields);
                    }
                }
            }

            //更新当前帧标记
            jedis.hset(SAVE_KEY, "order_view", String.valueOf(frame));

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
        restoreFrame(fileNo, 0);
    }

    public boolean isPositionBlocked(int x, int y) {
        if (x < 0 || x >= mapSize || y < 0 || y >= mapSize) return true;
        return blocks[x][y];
    }

    /** 分析员是否处于播放模式。非播放模式时 PlaybackMapView 不读取 Redis 实时数据。 */
    public boolean isPlaybackMode() { return playbackMode; }
}
