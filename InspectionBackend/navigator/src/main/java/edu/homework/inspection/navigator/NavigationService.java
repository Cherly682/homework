package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Blackboard;
import edu.homework.inspection.common.CarState;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.NavigatorTask;
import edu.homework.inspection.common.Point;
import edu.homework.inspection.common.RouteAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class NavigationService {
    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);
    private final TargetSelector targetSelector = new TargetSelector();

    public void process(Jedis jedis, NavigatorTask task) {
        long t0 = System.nanoTime();
        int width = Blackboard.mapWidth(jedis);
        int height = Blackboard.mapHeight(jedis);
        int carNo = task.carNumber();
        log.info("Navigator processing car {} at ({},{}) map={}x{}", carNo, task.getStartx(), task.getStarty(), width, height);

        long tLoad = System.nanoTime();
        GridMap map = loadMap(jedis, width, height);
        log.debug("Navigator car {}: map loaded in {}ms", carNo, (System.nanoTime() - tLoad) / 1_000_000L);

        Point start = new Point(task.getStartx(), task.getStarty());
        long tMarkOthers = System.nanoTime();
        markOtherCarsAsBlocked(jedis, map, carNo);
        log.debug("Navigator car {}: dynamic obstacles added in {}us", carNo, NANOSECONDS.toMicros(System.nanoTime() - tMarkOthers));

        long tChoose = System.nanoTime();
        Point target = targetSelector.chooseTarget(start, map);
        log.info("Navigator car {}: target selected {} at distance {}", carNo, target, start.manhattan(target));
        log.debug("Navigator car {}: target selection took {}ms", carNo, (System.nanoTime() - tChoose) / 1_000_000L);

        RouteAlgorithm algorithm = RouteAlgorithm.fromRedis(jedis.get(Keys.ALGORITHM));
        PathFinder finder = createFinder(algorithm);
        long startedAt = System.nanoTime();
        List<Point> path = finder.findPath(start, target, map);
        long costMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        String queueKey = Keys.carTaskQueue(carNo);
        jedis.del(queueKey);
        if (path.isEmpty()) {
            Blackboard.setCarState(jedis, carNo, CarState.IDLE);
            log.warn("Navigator car {}: no path found {} -> {} using {} ({}ms)", carNo, start, target, algorithm, costMillis);
            return;
        }
        long tWritePath = System.nanoTime();
        // 批量写入路径，避免逐个 RPUSH 的 N 次 Redis 往返
        String[] pathValues = new String[path.size()];
        for (int i = 0; i < path.size(); i++) {
            pathValues[i] = path.get(i).toQueueValue();
        }
        jedis.rpush(queueKey, pathValues);
        // 合并 endx/endy 为一次 HMSET
        Map<String, String> endPoint = new HashMap<>();
        endPoint.put("endx", String.valueOf(target.getX()));
        endPoint.put("endy", String.valueOf(target.getY()));
        jedis.hmset(Keys.carKey(carNo), endPoint);
        jedis.rpush("Viewqueue:" + task.getCarId(), String.valueOf(costMillis));
        log.debug("Navigator car {}: path written to Redis in {}us", carNo, NANOSECONDS.toMicros(System.nanoTime() - tWritePath));

        long totalMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("Navigator car {} PLANNED: {} steps, algo={}, searchCost={}ms, total={}ms, {} -> {}",
                carNo, path.size(), algorithm, costMillis, totalMs, start, target);
    }

    private GridMap loadMap(Jedis jedis, int width, int height) {
        int area = width * height;
        boolean[] blocked = new boolean[area];
        boolean[] explored = new boolean[area];
        // 批量拉取整个位图字节数组，避免逐格 GETBIT 导致 O(N²) Redis 调用
        byte[] blockBytes = jedis.get(Keys.BLOCK_VIEW.getBytes());
        byte[] mapBytes = jedis.get(Keys.MAP_VIEW.getBytes());
        for (int i = 0; i < area; i++) {
            int byteIdx = i / 8;
            int bitIdx = 7 - (i % 8);
            blocked[i] = blockBytes != null && byteIdx < blockBytes.length && ((blockBytes[byteIdx] >> bitIdx) & 1) != 0;
            explored[i] = mapBytes != null && byteIdx < mapBytes.length && ((mapBytes[byteIdx] >> bitIdx) & 1) != 0;
        }
        return new GridMap(width, height, blocked, explored);
    }

    private void markOtherCarsAsBlocked(Jedis jedis, GridMap map, int currentCarNo) {
        List<Integer> allCars = Blackboard.existingCarIds(jedis);
        if (allCars.size() <= 1) return;  // 只有本车，无需标记

        // Pipeline 批量获取所有其他小车位置（替代逐个 HMGET，~399 次 → 1 次 sync）
        Pipeline pipeline = jedis.pipelined();
        Map<Integer, Response<java.util.List<String>>> responses = new LinkedHashMap<>();
        for (Integer otherCar : allCars) {
            if (otherCar == currentCarNo) continue;
            responses.put(otherCar, pipeline.hmget(Keys.carKey(otherCar), "x", "y"));
        }
        pipeline.sync();

        int count = 0;
        for (Map.Entry<Integer, Response<java.util.List<String>>> entry : responses.entrySet()) {
            java.util.List<String> values = entry.getValue().get();
            if (values.get(0) != null && values.get(1) != null) {
                map.setBlocked(new Point(Integer.parseInt(values.get(0)), Integer.parseInt(values.get(1))), true);
                count++;
            }
        }
        log.debug("Navigator marked {} other cars as blocked for car {}", count, currentCarNo);
    }

    private PathFinder createFinder(RouteAlgorithm algorithm) {
        log.debug("Navigator selected algorithm: {}", algorithm);
        switch (algorithm) {
            case BIDIRECTIONAL_ASTAR:
                return new BidirectionalAStarPathFinder();
            case DIJKSTRA:
                return new DijkstraPathFinder();
            case ASTAR:
            default:
                return new AStarPathFinder();
        }
    }
}
