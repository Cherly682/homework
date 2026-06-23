package Configurator.Model;

import Utils.RedisConnect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


public class MapModel {
    private static final Logger log = LoggerFactory.getLogger(MapModel.class);

    // ==================== Redis 键名常量 ====================
    private Jedis jedis;
    private final String MAPVIEW_KEY = "MapView";
    private final String CARS_KEY_PREFIX = "Cars:";
    private final String BLOCKVIEW_KEY_ = "BlockView";
    private final String ALL_BLOCKVIEW_KEY = "blockview";
    private final String ALGORITHM_KEY = "Algorithm";
    private final String TASK_KEY_ = "_task_queue";


    private final String ASTAR_VALUE = "0";
    private final String ASTAR2_VALUE = "1";
    private final String DIJKASTRA_VALUE = "2";

    // ==================== 地图属性 ====================
    private int mapSize;
    private int area;
    private int tileSize;
    private int blockCount = 0;
    private int carCount = 0;
    private int knownCarCount = 0;   // 从 Redis 实时同步的小车数量（用于状态标签）
    private int knownBlockCount = 0; // 从 Redis 实时同步的障碍物数量（用于状态标签）
    private final double MAX_BLOCKS_RATIO = 0.2;
    private Random random = new Random();
    private Set<Point> Blocks;


    public MapModel(int mapSize) {
        this.mapSize = mapSize;
        this.area = mapSize * mapSize;
        this.tileSize = 700 / mapSize;
        this.Blocks = new HashSet<>();

        jedis = RedisConnect.getConnected();
        initialize();
        log.info("MapModel created: {}x{} area={} maxCars={} maxBlocks={}",
                mapSize, mapSize, area, area, (int) (area * MAX_BLOCKS_RATIO));
    }


    private void initialize() {
        // 只写入配置信息，不删除 Redis 中已有的地图状态
        // 这样其他已登录的客户端不会受到新客户端登录的影响
        jedis.set(ALGORITHM_KEY, "0");
        jedis.set("map_height", "" + mapSize);
        jedis.set("map_width", "" + mapSize);

        // 从 Redis 加载当前已有的障碍物数据（如果有的话）
        getBlocksFromRedis();
        // 从 Redis 扫描当前已有的小车数量
        int existingCars = 0;
        for (String key : scanKeys(CARS_KEY_PREFIX + "*")) {
            existingCars++;
        }
        this.carCount = existingCars;
        log.info("MapModel initialized: {}x{}, loaded {} existing cars, {} existing blocks",
                mapSize, mapSize, carCount, blockCount);
    }


