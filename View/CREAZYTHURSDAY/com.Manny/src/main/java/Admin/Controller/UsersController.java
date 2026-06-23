package Admin.Controller;

import Admin.Model.UsersModel;
import Admin.View.UsersView;
import Login.Model.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class UsersController {
    private static final Logger log = LoggerFactory.getLogger(UsersController.class);
    private UsersModel model;
    private UsersView view;


    public UsersController(UsersModel model, UsersView view) {
        this.model = model;
        this.view = view;
        initialize();
    }


    private void initialize() {
        List<UserModel> users = model.getAllUsers();
        view.setUsers(users);
        log.info("UsersController: loaded {} users", users.size());
    }


    public void refreshUsers() {
        List<UserModel> users = model.getAllUsers();
        view.setUsers(users);
    }


    public void addUser(String username, String encryptedPassword, String role) {
        model.addUser(username, encryptedPassword, role);
        refreshUsers();
    }


    public void deleteUser(String username) {
        model.deleteUser(username);
        refreshUsers();
    }


    public void modifyUser(String username, String password, String role) {
        model.updateUser(username, password, role);
        refreshUsers();
    }
}
