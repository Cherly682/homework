package edu.homework.inspection.navigator;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.JsonSupport;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.NavigatorTask;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.RedisProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class NavigationWorker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(NavigationWorker.class);
    private final int id;
    private final NavigationService service = new NavigationService();

    public NavigationWorker(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        String queueName = "navigator.no" + id;
        long t0 = System.nanoTime();
        try (Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
            try (Jedis jedis = RedisProvider.get()) {
                jedis.hset(Keys.NAVIGATOR_STATUS, "nav_" + id + ":working", "false");
            }
            channel.basicConsume(queueName, true, (consumerTag, delivery) -> {
                long tMsg = System.nanoTime();
                String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                try (Jedis taskJedis = RedisProvider.get()) {
                    taskJedis.hset(Keys.NAVIGATOR_STATUS, "nav_" + id + ":working", "true");
                    NavigatorTask task = JsonSupport.fromJson(json, NavigatorTask.class);
                    log.debug("Navigator {} received task: {}", id, task.getCarId());
                    service.process(taskJedis, task);
                } catch (Exception e) {
                    log.error("Navigator {} failed: {}", id, e.getMessage(), e);
                } finally {
                    try (Jedis statusJedis = RedisProvider.get()) {
                        statusJedis.hset(Keys.NAVIGATOR_STATUS, "nav_" + id + ":working", "false");
                    }
                }
                log.debug("Navigator {} message processed in {}ms", id, (System.nanoTime() - tMsg) / 1_000_000L);
            }, consumerTag -> {
            });
            log.info("Navigator worker {} listening on {} (startup={}ms)", id, queueName, (System.nanoTime() - t0) / 1_000_000L);
            new CountDownLatch(1).await();
        } catch (Exception e) {
            log.error("Navigator worker {} stopped: {}", id, e.getMessage(), e);
            throw new IllegalStateException("Navigator worker " + id + " stopped", e);
        }
    }
}
