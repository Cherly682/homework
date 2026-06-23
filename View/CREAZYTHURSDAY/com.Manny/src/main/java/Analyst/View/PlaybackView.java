package Analyst.View;

import Analyst.Model.PlaybackModel;
import Utils.ImageLoader;
import Utils.NavigationBar;
import Utils.RedisConnect;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Hashtable;


public class PlaybackView extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(PlaybackView.class);
    private JButton playButton;
    private JButton pauseButton;
    private JButton resetButton;
    private PlaybackMapView playbackMapView;
    private PlaybackModel playbackModel;
    private final String SAVE_KEY = "Save";
    private final String FILE_NUM_KEY = "file_num";
    private int fileCount;
    private Jedis jedis;
    private JComboBox<String> comboBox;
    private JPanel rightPanel;
    private JButton refreshButton;

    JRadioButton halfSpeedButton;
    JRadioButton normalSpeedButton;
    JRadioButton doubleSpeedButton;

    private JSlider progressSlider;
    private JLabel progressLabel;
    private javax.swing.Timer progressUpdateTimer;


    public PlaybackView() {
        jedis = RedisConnect.getConnected();
        this.playbackModel = new PlaybackModel(jedis);
        this.playbackMapView = new PlaybackMapView(playbackModel, jedis);
        init();
        startProgressUpdater();
        log.info("PlaybackView 初始化完成");
    }


    private void init() {
        setTitle("回放系统");
        try {
            setIconImage(ImageLoader.load("logo.jpg"));
        } catch (Exception e) {
            log.warn("加载图标失败: {}", e.getMessage());
        }
        setSize(1000, 880);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    StartProducer.sendPlaybackStop();
                } catch (Exception ex) {
                    log.error("关闭窗口时发送停止消息失败: {}", ex.getMessage());
                }
                System.exit(0);
            }
        });
        setLocationRelativeTo(null);

        // 设置
        setJMenuBar(NavigationBar.create(this, true));

        // 按钮
        playButton = new JButton("开始回放");
        pauseButton = new JButton("暂停");
        resetButton = new JButton("重置");

        setLayout(new BorderLayout());

        createCenterPanel();
        createBottomPanel();
        createRightPanel();
    }

    /**
     * 每 500ms 轮询一次 Redis 中的当前帧号（order_view），
     * 同步滑块位置和进度文字。
     *
     * 同时检测总帧数（last_view）的变化：
     * 如果总帧数变大了（新的回放数据写入），自动扩展滑块范围。
     *
     * updatingFromTimer 标记：防止定时器程序化设置滑块值时触发 ChangeListener。
     */
    private void startProgressUpdater() {
        progressUpdateTimer = new javax.swing.Timer(500, e -> {
            try {
                int orderView = playbackModel.getOrderView();
                int lastView = playbackModel.getLastView();

                // 总帧数变化 扩展滑块范围
                if (lastView > progressSlider.getMaximum()) {
                    updatingFromTimer = true;
                    progressSlider.setMaximum(lastView);
                    progressSlider.setMajorTickSpacing(Math.max(1, lastView / 10));

                    // 重新生成刻度标签
                    Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
                    for (int i = 0; i <= lastView; i += Math.max(1, lastView / 10)) {
                        JLabel lbl = new JLabel(String.valueOf(i));
                        lbl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
                        labelTable.put(i, lbl);
                    }
                    progressSlider.setLabelTable(labelTable);
                    updatingFromTimer = false;
                }

                // 当前帧变化 移动滑块
                if (orderView != progressSlider.getValue()) {
                    updatingFromTimer = true;
                    if (orderView <= lastView) {
                        progressSlider.setValue(orderView);
                    }
                    progressLabel.setText("当前帧: " + orderView + " / " + lastView);
                    updatingFromTimer = false;
                }
            } catch (Exception ex) {
                // 轮询 Redis 时的异常，忽略
            }
        });
        progressUpdateTimer.start();
    }


    private boolean updatingFromTimer = false;
    public boolean isUpdatingFromTimer() { return updatingFromTimer; }

    /**
     * 刷新进度滑块范围和标签
     * 选择新记录时调用
     */
    public void refreshProgressSlider() {
        int lastView = playbackModel.getLastView();

        progressSlider.setMinimum(0);
        progressSlider.setMaximum(lastView);
        progressSlider.setValue(0);
        progressSlider.setMajorTickSpacing(Math.max(1, lastView / 10));

        // 生成刻度标签
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i <= lastView; i += Math.max(1, lastView / 10)) {
            JLabel lbl = new JLabel(String.valueOf(i));
            lbl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
            labelTable.put(i, lbl);
        }
        progressSlider.setLabelTable(labelTable);
        progressLabel.setText("当前帧: 0 / " + lastView);

        log.info("进度滑块已刷新: lastView={}", lastView);
    }

    // ==================== 各区域面板 ====================

    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(playbackMapView, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void createBottomPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 12));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));

        Font btnFont = new Font("Microsoft YaHei", Font.BOLD, 16);


        playButton.setFont(btnFont);
        playButton.setBackground(new Color(60, 140, 80));
        playButton.setForeground(Color.WHITE);
        playButton.setFocusPainted(false);
        playButton.setPreferredSize(new Dimension(130, 42));


        pauseButton.setFont(btnFont);
        pauseButton.setBackground(new Color(70, 130, 180));
        pauseButton.setForeground(Color.WHITE);
        pauseButton.setFocusPainted(false);
        pauseButton.setPreferredSize(new Dimension(130, 42));


        resetButton.setFont(btnFont);
        resetButton.setBackground(new Color(200, 80, 80));
        resetButton.setForeground(Color.WHITE);
        resetButton.setFocusPainted(false);
        resetButton.setPreferredSize(new Dimension(130, 42));

        refreshButton = new JButton("刷新记录");
        refreshButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        refreshButton.setBackground(new Color(120, 120, 120));
        refreshButton.setForeground(Color.WHITE);
        refreshButton.setFocusPainted(false);
        refreshButton.setPreferredSize(new Dimension(130, 42));

        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(refreshButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void createRightPanel() {
        rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        createRecordPanel();
        rightPanel.add(Box.createVerticalStrut(12));
        createSpeedPanel();
        rightPanel.add(Box.createVerticalStrut(12));
        createStepPanel();

        add(rightPanel, BorderLayout.EAST);
    }

    private void createRecordPanel() {
        JPanel recordPanel = new JPanel();
        recordPanel.setLayout(new BoxLayout(recordPanel, BoxLayout.Y_AXIS));
        recordPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(140, 140, 140), 1),
                        "记录选择",
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font("Microsoft YaHei", Font.BOLD, 15)
                ),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // 从 Redis 读取记录总数
        String fileNumStr = jedis.hget(SAVE_KEY, FILE_NUM_KEY);
        fileCount = fileNumStr != null ? Integer.parseInt(fileNumStr) : 0;

        comboBox = new JComboBox<>();
        comboBox.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        populateComboBox();

        comboBox.setPreferredSize(new Dimension(220, 34));
        comboBox.setMaximumSize(new Dimension(220, 34));
        comboBox.setMinimumSize(new Dimension(220, 34));

        recordPanel.add(comboBox);
        rightPanel.add(recordPanel);
        log.info("回放记录面板: 找到{}条记录", fileCount);
    }

    /** 重新从 Redis 拉取记录列表并刷新下拉框 */
    public void refreshRecordList() {
        String fileNumStr = jedis.hget(SAVE_KEY, FILE_NUM_KEY);
        int newFileCount = fileNumStr != null ? Integer.parseInt(fileNumStr) : 0;
        if (newFileCount != fileCount) {
            fileCount = newFileCount;
            log.info("回放记录刷新: 记录数 {} -> {}", comboBox.getItemCount(), fileCount);
        }
        int previousSelection = comboBox.getSelectedIndex();
        comboBox.removeAllItems();
        populateComboBox();
        // 尝试恢复之前的选择
        if (previousSelection >= 0 && previousSelection < comboBox.getItemCount()) {
            comboBox.setSelectedIndex(previousSelection);
        }
    }

    /** 填充下拉框列表项 */
    private void populateComboBox() {
        for (int i = 1; i <= fileCount; i++) {
            String ts = playbackModel.getRecordTimestamp(i);
            comboBox.addItem("记录 " + i + " (" + ts + ")");
        }
    }


    private void createSpeedPanel() {
        JPanel speedPanel = new JPanel();
        speedPanel.setLayout(new BoxLayout(speedPanel, BoxLayout.Y_AXIS));
        speedPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(140, 140, 140), 1),
                        "播放速度",
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font("Microsoft YaHei", Font.BOLD, 15)
                ),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        ButtonGroup speedGroup = new ButtonGroup();  // 确保单选

        Font radioFont = new Font("Microsoft YaHei", Font.PLAIN, 15);
        halfSpeedButton = new JRadioButton("0.5x");
        normalSpeedButton = new JRadioButton("1.0x", true);  // 默认选 1.0x
        doubleSpeedButton = new JRadioButton("2.0x");

        halfSpeedButton.setFont(radioFont);
        normalSpeedButton.setFont(radioFont);
        doubleSpeedButton.setFont(radioFont);

        speedGroup.add(halfSpeedButton);
        speedGroup.add(normalSpeedButton);
        speedGroup.add(doubleSpeedButton);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        radioPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        radioPanel.add(halfSpeedButton);
        radioPanel.add(normalSpeedButton);
        radioPanel.add(doubleSpeedButton);

        speedPanel.add(radioPanel);
        rightPanel.add(speedPanel);
    }


    private void createStepPanel() {
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(140, 140, 140), 1),
                        "进度",
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font("Microsoft YaHei", Font.BOLD, 15)
                ),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        int lastView = playbackModel.getLastView();


        progressSlider = new JSlider(JSlider.HORIZONTAL, 0, lastView, 0);
        progressSlider.setMajorTickSpacing(Math.max(1, lastView / 10));
        progressSlider.setPaintTicks(true);    // 显示刻度线
        progressSlider.setPaintLabels(true);   // 显示刻度标签
        progressSlider.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
        progressSlider.setSnapToTicks(true);   // 吸附到刻度位置

        // 自定义刻度标签
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i <= lastView; i += Math.max(1, lastView / 10)) {
            JLabel lbl = new JLabel(String.valueOf(i));
            lbl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
            labelTable.put(i, lbl);
        }
        progressSlider.setLabelTable(labelTable);

        progressSlider.setPreferredSize(new Dimension(220, 80));
        progressSlider.setMaximumSize(new Dimension(220, 80));

        progressLabel = new JLabel("当前帧: 0 / " + lastView);
        progressLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        progressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        progressPanel.add(progressSlider);
        progressPanel.add(Box.createVerticalStrut(5));
        progressPanel.add(progressLabel);
        rightPanel.add(progressPanel);
        progressPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        log.info("回放进度滑块: lastView={}", lastView);
    }

    // ==================== Getter 方法（供 Controller 绑定事件）====================

    public JButton getPlayButton() { return playButton; }
    public JButton getPauseButton() { return pauseButton; }
    public JButton getResetButton() { return resetButton; }

    public void resetComboBox() {
        comboBox.setSelectedIndex(-1);
    }

    public PlaybackMapView getPlaybackMapView() { return playbackMapView; }
    public PlaybackModel getPlaybackModel() { return playbackModel; }
    public JFrame getFrame() { return this; }
    public JComboBox<String> getComboBox() { return comboBox; }
    public JButton getRefreshButton() { return refreshButton; }
    public JRadioButton getHalfSpeedButton() { return halfSpeedButton; }
    public JRadioButton getNormalSpeedButton() { return normalSpeedButton; }
    public JRadioButton getDoubleSpeedButton() { return doubleSpeedButton; }
    public JSlider getProgressSlider() { return progressSlider; }

    public static void main(String[] args) {
        new PlaybackView().setVisible(true);
    }
}
