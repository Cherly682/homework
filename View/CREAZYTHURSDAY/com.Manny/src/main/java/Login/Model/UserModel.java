package Login.Model;

import Utils.RedisConnect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;


public class UserModel {
    private static final Logger log = LoggerFactory.getLogger(UserModel.class);

    // ==================== 字段（Fields）====================
    private String username;
    private String password;
    private String role;

    // Redis 中存储用户信息的 key 前缀
    private final String USERS_KEY_PREFIX = "Users:";

    // 获取 Redis 连接
    private Jedis jedis = RedisConnect.getConnected();

    /**
     * 确保 Redis 连接可用。如果当前连接已断开，自动从连接池获取新连接。
     */
    private void ensureConnection() {
        try {
            if (jedis == null || !jedis.isConnected() || !"PONG".equals(jedis.ping())) {
                renewConnection();
            }
        } catch (Exception e) {
            log.warn("UserModel Redis connection check failed, renewing: {}", e.getMessage());
            renewConnection();
        }
    }

    private void renewConnection() {
        try {
            if (jedis != null) {
                try { jedis.close(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        jedis = RedisConnect.getConnected();
        log.info("UserModel: Redis connection renewed");
    }

    // ==================== 构造方法 ====================

    public UserModel() { }

    public UserModel(String username, String password, String role) {
        this.username = username;
        this.password = encryptPassword(password);
        this.role = role;
    }


    public static UserModel fromRedis(String username, String password, String role) {
        UserModel user = new UserModel();
        user.username = username;
        user.password = password;
        user.role = role;
        return user;
    }

    // ==================== Getter 和 Setter ====================

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getPassword() { return password; }

    public void setPassword(String password) {
        this.password = encryptPassword(password);
    }

    // ==================== 核心业务逻辑 ====================

    public boolean validate() {
        ensureConnection();
        try {
            // 拼接 Redis key
            String key = USERS_KEY_PREFIX + username;
            // 获取该用户在 Redis 中的所有字段
            Map<String, String> userProperties = jedis.hgetAll(key);

            //用户数据存在且不为空
            if (userProperties != null && !userProperties.isEmpty()) {
                String storedPassword = userProperties.get("password");
                String storedRole = userProperties.get("role");

                // 比对加密后的密码是否一致
                if (storedPassword != null && storedPassword.equals(password)
                        && storedRole != null) {
                    this.role = storedRole;
                    log.debug("User validated: username={} role={}", username, storedRole);
                    return true;
                }
            }
            log.warn("User validation FAILED: username={}", username);
        } catch (Exception e) {
            log.error("User validation error: {}", e.getMessage(), e);
        }
        return false;
    }

    // ==================== 密码加密 ====================


    private static String encryptPassword(String password) {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedPassword = md.digest(password.getBytes());
            return bytesToHex(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            throw new RuntimeException(e);
        }
    }


    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
