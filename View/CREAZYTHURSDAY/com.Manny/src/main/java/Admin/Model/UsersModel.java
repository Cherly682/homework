package Admin.Model;

import Login.Model.UserModel;
import Utils.RedisConnect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UsersModel {
    private static final Logger log = LoggerFactory.getLogger(UsersModel.class);
    public static final String[] ROLES = {"Configurator", "Analyst", "Admin"};

    private Jedis jedis = RedisConnect.getConnected();

    private final String USERS_KEY_PREFIX = "Users:";


    public List<UserModel> getAllUsers() {
        List<UserModel> users = new ArrayList<>();


        Set<String> keys = jedis.keys(USERS_KEY_PREFIX + "*");
        for (String key : keys) {
            Map<String, String> userMap = jedis.hgetAll(key);
            String username = key.split(":")[1];
            String password = userMap.get("password");
            String role = userMap.get("role");
            users.add(UserModel.fromRedis(username, password, role));
        }
        log.debug("UsersModel: loaded {} users from Redis", users.size());
        return users;
    }


    public void addUser(String username, String password, String role) {
        UserModel user = new UserModel(username, password, role);
        String key = USERS_KEY_PREFIX + username;
        jedis.hset(key, "password", user.getPassword());  // 已经是加密后的密码
        jedis.hset(key, "role", role);
        log.info("UsersModel: user added username={} role={}", username, role);
    }


    public void deleteUser(String username) {
        String key = USERS_KEY_PREFIX + username;
        jedis.del(key);  // 删除整条记录
        log.info("UsersModel: user deleted username={}", username);
    }


    public void updateUser(String username, String password, String role) {
        String key = USERS_KEY_PREFIX + username;
        // 只有传入了非空的密码时才更新密码
        if (password != null && !password.trim().isEmpty()) {
            UserModel user = new UserModel(username, password, role);
            jedis.hset(key, "password", user.getPassword());
        }
        jedis.hset(key, "role", role);
    }
}
