package edu.homework.inspection.car;

import edu.homework.inspection.common.RabbitProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarMain {
    private static final Logger log = LoggerFactory.getLogger(CarMain.class);

    public static void main(String[] args) throws Exception {
        try (com.rabbitmq.client.Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
        }
        CarManager manager = new CarManager();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Car shutdown hook triggered");
            manager.stop();
        }));
        log.info("CarMain starting CarManager");
        manager.run();
    }
}
