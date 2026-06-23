package edu.homework.inspection.navigator;

import edu.homework.inspection.common.AppConfig;
import edu.homework.inspection.common.RabbitProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NavigatorMain {
    private static final Logger log = LoggerFactory.getLogger(NavigatorMain.class);

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        try (com.rabbitmq.client.Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
        }
        int workerCount = AppConfig.navigatorWorkerCount();
        log.info("NavigatorMain starting {} workers", workerCount);
        for (int i = 1; i <= workerCount; i++) {
            Thread thread = new Thread(new NavigationWorker(i), "navigator-" + i);
            thread.setDaemon(false);
            thread.start();
        }
        log.info("NavigatorMain: all {} workers started in {}ms", workerCount, (System.nanoTime() - t0) / 1_000_000L);
    }
}
