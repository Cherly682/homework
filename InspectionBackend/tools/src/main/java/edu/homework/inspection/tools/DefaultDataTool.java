package edu.homework.inspection.tools;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.AppConfig;
import edu.homework.inspection.common.Keys;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.RedisProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class DefaultDataTool {
    private static final Logger log = LoggerFactory.getLogger(DefaultDataTool.class);

    public static void main(String[] args) throws Exception {
        try {
            String command = args.length == 0 ? "init" : args[0];
            if ("init".equalsIgnoreCase(command)) {
                declareQueues();
                seedUsers();
                log.info("Initialization completed");
            } else if ("seed-users".equalsIgnoreCase(command)) {
                seedUsers();
            } else if ("declare-queues".equalsIgnoreCase(command)) {
                declareQueues();
            } else if ("clean-runtime".equalsIgnoreCase(command)) {
                cleanRuntime();
            } else {
                log.error("Unknown command: {}. Usage: init | seed-users | declare-queues | clean-runtime", command);
            }
        } finally {
            RabbitProvider.close();
            RedisProvider.close();
        }
    }

    private static void declareQueues() throws Exception {
        long t0 = System.nanoTime();
        try (Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
        }
        log.info("RabbitMQ topology declared on {}:{}{} in {}ms",
                AppConfig.rabbitHost(), AppConfig.rabbitPort(), AppConfig.rabbitVirtualHost(),
                (System.nanoTime() - t0) / 1_000_000L);
    }

    private static void seedUsers() {
        try (Jedis jedis = RedisProvider.get()) {
            writeUser(jedis, "admin", "admin123", "Admin");
            writeUser(jedis, "config", "config123", "Configurator");
            writeUser(jedis, "analyst", "analyst123", "Analyst");
        }
        log.info("Default users written: admin/config/analyst");
    }

    private static void writeUser(Jedis jedis, String username, String password, String role) {
        Map<String, String> fields = new HashMap<>();
        fields.put("password", sha256(password));
        fields.put("role", role);
        jedis.hmset(Keys.USERS_PREFIX + username, fields);
        log.debug("User {} written (role={})", username, role);
    }

    private static void cleanRuntime() {
        try (Jedis jedis = RedisProvider.get()) {
            for (String pattern : new String[]{
                    Keys.CARS_PREFIX + "*",
                    "*_task_queue",
                    Keys.RECORDER_FRAME_PREFIX + "*",
                    "Viewqueue:*"
            }) {
                deleteByPattern(jedis, pattern);
            }
            jedis.del(Keys.MAP_VIEW);
            jedis.del(Keys.BLOCK_VIEW);
            jedis.del(Keys.NAVIGATOR_STATUS);
            jedis.del(Keys.SAVE);
        }
        log.info("Runtime Redis keys cleaned");
    }

    private static void deleteByPattern(Jedis jedis, String pattern) {
        String cursor = "0";
        ScanParams params = new ScanParams().match(pattern).count(100);
        int deleted = 0;
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            if (!result.getResult().isEmpty()) {
                jedis.del(result.getResult().toArray(new String[0]));
                deleted += result.getResult().size();
            }
            cursor = result.getCursor();
        } while (!"0".equals(cursor));
        log.debug("Deleted {} keys matching {}", deleted, pattern);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
