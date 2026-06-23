package edu.homework.inspection.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class Blackboard {
    private static final Logger log = LoggerFactory.getLogger(Blackboard.class);

    private Blackboard() {
    }

    public static int mapWidth(Jedis jedis) {
        String value = jedis.get(Keys.MAP_WIDTH);
        return value == null ? 20 : Integer.parseInt(value);
    }

    public static int mapHeight(Jedis jedis) {
        String value = jedis.get(Keys.MAP_HEIGHT);
        return value == null ? mapWidth(jedis) : Integer.parseInt(value);
    }

    public static int mapArea(Jedis jedis) {
        return mapWidth(jedis) * mapHeight(jedis);
    }

    public static List<Integer> existingCarIds(Jedis jedis) {
        List<Integer> ids = new ArrayList<>();
        String cursor = "0";
        ScanParams params = new ScanParams().match(Keys.CARS_PREFIX + "*").count(20);
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            cursor = result.getCursor();
            for (String key : result.getResult()) {
                try {
                    ids.add(Integer.parseInt(key.substring(Keys.CARS_PREFIX.length())));
                } catch (RuntimeException ignored) {
                }
            }
        } while (!"0".equals(cursor));
        Collections.sort(ids);
        return ids;
    }

    public static boolean hasCar(Jedis jedis, int id) {
        return jedis.exists(Keys.carKey(id));
    }

    public static Point getCarPoint(Jedis jedis, int id) {
        String key = Keys.carKey(id);
        String x = jedis.hget(key, "x");
        String y = jedis.hget(key, "y");
        if (x == null || y == null) {
            return null;
        }
        return new Point(Integer.parseInt(x), Integer.parseInt(y));
    }

    public static void setCarPoint(Jedis jedis, int id, Point point, Direction direction) {
        long t0 = System.nanoTime();
        Map<String, String> values = new HashMap<>();
        values.put("x", String.valueOf(point.getX()));
        values.put("y", String.valueOf(point.getY()));
        values.put("direction", direction.name());
        jedis.hmset(Keys.carKey(id), values);
        log.debug("Blackboard setCarPoint car={} pos={} dir={} -> {}us",
                id, point, direction, NANOSECONDS.toMicros(System.nanoTime() - t0));
    }

    public static CarState getCarState(Jedis jedis, int id) {
        return CarState.fromCode(jedis.hget(Keys.carKey(id), "state"));
    }

    public static void setCarState(Jedis jedis, int id, CarState state) {
        long t0 = System.nanoTime();
        jedis.hset(Keys.carKey(id), "state", String.valueOf(state.code()));
        log.debug("Blackboard setCarState car={} {}->{}  {}us",
                id, jedis.hget(Keys.carKey(id), "state"), state, NANOSECONDS.toMicros(System.nanoTime() - t0));
    }

    public static boolean isBlocked(Jedis jedis, int width, Point point) {
        return jedis.getbit(Keys.BLOCK_VIEW, point.index(width));
    }

    public static boolean isExplored(Jedis jedis, int width, Point point) {
        return jedis.getbit(Keys.MAP_VIEW, point.index(width));
    }

    /**
     * 点亮小车周围 3×3 区域，带视线阻挡检测。
     * 对角格子只有当两侧正交邻格中至少一个非障碍时才点亮，
     * 防止"隔墙点亮"死区域。
     */
    public static void illuminate3x3(Jedis jedis, int width, int height, Point center) {
        int cx = center.getX();
        int cy = center.getY();

        // 先点亮正交方向（上下左右）
        illuminateIfOpen(jedis, width, height, cx, cy);           // 中心
        illuminateIfOpen(jedis, width, height, cx - 1, cy);       // 上
        illuminateIfOpen(jedis, width, height, cx + 1, cy);       // 下
        illuminateIfOpen(jedis, width, height, cx, cy - 1);       // 左
        illuminateIfOpen(jedis, width, height, cx, cy + 1);       // 右

        // 对角格：需要两侧正交邻格至少一者非障碍（有视线可达）
        // 左上 (-1,-1)：需要 左(0,-1) 或 上(-1,0) 非障碍
        if (passable(jedis, width, height, cx, cy - 1) || passable(jedis, width, height, cx - 1, cy))
            illuminateIfOpen(jedis, width, height, cx - 1, cy - 1);
        // 右上 (-1,+1)：需要 右(0,+1) 或 上(-1,0) 非障碍
        if (passable(jedis, width, height, cx, cy + 1) || passable(jedis, width, height, cx - 1, cy))
            illuminateIfOpen(jedis, width, height, cx - 1, cy + 1);
        // 左下 (+1,-1)：需要 左(0,-1) 或 下(+1,0) 非障碍
        if (passable(jedis, width, height, cx, cy - 1) || passable(jedis, width, height, cx + 1, cy))
            illuminateIfOpen(jedis, width, height, cx + 1, cy - 1);
        // 右下 (+1,+1)：需要 右(0,+1) 或 下(+1,0) 非障碍
        if (passable(jedis, width, height, cx, cy + 1) || passable(jedis, width, height, cx + 1, cy))
            illuminateIfOpen(jedis, width, height, cx + 1, cy + 1);
    }

    private static boolean inMap(int width, int height, int x, int y) {
        return x >= 0 && x < height && y >= 0 && y < width;
    }

    /** 检查格子在界内且非障碍 */
    private static boolean passable(Jedis jedis, int width, int height, int x, int y) {
        return inMap(width, height, x, y) && !isBlocked(jedis, width, new Point(x, y));
    }

    /** 如果格子在界内且非障碍，则点亮 */
    private static void illuminateIfOpen(Jedis jedis, int width, int height, int x, int y) {
        if (inMap(width, height, x, y) && !isBlocked(jedis, width, new Point(x, y))) {
            jedis.setbit(Keys.MAP_VIEW, new Point(x, y).index(width), true);
        }
    }

    public static double exploredRatio(Jedis jedis) {
        long t0 = System.nanoTime();
        int width = mapWidth(jedis);
        int height = mapHeight(jedis);
        // 批量拉取位图字节数组，避免逐格 GETBIT 导致 O(N²) Redis 调用
        byte[] blockBytes = jedis.get(Keys.BLOCK_VIEW.getBytes());
        byte[] mapBytes = jedis.get(Keys.MAP_VIEW.getBytes());
        long freeCells = 0;
        long exploredFreeCells = 0;
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                long idx = (long) x * width + y;
                int byteIdx = (int) (idx / 8);
                int bitIdx = 7 - (int) (idx % 8);
                boolean blocked = blockBytes != null && byteIdx < blockBytes.length && ((blockBytes[byteIdx] >> bitIdx) & 1) != 0;
                if (blocked) continue;
                freeCells++;
                boolean explored = mapBytes != null && byteIdx < mapBytes.length && ((mapBytes[byteIdx] >> bitIdx) & 1) != 0;
                if (explored) exploredFreeCells++;
            }
        }
        double ratio = freeCells == 0 ? 1.0 : exploredFreeCells / (double) freeCells;
        log.debug("Blackboard exploredRatio={:.2f}% ({}ms)",
                ratio * 100, (System.nanoTime() - t0) / 1_000_000L);
        return ratio;
    }

    /**
     * BFS 从小车位置出发，检查可达区域内所有非障碍格子是否均已探索。
     * 不可达区域（被障碍物包围的格子）不参与判断，避免因地图不连通导致巡检永不停止。
     *
     * @return true 表示所有可达格子均已探索（即巡检可结束）
     */
    public static boolean isFullyExplored(Jedis jedis) {
        int width = mapWidth(jedis);
        int height = mapHeight(jedis);
        // 批量加载位图到本地内存，避免 BFS 中逐格 GETBIT
        byte[] blockBytes = jedis.get(Keys.BLOCK_VIEW.getBytes());
        byte[] mapBytes = jedis.get(Keys.MAP_VIEW.getBytes());
        Set<Point> reachable = bfsReachableLocal(jedis, width, height, blockBytes);
        if (reachable.isEmpty()) {
            return exploredRatioLocal(width, height, blockBytes, mapBytes) >= 1.0d;
        }
        for (Point p : reachable) {
            long idx = (long) p.getX() * width + p.getY();
            int byteIdx = (int) (idx / 8);
            int bitIdx = 7 - (int) (idx % 8);
            boolean explored = mapBytes != null && byteIdx < mapBytes.length && ((mapBytes[byteIdx] >> bitIdx) & 1) != 0;
            if (!explored) return false;
        }
        return true;
    }

    /**
     * 检查是否存在小车无法到达的非障碍格子（被障碍物包围的区域）。
     * 应在 isFullyExplored() 返回 true 后调用，用于判断是否需要提示"部分地块无法进入"。
     */
    public static boolean hasUnreachableFreeCells(Jedis jedis) {
        int width = mapWidth(jedis);
        int height = mapHeight(jedis);
        byte[] blockBytes = jedis.get(Keys.BLOCK_VIEW.getBytes());
        Set<Point> reachable = bfsReachableLocal(jedis, width, height, blockBytes);
        if (reachable.isEmpty()) return false;
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                long idx = (long) x * width + y;
                int byteIdx = (int) (idx / 8);
                int bitIdx = 7 - (int) (idx % 8);
                boolean blocked = blockBytes != null && byteIdx < blockBytes.length && ((blockBytes[byteIdx] >> bitIdx) & 1) != 0;
                if (!blocked && !reachable.contains(new Point(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * BFS 从所有小车位置出发，返回可达的非障碍格子集合（使用本地 blockBytes，零 Redis 调用）。
     */
    private static Set<Point> bfsReachableLocal(Jedis jedis, int width, int height, byte[] blockBytes) {
        Set<Point> visited = new HashSet<>();
        Queue<Point> queue = new ArrayDeque<>();

        for (Integer carId : existingCarIds(jedis)) {
            Point pos = getCarPoint(jedis, carId);
            if (pos != null) {
                long idx = (long) pos.getX() * width + pos.getY();
                int byteIdx = (int) (idx / 8);
                int bitIdx = 7 - (int) (idx % 8);
                boolean blocked = blockBytes != null && byteIdx < blockBytes.length && ((blockBytes[byteIdx] >> bitIdx) & 1) != 0;
                if (!blocked && visited.add(pos)) {
                    queue.add(pos);
                }
            }
        }

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            Point cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur.getX() + d[0];
                int ny = cur.getY() + d[1];
                if (nx < 0 || nx >= height || ny < 0 || ny >= width) continue;
                long idx = (long) nx * width + ny;
                int byteIdx = (int) (idx / 8);
                int bitIdx = 7 - (int) (idx % 8);
                boolean blocked = blockBytes != null && byteIdx < blockBytes.length && ((blockBytes[byteIdx] >> bitIdx) & 1) != 0;
                if (!blocked) {
                    Point next = new Point(nx, ny);
                    if (visited.add(next)) queue.add(next);
                }
            }
        }
        return visited;
    }

    /** 用于 isFullyExplored 回退判断的本地版本，使用已加载的字节数组，零 Redis 调用。 */
    private static double exploredRatioLocal(int width, int height, byte[] blockBytes, byte[] mapBytes) {
        long free = 0, exploredFree = 0;
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                long idx = (long) x * width + y;
                int bi = (int) (idx / 8);
                int bti = 7 - (int) (idx % 8);
                if (blockBytes != null && bi < blockBytes.length && ((blockBytes[bi] >> bti) & 1) != 0) continue;
                free++;
                if (mapBytes != null && bi < mapBytes.length && ((mapBytes[bi] >> bti) & 1) != 0) exploredFree++;
            }
        }
        return free == 0 ? 1.0 : exploredFree / (double) free;
    }

    public static void resetExplorationRun(Jedis jedis) {
        int width = mapWidth(jedis);
        int height = mapHeight(jedis);
        jedis.del(Keys.MAP_VIEW);
        jedis.del(Keys.NAVIGATOR_STATUS);
        for (Integer carId : existingCarIds(jedis)) {
            jedis.del(Keys.carTaskQueue(carId));
            Map<String, String> values = new HashMap<>();
            values.put("state", String.valueOf(CarState.IDLE.code()));
            values.put("endx", "-1");
            values.put("endy", "-1");
            jedis.hmset(Keys.carKey(carId), values);

            Point point = getCarPoint(jedis, carId);
            if (point != null && !isBlocked(jedis, width, point)) {
                illuminate3x3(jedis, width, height, point);
            }
        }
        log.info("Blackboard exploration run reset: {} cars, map {}x{}", existingCarIds(jedis).size(), width, height);
    }

    public static byte[] mapViewSnapshot(Jedis jedis) {
        byte[] bytes = jedis.get(Keys.MAP_VIEW.getBytes());
        return bytes == null ? new byte[0] : bytes;
    }

    public static void restoreMapView(Jedis jedis, byte[] value) {
        if (value == null || value.length == 0) {
            jedis.del(Keys.MAP_VIEW);
        } else {
            jedis.set(Keys.MAP_VIEW.getBytes(), value);
        }
    }
}
