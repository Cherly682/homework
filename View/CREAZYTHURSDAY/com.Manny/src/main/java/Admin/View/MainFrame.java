package Admin.View;

import Admin.Model.UsersModel;
import Admin.Controller.UsersController;
import Login.Model.UserModel;
import Utils.ImageLoader;
import Utils.NavigationBar;
import Utils.StartProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class MainFrame extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

    // ==================== MVC 三要素 ====================
    private UsersModel model;
    private UsersView userView;
    private UsersController controller;


    public MainFrame() {
        // 创建模型
        model = new UsersModel();
        // 创建视图
        userView = new UsersView();
        // 创建控制器
        controller = new UsersController(model, userView);

        //构建窗口界面
        initializeView();
        //绑定按钮事件
        initializeController();
        log.info("MainFrame (Admin) initialized");
    }


    private void initializeView() {
        setLayout(new BorderLayout());
        add(userView, BorderLayout.CENTER);
        setTitle("管理员面板");
        try {
            setIconImage(ImageLoader.load("logo.jpg"));
        } catch (Exception e) {
            log.warn("Failed to load icon: {}", e.getMessage());
        }

        setSize(950, 600);

        // DO_NOTHING_ON_CLOSE：禁止默认关闭行为
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    StartProducer.sendStopMessage();  // 通知后端停止
                } catch (Exception ex) {
                    log.error("Failed to send stop message on window close: {}", ex.getMessage());
                }
                System.exit(0);
            }
        });

        // 窗口居中
        setLocationRelativeTo(null);
        // 设置
        setJMenuBar(NavigationBar.create(this));
        // 显示窗口
        setVisible(true);
    }

    private void initializeController() {
        // ==================== 刷新按钮 ====================
        // 从 Redis 重新加载并显示所有用户
        userView.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<UserModel> users = model.getAllUsers();
                userView.setUsers(users);
            }
        });

        // 刷新监听器
        userView.addRefreshButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<UserModel> users = model.getAllUsers();
                userView.setUsers(users);
                log.info("Admin: user list refreshed ({} users)", users.size());
            }
        });

        // ==================== 添加用户按钮 ====================
        userView.addAddButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userView.getUsername();
                String password = userView.getPassword();
                String role = userView.getRole();

                if (username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        userView,
                        "用户名和密码不能为空",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                controller.addUser(username, password, role);
                log.info("Admin: user ADDED username={} role={}", username, role);
            }
        });

        // ==================== 删除用户按钮 ====================
        userView.addDeleteButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UserModel selectedUser = userView.getSelectedUser();
                if (selectedUser == null) {
                    JOptionPane.showMessageDialog(
                        userView,
                        "请选择一个用户",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                log.info("Admin: user DELETED username={}", selectedUser.getUsername());
                controller.deleteUser(selectedUser.getUsername());
            }
        });

        // ==================== 表格行选择事件 ====================
        userView.addTableSelectionListener(e -> {
            // getValueIsAdjusting：选择操作还在进行中不触发
            if (!e.getValueIsAdjusting()) {
                UserModel selectedUser = userView.getSelectedUser();
                if (selectedUser != null) {
                    // 自动填充输入区域
                    userView.setUsername(selectedUser.getUsername());
                    userView.setRole(selectedUser.getRole());
                    userView.setPassword("");
                }
            }
        });

        // ==================== 修改用户按钮 ====================
        userView.addModifyButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UserModel selectedUser = userView.getSelectedUser();
                if (selectedUser == null) {
                    JOptionPane.showMessageDialog(
                        userView,
                        "请先在表格中选择一个用户",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                String oldUsername = selectedUser.getUsername();
                String newPassword = userView.getPassword();
                String newRole = userView.getRole();
                controller.modifyUser(oldUsername, newPassword, newRole);
                log.info("Admin: user MODIFIED oldUsername={} newRole={}", oldUsername, newRole);
            }
        });
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainFrame::new);
    }
}
