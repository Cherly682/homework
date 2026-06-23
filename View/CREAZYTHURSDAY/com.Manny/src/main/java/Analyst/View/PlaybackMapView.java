package Analyst.View;

import Analyst.Model.PlaybackModel;
import Utils.ImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class PlaybackMapView extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(PlaybackMapView.class);
    private PlaybackModel playbackModel;
    private Jedis jedis;
    private javax.swing.Timer timer;

    // ==================== 本地缓存 ====================
    private boolean[][] exploredCache;
    private boolean[][] blockedCache;
    private List<Map<String, String>> carCache = new ArrayList<>();
    private Map<String, BufferedImage> imageCache = new HashMap<>();

    // ==================== 离屏渲染 ====================
    private BufferedImage offscreenImage;

    // ==================== 状态追踪 ====================
    private Map<String, Point> lastCarPositions = new HashMap<>();
    private Map<String, String> lastCarDirections = new HashMap<>();
    private boolean isFirstRender = true;
    private int renderCount = 0;


    private double currentSpeed = 1.0;


    public PlaybackMapView(PlaybackModel sharedModel, Jedis sharedJedis) {
        this.jedis = sharedJedis;
        this.playbackModel = sharedModel;
        setPreferredSize(new Dimension(750, 700));
        int size = playbackModel.getMapSize();
        this.exploredCache = new boolean[size][size];
        this.blockedCache = new boolean[size][size];
        this.isFirstRender = true;
        this.currentSpeed = playbackModel.getSpeed();

        // 创建定时渲染器：间隔由当前速度决定
        timer = new javax.swing.Timer(getTimerDelay(), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long t0 = System.nanoTime();

                // 检测速度变化，动态调整间隔
                double speed = playbackModel.getSpeed();
                if (Math.abs(speed - currentSpeed) > 0.01) {
                    currentSpeed = speed;
                    timer.setDelay(getTimerDelay());
                }

                updateCacheFromRedis();  // 从 Redis 获取最新帧数据
                repaint();               // 触发重绘

                log.debug("PlaybackMapView 渲染周期: {}ms",
                        (System.nanoTime() - t0) / 1_000_000L);
            }
        });
        timer.start();
        log.info("PlaybackMapView 初始化: {}x{} 定时器={}ms", size, size, getTimerDelay());
    }


    private int getTimerDelay() {
        if (currentSpeed <= 0) currentSpeed = 1.0;
        return Math.max(50, Math.min(2000, (int) Math.round(500.0 / currentSpeed)));
    }


    public void onSpeedChanged(double speed) {
        this.currentSpeed = speed;
        timer.setDelay(getTimerDelay());
        log.info("PlaybackMapView 速度切换: {}x, 定时器={}ms", speed, getTimerDelay());
    }


    public float isFallExplored() {
        int size = playbackModel.getMapSize();
        if (blockedCache == null || blockedCache.length != size
                || exploredCache == null || exploredCache.length != size) {
            return 0.0f;
        }

        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        for (Map<String, String> carInfo : carCache) {
            try {
                int x = Integer.parseInt(carInfo.get("x"));
                int y = Integer.parseInt(carInfo.get("y"));
                if (x >= 0 && x < size && y >= 0 && y < size && !blockedCache[x][y]) {
                    String key = x + "," + y;
                    if (visited.add(key)) queue.add(key);
                }
            } catch (Exception ignored) {}
        }

        if (visited.isEmpty()) {
            int cnt = 0;
            for (boolean[] row : exploredCache) {
                for (boolean cell : row) {
                    if (cell) cnt++;
                }
            }
            return (float) (cnt * 1.0 / playbackModel.getArea());
        }

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            String[] parts = cur.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cy = Integer.parseInt(parts[1]);
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                if (blockedCache[nx][ny]) continue;
                String next = nx + "," + ny;
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        for (String key : visited) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            if (!exploredCache[x][y]) {
                return 0.0f;
            }
        }
        return 1.0f;
    }


    private void updateCacheFromRedis() {
        long t0 = System.nanoTime();

        // 第一件事：从 Redis 同步最新地图尺寸
        // 这是修复的核心——不再依赖 PlaybackModel 构造时缓存的旧值
        boolean sizeChanged = playbackModel.syncMapSizeFromRedis();
        int modelSize = playbackModel.getMapSize();
        int cacheSize = exploredCache != null ? exploredCache.length : 0;

        // 地图尺寸变了重新分配缓存
        if (sizeChanged || cacheSize != modelSize) {
            exploredCache = new boolean[modelSize][modelSize];
            blockedCache = new boolean[modelSize][modelSize];
            carCache.clear();
            imageCache.clear();
            lastCarPositions.clear();
            lastCarDirections.clear();
            isFirstRender = true;
            offscreenImage = null;  // 尺寸变了，离屏图像也失效
            log.info("PlaybackMapView 地图尺寸变更: {} -> {} (来自Redis map_width)", cacheSize, modelSize);
            SwingUtilities.invokeLater(this::repaint);
        }

        // 批量读取探索位图
        byte[] mapViewBytes = jedis.get("MapView".getBytes());
        if (mapViewBytes != null && mapViewBytes.length > 0) {
            for (int i = 0; i < modelSize; i++) {
                for (int j = 0; j < modelSize; j++) {
                    long bitIndex = (long) i * modelSize + j;
                    int byteIndex = (int) (bitIndex / 8);
                    int bitInByte = 7 - (int)(bitIndex % 8);
                    if (byteIndex < mapViewBytes.length) {
                        exploredCache[i][j] = ((mapViewBytes[byteIndex] >> bitInByte) & 1) != 0;
                    }
                }
            }
        }

        // 批量读取障碍物位图
        byte[] blockViewBytes = jedis.get("blockview".getBytes());
        if (blockViewBytes != null && blockViewBytes.length > 0) {
            for (int i = 0; i < modelSize; i++) {
                for (int j = 0; j < modelSize; j++) {
                    long bitIndex = (long) i * modelSize + j;
                    int byteIndex = (int) (bitIndex / 8);
                    int bitInByte = 7 - (int)(bitIndex % 8);
                    if (byteIndex < blockViewBytes.length) {
                        blockedCache[i][j] = ((blockViewBytes[byteIndex] >> bitInByte) & 1) != 0;
                    }
                }
            }
        }

        // 扫描小车数据并检测变化
        List<Map<String, String>> newCarCache = new ArrayList<>();
        boolean hasCarUpdate = false;

        try {
            String cursor = "0";
            ScanParams scanParams = new ScanParams().match("Cars:*");
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();
                for (String key : keys) {
                    Map<String, String> carInfo = jedis.hgetAll(key);
                    if (carInfo != null && !carInfo.isEmpty()) {
                        newCarCache.add(carInfo);
                        String carId = key.replace("Cars:", "");
                        int x = Integer.parseInt(carInfo.get("x"));
                        int y = Integer.parseInt(carInfo.get("y"));
                        String curDirection = carInfo.get("direction");
                        Point currentPos = new Point(x, y);
                        String lastDirection = lastCarDirections.get(carId);
                        Point lastPos = lastCarPositions.get(carId);
                        if (lastDirection == null || !lastDirection.equals(curDirection)
                                || lastPos == null || !lastPos.equals(currentPos)) {
                            hasCarUpdate = true;
                        }
                        lastCarPositions.put(carId, currentPos);
                        lastCarDirections.put(carId, curDirection);
                    }
                }
                cursor = scanResult.getCursor();
            } while (!cursor.equals("0"));

            if (hasCarUpdate || carCache.size() != newCarCache.size()) {
                carCache = newCarCache;
            }
        } catch (Exception e) {
            log.error("PlaybackMapView 读取小车数据失败: {}", e.getMessage(), e);
        }

        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
        if (renderCount % 5 == 0 || hasCarUpdate) {
            log.debug("PlaybackMapView 缓存更新: {}ms, cars={} exploredBits={}",
                    elapsed, carCache.size(),
                    mapViewBytes != null ? mapViewBytes.length : 0);
        }
    }

    //离屏渲染
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        long t0 = System.nanoTime();
        renderCount++;

        int mapSize = playbackModel.getMapSize();
        int tileSize = playbackModel.getTileSize();
        int tileSizeMinus1 = Math.max(1, tileSize - 1);

        int panelW = getWidth();
        int panelH = getHeight();
        if (panelW <= 0 || panelH <= 0) return;

        // 确保离屏图像存在且尺寸正确
        if (offscreenImage == null
                || offscreenImage.getWidth() != panelW
                || offscreenImage.getHeight() != panelH) {
            offscreenImage = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_RGB);
            isFirstRender = true;
        }

        Graphics2D g2d = offscreenImage.createGraphics();
        if (isFirstRender) {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, panelW, panelH);
            isFirstRender = false;
        }

        if (exploredCache == null || blockedCache == null || exploredCache.length != mapSize) {
            updateCacheFromRedis();
        }

        // 颜色方案
        Color bgColor = new Color(0xCA, 0xCE, 0xD1);
        Color blockColor = new Color(0x41, 0x4B, 0x4E);
        Color exploredColor = new Color(0x88, 0xAD, 0xA6);

        // 绘制所有格子
        for (int i = 0; i < mapSize; i++) {
            int x = i * tileSize;
            for (int j = 0; j < mapSize; j++) {
                int y = j * tileSize;
                boolean explored = exploredCache[i][j];
                boolean blocked = blockedCache[i][j];
                Color color = blocked ? blockColor : (explored ? exploredColor : bgColor);
                g2d.setColor(color);
                g2d.fillRect(x, y, tileSizeMinus1, tileSizeMinus1);
            }
        }

        // 绘制所有小车
        for (Map<String, String> carInfo : carCache) {
            try {
                int x = Integer.parseInt(carInfo.get("x"));
                int y = Integer.parseInt(carInfo.get("y"));
                String direction = carInfo.get("direction");
                BufferedImage carImg = getCachedCarImage(direction);
                if (carImg != null) {
                    g2d.drawImage(carImg, x * tileSize, y * tileSize,
                            tileSizeMinus1, tileSizeMinus1, null);
                }
            } catch (Exception e) {
                // 单个小车渲染错误跳过
            }
        }
        g2d.dispose();

        // ========== 将离屏图像一次性绘制到屏幕 ==========
        g.drawImage(offscreenImage, 0, 0, this);

        if (renderCount % 10 == 0) {
            log.info("PlaybackMapView 渲染 #{}: {} 辆小车, 地图={}x{}, tileSize={}, 耗时={}ms, 速度={}x",
                    renderCount, carCache.size(), mapSize, mapSize, tileSize,
                    (System.nanoTime() - t0) / 1_000_000L, currentSpeed);
        }
    }


    private BufferedImage getCachedCarImage(String direction) {
        if (imageCache.containsKey(direction)) {
            return imageCache.get(direction);
        }
        try {
            BufferedImage img = ImageLoader.load("car" + direction + ".png");
            imageCache.put(direction, img);
            return img;
        } catch (Exception e) {
            log.error("PlaybackMapView 无法加载小车图片: car{}.png", direction);
            return null;
        }
    }


    public void syncFromRedis() {
        offscreenImage = null;
        isFirstRender = true;
        updateCacheFromRedis();
        if (getWidth() > 0 && getHeight() > 0) {
            paintImmediately(0, 0, getWidth(), getHeight());  // 立即绘制，不等定时器
        } else {
            repaint();
        }
        log.debug("PlaybackMapView: syncFromRedis 完成, cars={}", carCache.size());
    }


    public void resetDisplay() {
        int size = playbackModel.getMapSize();
        exploredCache = new boolean[size][size];
        blockedCache = new boolean[size][size];
        carCache.clear();
        lastCarPositions.clear();
        lastCarDirections.clear();
        imageCache.clear();
        offscreenImage = null;
        isFirstRender = true;
        renderCount = 0;
        updateCacheFromRedis();
        if (getWidth() > 0 && getHeight() > 0) {
            paintImmediately(0, 0, getWidth(), getHeight());
        } else {
            repaint();
        }
        log.info("PlaybackMapView: resetDisplay 完成, {}x{}", size, size);
    }

    public PlaybackModel getPlaybackModel() {
        return playbackModel;
    }
}
