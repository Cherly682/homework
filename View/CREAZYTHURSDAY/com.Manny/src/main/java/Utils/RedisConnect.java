package Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.swing.JOptionPane;


public class RedisConnect {
    private static final Logger log = LoggerFactory.getLogger(RedisConnect.class);

    private static final String HOST = ConfigManager.get("redis.host", "localhost");
    private static final int PORT = ConfigManager.getInt("redis.port", 6379);
    private static final int DATABASE_INDEX = ConfigManager.getInt("redis.database", 9);
    private static final String PASSWORD = ConfigManager.get("redis.password");

    private static JedisPool pool;

    private static boolean connectionFailed = false;


    static {
        initializePool();
    }


    private static void initializePool() {
        try {

            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(ConfigManager.getInt("redis.pool.maxTotal", 50));
            config.setMaxIdle(ConfigManager.getInt("redis.pool.maxIdle", 10));
            config.setMinIdle(ConfigManager.getInt("redis.pool.minIdle", 2));
            config.setTestOnBorrow(true);
            config.setTestWhileIdle(true);
            config.setMinEvictableIdleTimeMillis(60000);
            config.setTimeBetweenEvictionRunsMillis(30000);


            int timeout = ConfigManager.getInt("redis.pool.timeout", 2000);



            if (PASSWORD != null && !PASSWORD.isEmpty()) {
                pool = new JedisPool(config, HOST, PORT, timeout, PASSWORD);
            } else {
                pool = new JedisPool(config, HOST, PORT, timeout);
            }


            testConnection();
            log.info("RedisConnect pool initialized: {}:{}/{}", HOST, PORT, DATABASE_INDEX);
        } catch (Exception e) {
            log.error("RedisConnect pool init failed: {}", e.getMessage(), e);
            handleConnectionFailure("Redis connection pool init failed: " + e.getMessage());
        }
    }


    public static Jedis getConnected() {
        try {

            if (pool == null || pool.isClosed()) {
                initializePool();
            }


            Jedis jedis = pool.getResource();

            jedis.select(DATABASE_INDEX);


            if (!testConnection(jedis)) {
                handleConnectionFailure("Redis connection lost");
                throw new JedisConnectionException("Redis connection unavailable");
            }


            connectionFailed = false;
            return jedis;
        } catch (Exception e) {
            log.error("RedisConnect getConnected failed: {}", e.getMessage(), e);
            handleConnectionFailure("Failed to get Redis connection: " + e.getMessage());
            throw new RuntimeException("Cannot get Redis connection", e);
        }
    }


    private static boolean testConnection() {
        try (Jedis jedis = pool.getResource()) {
            return testConnection(jedis);
        } catch (Exception e) {
            return false;
        }
    }


    private static boolean testConnection(Jedis jedis) {
        try {
            String result = jedis.ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            return false;
        }
    }


    private static void handleConnectionFailure(String message) {
        if (!connectionFailed) {
            connectionFailed = true;
            showErrorDialog(message);
            log.error("Redis connection error: {}", message);
        }
    }


    private static void showErrorDialog(String message) {
        try {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                JOptionPane.showMessageDialog(null,
                        message + "\nPlease check Redis server status and network connection",
                        "Redis Connection Error",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null,
                            message + "\nPlease check Redis server status and network connection",
                            "Redis Connection Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        } catch (Exception e) {
            log.error("Cannot show error dialog: {}", e.getMessage());
        }
    }

    public static void closePool() {
        try {
            if (pool != null && !pool.isClosed()) {
                pool.close();
                log.info("RedisConnect pool closed");
            }
        } catch (Exception e) {
            log.error("RedisConnect pool close error: {}", e.getMessage());
        }
    }


    public static boolean isHealthy() {
        return !connectionFailed && testConnection();
    }
}
