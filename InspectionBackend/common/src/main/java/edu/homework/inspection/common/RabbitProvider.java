package edu.homework.inspection.common;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public final class RabbitProvider {
    private static final Logger log = LoggerFactory.getLogger(RabbitProvider.class);
    private static Connection connection;

    private RabbitProvider() {
    }

    public static synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(AppConfig.rabbitHost());
            factory.setPort(AppConfig.rabbitPort());
            factory.setUsername(AppConfig.rabbitUsername());
            factory.setVirtualHost(AppConfig.rabbitVirtualHost());
            factory.setPassword(AppConfig.rabbitPassword());
            long t0 = System.nanoTime();
            connection = factory.newConnection("inspection-backend");
            log.info("RabbitMQ connected to {}:{}/{} in {}ms",
                    AppConfig.rabbitHost(), AppConfig.rabbitPort(), AppConfig.rabbitVirtualHost(),
                    (System.nanoTime() - t0) / 1_000_000L);
        }
        return connection;
    }

    public static Channel channel() throws IOException, TimeoutException {
        long t0 = System.nanoTime();
        Channel ch = connection().createChannel();
        log.debug("RabbitMQ channel created in {}ms", (System.nanoTime() - t0) / 1_000_000L);
        return ch;
    }

    public static void declareTopology(Channel channel) throws IOException {
        long t0 = System.nanoTime();
        channel.exchangeDeclare(SystemQueues.EXCHANGE_CONTROLLER, BuiltinExchangeType.FANOUT, true);
        channel.exchangeDeclare(SystemQueues.EXCHANGE_SAVE, BuiltinExchangeType.FANOUT, true);
        channel.exchangeDeclare(SystemQueues.EXCHANGE_CAR_BROADCAST, BuiltinExchangeType.FANOUT, true);
        channel.exchangeDeclare(SystemQueues.EXCHANGE_NAVIGATOR, BuiltinExchangeType.DIRECT, true);

        channel.queueDeclare(SystemQueues.QUEUE_CONTROLLER_START, true, false, false, null);
        channel.queueDeclare(SystemQueues.QUEUE_NAVIGATOR_START, true, false, false, null);
        channel.queueDeclare(SystemQueues.QUEUE_SAVE_START, true, false, false, null);

        channel.queueBind(SystemQueues.QUEUE_CONTROLLER_START, SystemQueues.EXCHANGE_CONTROLLER, "");
        channel.queueBind(SystemQueues.QUEUE_NAVIGATOR_START, SystemQueues.EXCHANGE_CONTROLLER, "");
        channel.queueBind(SystemQueues.QUEUE_SAVE_START, SystemQueues.EXCHANGE_SAVE, "");

        for (int i = 1; i <= AppConfig.maxCars(); i++) {
            channel.queueDeclare(SystemQueues.carQueue(i), true, false, false, null);
            channel.queueBind(SystemQueues.carQueue(i), SystemQueues.EXCHANGE_CAR_BROADCAST, "");
        }
        for (int i = 1; i <= AppConfig.navigatorWorkerCount(); i++) {
            channel.queueDeclare(SystemQueues.navigatorQueue(i), true, false, false, null);
            channel.queueBind(SystemQueues.navigatorQueue(i), SystemQueues.EXCHANGE_NAVIGATOR, SystemQueues.navigatorRoutingKey(i));
        }
        log.info("RabbitMQ topology declared ({} cars, {} navigators) in {}ms",
                AppConfig.maxCars(), AppConfig.navigatorWorkerCount(),
                (System.nanoTime() - t0) / 1_000_000L);
    }

    public static synchronized void close() {
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
                log.info("RabbitMQ connection closed");
            } catch (Exception e) {
                log.warn("RabbitMQ close error: {}", e.getMessage());
            }
        }
    }
}
