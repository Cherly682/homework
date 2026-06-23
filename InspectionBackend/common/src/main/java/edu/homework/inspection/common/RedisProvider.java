package edu.homework.inspection.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public final class RedisProvider {
    private static final Logger log = LoggerFactory.getLogger(RedisProvider.class);
    private static final JedisPool POOL = createPool();

    private RedisProvider() {
    }

    public static Jedis get() {
        long t0 = System.nanoTime();
        Jedis jedis = POOL.getResource();
        jedis.select(AppConfig.redisDatabase());
        log.debug("Redis get() -> {}ms", (System.nanoTime() - t0) / 1_000_000L);
        return jedis;
    }

    public static void close() {
        log.info("Redis pool closing");
        POOL.close();
    }

    private static JedisPool createPool() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(100);
        config.setMaxIdle(20);
        config.setMinIdle(4);
        config.setTestOnBorrow(false);
        config.setTestOnReturn(false);
        String host = AppConfig.redisHost();
        int port = AppConfig.redisPort();
        String password = AppConfig.redisPassword();
        log.info("Redis pool created: {}:{}/{}", host, port, AppConfig.redisDatabase());
        if (password == null || password.isEmpty()) {
            return new JedisPool(config, host, port, 2000);
        }
        return new JedisPool(config, host, port, 2000, password);
    }
}
