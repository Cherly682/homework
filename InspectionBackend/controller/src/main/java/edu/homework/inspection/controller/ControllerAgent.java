package edu.homework.inspection.controller;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.AppConfig;
import edu.homework.inspection.common.Blackboard;
import edu.homework.inspection.common.CarState;
import edu.homework.inspection.common.JsonSupport;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.NavigatorTask;
import edu.homework.inspection.common.Point;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.RedisProvider;
import edu.homework.inspection.common.SystemQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ControllerAgent implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ControllerAgent.class);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong tickCount = new AtomicLong(0);
    private Thread tickThread;
    private Channel tickChannel;

    public synchronized void startWork() {
        if (active.get()) {
            log.warn("Controller already running, ignoring startWork");
            return;
        }
        long t0 = System.nanoTime();
        try (Jedis jedis = RedisProvider.get()) {
            if (Blackboard.isFullyExplored(jedis)) {
                Blackboard.resetExplorationRun(jedis);
                log.info("Previous exploration was complete; runtime state reset for a new run");
            }
        }
        // 创建持久 Channel，只声明一次拓扑（避免每 tick ~411 次 AMQP 往返）
        try {
            tickChannel = RabbitProvider.channel();
            RabbitProvider.declareTopology(tickChannel);
            log.info("Controller tick channel created, topology declared once");
        } catch (Exception e) {
            log.error("Failed to create tick channel: {}", e.getMessage(), e);
            return;
        }
        active.set(true);
        tickThread = new Thread(this::tickLoop, "controller-tick-loop");
        tickThread.setDaemon(false);
        tickThread.start();
        log.info("Controller started (tickInterval={}ms, init cost={}ms)",
                AppConfig.tickMillis(), (System.nanoTime() - t0) / 1_000_000L);
    }

    public synchronized void stopWork() {
        active.set(false);
        if (tickThread != null) {
            tickThread.interrupt();
        }
        closeChannel();
        log.info("Controller stopped at tick {}", tickCount.get());
    }

    private void closeChannel() {
        try {
            if (tickChannel != null && tickChannel.isOpen()) {
                tickChannel.close();
            }
        } catch (Exception e) {
            log.warn("Error closing tick channel: {}", e.getMessage());
        }
    }

    private void tickLoop() {
        long tickIntervalMs = AppConfig.tickMillis();
        while (active.get()) {
            long tickStart = System.nanoTime();
            try {
                tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                active.set(false);
            } catch (Exception e) {
                log.error("Controller tick failed: {}", e.getMessage(), e);
            }
            long elapsed = (System.nanoTime() - tickStart) / 1_000_000L;
            long sleepMs = Math.max(0, tickIntervalMs - elapsed);
            if (elapsed > tickIntervalMs) {
                log.info("Tick {} took {}ms (exceeded interval)", tickCount.get(), elapsed);
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                active.set(false);
            }
        }
    }

    private void tick() throws Exception {
        long tickNo = tickCount.incrementAndGet();
        long t0 = System.nanoTime();
        try (Jedis jedis = RedisProvider.get()) {
            // Step 1: 扫描所有小车（一次 SCAN，COUNT=500）
            List<Integer> cars = Blackboard.existingCarIds(jedis);
            if (cars.isEmpty()) {
                log.debug("Tick {}: no cars found, skipping", tickNo);
                return;
            }

            // Step 2: 一次性读取 map 元数据（后续步骤复用，避免重复 GET）
            int width = Blackboard.mapWidth(jedis);
            int height = Blackboard.mapHeight(jedis);
            byte[] blockBytes = jedis.get(Keys.BLOCK_VIEW.getBytes());
            byte[] mapBytes = jedis.get(Keys.MAP_VIEW.getBytes());

            // Step 3: 使用预加载数据检查是否探索完成（不再内部重复 SCAN + GET）
            if (Blackboard.isFullyExplored(jedis, cars, width, height, blockBytes, mapBytes)) {
                log.info("Tick {}: exploration complete", tickNo);
                complete();
                return;
            }

            // Step 4: 使用预加载位图计算探索率（纯本地，零 Redis 调用）
            double exploredRate = Blackboard.exploredRatio(width, height, blockBytes, mapBytes);

            // Step 5: Pipeline 批量获取所有小车的状态和队列长度
            //         将原来 800 次独立 HGET+LLEN 往返合并为 1 次 Pipeline sync
            Pipeline pipeline = jedis.pipelined();
            Map<Integer, Response<String>> stateResponses = new LinkedHashMap<>();
            Map<Integer, Response<Long>> llenResponses = new LinkedHashMap<>();
            for (Integer carId : cars) {
                stateResponses.put(carId, pipeline.hget(Keys.carKey(carId), "state"));
                llenResponses.put(carId, pipeline.llen(Keys.carTaskQueue(carId)));
            }
            pipeline.sync();

            // Step 6: 处理 Pipeline 结果，分派空闲小车
            int idleCount = 0;
            int assignedCount = 0;
            for (Integer carId : cars) {
                CarState state = CarState.fromCode(stateResponses.get(carId).get());
                long queueLen = llenResponses.get(carId).get();
                if (state == CarState.IDLE && queueLen == 0) {
                    dispatchNavigatorTask(jedis, carId);
                    assignedCount++;
                }
                if (state == CarState.IDLE) {
                    idleCount++;
                }
            }

            // Step 7: 广播移动命令（复用持久 Channel）
            tickChannel.basicPublish(SystemQueues.EXCHANGE_CAR_BROADCAST, "", null, "1".getBytes(StandardCharsets.UTF_8));
            long tickMs = (System.nanoTime() - t0) / 1_000_000L;
            log.info("Tick {}: {} cars, explored={}%, idle={}, assigned={}, cost={}ms",
                    tickNo, cars.size(), String.format("%.1f", exploredRate * 100), idleCount, assignedCount, tickMs);
        }
    }

    private void dispatchNavigatorTask(Jedis jedis, int carId) throws Exception {
        Point point = Blackboard.getCarPoint(jedis, carId);
        if (point == null) {
            log.debug("dispatchNavigatorTask: car {} has no position, skip", carId);
            return;
        }
        int navigatorId = firstAvailableNavigator(jedis);
        if (navigatorId == 0) {
            log.debug("dispatchNavigatorTask: no free navigator, skip car {}", carId);
            return;
        }

        NavigatorTask task = new NavigatorTask(Keys.carKey(carId), point.getX(), point.getY());
        tickChannel.basicPublish(
                SystemQueues.EXCHANGE_NAVIGATOR,
                SystemQueues.navigatorRoutingKey(navigatorId),
                null,
                JsonSupport.toJson(task).getBytes(StandardCharsets.UTF_8)
        );
        Blackboard.setCarState(jedis, carId, CarState.ASSIGNING);
        log.info("Tick {}: car {} at {} dispatched to navigator {}", tickCount.get(), carId, point, navigatorId);
    }

    private int firstAvailableNavigator(Jedis jedis) {
        // 一次性 HMGET 读取全部 navigator 状态（替代逐个 HGET）
        String[] fields = new String[AppConfig.navigatorWorkerCount()];
        for (int i = 0; i < AppConfig.navigatorWorkerCount(); i++) {
            fields[i] = "nav_" + (i + 1) + ":working";
        }
        java.util.List<String> values = jedis.hmget(Keys.NAVIGATOR_STATUS, fields);
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value == null || !"true".equalsIgnoreCase(value)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void complete() throws Exception {
        log.info("Exploration completed; stopping backend at tick {}", tickCount.get());
        active.set(false);
        try (Jedis jedis = RedisProvider.get()) {
            String result = Blackboard.hasUnreachableFreeCells(jedis) ? "partial" : "complete";
            jedis.set("exploration_result", result);
            log.info("Exploration result: {}", result);
        }
        tickChannel.basicPublish(SystemQueues.EXCHANGE_SAVE, "", null, "0".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        stopWork();
    }
}
