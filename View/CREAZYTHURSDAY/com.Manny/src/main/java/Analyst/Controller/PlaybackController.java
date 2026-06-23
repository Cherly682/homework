package Analyst.Controller;

import Analyst.Model.PlaybackModel;
import Analyst.View.PlaybackView;
import Utils.RedisConnect;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class PlaybackController {
    private static final Logger log = LoggerFactory.getLogger(PlaybackController.class);
    private PlaybackView playbackView;
    private javax.swing.Timer explorationMonitor;
    private PlaybackModel playbackModel;
    private boolean isPaused = false;
    private javax.swing.Timer uiSyncTimer; // UI 状态同步定时器（跨客户端）
    private volatile boolean syncingSpeedUI = false; // 守卫：防止速度单选回环
    private volatile boolean syncingComboUI = false; // 守卫：防止下拉框回环


    public PlaybackController() throws Exception {
        this.playbackView = new PlaybackView();
        this.playbackModel = playbackView.getPlaybackModel();
        setupEventListeners();
        playbackView.setVisible(true);
        startExplorationMonitor();
        startUISyncTimer();
        log.info("PlaybackController 初始化完成");
    }


    private void setupEventListeners() {

        // ==================== 开始回放按钮 ====================
        playbackView.getPlayButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    isPaused = false;
                    playbackView.getPauseButton().setText("暂停");
                    StartProducer.sendBackMessage();
                    // 写入 Redis 供其他客户端同步回放状态
                    try (Jedis j = RedisConnect.getConnected()) {
                        j.hset("Save", "playback_state", "playing");
                    }
                    log.info("回放开始指令已发送");
                } catch (Exception e) {
                    log.error("发送回放开始指令失败: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(playbackView.getFrame(),
                            "发送回放开始指令失败: " + e.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // ==================== 重置按钮 ====================
        playbackView.getResetButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    isPaused = false;
                    playbackView.getPauseButton().setText("暂停");

                    // 发送停止命令，并禁用按钮防止重复点击
                    StartProducer.sendStopMessage();
                    // 清除回放状态标记（其他客户端检测到后同步按钮文字）
                    try (Jedis j = RedisConnect.getConnected()) {
                        j.hdel("Save", "playback_state");
                    }
                    playbackView.getResetButton().setEnabled(false);

                    // 延迟 300ms 等待后端完全停止
                    javax.swing.Timer delayTimer = new javax.swing.Timer(300, evt -> {
                        try {
                            playbackModel.resetMap();               // 清空 Redis 状态
                            playbackView.getPlaybackMapView().resetDisplay();  // 清空渲染缓存
                            playbackView.resetComboBox();           // 清除记录选择
                            playbackView.refreshProgressSlider();   // 重置进度条
                            playbackView.getResetButton().setEnabled(true);
                            log.info("回放重置完成");
                        } catch (Exception ex) {
                            log.error("重置回放失败: {}", ex.getMessage(), ex);
                            playbackView.getResetButton().setEnabled(true);
                            JOptionPane.showMessageDialog(playbackView.getFrame(),
                                    "重置失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    delayTimer.setRepeats(false);  // 只执行一次
                    delayTimer.start();
                } catch (Exception e) {
                    log.error("发送停止命令失败: {}", e.getMessage(), e);
                    playbackView.getResetButton().setEnabled(true);
                    JOptionPane.showMessageDialog(playbackView.getFrame(),
                            "重置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // ==================== 暂停 / 继续按钮 ====================
        playbackView.getPauseButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (isPaused) {
                    try {
                        StartProducer.sendBackMessage();
                        playbackView.getPauseButton().setText("暂停");
                        isPaused = false;
                        try (Jedis j = RedisConnect.getConnected()) {
                            j.hset("Save", "playback_state", "playing");
                        }
                        log.info("回放继续");
                    } catch (Exception e) {
                        log.error("继续回放失败: {}", e.getMessage(), e);
                        JOptionPane.showMessageDialog(playbackView.getFrame(),
                                "继续回放失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    try {
                        StartProducer.sendStopMessage();
                        playbackView.getPauseButton().setText("继续");
                        isPaused = true;
                        try (Jedis j = RedisConnect.getConnected()) {
                            j.hset("Save", "playback_state", "paused");
                        }
                        log.info("回放暂停");
                    } catch (Exception e) {
                        log.error("暂停回放失败: {}", e.getMessage(), e);
                        JOptionPane.showMessageDialog(playbackView.getFrame(),
                                "暂停回放失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        // ==================== 记录选择下拉框 ====================
        playbackView.getComboBox().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (syncingComboUI) return; // 同步触发时不重复写入 Redis
                JComboBox<String> comboBox = (JComboBox<String>) actionEvent.getSource();
                int selectedIndex = comboBox.getSelectedIndex();
                if (selectedIndex < 0) return;  // 未选中（重置后）

                int fileNo = selectedIndex + 1;
                playbackModel.setExplorationChoice(String.valueOf(fileNo));

                // 加载该记录的第一帧，让用户看到初始画面
                playbackModel.restoreFrame(fileNo, 0);
                playbackView.getPlaybackMapView().syncFromRedis();

                // 刷新进度滑块
                playbackView.refreshProgressSlider();
                log.info("已选择记录: {} (fileNo={})", comboBox.getSelectedItem(), fileNo);
            }
        });

        // ==================== 速度切换 ====================
        ActionListener speedListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (syncingSpeedUI) return; // 同步触发时不重复写入 Redis
                String speed;
                if (actionEvent.getSource() == playbackView.getHalfSpeedButton()) {
                    speed = "0.5";
                } else if (actionEvent.getSource() == playbackView.getDoubleSpeedButton()) {
                    speed = "2.0";
                } else {
                    speed = "1.0";
                }
                playbackModel.setSpeedChoice(speed);
                playbackView.getPlaybackMapView().onSpeedChanged(Double.parseDouble(speed));
                log.info("回放速度: {}x", speed);
            }
        };
        playbackView.getHalfSpeedButton().addActionListener(speedListener);
        playbackView.getNormalSpeedButton().addActionListener(speedListener);
        playbackView.getDoubleSpeedButton().addActionListener(speedListener);

        // ==================== 进度滑块拖动 ====================
        playbackView.getProgressSlider().addChangeListener(e -> {
            // 忽略定时器触发的 setValue（由 updatingFromTimer 标记控制）
            if (playbackView.isUpdatingFromTimer()) return;
            // 只在用户松开鼠标后触发（非"正在拖动中"）
            if (!playbackView.getProgressSlider().getValueIsAdjusting()) {
                int frame = playbackView.getProgressSlider().getValue();
                int fileNo = playbackModel.getCurrentFileNo();
                if (fileNo > 0) {
                    boolean ok = playbackModel.restoreFrame(fileNo, frame);
                    if (ok) {
                        playbackView.getPlaybackMapView().syncFromRedis();
                    }
                    log.info("回放进度跳转: fileNo={} frame={} result={}", fileNo, frame, ok);
                }
            }
        });
    }

    // ==================== UI 状态同步（跨客户端）====================

    /** 启动 UI 同步定时器：每 500ms 从 Redis 拉取并更新 UI 状态 */
    private void startUISyncTimer() {
        uiSyncTimer = new javax.swing.Timer(500, e -> syncUIFromRedis());
        uiSyncTimer.start();
        log.info("Playback UI同步定时器已启动 (500ms间隔)");
    }

    /** 从 Redis 读取其他客户端写入的回放状态并更新本地 UI */
    private void syncUIFromRedis() {
        try (Jedis jedis = RedisConnect.getConnected()) {
            // 同步回放速度单选按钮
            double speed = playbackModel.getSpeed();
            syncingSpeedUI = true;
            if (Math.abs(speed - 0.5) < 0.01) {
                playbackView.getHalfSpeedButton().setSelected(true);
            } else if (Math.abs(speed - 2.0) < 0.01) {
                playbackView.getDoubleSpeedButton().setSelected(true);
            } else {
                playbackView.getNormalSpeedButton().setSelected(true);
            }
            syncingSpeedUI = false;

            // 同步回放暂停/播放状态
            String state = jedis.hget("Save", "playback_state");
            if ("paused".equals(state) && !isPaused) {
                isPaused = true;
                playbackView.getPauseButton().setText("继续");
            } else if ("playing".equals(state) && isPaused) {
                isPaused = false;
                playbackView.getPauseButton().setText("暂停");
            }

            // 同步记录选择下拉框
            int fileNo = playbackModel.getCurrentFileNo();
            if (fileNo > 0 && fileNo <= playbackView.getComboBox().getItemCount()) {
                int idx = fileNo - 1;
                if (playbackView.getComboBox().getSelectedIndex() != idx) {
                    syncingComboUI = true;
                    playbackView.getComboBox().setSelectedIndex(idx);
                    syncingComboUI = false;
                }
            }
        } catch (Exception ex) {
            log.debug("Playback UI sync from Redis failed: {}", ex.getMessage());
        }
    }

    private void startExplorationMonitor() {
        if (explorationMonitor != null && explorationMonitor.isRunning()) {
            return;  // 已在运行
        }
        explorationMonitor = new javax.swing.Timer(500, e -> {
            if (playbackView.getPlaybackMapView().isFallExplored() == 1) {
                try {
                    StartProducer.sendStopMessage();
                    log.info("回放: 地图已完全探索，自动停止");
                    JOptionPane.showMessageDialog(playbackView.getFrame(), "地图已完全探索！");
                } catch (Exception ex) {
                    log.error("自动停止失败: {}", ex.getMessage());
                }
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });
        explorationMonitor.start();
    }

    public void show() {
        playbackView.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        new PlaybackController().show();
    }
}
