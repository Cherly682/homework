package edu.homework.inspection.controller;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.RedisProvider;
import edu.homework.inspection.common.SystemQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ControllerMain {
    private static final Logger log = LoggerFactory.getLogger(ControllerMain.class);

    public static void main(String[] args) throws Exception {
        // ---- 分布式单实例锁 ----
        String instanceId = UUID.randomUUID().toString();
        try (Jedis jedis = RedisProvider.get()) {
            String result = jedis.set(Keys.CONTROLLER_LOCK, instanceId,
                    SetParams.setParams().nx().ex(30));
            if (result == null || !"OK".equals(result)) {
                System.err.println("Controller 实例已在运行，当前实例退出。");
                System.exit(1);
            }
            log.info("Controller instance lock acquired: {}", instanceId);
        }

        // 定时续约（每 10 秒续约一次，TTL 30 秒，留足容错空间）
        ScheduledExecutorService renewalExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "controller-lock-renewal");
            t.setDaemon(true);
            return t;
        });
        renewalExecutor.scheduleAtFixedRate(() -> {
            try (Jedis jedis = RedisProvider.get()) {
                // 仅当当前实例仍持有锁时才续约（Lua 脚本保证原子性）
                String script = "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('EXPIRE', KEYS[1], ARGV[2]) else return 0 end";
                jedis.eval(script, java.util.Collections.singletonList(Keys.CONTROLLER_LOCK),
                        java.util.Arrays.asList(instanceId, "30"));
            } catch (Exception e) {
                log.warn("Controller lock renewal failed: {}", e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);

        ControllerAgent agent = new ControllerAgent();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Controller shutdown hook triggered");
            agent.close();
            renewalExecutor.shutdown();
            // 释放锁
            try (Jedis jedis = RedisProvider.get()) {
                String script = "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('DEL', KEYS[1]) else return 0 end";
                jedis.eval(script, java.util.Collections.singletonList(Keys.CONTROLLER_LOCK),
                        java.util.Collections.singletonList(instanceId));
            } catch (Exception e) {
                log.warn("Failed to release controller lock: {}", e.getMessage());
            }
        }));

        Channel channel = RabbitProvider.channel();
        RabbitProvider.declareTopology(channel);
        channel.basicConsume(SystemQueues.QUEUE_CONTROLLER_START, true, (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8).trim();
            log.info("Controller received command: {}", message);
            if ("1".equals(message)) {
                try {
                    agent.startWork();
                } catch (Exception e) {
                    log.error("Controller startWork failed: {}", e.getMessage(), e);
                }
            } else if ("0".equals(message)) {
                agent.stopWork();
            } else {
                log.warn("Controller ignored unknown command: {}", message);
            }
        }, consumerTag -> {
        });

        log.info("Controller listening on {}", SystemQueues.QUEUE_CONTROLLER_START);
        new CountDownLatch(1).await();
    }
}
