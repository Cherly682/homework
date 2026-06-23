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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ControllerAgent implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ControllerAgent.class);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong tickCount = new AtomicLong(0);
    private Thread tickThread;

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
        log.info("Controller stopped at tick {}", tickCount.get());
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
        try (Jedis jedis = RedisProvider.get(); Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
            List<Integer> cars = Blackboard.existingCarIds(jedis);
            if (cars.isEmpty()) {
                log.debug("Tick {}: no cars found, skipping", tickNo);
                return;
            }

            if (Blackboard.isFullyExplored(jedis)) {
                log.info("Tick {}: exploration complete", tickNo);
                complete(channel);
                return;
            }

            double exploredRate = Blackboard.exploredRatio(jedis);
            int idleCount = 0;
            int assignedCount = 0;
            for (Integer carId : cars) {
                CarState state = Blackboard.getCarState(jedis, carId);
                if (state == CarState.IDLE && jedis.llen(Keys.carTaskQueue(carId)) == 0) {
                    dispatchNavigatorTask(jedis, channel, carId);
                    assignedCount++;
                }
                if (state == CarState.IDLE) {
                    idleCount++;
                }
            }

            channel.basicPublish(SystemQueues.EXCHANGE_CAR_BROADCAST, "", null, "1".getBytes(StandardCharsets.UTF_8));
            long tickMs = (System.nanoTime() - t0) / 1_000_000L;
            log.info("Tick {}: {} cars, explored={}%, idle={}, assigned={}, cost={}ms",
                    tickNo, cars.size(), String.format("%.1f", exploredRate * 100), idleCount, assignedCount, tickMs);
        }
    }

    private void dispatchNavigatorTask(Jedis jedis, Channel channel, int carId) throws Exception {
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
        channel.basicPublish(
                SystemQueues.EXCHANGE_NAVIGATOR,
                SystemQueues.navigatorRoutingKey(navigatorId),
                null,
                JsonSupport.toJson(task).getBytes(StandardCharsets.UTF_8)
        );
        Blackboard.setCarState(jedis, carId, CarState.ASSIGNING);
        log.info("Tick {}: car {} at {} dispatched to navigator {}", tickCount.get(), carId, point, navigatorId);
    }

    private int firstAvailableNavigator(Jedis jedis) {
        for (int i = 1; i <= AppConfig.navigatorWorkerCount(); i++) {
            String value = jedis.hget(Keys.NAVIGATOR_STATUS, "nav_" + i + ":working");
            if (value == null || !"true".equalsIgnoreCase(value)) {
                return i;
            }
        }
        return 0;
    }

    private void complete(Channel channel) throws Exception {
        log.info("Exploration completed; stopping backend at tick {}", tickCount.get());
        active.set(false);
        try (Jedis jedis = RedisProvider.get()) {
            String result = Blackboard.hasUnreachableFreeCells(jedis) ? "partial" : "complete";
            jedis.set("exploration_result", result);
            log.info("Exploration result: {}", result);
        }
        channel.basicPublish(SystemQueues.EXCHANGE_SAVE, "", null, "0".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        stopWork();
    }
}
