# InspectionBackend

这是配合现有 `View` Swing 前端使用的分布式黑板后端。项目补齐老师实验要求中的控制器、导航器、小车知识源、记录/回放和初始化工具。

## 模块

- `common`：Redis/RabbitMQ 配置、黑板 key、消息模型、公共工具。
- `controller`：唯一节拍调度器，监听 `controller.start`。
- `navigator`：4 个导航工作线程，监听 `navigator.no1` 到 `navigator.no4`。
- `car`：自动发现 `Cars:1..5`，每辆小车独立监听 `car.noN`。
- `recorder`：监听 `save.start`，保存探索帧并支持回放恢复。
- `tools`：初始化用户、声明队列、清理运行态数据。
- `launcher`：本地一键启动后端所有模块。

## 默认账号

- 管理员：`admin / admin123`
- 配置员：`config / config123`
- 分析员：`analyst / analyst123`

## 默认连接

- Redis：`localhost:6379`，数据库 `9`
- RabbitMQ：`localhost:5672`，vhost/user/password 均为 `Mio123`

配置可通过 `common/src/main/resources/application.properties` 或环境变量覆盖。

## 快速运行

```bash
mvn clean package -DskipTests
mvn -pl tools -am exec:java -Dexec.mainClass=edu.homework.inspection.tools.DefaultDataTool -Dexec.args=init
mvn -pl launcher -am exec:java -Dexec.mainClass=edu.homework.inspection.launcher.LauncherMain
```

然后启动现有 `View` 项目中的 `Login.Main`，使用 `config/config123` 登录配置地图并开始探索。
