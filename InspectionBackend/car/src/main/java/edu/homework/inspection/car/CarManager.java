package edu.homework.inspection.car;

import edu.homework.inspection.common.AppConfig;
import edu.homework.inspection.common.Blackboard;
import edu.homework.inspection.common.RedisProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CarManager implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(CarManager.class);
    private final Map<Integer, Thread> cars = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    @Override
    public void run() {
        log.info("CarManager scanning for cars (max {})", AppConfig.maxCars());
        while (running) {
            long t0 = System.nanoTime();
            try (Jedis jedis = RedisProvider.get()) {
                int found = 0;
                for (int i = 1; i <= AppConfig.maxCars(); i++) {
                    if (Blackboard.hasCar(jedis, i) && !cars.containsKey(i)) {
                        Thread thread = new Thread(new CarAgent(i), "car-" + i);
                        thread.setDaemon(false);
                        cars.put(i, thread);
                        thread.start();
                        log.info("CarManager started car {}", i);
                        found++;
                    }
                }
                if (found > 0) {
                    log.info("CarManager scan: {} new cars started in {}ms, total active={}",
                            found, (System.nanoTime() - t0) / 1_000_000L, cars.size());
                }
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                log.error("CarManager scan failed: {}", e.getMessage(), e);
            }
        }
    }

    public void stop() {
        running = false;
        log.info("CarManager stopped ({} cars were active)", cars.size());
    }
}
