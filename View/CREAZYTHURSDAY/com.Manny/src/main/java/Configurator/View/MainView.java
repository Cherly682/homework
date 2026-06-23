package Configurator.View;

import Utils.ImageLoader;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class MainView {
    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    private JFrame frame;
    private final int WIDTH = 1060;
    private final int HEIGHT = 800;
    private JMenuBar menuBar;
    private JSplitPane splitPane;     // 左右分割面板
    private MapView mapPanel;
    private JPanel rightPanel;

    // ==================== 地图配置组件 ====================
    private JRadioButton defaultMapRadio;
    private JRadioButton customMapRadio;
    private ButtonGroup mapButtonGroup;       // 确保单选

    // ==================== 小车配置组件 ====================
    private JLabel carStatusLabel;
    private JButton autoAddCarBtn;
    private JToggleButton carPlacementToggle;

    // ==================== 障碍物配置组件 ====================
    private JLabel blockStatusLabel;
    private JButton autoAddBlockBtn;
    private JToggleButton blockPlacementToggle;

    // ==================== 算法选择组件 ====================
    private JRadioButton aStarRadio;
    private JRadioButton aStar2Radio;
    private JRadioButton dijkstraRadio;
    private ButtonGroup algoButtonGroup;

    // ==================== 控制按钮 ====================
    private JButton startButton;
    private JButton resetButton;
    private JButton stopButton;

    // ==================== 系统状态 ====================
    private StatusPanel statusPanel;

    // ==================== 计时器 ====================
    private JLabel timeLabel;
    private Timer timer;           // java.util.Timer
    private long startTime;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("mm:ss:SSS");


    public MainView(MapView mapPanel) throws IOException {
        this.mapPanel = mapPanel;
        showHomeFrame();
    }

    public void showHomeFrame() throws IOException {
        initialize();
    }


    private void initialize() throws IOException {
        frame = new JFrame("小车探险：欢迎！");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    StartProducer.sendStopMessage();
                } catch (Exception ex) {
                    log.error("Failed to send stop message on window close: {}", ex.getMessage());
                }
                System.exit(0);
            }
        });
        frame.setResizable(false);  // 禁止调整窗口大小（简化布局计算）
        frame.setBounds(250, 150, WIDTH, HEIGHT);
        frame.setIconImage(ImageLoader.load("logo.jpg"));

        createMenuBar();
        createSplitPane();

        show();
        log.info("MainView initialized: {}x{}", WIDTH, HEIGHT);
    }


    private void createMenuBar() {
        menuBar = Utils.NavigationBar.create(frame);
        frame.setJMenuBar(menuBar);
    }


    private void createSplitPane() {
        splitPane = new JSplitPane();
        splitPane.setContinuousLayout(true);  // 拖动分隔线时实时更新布局
        splitPane.setDividerLocation(725);
        splitPane.setDividerSize(7);

        createLeftPanel();
        createRightPanel();

        frame.add(splitPane);
    }

    private void createLeftPanel() {
        mapPanel.setPreferredSize(new Dimension(750, 700));
        splitPane.setLeftComponent(mapPanel);
    }


    private void createRightPanel() {
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));


        configPanel.add(createMapConfigPanel());
        configPanel.add(Box.createVerticalStrut(4));
        configPanel.add(createCarConfigPanel());
        configPanel.add(Box.createVerticalStrut(4));
        configPanel.add(createBlockConfigPanel());
        configPanel.add(Box.createVerticalStrut(4));
        configPanel.add(createAlgorithmConfigPanel());
        configPanel.add(Box.createVerticalStrut(6));
        configPanel.add(createTimerPanel());
        configPanel.add(Box.createVerticalStrut(6));
        configPanel.add(createControlButtonPanel());
        configPanel.add(Box.createVerticalStrut(6));

        // 系统运行状态监控
        statusPanel = new StatusPanel();
        configPanel.add(statusPanel);

        // 嵌入滚动面板
        JScrollPane scrollPane = new JScrollPane(configPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        rightPanel.add(scrollPane, BorderLayout.CENTER);
        splitPane.setRightComponent(rightPanel);
    }

    // ==================== 各配置子面板的创建方法 ====================

    private JPanel createMapConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(createSectionBorder("地图配置"));
        panel.setMaximumSize(new Dimension(280, 82));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        defaultMapRadio = new JRadioButton("默认地图 (20×20)", true);  // 默认选中
        defaultMapRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        defaultMapRadio.setFocusPainted(false);

        customMapRadio = new JRadioButton("自定义地图");
        customMapRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        customMapRadio.setFocusPainted(false);

        // ButtonGroup 确保二选一
        mapButtonGroup = new ButtonGroup();
        mapButtonGroup.add(defaultMapRadio);
        mapButtonGroup.add(customMapRadio);

        panel.add(defaultMapRadio);
        panel.add(Box.createVerticalStrut(2));
        panel.add(customMapRadio);
        return panel;
    }

    private JPanel createCarConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(createSectionBorder("小车配置"));
        panel.setMaximumSize(new Dimension(280, 90));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        carStatusLabel = new JLabel("已添加: 0 辆小车");
        carStatusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        carStatusLabel.setForeground(new Color(100, 100, 100));
        carStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        autoAddCarBtn = new JButton("随机生成");
        autoAddCarBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        autoAddCarBtn.setFocusPainted(false);
        autoAddCarBtn.setPreferredSize(new Dimension(110, 30));

        carPlacementToggle = new JToggleButton("点击放置");
        carPlacementToggle.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        carPlacementToggle.setFocusPainted(false);
        carPlacementToggle.setPreferredSize(new Dimension(120, 30));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.add(autoAddCarBtn);
        btnRow.add(carPlacementToggle);

        panel.add(carStatusLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(btnRow);
        return panel;
    }

    private JPanel createBlockConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(createSectionBorder("障碍物配置"));
        panel.setMaximumSize(new Dimension(280, 90));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        blockStatusLabel = new JLabel("已添加: 0 个障碍物");
        blockStatusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        blockStatusLabel.setForeground(new Color(100, 100, 100));
        blockStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        autoAddBlockBtn = new JButton("随机生成");
        autoAddBlockBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        autoAddBlockBtn.setFocusPainted(false);
        autoAddBlockBtn.setPreferredSize(new Dimension(110, 30));

        blockPlacementToggle = new JToggleButton("点击放置");
        blockPlacementToggle.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        blockPlacementToggle.setFocusPainted(false);
        blockPlacementToggle.setPreferredSize(new Dimension(120, 30));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.add(autoAddBlockBtn);
        btnRow.add(blockPlacementToggle);

        panel.add(blockStatusLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(btnRow);
        return panel;
    }

    private JPanel createAlgorithmConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(createSectionBorder("路径算法"));
        panel.setMaximumSize(new Dimension(280, 110));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        aStarRadio = new JRadioButton("A* 算法", true);  // 默认选中 A*
        aStarRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        aStarRadio.setFocusPainted(false);

        aStar2Radio = new JRadioButton("双向 A* 算法");
        aStar2Radio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        aStar2Radio.setFocusPainted(false);

        dijkstraRadio = new JRadioButton("Dijkstra 算法");
        dijkstraRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        dijkstraRadio.setFocusPainted(false);

        algoButtonGroup = new ButtonGroup();
        algoButtonGroup.add(aStarRadio);
        algoButtonGroup.add(aStar2Radio);
        algoButtonGroup.add(dijkstraRadio);

        panel.add(aStarRadio);
        panel.add(Box.createVerticalStrut(2));
        panel.add(aStar2Radio);
        panel.add(Box.createVerticalStrut(2));
        panel.add(dijkstraRadio);
        return panel;
    }

    private JPanel createTimerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(createSectionBorder("计时器"));
        panel.setMaximumSize(new Dimension(280, 90));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        timeLabel = new JLabel("00:00:000", JLabel.CENTER);
        timeLabel.setFont(new Font("Consolas", Font.BOLD, 36));
        timeLabel.setForeground(new Color(30, 30, 30));
        panel.add(timeLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createControlButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(280, 96));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        startButton = new JButton("开始巡检");
        startButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        startButton.setBackground(new Color(70, 130, 180));   // 蓝色
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setPreferredSize(new Dimension(115, 42));

        resetButton = new JButton("重置");
        resetButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        resetButton.setBackground(new Color(120, 120, 120));  // 灰色
        resetButton.setForeground(Color.WHITE);
        resetButton.setFocusPainted(false);
        resetButton.setPreferredSize(new Dimension(242, 36));

        stopButton = new JButton("停止巡检");
        stopButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        stopButton.setBackground(new Color(200, 80, 80));     // 红色
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.setPreferredSize(new Dimension(115, 42));


        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        mainRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainRow.add(startButton);
        mainRow.add(stopButton);


        JPanel resetRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        resetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetRow.add(resetButton);

        panel.add(mainRow);
        panel.add(Box.createVerticalStrut(6));
        panel.add(resetRow);
        return panel;
    }

    //统一风格边框
    private javax.swing.border.TitledBorder createSectionBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(160, 160, 160), 1),
                title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.PLAIN, 12));
    }

    // ==================== 计时器操作 ====================


    public void startTimer() {
        if (timer != null) {
            timer.cancel();  // 取消旧计时器
        }
        startTime = System.currentTimeMillis();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 确保 UI 更新在 EDT 上执行
                SwingUtilities.invokeLater(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    timeLabel.setText(formatTime(elapsed));
                });
            }
        }, 0, 10);  // 延迟 0ms 开始，每 10ms 更新
        log.info("Timer started");
    }

    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
            log.info("Timer stopped at {}", formatTime(System.currentTimeMillis() - startTime));
        }
    }

    public void resetTimer() {
        stopTimer();
        timeLabel.setText("00:00:000");
    }

    /**
     * 从 Redis 时间戳同步启动计时器（跨客户端同步）。
     * 如果已同步到相同时间戳则跳过，避免每 500ms 重启计时器。
     */
    public void syncStartTime(long redisStartTime) {
        if (timer != null && this.startTime == redisStartTime) {
            return; // 已同步到同一时间戳，无需重启
        }
        stopTimer();
        this.startTime = redisStartTime;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    timeLabel.setText(formatTime(elapsed));
                });
            }
        }, 0, 10);
        log.info("Timer synced from Redis: startTime={}", redisStartTime);
    }

    /** 查询计时器是否正在运行 */
    public boolean isTimerRunning() {
        return timer != null;
    }

    private String formatTime(long millis) {
        return timeFormat.format(new Date(millis));
    }

    // ==================== 状态更新 ====================

    public void setCarCount(int current, int max) {
        carStatusLabel.setText("已添加: " + current + " / " + max + " 辆小车");
    }

    public void setBlockCount(int current, int max) {
        blockStatusLabel.setText("已添加: " + current + " / " + max + " 个障碍物");
    }

    // ==================== MapView 切换 ====================

    public void updateMapView(MapView newMapView) {
        log.info("MainView updating MapView component");
        if (splitPane.getLeftComponent() == newMapView) {
            newMapView.revalidate();
            newMapView.repaint();
            return;
        }
        // 把旧 MapView 上的鼠标监听器迁移到新 MapView
        Component oldLeft = splitPane.getLeftComponent();
        if (oldLeft instanceof MapView) {
            for (MouseListener ml : ((MapView) oldLeft).getMouseListeners()) {
                newMapView.addMouseListener(ml);
            }
        }
        splitPane.setLeftComponent(newMapView);
        splitPane.revalidate();
        splitPane.repaint();
    }



    public JFrame getFrame() { return frame; }
    public JRadioButton getDefaultMapRadio() { return defaultMapRadio; }
    public JRadioButton getCustomMapRadio() { return customMapRadio; }
    public JButton getAutoAddCarBtn() { return autoAddCarBtn; }
    public JToggleButton getCarPlacementToggle() { return carPlacementToggle; }
    public JButton getAutoAddBlockBtn() { return autoAddBlockBtn; }
    public JToggleButton getBlockPlacementToggle() { return blockPlacementToggle; }
    public JRadioButton getAStarRadio() { return aStarRadio; }
    public JRadioButton getAStar2Radio() { return aStar2Radio; }
    public JRadioButton getDijkstraRadio() { return dijkstraRadio; }
    public JButton getStartButton() { return startButton; }
    public JButton getResetButton() { return resetButton; }
    public JButton getStopButton() { return stopButton; }

    public void show() { frame.setVisible(true); }

    public void close() {
        if (statusPanel != null) {
            statusPanel.stop();  // 停止状态监控定时器
        }
        frame.dispose();
    }
}
