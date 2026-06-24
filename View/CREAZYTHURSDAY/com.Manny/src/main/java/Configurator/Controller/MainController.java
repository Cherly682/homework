package Configurator.Controller;

import Configurator.Model.MapModel;
import Configurator.View.MainView;
import Configurator.View.MapView;
import Utils.RedisConnect;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private MainView mainView;
    private MapView mapView;
    private MapModel mapModel;
    private final int DEFAULT_MAPSIZE = 20;
    private Timer timer;  // 巡检完成监控定时器
    private javax.swing.Timer uiSyncTimer; // UI 状态同步定时器（跨客户端）
    private volatile boolean syncingUI = false; // 守卫：防止同步回环


    public MainController() throws Exception {
        mapModel = new MapModel(DEFAULT_MAPSIZE);
        mapView = new MapView(mapModel);
        mainView = new MainView(mapView);
        log.info("MainController initialized: default map={}x{}", DEFAULT_MAPSIZE, DEFAULT_MAPSIZE);

        updateStatusLabels();
        setupEventListeners();
        startExplorationMonitor();
        startUISyncTimer();
    }

    private void setupEventListeners() {

        // ==================== 地图配置 ====================

        mainView.getDefaultMapRadio().addActionListener(e -> {
            if (!syncingUI) {
                log.info("Map config: default map selected (20x20)");
                mainView.getCarPlacementToggle().setSelected(false);
                mainView.getBlockPlacementToggle().setSelected(false);
                mapModel = new MapModel(DEFAULT_MAPSIZE);
                mapView.setModel(mapModel);
                updateStatusLabels();
                mapView.repaint();
            }
        });

        // 自定义地图：弹出输入框让用户输入边长
        mainView.getCustomMapRadio().addActionListener(e -> {
            if (syncingUI) return; // 同步触发时不弹对话框
            String sizeStr = JOptionPane.showInputDialog(mainView.getFrame(),
                    "请输入正方形地图边长:", "自定义地图", JOptionPane.QUESTION_MESSAGE);
            if (sizeStr != null && !sizeStr.isEmpty()) {
                try {
                    int size = Integer.parseInt(sizeStr);
                    if (size <= 0 || size > 100) {
                        JOptionPane.showMessageDialog(mainView.getFrame(), "请输入 1-100 之间的数字！");
                        resetToDefaultMap();
                        return;
                    }
                    log.info("Map config: custom map selected ({}x{})", size, size);
                    rebuildModel(size);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(mainView.getFrame(), "请输入有效的数字！");
                    resetToDefaultMap();
                }
            } else {
                resetToDefaultMap();
            }
        });

        // ==================== 小车配置 ====================

        // 随机生成小车
        mainView.getAutoAddCarBtn().addActionListener(e -> {
            int remaining = mapModel.getMaxCars() - mapModel.getCarCount();
            if (remaining <= 0) {
                JOptionPane.showMessageDialog(mainView.getFrame(), "已达到最大小车数量！");
                return;
            }
            String numsStr = JOptionPane.showInputDialog(mainView.getFrame(),
                    "请输入随机小车数量（剩余容量: " + remaining + "):");
            if (numsStr != null && !numsStr.isEmpty()) {
                try {
                    int nums = Integer.parseInt(numsStr);
                    if (nums <= 0) return;
                    // 限制不超过剩余容量
                    if (nums > remaining) {
                        JOptionPane.showMessageDialog(mainView.getFrame(),
                                "输入数量超过剩余容量，实际生成 " + remaining + " 辆");
                        nums = remaining;
                    }
                    int beforeCount = mapModel.getCarCount();
                    mapModel.autoAddCar(nums);
                    int actualGenerated = mapModel.getCarCount() - beforeCount;
                    log.info("Cars auto-added: requested={} actual={} total={}",
                            nums, actualGenerated, mapModel.getCarCount());
                    mapView.repaint();
                    updateStatusLabels();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(mainView.getFrame(), "请输入有效的数字！");
                }
            }
        });

        // 点击放置小车
        mainView.getCarPlacementToggle().addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                mainView.getBlockPlacementToggle().setSelected(false);
                log.info("Car manual placement mode ENABLED");
                enableCarPlacementMode();
            } else {
                mapView.removeCustomMouseListener();
                log.info("Car manual placement mode DISABLED");
            }
        });

        // ==================== 障碍物配置 ====================

        mainView.getAutoAddBlockBtn().addActionListener(e -> {
            String numsStr = JOptionPane.showInputDialog(mainView.getFrame(),
                    "请输入随机障碍物数量（最多 " + mapModel.getMaxBlocks() + "):");
            if (numsStr != null && !numsStr.isEmpty()) {
                try {
                    int nums = Integer.parseInt(numsStr);
                    mapModel.autoAddBlock(nums);
                    log.info("Blocks auto-added: {} blocks, total blockCount={}",
                            nums, mapModel.getBlockCount());
                    mapView.repaint();
                    updateStatusLabels();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(mainView.getFrame(), "请输入有效的数字！");
                }
            }
        });

        mainView.getBlockPlacementToggle().addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                mainView.getCarPlacementToggle().setSelected(false);
                log.info("Block manual placement mode ENABLED");
                enableBlockPlacementMode();
            } else {
                mapView.removeCustomMouseListener();
                log.info("Block manual placement mode DISABLED");
            }
        });

        // ==================== 算法选择 ====================

        mainView.getAStarRadio().addActionListener(e -> {
            if (!syncingUI) {
                mapModel.setAStarAlgorithm();
                log.info("Algorithm selected: A*");
            }
        });
        mainView.getAStar2Radio().addActionListener(e -> {
            if (!syncingUI) {
                mapModel.setASTAR2Algorithm();
                log.info("Algorithm selected: Bidirectional A*");
            }
        });
        mainView.getDijkstraRadio().addActionListener(e -> {
            if (!syncingUI) {
                mapModel.setDIJKASTRAAlgorithm();
                log.info("Algorithm selected: Dijkstra");
            }
        });

        // ==================== 控制按钮 ====================


        mainView.getStartButton().addActionListener(e -> {
            try {
                startExplorationMonitor();
                StartProducer.sendStartMessage();
                mapModel.getBlocksFromRedis();
                mainView.startTimer();
                // 写入 Redis 供其他客户端同步计时器和 isStart 状态
                long now = System.currentTimeMillis();
                try (Jedis j = RedisConnect.getConnected()) {
                    j.set("inspection_start_time", String.valueOf(now));
                    log.info("Redis写入: inspection_start_time={}", now);
                }
                log.info("Exploration STARTED");
            } catch (Exception ex) {
                log.error("Failed to start exploration: {}", ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
            mapView.setIsStart(true);
            mapView.repaint();
        });


        mainView.getStopButton().addActionListener(e -> {
            try {
                StartProducer.sendStopMessage();
                mainView.stopTimer();
                mapView.setIsStart(false);
                // 删除 Redis 标记，其他客户端检测到后停止计时器
                try (Jedis j = RedisConnect.getConnected()) {
                    j.del("inspection_start_time");
                }
                log.info("Exploration STOPPED");
            } catch (Exception ex) {
                log.error("Failed to stop exploration: {}", ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        });


        mainView.getResetButton().addActionListener(e -> {
            log.info("Exploration RESET");
            resetExperiment();
        });
    }

    // ==================== 手动放置模式 ====================


    private void enableCarPlacementMode() {
        mapView.removeCustomMouseListener();
        mapView.addCustomMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                int tileX = e.getX() / mapModel.getTileSize();
                int tileY = e.getY() / mapModel.getTileSize();

                if (tileX >= 0 && tileX < mapModel.getMapSize()
                        && tileY >= 0 && tileY < mapModel.getMapSize()) {
                    // 检查冲突
                    if (!mapModel.isPositionBlocked(tileX, tileY)
                            && !mapModel.isPositionExplored(tileX, tileY)) {
                        // 检查上限
                        if (mapModel.getCarCount() >= mapModel.getMaxCars()) {
                            JOptionPane.showMessageDialog(mainView.getFrame(),
                                    "已达到最大小车数量（" + mapModel.getMaxCars() + ")!");
                            mainView.getCarPlacementToggle().setSelected(false);
                            return;
                        }
                        mapModel.addCar(tileX, tileY);
                        log.info("Car placed manually at ({},{}), carCount={}",
                                tileX, tileY, mapModel.getCarCount());
                        mapView.repaint();
                        updateStatusLabels();
                    }
                }
            }
        });
    }


    private void enableBlockPlacementMode() {
        mapView.removeCustomMouseListener();
        mapView.addCustomMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int tileX = e.getX() / mapModel.getTileSize();
                int tileY = e.getY() / mapModel.getTileSize();

                if (tileX >= 0 && tileX < mapModel.getMapSize()
                        && tileY >= 0 && tileY < mapModel.getMapSize()) {
                    if (!mapModel.isPositionBlocked(tileX, tileY)
                            && !mapModel.isPositionExplored(tileX, tileY)) {
                        if (mapModel.getBlockCount() >= mapModel.getMaxBlocks()) {
                            JOptionPane.showMessageDialog(mainView.getFrame(),
                                    "已达到最大障碍物数量（" + mapModel.getMaxBlocks() + ")!");
                            mainView.getBlockPlacementToggle().setSelected(false);
                            return;
                        }
                        mapModel.addBlock(tileX, tileY);
                        log.info("Block placed manually at ({},{}), blockCount={}",
                                tileX, tileY, mapModel.getBlockCount());
                        mapView.repaint();
                        updateStatusLabels();
                    }
                }
            }
        });
    }

    // ==================== 辅助方法 ====================


    private void rebuildModel(int size) {
        mapModel = new MapModel(size);
        mapView.setModel(mapModel);
        mainView.updateMapView(mapView);
        mainView.getCarPlacementToggle().setSelected(false);
        mainView.getBlockPlacementToggle().setSelected(false);
        updateStatusLabels();
    }

    private void resetToDefaultMap() {
        SwingUtilities.invokeLater(() -> mainView.getDefaultMapRadio().setSelected(true));
    }


    private void resetExperiment() {
        try {
            StartProducer.sendStopMessage();
        } catch (Exception ex) {
            log.error("Stop message failed during reset: {}", ex.getMessage());
        }
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        mainView.resetTimer();
        mainView.getCarPlacementToggle().setSelected(false);
        mainView.getBlockPlacementToggle().setSelected(false);
        mainView.getDefaultMapRadio().setSelected(true);
        mainView.getAStarRadio().setSelected(true);

        // 清空 Redis 中的地图状态（重置操作应当清除所有运行数据）
        try (Jedis j = RedisConnect.getConnected()) {
            j.del("inspection_start_time");
            j.del("MapView");
            j.del("blockview");
            j.del("navigator_status");
            // 删除所有小车数据
            for (String key : mapModel.scanKeys("Cars:*")) {
                j.del(key);
            }
            // 删除所有任务队列
            for (String key : mapModel.scanKeys("*_task_queue")) {
                j.del(key);
            }
            log.info("Redis地图状态已清空（重置）");
        }

        mapModel = new MapModel(DEFAULT_MAPSIZE);
        mapModel.setAStarAlgorithm();
        mapView.setModel(mapModel);
        mainView.updateMapView(mapView);
        updateStatusLabels();
        mapView.repaint();
        log.info("Experiment reset complete: default map, A* algorithm");
    }

    private void updateStatusLabels() {
        mainView.setCarCount(mapModel.getKnownCarCount(), mapModel.getMaxCars());
        mainView.setBlockCount(mapModel.getKnownBlockCount(), mapModel.getMaxBlocks());
    }

    // ==================== UI 状态同步（跨客户端）====================

    /** 启动 UI 同步定时器：每 500ms 从 Redis 拉取并更新 UI 状态 */
    private void startUISyncTimer() {
        uiSyncTimer = new javax.swing.Timer(500, e -> syncUIFromRedis());
        uiSyncTimer.start();
        log.info("Configurator UI同步定时器已启动 (500ms间隔)");
    }

    private int syncCycleCount = 0;

    /** 从 Redis 读取其他客户端写入的状态并更新本地 UI */
    private void syncUIFromRedis() {
        syncCycleCount++;
        try (Jedis jedis = RedisConnect.getConnected()) {
            // 每 20 轮（约10秒）打印一次心跳
            if (syncCycleCount % 20 == 0) {
                log.info("UI同步心跳 #{}: timerRunning={}, inspection_start_time={}",
                        syncCycleCount, mainView.isTimerRunning(),
                        jedis.get("inspection_start_time"));
            }
            // 同步算法单选按钮
            String algo = jedis.get("Algorithm");
            if (algo != null) {
                syncingUI = true;
                switch (algo.trim()) {
                    case "0": mainView.getAStarRadio().setSelected(true); break;
                    case "1": mainView.getAStar2Radio().setSelected(true); break;
                    case "2": mainView.getDijkstraRadio().setSelected(true); break;
                }
                syncingUI = false;
            }

            // 同步地图类型单选按钮（根据当前 map_width 推断）
            String widthStr = jedis.get("map_width");
            if (widthStr != null && !widthStr.trim().isEmpty()) {
                int width = Integer.parseInt(widthStr.trim());
                syncingUI = true;
                if (width == DEFAULT_MAPSIZE) {
                    mainView.getDefaultMapRadio().setSelected(true);
                } else {
                    mainView.getCustomMapRadio().setSelected(true);
                }
                syncingUI = false;
            }

            // 同步计时器：只在本地计时器未运行时才从 Redis 同步启动
            String startTimeStr = jedis.get("inspection_start_time");
            if (startTimeStr != null && !startTimeStr.trim().isEmpty()) {
                if (!mainView.isTimerRunning()) {
                    long redisStartTime = Long.parseLong(startTimeStr.trim());
                    log.info("UI同步: 检测到Redis计时器标记，启动本地计时器 (redisStartTime={})", redisStartTime);
                    mainView.syncStartTime(redisStartTime);
                }
            } else {
                if (mainView.isTimerRunning()) {
                    log.info("UI同步: Redis计时器标记已清除，重置本地计时器");
                    mainView.resetTimer();
                }
            }
        } catch (Exception ex) {
            log.debug("UI sync from Redis failed: {}", ex.getMessage());
        }

        // 同步小车/障碍物计数标签（数据由 MapView 每 200ms 从 Redis 刷新）
        updateStatusLabels();
    }

    // ==================== 巡检完成监控 ====================


    private void startExplorationMonitor() {
        if (timer != null && timer.isRunning()) {
            return;
        }
        timer = new Timer(1000, e -> {
            if (mapView.isFallExplored()) {
                try {
                    StartProducer.sendStopMessage();
                    mainView.stopTimer();
                    mapView.setIsStart(false);
                    // 删除 Redis 中的计时标记，防止 UI 同步定时器重新启动计时器
                    try (Jedis j = RedisConnect.getConnected()) {
                        j.del("inspection_start_time");
                        log.info("Redis: inspection_start_time 已删除（探索自动完成）");
                    }
                    if (mapView.hasUnreachableFreeCells()) {
                        JOptionPane.showMessageDialog(mainView.getFrame(),
                                "巡检完成！\n可探索区域已全部巡检完毕，"
                                + "部分地块因被障碍物包围而无法进入。",
                                "巡检完成（部分不可达）", JOptionPane.INFORMATION_MESSAGE);
                        log.info("Exploration COMPLETE with unreachable cells remaining");
                    } else {
                        JOptionPane.showMessageDialog(mainView.getFrame(), "地图已完全探索！");
                        log.info("Exploration COMPLETE: map fully explored");
                    }
                } catch (Exception ex) {
                    log.error("Failed to send stop on completion: {}", ex.getMessage());
                    throw new RuntimeException(ex);
                }
                ((Timer) e.getSource()).stop();
                timer = null;
            }
        });
        timer.start();
    }


    public static void main(String[] args) throws Exception {
        new MainController();
    }
}
