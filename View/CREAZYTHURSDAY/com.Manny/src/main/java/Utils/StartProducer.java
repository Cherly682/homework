package Utils;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StartProducer {
    private static final Logger log = LoggerFactory.getLogger(StartProducer.class);

    // ==================== RabbitMQ 资源命名 ====================
    private static final String QUEUE_CONTROLLER_START = "controller.start";
    private static final String QUEUE_NAVIGATOR_START = "navigator.start";
    private static final String EXCHANGE_CONTROLLER = "controller";
    private static final String QUEUE_SAVE_START = "save.start";
    private static final String EXCHANGE_SAVE = "save";

    // ==================== 连接和通道 ====================

    private static volatile Connection connection;
    private static volatile Channel channel;


    private static synchronized void ensureConnected() throws Exception {
        // 检查连接是否完好：连接不为空 且 通道不为空 且 两者都处于打开状态
        if (channel == null || !channel.isOpen() || connection == null || !connection.isOpen()) {
            // 清理可能存在的旧连接
            if (channel != null && channel.isOpen()) {
                try { channel.close(); } catch (Exception ignored) {}
            }
            if (connection != null && connection.isOpen()) {
                try { connection.close(); } catch (Exception ignored) {}
            }

            // 创建连接工厂，设置连接参数
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(ConfigManager.get("rabbitmq.host", "localhost"));
            factory.setVirtualHost(ConfigManager.get("rabbitmq.vhost", "/"));
            factory.setUsername(ConfigManager.get("rabbitmq.username", "guest"));
            factory.setPassword(ConfigManager.get("rabbitmq.password", "guest"));
            factory.setPort(ConfigManager.getInt("rabbitmq.port", 5672));

            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE_CONTROLLER, BuiltinExchangeType.FANOUT, true, false, null);
            channel.exchangeDeclare(EXCHANGE_SAVE, BuiltinExchangeType.FANOUT, true, false, null);

            channel.queueDeclare(QUEUE_CONTROLLER_START, true, false, false, null);
            channel.queueDeclare(QUEUE_NAVIGATOR_START, true, false, false, null);
            channel.queueDeclare(QUEUE_SAVE_START, true, false, false, null);

            channel.queueBind(QUEUE_CONTROLLER_START, EXCHANGE_CONTROLLER, "");
            channel.queueBind(QUEUE_NAVIGATOR_START, EXCHANGE_CONTROLLER, "");
            channel.queueBind(QUEUE_SAVE_START, EXCHANGE_SAVE, "");

            log.info("StartProducer lazy-connected to RabbitMQ");
        }
    }

    public StartProducer() throws Exception {
        ensureConnected();
        log.info("StartProducer initialized: RabbitMQ connected to {}:{}",
                 ConfigManager.get("rabbitmq.host", "localhost"),
                 ConfigManager.getInt("rabbitmq.port", 5672));
    }

    public static void sendStartMessage() throws Exception {
        ensureConnected();
        String start = "1";
        channel.basicPublish(EXCHANGE_CONTROLLER, "", null, start.getBytes());
        channel.basicPublish(EXCHANGE_SAVE, "", null, start.getBytes());
        log.info("StartProducer: SENT START command (controller.start + save.start)");
    }

    public static void sendBackMessage() throws Exception {
        ensureConnected();
        String back = "2";
        channel.basicPublish(EXCHANGE_SAVE, "", null, back.getBytes());
        log.info("StartProducer: SENT PLAYBACK command (save.start=2)");
    }


    public static void sendStopMessage() throws Exception {
        ensureConnected();
        String stop = "0";
        channel.basicPublish(EXCHANGE_CONTROLLER, "", null, stop.getBytes());
        channel.basicPublish(EXCHANGE_SAVE, "", null, stop.getBytes());
        log.info("StartProducer: SENT STOP command (controller.start=0 + save.start=0)");
    }

    /** 仅停止回放，不影响正在进行的录制（分析员专用） */
    public static void sendPlaybackStop() throws Exception {
        ensureConnected();
        String stopPlayback = "3";
        channel.basicPublish(EXCHANGE_SAVE, "", null, stopPlayback.getBytes());
        log.info("StartProducer: SENT PLAYBACK-STOP command (save.start=3)");
    }
}
