package edu.homework.inspection.recorder;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.SystemQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class RecorderMain {
    private static final Logger log = LoggerFactory.getLogger(RecorderMain.class);

    public static void main(String[] args) throws Exception {
        //创建录制服务
        RecorderService recorder = new RecorderService();

        //注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Recorder shutdown hook triggered");
            recorder.stopRecording();
            recorder.stopPlayback();
        }));

        //连接RabbitMQ
        Channel channel = RabbitProvider.channel();
        RabbitProvider.declareTopology(channel);

        //开始监听，不回复
        channel.basicConsume(SystemQueues.QUEUE_SAVE_START, true, (consumerTag, delivery) -> {
            //解析指令字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8).trim();
            log.info("Recorder received command: {}", message);

            // 根据指令执行对应操作
            if ("1".equals(message)) {
                //开始录制
                recorder.startRecording();
            } else if ("0".equals(message)) {
                //停止录制（不影响回放）
                recorder.stopRecording();
            } else if ("2".equals(message)) {
                //开始回放
                recorder.startPlayback();
            } else if ("3".equals(message)) {
                //仅停止回放
                recorder.stopPlayback();
            } else {
                log.warn("Recorder ignored unknown command: {}", message);
            }
        }, consumerTag -> {
            //不需要处理取消事件
        });

        log.info("Recorder listening on {}", SystemQueues.QUEUE_SAVE_START);


        new CountDownLatch(1).await();
    }
}
