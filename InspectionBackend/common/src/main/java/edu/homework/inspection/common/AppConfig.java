package edu.homework.inspection.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties PROPERTIES = loadProperties();

    private AppConfig() {
    }

    public static String redisHost() {
        return get("REDIS_HOST", "redis.host", "localhost");
    }

    public static int redisPort() {
        return getInt("REDIS_PORT", "redis.port", 6379);
    }

    public static int redisDatabase() {
        return getInt("REDIS_DATABASE", "redis.database", 9);
    }

    public static String redisPassword() {
        return get("REDIS_PASSWORD", "redis.password", "");
    }

    public static String rabbitHost() {
        return get("RABBITMQ_HOST", "rabbitmq.host", "localhost");
    }

    public static int rabbitPort() {
        return getInt("RABBITMQ_PORT", "rabbitmq.port", 5672);
    }

    public static String rabbitUsername() {
        return get("RABBITMQ_USERNAME", "rabbitmq.username", "guest");
    }

    public static String rabbitPassword() {
        return get("RABBITMQ_PASSWORD", "rabbitmq.password", "guest");
    }

    public static String rabbitVirtualHost() {
        return get("RABBITMQ_VHOST", "rabbitmq.virtualHost", "/");
    }

    public static long tickMillis() {
        return getLong("CONTROLLER_TICK_MILLIS", "controller.tickMillis", 500L);
    }

    public static int maxCars() {
        return getInt("CONTROLLER_MAX_CARS", "controller.maxCars", 5);
    }

    public static int navigatorWorkerCount() {
        return getInt("NAVIGATOR_WORKER_COUNT", "navigator.workerCount", 4);
    }

    private static String get(String envKey, String propKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        String propValue = PROPERTIES.getProperty(propKey);
        if (propValue != null && !propValue.trim().isEmpty()) {
            return propValue.trim();
        }
        return defaultValue;
    }

    private static int getInt(String envKey, String propKey, int defaultValue) {
        return Integer.parseInt(get(envKey, propKey, String.valueOf(defaultValue)));
    }

    private static long getLong(String envKey, String propKey, long defaultValue) {
        return Long.parseLong(get(envKey, propKey, String.valueOf(defaultValue)));
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }
        return properties;
    }
}