    private List<String> scanKeys(String pattern) {
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

    // ==================== Getter ====================
    public int getArea() { return area; }
    public int getMapSize() { return mapSize; }
    public int getTileSize() { return tileSize; }
    public int getBlockCount() { return blockCount; }

    /**
     * 从 Redis 同步最新地图尺寸。
     * 当另一个配置员客户端修改地图大小时，
     * 本客户端的 MapView 调用此方法可自动更新缓存。
     * 返回 true 表示尺寸发生了变化。
     */
    public boolean syncMapSizeFromRedis() {
        String widthStr = jedis.get("map_width");
        if (widthStr != null && !widthStr.trim().isEmpty()) {
            int newSize = Integer.parseInt(widthStr.trim());
            if (newSize != this.mapSize) {
                log.info("MapModel: 地图尺寸从 {} 变更为 {} (来自Redis)", this.mapSize, newSize);
                this.mapSize = newSize;
                this.area = mapSize * mapSize;
                this.tileSize = 700 / mapSize;
                this.Blocks.clear();
                getBlocksFromRedis();
                return true;
            }
        }
        return false;
    }
    public int getCarCount() { return carCount; }
    public int getMaxCars() { return area; }
    public int getMaxBlocks() { return (int) (area * MAX_BLOCKS_RATIO); }
    public void setKnownCarCount(int count) { this.knownCarCount = count; }
    public int getKnownCarCount() { return knownCarCount; }
    public void setKnownBlockCount(int count) { this.knownBlockCount = count; }
    public int getKnownBlockCount() { return knownBlockCount; }


    public boolean isPositionExplored(int x, int y) {
        long index = (long) x * mapSize + y;
        return jedis.getbit(MAPVIEW_KEY, index);
    }

    public boolean isPositionBlocked(int x, int y) {
        boolean isBlocked = false;
        if (Blocks.contains(new Point(x, y))) {
            isBlocked = true;
        }
        return isBlocked;
    }


    public void addCar(int x, int y) {
        carCount++;
        if (carCount > area) { return; }

        // 不能放在障碍物上
        long index = (long) x * mapSize + y;
        if (jedis.getbit(ALL_BLOCKVIEW_KEY, index)) {
            log.warn("Car cannot be placed on blocked cell ({},{})", x, y);
            carCount--;
            return;
        }

        // 不能与其他小车重叠
        for (String key : scanKeys(CARS_KEY_PREFIX + "*")) {
            String cx = jedis.hget(key, "x");
            String cy = jedis.hget(key, "y");
            if (cx != null && cy != null && Integer.parseInt(cx) == x && Integer.parseInt(cy) == y) {
                log.warn("Car cannot be placed at occupied cell ({},{})", x, y);
                carCount--;
                return;
            }
        }

        // 写入 Redis
        Map<String, String> car = new HashMap<>();
        long posIndex = (long) x * mapSize + y;
        jedis.setbit(MAPVIEW_KEY, posIndex, true);  // 标记该格为"已探索"
        car.put("x", String.valueOf(x));
        car.put("y", String.valueOf(y));
        car.put("endx", String.valueOf(-1));   // -1 表示没有目标
        car.put("endy", String.valueOf(-1));
        car.put("state", String.valueOf(0));    // 0 = 空闲状态
        car.put("direction", "U");              // 默认朝上
        jedis.hmset(CARS_KEY_PREFIX + carCount, car);
        log.info("Car {} added at ({},{})", carCount, x, y);
    }


    public void autoAddCar(int nums) {
        while (nums > 0) {
            if (carCount >= area) { return; }
            int x = random.nextInt(mapSize);
            int y = random.nextInt(mapSize);
            // 跳过障碍物和已探索的位置
            while (isPositionBlocked(x, y) || isPositionExplored(x, y)) {
                x = random.nextInt(mapSize);
                y = random.nextInt(mapSize);
            }
            addCar(x, y);
            nums--;
        }
    }


    public void addBlock(int x, int y) {
        try {
            // 检查上限
            if (blockCount >= (int) (area * MAX_BLOCKS_RATIO)) {
                log.warn("Max block count reached: {}", blockCount);
                return;
            }

            long index = (long) x * mapSize + y;

            if (jedis.getbit(ALL_BLOCKVIEW_KEY, index)) {
                return;
            }
            // 同时写入 Redis 和本地缓存
            jedis.setbit(ALL_BLOCKVIEW_KEY, index, true);
            Blocks.add(new Point(x, y));
            blockCount++;

            boolean isSet = jedis.getbit(ALL_BLOCKVIEW_KEY, index);
            log.info("Block ({},{}) {} -> total={}", x, y, isSet ? "SET" : "FAILED", blockCount);
        } catch (Exception e) {
            log.error("Add block failed: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(null,
                    "Add block failed: " + e.getMessage(),
                    "Redis Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }


    public void autoAddBlock(int nums) {
        int maxTries = area * 10;  // 最大尝试次数
        while (nums > 0) {
            if (blockCount >= (int) (area * MAX_BLOCKS_RATIO)) { return; }
            int tries = 0;
            int x, y;
            long index;
            do {
                x = random.nextInt(mapSize);
                y = random.nextInt(mapSize);
                index = (long) x * mapSize + y;
                tries++;
            } while (tries < maxTries
                    && (jedis.getbit(ALL_BLOCKVIEW_KEY, index)
                    || isPositionExplored(x, y)
                    || isPositionOccupiedByCar(x, y)));
            if (tries >= maxTries) {
                log.warn("Cannot find free position for block");
                return;
            }
            addBlock(x, y);
            nums--;
        }
    }


    private boolean isPositionOccupiedByCar(int x, int y) {
        for (String key : scanKeys(CARS_KEY_PREFIX + "*")) {
            String cx = jedis.hget(key, "x");
            String cy = jedis.hget(key, "y");
            if (cx != null && cy != null && Integer.parseInt(cx) == x && Integer.parseInt(cy) == y) {
                return true;
            }
        }
        return false;
    }


    public void getBlocksFromRedis() {
        int cnt = 0;
        Blocks.clear();
        byte[] bitmap = jedis.get(ALL_BLOCKVIEW_KEY.getBytes());
        if (bitmap != null) {
            for (int i = 0; i < mapSize; i++) {
                for (int j = 0; j < mapSize; j++) {
                    long index = (long) i * mapSize + j;
                    int byteIdx = (int) (index / 8);
                    int bitIdx = (int) (7 - (index % 8));
                    if (byteIdx < bitmap.length && ((bitmap[byteIdx] >> bitIdx) & 1) == 1) {
                        Blocks.add(new Point(i, j));
                        cnt++;
                    }
                }
            }
        }
        log.info("Loaded {} blocks from Redis blockview bitmap", cnt);
        this.blockCount = cnt;  // 同步计数器
    }

    public void setAStarAlgorithm()      { jedis.set(ALGORITHM_KEY, ASTAR_VALUE); }
    public void setASTAR2Algorithm()     { jedis.set(ALGORITHM_KEY, ASTAR2_VALUE); }
    public void setDIJKASTRAAlgorithm()  { jedis.set(ALGORITHM_KEY, DIJKASTRA_VALUE); }
}
