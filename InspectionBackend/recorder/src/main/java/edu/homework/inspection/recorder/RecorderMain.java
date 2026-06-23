package edu.homework.inspection.recorder;

import com.rabbitmq.client.Channel;
import edu.homework.inspection.common.RabbitProvider;
import edu.homework.inspection.common.SystemQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * ============================================================
 * 【录制器入口 —— RecorderMain】
 * ============================================================
 *
 * 【这个类的角色】
 * Recorder（录制器）是后端四大组件之一，负责两项核心工作：
 *
 *   1. 录制（Recording）：巡检过程中定期"拍照"——把 Redis 中当前的地图状态
 *     （探索位图、障碍物位图、所有小车的位置和方向）保存为一条历史记录。
 *      类比：行车记录仪，每隔一段时间拍一张照片存下来。
 *
 *   2. 回放（Playback）：把之前录制的历史记录逐帧"回放"出来——
 *      从存储中读取每一帧的快照，重新写入 Redis 的当前状态键，
 *      这样前端渲染器就能像看录像一样看到当时的地图状态。
 *      类比：DVD 播放器，把光盘里的影像一帧帧播出来。
 *
 * 【后端四大组件】
 * 整个后端是一个分布式系统，由四个独立的 Java 进程组成：
 *
 *   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
 *   │Controller│    │Navigator │    │   Car    │    │ Recorder │
 *   │ 总控制器 │    │ 导航器   │    │ 小车代理 │    │ 录制器   │
 *   └────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘
 *        │               │               │               │
 *        └───────────────┴───────┬───────┴───────────────┘
 *                                │
 *                          ┌─────▼─────┐
 *                          │   Redis   │  ← 数据中转站
 *                          └─────┬─────┘
 *                                │
 *                          ┌─────▼─────┐
 *                          │ RabbitMQ  │  ← 消息通信
 *                          └───────────┘
 *
 * 它们之间不直接调用对方的方法，而是通过 Redis（共享数据）和
 * RabbitMQ（消息通知）进行通信。这种架构叫做"黑板模式 + 消息驱动"。
 *
 * 【Recorder 的启动流程】
 *
 *   1. 创建 RecorderService（录制/回放服务对象）
 *   2. 注册 JVM 关闭钩子 → 程序退出时自动停止录制和回放
 *   3. 连接 RabbitMQ，声明交换机和队列
 *   4. 开始监听 save.start 队列 → 等待前端发来的指令
 *   5. 收到指令后根据内容执行对应操作：
 *      "1" = 开始录制
 *      "0" = 停止录制 + 停止回放
 *      "2" = 开始回放
 *   6. CountDownLatch(1).await() → 让主线程永久等待，保持程序不退出
 *      （直到收到中断信号或被 kill）
 *
 * 【类比理解】
 * RecorderMain 就像一台"录音机"的开关和信号接收器：
 * - 它插着电源（连接 RabbitMQ），随时等待遥控器（前端）发来的指令
 * - 按"录制"键 → 开始录音
 * - 按"停止"键 → 停止录音
 * - 按"播放"键 → 播放之前的录音
 */
public class RecorderMain {
    private static final Logger log = LoggerFactory.getLogger(RecorderMain.class);

    /**
     * 【main 方法 —— 程序入口】
     *
     * 这个 main 方法的逻辑非常清晰，体现了"启动 → 等待指令 → 执行"的模式。
     */
    public static void main(String[] args) throws Exception {
        // ==================== 第1步：创建录制服务 ====================
        RecorderService recorder = new RecorderService();

        // ==================== 第2步：注册 JVM 关闭钩子 ====================
        // Runtime.getRuntime().addShutdownHook() 注册一个"钩子"线程，
        // 当 JVM 收到终止信号时（如 Ctrl+C、kill 命令），这个线程会自动执行。
        //
        // 为什么需要关闭钩子？
        // 如果程序被强制终止时正在录制/回放，后台线程不会自动停止，
        // 可能导致数据不一致。关闭钩子确保"体面地退出"。
        //
        // 类比：就像离开房间时随手关灯——不管你是正常离开还是被赶出去的，
        // 都应该把灯关掉。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Recorder shutdown hook triggered");
            recorder.stopRecording();
            recorder.stopPlayback();
        }));

        // ==================== 第3步：连接 RabbitMQ 并声明拓扑 ====================
        // RabbitProvider.channel() → 获取一个 RabbitMQ 通道
        // RabbitProvider.declareTopology() → 声明所有需要的交换机和队列
        // （如果已经声明过，RabbitMQ 会忽略重复声明，所以不会出错）
        Channel channel = RabbitProvider.channel();
        RabbitProvider.declareTopology(channel);

        // ==================== 第4步：开始监听指令队列 ====================
        // basicConsume 参数说明：
        // - QUEUE_SAVE_START：监听的队列名（"save.start"）
        // - true：自动确认（autoAck）——收到消息后自动告诉 RabbitMQ "我收到了"
        // - delivery callback：收到消息时的处理逻辑（Lambda 表达式）
        // - cancel callback：监听被取消时的回调（这里为空，不需要处理）
        channel.basicConsume(SystemQueues.QUEUE_SAVE_START, true, (consumerTag, delivery) -> {
            // 从消息的字节数组中解析出指令字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8).trim();
            log.info("Recorder received command: {}", message);

            // 根据指令执行对应操作
            // 这里的 "1"/"0"/"2" 就是前端 StartProducer 发送的消息内容
            if ("1".equals(message)) {
                // 指令 "1" = 开始录制
                recorder.startRecording();
            } else if ("0".equals(message)) {
                // 指令 "0" = 停止一切（录制 + 回放）
                recorder.stopRecording();
                recorder.stopPlayback();
            } else if ("2".equals(message)) {
                // 指令 "2" = 开始回放
                recorder.startPlayback();
            } else if ("3".equals(message)) {
                // 指令 "3" = 仅停止回放（不影响正在进行的录制，分析员专用）
                recorder.stopPlayback();
            } else {
                log.warn("Recorder ignored unknown command: {}", message);
            }
        }, consumerTag -> {
            // 监听取消回调：此处为空，因为不需要处理取消事件
        });

        log.info("Recorder listening on {}", SystemQueues.QUEUE_SAVE_START);

        // ==================== 第5步：永久等待 ====================
        // CountDownLatch(1) 是一个"倒计时门闩"。
        // 初始计数为 1，await() 会一直阻塞直到计数变为 0。
        // 由于没有任何地方调用 countDown()，这个等待永远不会结束。
        //
        // 这是一种常见的"让主线程永久等待"的技巧。
        // 如果不这样，main 方法执行完就会退出，整个程序就结束了。
        //
        // 类比：就像便利店的夜班店员——在没顾客的时候也要守在店里，
        // 不能因为没有顾客就关门回家。
        new CountDownLatch(1).await();
    }
}
