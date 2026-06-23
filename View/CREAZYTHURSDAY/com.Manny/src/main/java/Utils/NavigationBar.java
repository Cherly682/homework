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
 *
 * 【这个类是干什么的？】
 * 程序中三种用户（配置员、分析员、管理员）登录后看到的窗口都需要
 * 相同的菜单栏，包含"切换账号"和"退出"两个功能。
 *
 * 与其在三个地方写三遍相同的代码（代码重复是万恶之源！），
 * 不如写一个工具方法，接收当前窗口作为参数，返回一个配置好的菜单栏。
 *
 * 【设计模式：DRY 原则】
 * DRY = Don't Repeat Yourself（不要重复自己）
 * 这是编程中最重要的原则之一。相同的逻辑如果出现在多个地方，
 * 修改时很容易忘记改某处，导致 bug。
 *
 * 【类比理解】
 * 就像餐馆的"标准套餐"——不管哪个服务员来点单，
 * "切换账号"和"退出"这两个选项总是包含在菜单里，
 * 不需要每个服务员自己重新设计菜单。
 */
public class NavigationBar {
    private static final Logger log = LoggerFactory.getLogger(NavigationBar.class);


    public static JMenuBar create(JFrame parentFrame) {
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
                log.info("用户确认退出系统");
                try {
                    StartProducer.sendStopMessage();  // 通知后端停止
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
