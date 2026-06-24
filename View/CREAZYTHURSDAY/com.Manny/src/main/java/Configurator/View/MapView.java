package Configurator.View;

import Configurator.Model.MapModel;
import Utils.ImageLoader;
import Utils.RedisConnect;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class MapView extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(MapView.class);
    private Jedis jedis;
    private MapModel model;
    private MouseAdapter mouseListener;
    private javax.swing.Timer timer;

    private final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {0, 0}};

    // ==================== 本地缓存 ====================
    private boolean[][] exploredCache;
    private boolean[][] blockCache;
    private List<Map<String, String>> carCache = new ArrayList<>();
    private Map<String, BufferedImage> imageCache = new HashMap<>();

    // 上次小车的位置和方向
    private Map<String, Point> lastCarPositions = new HashMap<>();
    private Map<String, String> lastCarDirections = new HashMap<>();

    private boolean isFirstRender = true;  // 首次渲染标记
    private boolean isStart = false;       // 是否正在巡检中
    private int renderCount = 0;           // 帧计数
    private StartProducer startProducer;

    //每200ms缓存更新，触发重绘
    public MapView(MapModel model) throws Exception {
        startProducer = new StartProducer();
        jedis = RedisConnect.getConnected();
        this.model = model;
        setPreferredSize(new Dimension(750, 700));

        timer = new javax.swing.Timer(200, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long t0 = System.nanoTime();
                updateCacheFromRedis();
                repaint();               // 触发界面重绘
                log.debug("MapView render cycle: {}ms",
                        (System.nanoTime() - t0) / 1_000_000L);
            }
        });
        timer.start();
        log.info("MapView initialized: {}x{} timer=200ms",
                model.getMapSize(), model.getMapSize());
    }

    /**
     * 确保 Redis 连接可用。如果当前连接已断开，自动从连接池获取新连接。
     */
    private void ensureConnection() {
        try {
            if (jedis == null || !jedis.isConnected() || !"PONG".equals(jedis.ping())) {
                renewConnection();
            }
        } catch (Exception e) {
            log.warn("MapView Redis connection check failed, renewing: {}", e.getMessage());
            renewConnection();
        }
    }

    private void renewConnection() {
        try {
            if (jedis != null) {
                try { jedis.close(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        jedis = RedisConnect.getConnected();
        log.info("MapView: Redis connection renewed");
    }

    //BFS可达性判断
    public boolean isFallExplored() {
        Set<String> reachable = bfsReachableLocal();
        if (reachable.isEmpty()) {
            int size = model.getMapSize();
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (!blockCache[i][j] && !exploredCache[i][j]) {
                        return false;
                    }
                }
            }
            return true;
        }
        int size = model.getMapSize();
        for (String key : reachable) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            if (x >= 0 && x < size && y >= 0 && y < size && !exploredCache[x][y]) {
                return false;
            }
        }
        return true;
    }


    public boolean hasUnreachableFreeCells() {
        Set<String> reachable = bfsReachableLocal();
        if (reachable.isEmpty()) return false;
        int size = model.getMapSize();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!blockCache[i][j] && !reachable.contains(i + "," + j)) {
                    return true;
                }
            }
        }
        return false;
    }


    private Set<String> bfsReachableLocal() {
        Set<String> visited = new HashSet<>();
        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        int size = model.getMapSize();

        for (Map<String, String> carInfo : carCache) {
            try {
                int x = Integer.parseInt(carInfo.get("x"));
                int y = Integer.parseInt(carInfo.get("y"));
                if (x >= 0 && x < size && y >= 0 && y < size && !blockCache[x][y]) {
                    String pos = x + "," + y;
                    if (visited.add(pos)) queue.add(pos);
                }
            } catch (Exception ignored) {}
        }

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            String cur = queue.poll();  // 取出队首
            String[] parts = cur.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cy = Integer.parseInt(parts[1]);
            // 探索四个方向
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                if (blockCache[nx][ny]) continue;
                String next = nx + "," + ny;
                if (visited.add(next)) queue.add(next);
            }
        }
        return visited;
    }

   //批量拉取bitmap降低时间复杂度
    private void updateCacheFromRedis() {
        ensureConnection();
        long t0 = System.nanoTime();

        // 从 Redis 同步最新地图尺寸（修复多客户端同步问题）
        boolean sizeChanged = model.syncMapSizeFromRedis();
        int size = model.getMapSize();

        if (exploredCache == null || exploredCache.length != size || sizeChanged) {
            exploredCache = new boolean[size][size];
            blockCache = new boolean[size][size];
            isFirstRender = true;
            log.info("MapView cache re-allocated: {}x{}", size, size);
        }
        if (blockCache == null || blockCache.length != size) {
            blockCache = new boolean[size][size];
            isFirstRender = true;
        }

        // 批量拉取位图字节数组
        int exploredDirty = 0;
        byte[] mapViewBytes = jedis.get("MapView".getBytes());
        byte[] blockViewBytes = jedis.get("blockview".getBytes());

        // 逐格解析位图到缓存数组
        int blockedCellCount = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                long index = (long) i * size + j;
                int byteIdx = (int) (index / 8);
                int bitIdx = 7 - (int) (index % 8);  // 大端序

                boolean explored = mapViewBytes != null
                        && byteIdx < mapViewBytes.length
                        && ((mapViewBytes[byteIdx] >> bitIdx) & 1) != 0;
                boolean blocked = blockViewBytes != null
                        && byteIdx < blockViewBytes.length
                        && ((blockViewBytes[byteIdx] >> bitIdx) & 1) != 0;
                if (blocked) blockedCellCount++;

                if (isFirstRender || exploredCache[i][j] != explored
                        || blockCache[i][j] != blocked) {
                    exploredDirty++;
                }
                exploredCache[i][j] = explored;
                blockCache[i][j] = blocked;
            }
        }
        model.setKnownBlockCount(blockedCellCount);

        // 扫描小车数据
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
                        // 检测小车是否移动
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
            // 每轮渲染周期更新模型中的小车计数（用于跨客户端同步状态标签）
            model.setKnownCarCount(newCarCache.size());
        } catch (Exception e) {
            log.error("MapView car read failed: {}", e.getMessage(), e);
        }

        if (isFirstRender) {
            isFirstRender = false;
        }

        // 从 Redis 同步巡检运行状态（跨客户端同步）
        String startTimeVal = jedis.get("inspection_start_time");
        boolean remoteIsStart = (startTimeVal != null && !startTimeVal.trim().isEmpty());
        if (remoteIsStart != this.isStart) {
            setIsStart(remoteIsStart);
        }

        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
        if (exploredDirty > 0 || hasCarUpdate) {
            log.debug("MapView cache update: exploredDirty={}, hasCarUpdate={}, time={}ms",
                    exploredDirty, hasCarUpdate, elapsed);
        }
    }

   //渲染方法
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        long t0 = System.nanoTime();
        renderCount++;

        Graphics2D g2d = (Graphics2D) g.create();

        int mapSize = model.getMapSize();
        // 取面板宽高的较小值，确保正方形地图完整显示
        int available = Math.min(getWidth(), getHeight());
        int tileSize = available > 50 ? available / mapSize : model.getTileSize();
        int tileSizeMinus1 = Math.max(1, tileSize - 1);

        // 颜色定义
        Color bgColor = new Color(0xCA, 0xCE, 0xD1);
        Color blockColor = new Color(0x41, 0x4B, 0x4E);
        Color exploredColor = new Color(0x88, 0xAD, 0xA6);

        // 缓存初始化
        if (exploredCache == null || blockCache == null) {
            updateCacheFromRedis();
        }

        // 绘制所有格子
        for (int i = 0; i < mapSize; i++) {
            for (int j = 0; j < mapSize; j++) {
                boolean blocked = blockCache != null && blockCache[i][j];
                boolean explored = exploredCache != null && exploredCache[i][j];
                Color color = blocked ? blockColor : (explored ? exploredColor : bgColor);
                g2d.setColor(color);
                g2d.fillRect(i * tileSize, j * tileSize, tileSizeMinus1, tileSizeMinus1);
            }
        }

        // 绘制所有小车
        for (Map<String, String> carInfo : carCache) {
            try {
                int x = Integer.parseInt(carInfo.get("x"));
                int y = Integer.parseInt(carInfo.get("y"));
                String direction = carInfo.get("direction");

                // 根据方向加载对应的小车图片
                BufferedImage carImg = getCachedCarImage(direction);
                if (carImg != null) {
                    g2d.drawImage(carImg, x * tileSize, y * tileSize,
                                  tileSizeMinus1, tileSizeMinus1, null);
                }
            } catch (Exception e) {
                // 单个小车渲染失败不影响整体
            }
        }

        g2d.dispose();

        // 每 5 帧打印一次详细日志
        if (renderCount % 5 == 0) {
            log.info("MapView render #{}: {} cars, tiles={}x{}, cost={}ms",
                    renderCount, carCache.size(), mapSize, mapSize,
                    (System.nanoTime() - t0) / 1_000_000L);
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
            log.error("MapView cannot load car image: car{}.png", direction);
            return null;
        }
    }


    public void setModel(MapModel model) {
        this.model = model;
        exploredCache = null;
        blockCache = null;
        carCache.clear();
        lastCarPositions.clear();
        lastCarDirections.clear();
        isFirstRender = true;
        isStart = false;
        renderCount = 0;
        log.info("MapView model reset: {}x{}", model.getMapSize(), model.getMapSize());
        repaint();
        if (isDisplayable()) {
            paintImmediately(0, 0, getWidth(), getHeight());
        }
    }


    public void addCustomMouseListener(MouseAdapter newmouseListener) {
        addMouseListener(newmouseListener);
        mouseListener = newmouseListener;
    }


    public void removeCustomMouseListener() {
        if (mouseListener != null) {
            removeMouseListener(mouseListener);
            mouseListener = null;
        }
    }


    public void setIsStart(boolean isStart) {
        this.isStart = isStart;
        log.info("MapView exploration state changed: isStart={}", isStart);
        if (isStart) {
            isFirstRender = true;
            blockCache = null;  // 清空障碍物缓存，强制重新加载
            renderCount = 0;
            repaint();
        }
    }
}
