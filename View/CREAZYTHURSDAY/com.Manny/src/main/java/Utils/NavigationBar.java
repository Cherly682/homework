package Utils;

import Login.Controller.LoginController;
import Login.Model.UserModel;
import Login.View.LoginView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * ============================================================
 * 【共享导航栏 —— NavigationBar】
 * ============================================================
 */
public class NavigationBar {
    private static final Logger log = LoggerFactory.getLogger(NavigationBar.class);


    public static JMenuBar create(JFrame parentFrame) {
        return create(parentFrame, false);
    }

    /**
     * 创建导航菜单栏。
     * @param parentFrame 父窗口
     * @param isAnalyst 是否为分析员界面（分析员退出时仅停止回放，不影响配置员）
     */
    public static JMenuBar create(JFrame parentFrame, boolean isAnalyst) {
        // 创建菜单栏容器
        JMenuBar menuBar = new JMenuBar();

        // 创建一个名为"设置"的菜单
        JMenu settingsMenu = new JMenu("设置");
        settingsMenu.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        // 创建"切换账号"菜单项
        JMenuItem switchAccountItem = new JMenuItem("切换账号");
        switchAccountItem.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        // 创建"退出"菜单项
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        switchAccountItem.addActionListener(e -> {
            log.info("切换账号: 关闭当前窗口，打开登录界面");
            parentFrame.dispose();
            SwingUtilities.invokeLater(() -> {
                try {
                    new LoginController(new LoginView(), new UserModel()).showView();
                } catch (IOException ex) {
                    log.error("切换账号失败: {}", ex.getMessage(), ex);
                }
            });
        });

        exitItem.addActionListener(e -> {
            // 弹出确认对话框
            int choice = JOptionPane.showConfirmDialog(parentFrame,
                    "确定要退出系统吗？",
                    "确认退出",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                log.info("用户确认退出系统 (isAnalyst={})", isAnalyst);
                try {
                    if (isAnalyst) {
                        StartProducer.sendPlaybackStop();  // 分析员：仅停止回放
                    } else {
                        StartProducer.sendStopMessage();   // 配置员/管理员：停止一切
                    }
                } catch (Exception ex) {
                    log.error("发送停止消息失败: {}", ex.getMessage());
                }
                parentFrame.dispose();
                System.exit(0);  // 终止 Java 进程
            }
        });

        // 把菜单项添加到菜单中，再把菜单添加到菜单栏
        settingsMenu.add(switchAccountItem);
        settingsMenu.add(exitItem);
        menuBar.add(settingsMenu);
        return menuBar;
    }
}
