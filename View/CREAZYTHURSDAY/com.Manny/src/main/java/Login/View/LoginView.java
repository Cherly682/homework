package Login.View;

import Utils.BackGroundPanel;
import Utils.ImageLoader;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;


public class LoginView {
    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    // ==================== Swing 组件声明 ====================
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton LoginButton;

    // 窗口尺寸常量
    final int WIDTH = 650;
    final int HEIGHT = 420;

    public LoginView() throws IOException {
        initialize();
        log.info("LoginView initialized");
    }


    private void initialize() throws IOException {
        // ==================== 1. 创建主窗口 ====================
        frame = new JFrame("小车探险——登录");
        // DO_NOTHING_ON_CLOSE：点击关闭按钮时什么都不做
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // ==================== 2. 窗口关闭监听 ====================
        // WindowAdapter 是"窗口事件适配器"——让你只重写感兴趣的方法
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    // 先通知后端停止
                    StartProducer.sendStopMessage();
                } catch (Exception ex) {
                    log.error("Failed to send stop message on window close: {}", ex.getMessage());
                }
                // 然后终止程序
                System.exit(0);
            }
        });

        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setIconImage(ImageLoader.load("logo.jpg"));

        // ==================== 3. 创建带背景图的面板 ====================
        BackGroundPanel bgPanel = new BackGroundPanel(ImageLoader.load("login.png"));
        bgPanel.setLayout(new GridBagLayout());

        // ==================== 4. 设置字体 ====================
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 18);  // 标签用
        Font fieldFont = new Font("Microsoft YaHei", Font.PLAIN, 18);  // 输入框用
        Font buttonFont = new Font("Microsoft YaHei", Font.BOLD, 18);  // 按钮用（加粗）

        // ==================== 5. 布局约束对象 ====================
        // 控制每个组件的位置和大小
        GridBagConstraints gbc = new GridBagConstraints();
        // 设置留白
        gbc.insets = new Insets(10, 10, 10, 10);

        // ==================== 6. 标题标签 ====================
        JLabel titleLabel = new JLabel("Cars Adventure", JLabel.CENTER);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        titleLabel.setForeground(new Color(50, 50, 50));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 10, 30, 10);
        bgPanel.add(titleLabel, gbc);

        // 恢复默认留白
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = 1;    // 恢复单列

        // ==================== 7. 用户名标签和输入框 ====================
        // "用户名" 标签
        JLabel uLabel = new JLabel("用户名");
        uLabel.setFont(labelFont);
        gbc.gridx = 0;
        gbc.gridy = 1;
        bgPanel.add(uLabel, gbc);

        // 用户名输入框
        usernameField = new JTextField(20);
        usernameField.setFont(fieldFont);
        usernameField.setPreferredSize(new Dimension(250, 36));
        gbc.gridx = 1;
        gbc.gridy = 1;
        bgPanel.add(usernameField, gbc);

        // ==================== 8. 密码标签和输入框 ====================
        JLabel pLabel = new JLabel("密码");
        pLabel.setFont(labelFont);
        gbc.gridx = 0;
        gbc.gridy = 2;
        bgPanel.add(pLabel, gbc);

        passwordField = new JPasswordField(20);
        passwordField.setFont(fieldFont);
        passwordField.setPreferredSize(new Dimension(250, 36));
        gbc.gridx = 1;
        gbc.gridy = 2;
        bgPanel.add(passwordField, gbc);

        // ==================== 9. 登录按钮 ====================
        LoginButton = new JButton("登录");
        LoginButton.setFont(buttonFont);
        LoginButton.setPreferredSize(new Dimension(200, 42));

        LoginButton.setBackground(new Color(70, 130, 180));
        LoginButton.setForeground(Color.WHITE);
        LoginButton.setFocusPainted(false);
        // 设置按钮内边距：
        LoginButton.setBorder(BorderFactory.createEmptyBorder(8, 30, 8, 30));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(30, 10, 10, 10);  // 顶部多留白
        gbc.anchor = GridBagConstraints.CENTER;    // 居中
        gbc.fill = GridBagConstraints.NONE;        // 不拉伸
        bgPanel.add(LoginButton, gbc);

        frame.add(bgPanel);
    }


    public String getUsername() {
        return usernameField.getText();
    }


    public String getPassword() {
        return new String(passwordField.getPassword()).trim();
    }


    public void addLoginButtonActionListener(ActionListener actionListener) {
        LoginButton.addActionListener(actionListener);
    }


    public void show() {
        frame.setVisible(true);
        log.info("LoginView shown");
    }


    public void close() {
        frame.dispose();
        log.info("LoginView closed");
    }


    public void showMessage(String message) {
        log.info("LoginView dialog: {}", message);
        JOptionPane.showMessageDialog(frame, message);
    }


    public void showErrorMessage(String message) {
        log.warn("LoginView error dialog: {}", message);
        JOptionPane.showMessageDialog(frame, message, "错误", JOptionPane.ERROR_MESSAGE);
    }
}
