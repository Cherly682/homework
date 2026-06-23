package Configurator.View;

import Utils.RedisConnect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import java.awt.*;

public class StatusPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(StatusPanel.class);

    // 五个状态标签（JLabel）
    private final JLabel controllerStatus = new JLabel();
    private final JLabel navigatorStatus = new JLabel();
    private final JLabel carStatus = new JLabel();
    private final JLabel recorderStatus = new JLabel();
    private final JLabel exploreStatus = new JLabel();

    // 每 2 秒自动刷新一次
    private final javax.swing.Timer timer;

    public StatusPanel() {

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(createSectionBorder("系统运行状态"));
        setMaximumSize(new Dimension(280, 140));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        Font statusFont = new Font("Microsoft YaHei", Font.PLAIN, 12);


        controllerStatus.setFont(statusFont);
        controllerStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        controllerStatus.setText("🟡 Controller: 检测中...");

        navigatorStatus.setFont(statusFont);
        navigatorStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigatorStatus.setText("🟡 Navigator: 检测中...");

        carStatus.setFont(statusFont);
        carStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        carStatus.setText("🟡 小车: 检测中...");

        recorderStatus.setFont(statusFont);
        recorderStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        recorderStatus.setText("🟡 Recorder: 检测中...");

        exploreStatus.setFont(statusFont);
        exploreStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        exploreStatus.setText("📊 探索: 检测中...");


        add(controllerStatus);
        add(Box.createVerticalStrut(2));
        add(navigatorStatus);
        add(Box.createVerticalStrut(2));
        add(carStatus);
        add(Box.createVerticalStrut(2));
        add(recorderStatus);
        add(Box.createVerticalStrut(2));
        add(exploreStatus);


        timer = new javax.swing.Timer(2000, e -> refreshStatus());
        timer.start();
        log.info("StatusPanel initialized: monitoring every 2s");
    }


    private void refreshStatus() {
        try {
            Jedis jedis = RedisConnect.getConnected();

            // 1. Controller 状态
            boolean controllerAlive = jedis.exists("controller:lock");
            controllerStatus.setText(controllerAlive ? "🟢 Controller: 运行中" : "🔴 Controller: 未运行");

            // 2. Navigator 状态
            var navMap = jedis.hgetAll("navigator_status");
            if (navMap.isEmpty()) {
                navigatorStatus.setText("🟠 Navigator: 未连接");
            } else {
                int busy = 0;
                for (String v : navMap.values()) {
                    if ("true".equalsIgnoreCase(v)) busy++;
                }
                int total = navMap.size();
                navigatorStatus.setText("🟢 Navigator: " + total + " 个 worker（"
                        + busy + " 繁忙 / " + (total - busy) + " 空闲）");
            }

            // 3. 小车状态
            var scanResult = jedis.scan("0", new redis.clients.jedis.ScanParams().match("Cars:*"));
            int carCount = scanResult.getResult().size();
            carStatus.setText(carCount > 0 ? "🟢 小车: " + carCount + " 辆在线" : "🟠 小车: 无小车");

            // 4. Recorder 状态
            var saveMap = jedis.hgetAll("Save");
            boolean recording = saveMap.containsKey("file_num") && !"0".equals(saveMap.get("file_num"));
            recorderStatus.setText(recording ? "🟢 Recorder: 录制中" : "⚪ Recorder: 空闲");

            // 5. 探索进度
            exploreProgress(jedis);

            jedis.close();
        } catch (Exception e) {
            log.debug("StatusPanel refresh failed: {}", e.getMessage());
            controllerStatus.setText("🔴 状态读取失败");
        }
    }


    private void exploreProgress(Jedis jedis) {
        try {
            String widthStr = jedis.get("map_width");
            String heightStr = jedis.get("map_height");
            int w = (widthStr != null) ? Integer.parseInt(widthStr) : 20;
            int h = (heightStr != null) ? Integer.parseInt(heightStr) : 20;
            int total = w * h;

            byte[] mapBytes = jedis.get("MapView".getBytes());
            if (mapBytes == null || mapBytes.length == 0) {
                exploreStatus.setText("📊 探索: 0%");
                return;
            }

            int explored = 0;
            int blockCount = 0;
            byte[] blockBytes = jedis.get("blockview".getBytes());
            for (int i = 0; i < total; i++) {
                int byteIdx = i / 8;
                int bitIdx = 7 - (i % 8);
                if (byteIdx < mapBytes.length && ((mapBytes[byteIdx] >> bitIdx) & 1) != 0) {
                    explored++;
                }
                if (blockBytes != null && byteIdx < blockBytes.length
                        && ((blockBytes[byteIdx] >> bitIdx) & 1) != 0) {
                    blockCount++;
                }
            }

            // 可探索区域 = 总面积 - 障碍物
            int accessible = total - blockCount;
            double pct = accessible > 0 ? (100.0 * explored / accessible) : 0;
            exploreStatus.setText(String.format("📊 探索: %d/%d (%.1f%%)", explored, accessible, pct));
        } catch (Exception e) {
            exploreStatus.setText("📊 探索: --");
        }
    }

    public void stop() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
    }

    private javax.swing.border.TitledBorder createSectionBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(160, 160, 160), 1),
                title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.PLAIN, 12));
    }
}
