package edu.homework.inspection.car;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.Blackboard;
import edu.homework.inspection.common.CarState;
import edu.homework.inspection.common.Direction;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.Point;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.RedisProvider;
import edu.homework.inspection.common.SystemQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class CarAgent implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(CarAgent.class);
    private final int id;

    public CarAgent(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        try (Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
            channel.basicConsume(SystemQueues.carQueue(id), true, (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8).trim();
                if ("1".equals(message)) {
                    handleTick();
                }
            }, consumerTag -> {
            });
            log.info("Car {} listening on {}", id, SystemQueues.carQueue(id));
            new CountDownLatch(1).await();
        } catch (Exception e) {
            log.error("Car {} stopped: {}", id, e.getMessage(), e);
            throw new IllegalStateException("Car " + id + " stopped", e);
        }
    }

    private void handleTick() {
        long tickStart = System.nanoTime();
        try (Jedis jedis = RedisProvider.get()) {
            // Step 1: check if car exists
            long tCheck = System.nanoTime();
            if (!Blackboard.hasCar(jedis, id)) {
                log.debug("Car {} tick: car not in blackboard, skip", id);
                return;
            }

            CarState state = Blackboard.getCarState(jedis, id);
            if (state == CarState.IDLE || state == CarState.BLOCKED) {
                log.trace("Car {} tick: state={}, skip", id, state);
                return;
            }
            long tStateCheck = System.nanoTime();

            // Step 2: pop next route step
            String taskQueue = Keys.carTaskQueue(id);
            String nextRaw = jedis.lpop(taskQueue);
            long tQueuePop = System.nanoTime();
            if (nextRaw == null) {
                if (state == CarState.RUNNING) {
                    Blackboard.setCarState(jedis, id, CarState.IDLE);
                    log.info("Car {} ROUTE_DONE: path exhausted, transition RUNNING->IDLE", id);
                }
                return;
            }

            // Step 3: get current position
            Point current = Blackboard.getCarPoint(jedis, id);
            long tGetPos = System.nanoTime();
            if (current == null) {
                Blackboard.setCarState(jedis, id, CarState.IDLE);
                log.warn("Car {} has no position, reset to IDLE", id);
                return;
            }
            Point next = Point.parseQueueValue(nextRaw);
            if (current.equals(next)) {
                jedis.del(taskQueue);
                Blackboard.setCarState(jedis, id, CarState.IDLE);
                log.debug("Car {} next step equals current, path cleared, set IDLE", id);
                return;
            }

            // Step 4: validate move
            int width = Blackboard.mapWidth(jedis);
            int height = Blackboard.mapHeight(jedis);
            long tMapRead = System.nanoTime();
            if (!isAdjacent(current, next) || Blackboard.isBlocked(jedis, width, next)) {
                jedis.del(taskQueue);
                Blackboard.setCarState(jedis, id, CarState.IDLE);
                log.warn("Car {} BLOCKED at next step {} (adjacent={}, blocked={}) -> path cleared",
                        id, next, isAdjacent(current, next), Blackboard.isBlocked(jedis, width, next));
                return;
            }
            long tValidate = System.nanoTime();

            // Step 4.5: 原子抢占目标格子，防止两车同时移动到同一位置
            String reserveKey = "car_reserve:" + next.getX() + ":" + next.getY();
            long reserved = jedis.setnx(reserveKey, String.valueOf(id));
            if (reserved == 0) {
                // 目标格已被其他小车抢占
                jedis.del(taskQueue);
                Blackboard.setCarState(jedis, id, CarState.IDLE);
                log.warn("Car {} COLLISION_AVOIDED: target {} already reserved by another car -> path cleared", id, next);
                return;
            }
            jedis.expire(reserveKey, 5);
            long tReserve = System.nanoTime();

            // Step 5: execute move
            Direction direction = Direction.between(current, next);
            long tPreMove = System.nanoTime();
            Blackboard.setCarState(jedis, id, CarState.RUNNING);
            Blackboard.setCarPoint(jedis, id, next, direction);
            jedis.del(reserveKey);
            long tPosUpdate = System.nanoTime();
            Blackboard.illuminate3x3(jedis, width, height, next);
            long tIlluminate = System.nanoTime();
            long remainingSteps = jedis.llen(taskQueue);
            long tFinalCheck = System.nanoTime();

            // Step 6: transition state
            if (remainingSteps == 0) {
                Blackboard.setCarState(jedis, id, CarState.IDLE);
                log.info("Car {} ROUTE_DONE: arrived at target, path completed", id);
            } else {
                Blackboard.setCarState(jedis, id, CarState.RUNNING);
            }
            long tStateTransition = System.nanoTime();

            // Per-step total timing breakdown
            long total = (System.nanoTime() - tickStart) / 1_000_000L;
            log.info("Car {} MOVE {}->{} | timing: total={}ms check={}us queuePop={}us getPos={}us validate={}us " +
                     "posUpdate={}us illuminate={}us routeCheck={}us stateTrans={}us remainingSteps={}",
                    id, current, next, total,
                    NANOSECONDS.toMicros(tStateCheck - tCheck),
                    NANOSECONDS.toMicros(tQueuePop - tStateCheck),
                    NANOSECONDS.toMicros(tGetPos - tQueuePop),
                    NANOSECONDS.toMicros(tValidate - tGetPos),
                    NANOSECONDS.toMicros(tPosUpdate - tPreMove),
                    NANOSECONDS.toMicros(tIlluminate - tPosUpdate),
                    NANOSECONDS.toMicros(tFinalCheck - tIlluminate),
                    NANOSECONDS.toMicros(tStateTransition - tFinalCheck),
                    remainingSteps);
        } catch (Exception e) {
            log.error("Car {} tick failed: {}", id, e.getMessage(), e);
        }
    }

    private boolean isAdjacent(Point a, Point b) {
        return a.manhattan(b) == 1;
    }
}
