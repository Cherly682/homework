package Login.Controller;
import Admin.View.MainFrame;
import Analyst.Controller.PlaybackController;
import Configurator.Controller.MainController;
import Login.Model.UserModel;
import Login.View.LoginView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    private LoginView view;
    private UserModel user;


    public LoginController(LoginView view, UserModel user) {
        this.view = view;
        this.user = user;
        setupEventListeners();
    }


    private void setupEventListeners() {
        view.addLoginButtonActionListener(e -> {
            try {
                login();
            } catch (Exception ex) {
                log.error("Login error: {}", ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        });
    }


    private void login() throws Exception {
        // 从视图获取用户输入
        String username = view.getUsername();
        String password = view.getPassword();

        //空值检查
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("Login attempt with empty credentials");
            view.showErrorMessage("用户名和密码不能为空");
            return;
        }

        // 将用户输入写入模型
        user.setUsername(username);
        user.setPassword(password);

        // 调用模型的验证方法
        if (!user.validate()) {
            log.warn("Login FAILED for user: {}", username);
            view.showErrorMessage("用户名或密码错误");
            return;
        }

        // 根据角色跳转到不同的工作界面
        String role = user.getRole();
        log.info("Login SUCCESS: user={} role={}", username, role);

        switch (role) {
            case "Configurator":
                // 配置员
                view.showMessage("配置员登录成功");
                new MainController();       // 创建配置员控制器
                view.close();
                log.info("MainController (Configurator) opened for user: {}", username);
                break;

            case "Analyst":
                // 分析员
                view.showMessage("分析员登录成功");
                new PlaybackController();    // 创建回放控制器
                view.close();
                log.info("PlaybackController (Analyst) opened for user: {}", username);
                break;

            case "Admin":
                // 管理员
                view.showMessage("管理员登录成功");
                new MainFrame();             // 创建管理员窗口
                view.close();
                log.info("MainFrame (Admin) opened for user: {}", username);
                break;

            default:
                // 未知角色
                log.warn("Unknown role '{}' for user: {}", role, username);
                view.showErrorMessage("未知角色: " + role);
                break;
        }
    }

    public void showView() {
        view.show();
    }
}
