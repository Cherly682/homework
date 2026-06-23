package Login.Main;

import Login.Controller.LoginController;
import Login.Model.UserModel;
import Login.View.LoginView;
import Utils.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;


public class Main {
    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // 启动时打印当前配置信息到日志
        ConfigManager.dumpConfig();
        // 安全检查：是否在使用默认密码连接远程服务器？
        ConfigManager.validate();

        SwingUtilities.invokeLater(() -> {
            try {
                log.info("Frontend starting...");

                // 1. 创建视图
                LoginView view = new LoginView();
                // 2. 创建模型
                UserModel user = new UserModel();
                // 3. 创建控制器
                new LoginController(view, user).showView();

                log.info("Frontend launched: login window shown");
            } catch (IOException e) {
                log.error("Frontend startup failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }
}
