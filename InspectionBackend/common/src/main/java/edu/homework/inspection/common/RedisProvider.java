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
        config.setMaxTotal(200);          // 增大以支持 Pipeline 并发
        config.setMaxIdle(30);
        config.setMinIdle(8);
        config.setTestOnBorrow(true);     // 跨主机部署：借用时验证连接有效性
        config.setTestOnReturn(false);
        config.setMaxWaitMillis(3000);    // 等待连接超时，避免无限阻塞
        config.setMinEvictableIdleTimeMillis(60000);
        config.setTimeBetweenEvictionRunsMillis(30000);
        String host = AppConfig.redisHost();
        int port = AppConfig.redisPort();
        String password = AppConfig.redisPassword();
        log.info("Redis pool created: {}:{}/{}", host, port, AppConfig.redisDatabase());
        if (password == null || password.isEmpty()) {
            return new JedisPool(config, host, port, 3000);
        }
        return new JedisPool(config, host, port, 3000, password);
    }
}
