package Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * ============================================================
 * 【统一配置管理器 —— ConfigManager】
 * ============================================================
 *   1. 环境变量（最高优先级）
 *   2. JVM 系统属性（-D 参数）
 *   3. config.properties 文件
 *   4. 代码中指定的默认值（最低优先级）
 */
public class ConfigManager {
    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    // 配置文件的文件名
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    static {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);  // 把 .properties 文件的内容加载到 props 对象
                log.info("ConfigManager: 已加载 {}", CONFIG_FILE);
            } else {
                log.warn("ConfigManager: 未找到 {}，将使用环境变量/默认值", CONFIG_FILE);
            }
        } catch (IOException e) {
            log.error("ConfigManager: 加载 {} 失败: {}", CONFIG_FILE, e.getMessage());
        }
    }


    public static String get(String key, String defaultValue) {
        // 查环境变量
        String envKey = key.replace('.', '_').toUpperCase();
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isEmpty()) {
            return envVal;
        }

        // 查 JVM 系统属性
        String sysVal = System.getProperty(key);
        if (sysVal != null && !sysVal.isEmpty()) {
            return sysVal;
        }

        // 查 config.properties
        String propVal = props.getProperty(key);
        if (propVal != null && !propVal.isEmpty()) {
            return propVal;
        }

        //都找不到，返回默认值
        return defaultValue;
    }


    public static String get(String key) {
        return get(key, null);
    }


    public static int getInt(String key, int defaultValue) {
        String val = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(val);  // 字符串 → 整数
        } catch (NumberFormatException e) {
            log.warn("ConfigManager: {} 的值 '{}' 无法解析为 int，使用默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }


    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(val);
    }


    public static void dumpConfig() {
        String redisHost = get("redis.host", "localhost");
        String redisPort = get("redis.port", "6379");
        String redisDb   = get("redis.database", "9");
        String mqHost    = get("rabbitmq.host", "localhost");
        String mqPort    = get("rabbitmq.port", "5672");
        String mqVhost   = get("rabbitmq.vhost", "/");
        String mqUser    = get("rabbitmq.username", "guest");

        log.info("========== 当前配置（密码已隐藏）==========");
        log.info("Redis  : {}:{}/{}", redisHost, redisPort, redisDb);
        log.info("RabbitMQ: {}:{}{} (user={})", mqHost, mqPort, mqVhost, mqUser);
        log.info("Log dir: {}", get("log.dir", "logs"));
        log.info("==========================================");
    }

  //安全检查
    public static void validate() {
        String redisHost = get("redis.host", "localhost");
        String mqHost    = get("rabbitmq.host", "localhost");
        String mqUser    = get("rabbitmq.username", "guest");
        String mqPass    = get("rabbitmq.password", "guest");

        // 判断是否连接本地服务
        boolean localRedis   = "localhost".equals(redisHost) || "127.0.0.1".equals(redisHost);
        boolean localRabbit  = "localhost".equals(mqHost) || "127.0.0.1".equals(mqHost);
        // 判断是否使用了默认的 guest/guest 密码
        boolean defaultCreds = "guest".equals(mqUser) && "guest".equals(mqPass);

        if (localRedis && localRabbit) {
            log.info("CONFIG: 本地模式 (开发环境)");
        } else {
            log.info("CONFIG: 远程模式 -> Redis={}:{}  RabbitMQ={}:{}",
                    redisHost, get("redis.port", "6379"),
                    mqHost, get("rabbitmq.port", "5672"));
        }


        if (!localRabbit && defaultCreds) {
            log.warn("!!! 安全警告: 连接远程 RabbitMQ 但使用默认 guest/guest 密码 !!!");
        }
    }
}
