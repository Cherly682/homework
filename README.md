# 分布式变电站多车巡检仿真系统

> **架构风格**：黑板架构（Blackboard Architecture）  
> **技术栈**：Java 11+/19+, Redis, RabbitMQ, Swing, Maven  
> **课程**：软件工程课程设计

---

## 项目简介

本项目模拟变电站巡检场景——在二维网格地图中，多台巡检机器人（小车）通过黑板（Redis）共享状态，通过消息总线（RabbitMQ）接收调度命令，协作探索整个地图区域，实现全区域覆盖巡检。

**核心特性**：
- 黑板架构——各知识源独立，仅通过 Redis 黑板 + RabbitMQ 消息总线和 Controller 节拍调度协同工作
- 三种路径算法——A\*（八方向）、双向 A\*、Dijkstra，运行时热切换
- 录制回放——支持探索过程录制、回放、调速、进度拖拽
- 碰撞避免——Redis 原子抢占锁 + 3×3 视野视线阻挡检测
- BFS 可达性感知——自动识别并跳过障碍物包围的不可达区域
- 独立组件部署——各模块编译为可执行 fat JAR，各自独立 JVM 进程运行

---

## 课设要求满足情况总览

| # | 课设要求 | 状态 | 验证方法 | 细节说明 |
|---|---------|------|----------|----------|
| 1 | **每个构件编译为独立计算组件（exe/jar/其他）** | ✅ 满足 | `mvn clean package -DskipTests` 后检查各模块 `target/` 目录，存在 `controller.jar`, `navigator.jar`, `car.jar`, `recorder.jar`, `launcher.jar`, `tools.jar` 共 6 个 fat JAR | 通过 `maven-shade-plugin` 将每个模块及其所有依赖打包为单一 JAR，Manifest 中写入 `Main-Class` 条目，支持 `java -jar xxx.jar` 直接运行。每个 JAR 约 4.2MB，包含 Jedis、amqp-client、Jackson、Logback 等全部依赖。View 前端为 Swing 桌面应用，编译为独立 JAR，通过 `mvn exec:java` 或直接运行 |
| 2 | **可通过控制台/桌面/网络程序独立启动** | ✅ 满足 | 在多个终端分别执行 `java -jar controller.jar` 等命令，各模块独立启动并输出各自日志 | 每个 fat JAR 均有独立的 `main()` 入口。Launcher 通过 `ProcessBuilder` 启动 4 个独立 Java 进程，每个进程拥有独立的 JVM、堆内存和类加载器。View 为 Swing 桌面程序，可直接双击或命令行启动。也可通过 `java -jar launcher.jar` 一键启动全部后端模块 |
| 3 | **用户可独立启动、添加新的小车参加地图探索** | ✅ 满足 | 登录配置员后点击"随机生成"添加小车；或直接通过 `redis-cli` 创建 `Cars:N` hash 键 | CarManager 每 1 秒扫描 Redis 中 `Cars:*` 键，动态发现新增小车并自动启动对应的 `CarAgent` 线程。前端点击地图格子可手动放置小车。运行时在 Redis 中直接 `HSET Cars:6 x 10 y 10 direction 1 state IDLE` 即可动态加入新小车。最大数量为地图格数（20×20=400 辆） |
| 4 | **导航器可启动 1~N 个，分解导航压力** | ✅ 满足 | 修改 `application.properties` 中 `navigator.workerCount` 值（或环境变量 `NAVIGATOR_WORKER_COUNT`），重启后验证 worker 数量 | `NavigatorMain` 启动时根据 `AppConfig.navigatorWorkerCount()` 创建对应数量的 `NavigationWorker` 线程，每个 worker 监听独立队列 `navigator.no1~noN`。Controller 通过 `navigator_status` Hash 轮询空闲 worker 分发导航任务，天然负载均衡。默认 8 个 worker，建议根据地图大小和小车数量调整（N 越大并发处理能力越强） |
| 5 | **显示界面可独立部署多个，同时显示系统状态** | ✅ 满足 | 启动两个 View 实例，分别登录同角色或不同角色，观察两个界面的地图渲染是否同步 | View 前端直接读取 Redis 获取地图/小车状态，通过 RabbitMQ 发送控制命令。多个 View 实例之间完全无耦合——各自独立连接 Redis 和 RabbitMQ，各自每 200ms 轮询刷新。可同时开配置员界面（操作地图）和分析员界面（观察回放），地图状态实时同步 |
| 6 | **控制器独立运行，且操作系统只能运行一个实例** | ✅ 满足 | 启动第一个 Controller：正常；启动第二个 Controller：输出"Controller 实例已在运行"并 `System.exit(1)` | 使用 Redis `SETNX controller:lock <instanceId> EX 30` 实现分布式单实例锁。获取锁后每 10 秒通过 Lua 脚本续约（仅当锁仍归属当前实例时才续约），JVM 关闭时通过 shutdown hook 释放锁。锁 TTL 为 30 秒——若实例崩溃，锁最多 30 秒后自动释放，可被新实例获取 |
| 7 | **各组件之间共享数据** | ✅ 满足 | 观察 Redis 中的 `MapView`, `blockview`, `Cars:*`, `navigator_status` 等键在各模块间实时同步 | 采用黑板架构：Redis 作为集中共享数据空间。Controller 读写小车状态和地图，Navigator 读地图写路径队列，Car 读路径队列写位置和视野，Recorder 读全量数据写帧快照，View 读地图和状态。所有组件通过 RabbitMQ 消息总线协调——Controller 是唯一调度器，控制整体节拍 |
| 8 | **各组件高度独立** | ✅ 满足 | 单独启动 Navigator/Car/Recorder 其中任一个，其他模块不受影响。单独停止某个模块，系统其余部分继续运行（功能降级） | 各模块之间仅通过 Redis（数据）和 RabbitMQ（消息）通信，无直接 import 依赖。Launcher 通过 `ProcessBuilder` 启动各模块为独立 JVM 进程。各模块可独立启动、停止、替换——例如：`java -jar navigator.jar` 可单独重启导航器而不影响正在运行的小车。common 模块作为共享库被所有模块依赖（仅 compile 期，运行时通过 fat JAR 自包含） |
| 9 | **各组件易于升级** | ✅ 满足 | 修改一个模块代码后仅重新构建该模块：`mvn -pl navigator clean package -DskipTests`，然后重启该进程，其他组件无需重启 | 由于各模块是独立 fat JAR + 独立进程，升级 navigator 只需替换 `navigator.jar` 并重启该进程，Controller/Car/Recorder/View 继续运行不受任何影响。接口（Redis Key + RabbitMQ 消息格式）向后兼容时即可热升级 |
| 10 | **保证独立性前提下尽可能提高运行效率** | ✅ 满足 | 查看日志中的 tick 耗时：`Tick N: ... cost=Xms`，典型 tick < 300ms | 关键优化：① 位图批量读取——从 O(N²) 次 Redis GETBIT 调用改为单次 GET 字节数组本地解码，渲染 20×20 地图从 ~2000ms 降至 < 2ms；② 路径批量写入——RPUSH 一次写入整条路径；③ 导航 worker 池——8 个 worker 并发处理多车导航任务；④ CarManager 扫描间隔 1s——动态发现小车无需人工干预 |
| 11 | **易于调试** | ✅ 满足 | 查看 `logs/backend.log` 和 `logs/frontend.log` | 全链路 SLF4J + Logback 日志：Controller 节拍（tick 号、探索率、小车状态）、Car 每步耗时（各阶段微秒级分解）、Navigator 路径规划（算法、花费、路径长度）、Recorder 录制回放、View 渲染周期和用户操作。日志滚动保留 7 天。各模块独立进程各自写日志到同一文件（通过 logback 文件追加模式） |
| 12 | **需要一个界面组件查看运行状态** | ✅ 满足 | 启动 View 后在配置界面右下角查看"系统运行状态"面板 | View 前端 `StatusPanel` 每 2 秒从 Redis 读取并展示：🟢/🔴 Controller 是否运行、Navigator worker 数量和忙闲分布、在线小车数量、Recorder 录制/空闲状态、探索百分比（已探索格数 / 可探索格数）。该面板在配置员、分析员、管理员三种用户界面中均可显示 |

---

## 环境要求

| 组件 | 版本 | 默认连接 |
|------|------|----------|
| JDK | 11+（后端）/ 19+（前端） | - |
| Maven | 3.8+ | - |
| Redis | 5.x+ | `localhost:6379`，DB 9，无密码 |
| RabbitMQ | 3.x+ | `localhost:5672`，`guest/guest`，vhost `/` |

配置项在 `InspectionBackend/common/src/main/resources/application.properties`，所有参数均可通过同名环境变量覆盖：
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `REDIS_PASSWORD`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`
- `CONTROLLER_TICK_MILLIS`（节拍间隔，默认 500ms）
- `CONTROLLER_MAX_CARS`（最大小车数，默认 5）
- `NAVIGATOR_WORKER_COUNT`（导航 worker 数，默认 8）

---

## 快速开始

### 一键启动（推荐）

确保 Redis 和 RabbitMQ 已运行，然后双击根目录 **`start.bat`** 打开交互式菜单，选择要启动的组件（支持逗号分隔多选，如 `3,A` 同时启动前端和 Car）。或直接执行 PowerShell 脚本：

```powershell
# 一键启动全部（构建 + 初始化 + 后端 + 前端）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-all.ps1

# 仅启动后端
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-backend.ps1

# 仅启动前端
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-frontend.ps1
```

`run-all.ps1` 自动完成：构建后端 fat JAR → 编译前端 → 初始化 Redis → 启动后端（4 个独立终端窗口）→ 启动前端（前台 GUI 窗口）。关闭前端窗口即停止全部进程。

登录凭据：**`config / config123`**（配置员）。

### 脚本目录

所有脚本统一位于 `scripts/` 目录，共 14 个脚本。

| 脚本 | 功能 |
|------|------|
| `run-all.ps1` | **一键启动全部**（编译 + 初始化 + 后端 + 前端） |
| `run-backend.ps1` | 启动全部后端模块（4 个独立终端窗口） |
| `run-frontend.ps1` | 启动前端 GUI |
| `run-controller.ps1` | Controller 独立编译+运行 |
| `run-navigator.ps1` | Navigator 独立编译+运行 |
| `run-car.ps1` | Car 独立编译+运行 |
| `run-recorder.ps1` | Recorder 独立编译+运行 |
| `run-tools.ps1` | Tools 操作（`-Action init|clean-runtime`） |
| `build-all.ps1` | 编译全部模块（后端 + 前端） |
| `build-backend.ps1` | 仅编译后端全部模块 |
| `build-frontend.ps1` | 仅编译前端 |
| `config.ps1` | 连接配置交互（Redis/RabbitMQ 地址，保存到 `connection.conf`） |
| `env.ps1` | 环境变量加载器（从 `connection.conf` 注入配置） |
| `menu.ps1` | 交互式菜单（支持多选，启动前自动询问连接地址） |

脚本日志输出到 `scripts/logs/` 目录，每个脚本独立记录。

### 多主机部署

启动任何组件时，脚本会先询问 Redis 和 RabbitMQ 服务器地址并保存到 `scripts/connection.conf`。同一台主机再次启动时直接回车即可使用上次配置。

| 主机 | 运行组件 | 连接服务器 |
|------|----------|-----------|
| 服务器 | Redis + RabbitMQ | - |
| 主机 A | Controller | → 服务器 |
| 主机 B | Navigator | → 服务器 |
| 主机 C | Car | → 服务器 |
| 主机 D | View + Recorder | → 服务器 |

原理：所有组件仅通过 Redis（黑板）+ RabbitMQ（消息总线）通信，无直接 TCP 连接。各主机 Java 进程通过环境变量（`REDIS_HOST`、`RABBITMQ_HOST` 等）指向同一服务器即可协同工作。

详细操作步骤见 [多主机部署操作指南](多主机部署操作指南.md)。

### 手动构建

```bash
# 后端（生成 6 个 fat JAR）
cd InspectionBackend
mvn clean package -DskipTests

# 前端（仅编译）
cd View/CREAZYTHURSDAY/com.Manny
mvn clean compile
```

### 手动启动

```bash
# 1. 初始化（首次运行或清理后执行一次）
cd InspectionBackend
java -jar tools/target/tools.jar init
java -jar tools/target/tools.jar clean-runtime

# 2. 启动后端（一键启动 4 个模块为独立进程）
java -jar launcher/target/launcher.jar

# 2' 或分别独立启动各模块（需要各终端）
java -jar controller/target/controller.jar &
java -jar navigator/target/navigator.jar &
java -jar car/target/car.jar &
java -jar recorder/target/recorder.jar &

# 3. 启动前端（另一个终端）
cd View/CREAZYTHURSDAY/com.Manny
mvn exec:java -Dexec.mainClass=Login.Main.Main
```

### 运行测试

```bash
cd InspectionBackend
mvn test
# 单个测试
mvn -pl navigator test -Dtest=PathFinderTest
mvn -pl car test -Dtest=LightingTest
```

---

## 系统功能使用说明

### 默认账号

| 角色 | 用户名 | 密码 | 权限 |
|------|--------|------|------|
| 管理员 | `admin` | `admin123` | 用户管理（增删改查） |
| 配置员 | `config` | `config123` | 地图配置、小车/障碍物管理、启动巡检 |
| 分析员 | `analyst` | `analyst123` | 回放查看历史探索记录 |

### 配置员界面

1. **地图配置**——选择"默认地图（20×20）"或"自定义地图"（输入 1-100 边长）
2. **小车配置**——
   - "随机生成"：输入数量，系统在空闲格随机放置小车（自动避开障碍物和其他小车）
   - "点击放置"：开启后在网格上单击放置小车（黄色三角，箭头方向随机）
3. **障碍物配置**——
   - "随机生成"：输入数量，系统随机放置障碍物（自动避开小车）
   - "点击放置"：开启后在网格上单击放置障碍物（深色方块）
4. **路径算法**——选择 A\*（八方向）、双向 A\* 或 Dijkstra，运行时切换
5. **控制按钮**——
   - "开始巡检"：写入地图数据到 Redis → 向 Controller 发送启动命令（`"1"`）
   - "停止巡检"：向 Controller 发送停止命令（`"0"`）
   - "重置"：停止巡检 + 计时归零 + 恢复默认地图 + 清空小车和障碍物
6. **计时器**——显示探索持续时间（分:秒:毫秒）
7. **系统运行状态**——实时显示 Controller/Navigator/Car/Recorder/探索进度

### 分析员界面

1. 从左侧列表选择回放记录（显示格式化时间如"06-18 14:30"）
2. 选中后立即加载初始帧到地图
3. 点击"开始回放"观看探索过程
4. **回放控制**——
   - 暂停/继续：暂停或恢复播放
   - 速度按钮：1× / 2× / 3× 三档调速
   - 进度滑块：拖拽跳转到任意帧
   - 重置：清空画面，选择其他记录
5. 地图实时显示：已探索区（浅绿色）、障碍物（深灰色）、小车（带箭头方向的车辆图标）

### 管理员界面

- 用户管理：增删改查系统用户
- 设置用户名、密码、角色

### 探索完成条件

- **完全探索**：所有非障碍方格均已探索 → 弹窗"地图已完全探索！"，`exploration_result` = `complete`
- **部分不可达**：存在障碍物包围的不可达区域 → 弹窗"可探索区域已全部巡检完毕，部分地块因被障碍物包围而无法进入"，`exploration_result` = `partial`

---

## 项目结构

```
homework/
├── README.md                          # 项目说明
├── CLAUDE.md                          # Claude Code 项目指南
├── start.bat                          # 交互式菜单启动入口
├── 多主机部署操作指南.md                # 多主机分布式部署操作步骤
├── logs/                              # 运行时日志
│   ├── backend.log                    # 后端全链路日志
│   └── frontend.log                   # 前端渲染和操作日志
├── scripts/                           # 启动/构建/配置脚本（14 个）
│   ├── menu.ps1                       # 交互式菜单
│   ├── run-all.ps1                    # 一键启动全部
│   ├── run-backend.ps1                # 启动后端全部模块
│   ├── run-frontend.ps1               # 启动前端 GUI
│   ├── run-controller.ps1             # Controller 独立运行
│   ├── run-navigator.ps1              # Navigator 独立运行
│   ├── run-car.ps1                    # Car 独立运行
│   ├── run-recorder.ps1               # Recorder 独立运行
│   ├── run-tools.ps1                  # Tools 操作
│   ├── build-all.ps1                  # 编译全部模块
│   ├── build-backend.ps1              # 仅编译后端
│   ├── build-frontend.ps1             # 仅编译前端
│   ├── config.ps1                     # 连接配置交互
│   ├── env.ps1                        # 环境变量加载器
│   ├── connection.conf                # 连接配置缓存
│   └── logs/                          # 脚本运行日志
├── InspectionBackend/                 # Maven 多模块后端（Java 11）
│   ├── pom.xml                        # 父 POM，管理 7 个子模块
│   ├── common/                        # 公共库：Redis/RabbitMQ 配置、黑板键、消息模型
│   ├── controller/                    # 节拍调度器 → controller.jar
│   ├── navigator/                     # 路径规划器（A*/双向A*/Dijkstra）→ navigator.jar
│   ├── car/                           # 小车知识源 → car.jar
│   ├── recorder/                      # 录制/回放 → recorder.jar
│   ├── tools/                         # 初始化工具（init/clean/seeds）→ tools.jar
│   └── launcher/                      # 进程启动器 → launcher.jar
├── View/                              # Swing 桌面前端（Java 19）
│   └── CREAZYTHURSDAY/com.Manny/
│       ├── pom.xml
│       └── src/main/java/
│           ├── Login/                 # 登录模块（MVC）
│           ├── Admin/                 # 管理员模块
│           ├── Analyst/               # 分析员模块（回放）
│           ├── Configurator/          # 配置员模块（地图/小车/障碍物/巡检）
│           └── Utils/                 # 工具类
├── 参考架构plan_all_v3.md             # 架构设计参考文档
├── 黑板风格（分布式）2021修改版(2).docx  # 课设任务书
└── 圣遗物/                            # 参考作品
```

---

## 架构总览

```
┌────────────────────────────────────────────────────┐
│                  RabbitMQ 消息总线                  │
│  controller.start | save.start | car.broadcast      │
│  navigator.messagesent (Direct, 路由到 no1~no8)     │
└──────┬──────────┬──────────┬──────────┬────────────┘
       │          │          │          │
  ┌────▼───┐ ┌───▼────┐ ┌───▼───┐ ┌───▼──────┐
  │Controller│ │Navigator│ │  Car  │ │Recorder  │
  │ 节拍调度 │ │路径规划 │ │小车移动│ │ 录制回放 │
  └────┬────┘ └───┬────┘ └──┬───┘ └────┬─────┘
       │          │         │          │
       └──────────┼─────────┼──────────┘
                  │         │
          ┌───────▼─────────▼───────┐
          │     Redis 黑板          │
          │  MapView | blockview    │
          │  Cars:N | N_task_queue  │
          │  navigator_status       │
          │  controller:lock        │
          └───────────┬─────────────┘
                      │
              ┌───────▼───────┐
              │  View (Swing) │
              │  配置/分析/管理 │
              └───────────────┘
```

**核心约束**：
- 各知识源（Navigator/Car/Recorder）仅通过 Redis（数据）和 RabbitMQ（消息）与 Controller 通信
- Controller 是唯一调度器——按节拍扫描空闲小车 → 分发导航任务 → 广播移动命令 → 检查探索完成
- 知识源之间不直接通信，不知道彼此的存在
- View 前端直接读写 Redis 获取地图状态，通过 RabbitMQ 发送启停命令

### 小车状态机

```
IDLE → ASSIGNING → (路径就绪) → (收到 TICK) → MOVING → IDLE/ASSIGNING
                                                    ↓
                                              ROUTE_DONE（路径走完）
                                              COLLISION_AVOIDED（碰撞避让，清空路径）
```

### 路径算法（PatrolAlgo）

| 算法编号 | 名称 | 特性 |
|---------|------|------|
| 0 | A\* | 八方向启发式搜索，曼哈顿距离启发函数，效率最高 |
| 1 | 双向 A\* | 从起点和目标同时搜索，大地图更优 |
| 2 | Dijkstra | 全局最短路径，无启发式，保证最优但较慢 |

### RabbitMQ 队列拓扑

| 队列/交换器 | 类型 | 用途 |
|-------------|------|------|
| `controller.start` | Classic Queue | Controller 启停指令 |
| `navigator.messagesent` | Direct Exchange | 分发导航任务到 `navigator.no1~no8` |
| `car.broadcast` | Fanout Exchange | 广播节拍移动命令到 `car.no1~noN` |
| `save.start` | Classic Queue | Recorder 录制/回放指令 |

### Redis 黑板键设计

| Key | 类型 | 说明 |
|-----|------|------|
| `map_width` / `map_height` | String | 地图尺寸 |
| `MapView` | Bitmap | 探索视野，1=已探索 |
| `blockview` | Bitmap | 障碍物标记 |
| `Cars:{id}` | Hash | 小车信息（x, y, direction, state, endx, endy） |
| `{id}_task_queue` | List | 小车路径队列，Navigator 写入，Car 消费 |
| `navigator_status` | Hash | Navigator worker 忙闲标记 |
| `Save` | Hash | 录制/回放元数据 |
| `Record:{fileNo}:{frame}` | Hash | 录制帧快照 |
| `car_reserve:{x}:{y}` | String (TTL 5s) | 移动目标格原子抢占锁 |
| `controller:lock` | String (TTL 30s) | Controller 分布式单实例锁 |
| `Algorithm` | String | 路径算法编号 0/1/2 |
| `Users:{name}` | Hash | 用户（password SHA-256, role） |

---


## 技术要点

### 碰撞避免

两阶段防护：
1. **SETNX 原子抢占**：移动前 `SETNX car_reserve:{x}:{y} <carId>`，TTL 5s，抢占失败说明格子被另一车锁定
2. **哈希占用检查**：若 SETNX 通过，再检查目标格是否已被其他小车的 `Cars:{id}` hash 占据，是则原地让路

### 视线阻挡检测

点亮 3×3 的 9 个格子时，对角线格子需要检查两侧正交邻格是否均被障碍物阻挡——防止"隔墙点灯"。例如：点亮格 (x+1, y+1) 时，若 (x+1, y) 和 (x, y+1) 均为障碍物则 (x+1, y+1) 不可见。

### 可达性感知

BFS 从小车当前位置出发计算所有可到达格子。若探索率 100% 但仍有 BFS 范围内的未探索格，则存在障碍物包围的不可达区域——系统自动停止并提示。

### 分段距离策略

导航器选择目标时采用贪心最近策略，但若剩余 >1 个未探索格子，仅分配距离 ≥1 的目标；最后一个未探索格子无距离限制。

---

## 开发变更日志

- 接入日志系统（SLF4J + Logback）。
- 一键启动脚本 `start.bat` ——双击打开交互式菜单，支持多选启动组件。
- 启动前自动终止残留 Java 进程，解决 MQ 通道占用问题。
- 前端关闭时自动停止后端进程。
- 全 UI 中文化，指定中文字体消除乱码。
- 回放功能修复与增强（按钮逻辑、进度滑块、调速、地图尺寸自适应、位图读取方向修正）。
- 导航策略从随机改为贪心最近，大幅提升探索效率。
- BFS 可达性检查——障碍物包围的不可达区域自动识别和提示。
- 小车方向计算与屏幕坐标系对齐。
- 碰撞避免——`car_reserve` 原子抢占锁 + 哈希占用检查。
- 视线阻挡检测——对角线格子隔墙不可见。
- 小车数量上限从 5 升至地图格数（400 辆）。
- 导航器线程数从 4 提升至 8。
- 位图批量读取替代逐格 GETBIT，性能提升 1000 倍。
- 各模块编译为独立可执行 fat JAR，支持 `java -jar` 独立启动。
- Launcher 改为进程启动（ProcessBuilder），各模块独立 JVM 进程。
- Controller 增加 Redis 分布式单实例锁。
- 前端增加系统运行状态监控面板（StatusPanel）。
- 支持多主机分布式部署——通过环境变量配置远程 Redis/RabbitMQ 地址，各主机只需指向同一服务器即可协同工作。
- 交互式菜单多选功能——支持逗号分隔同时启动多个组件（如 `3,A` 启动前端 + Car）。
- 中间件检查改为 TCP 端口探测——不再依赖本地 `redis-cli`/`rabbitmqctl` 命令，适配远程部署场景。
- 连接配置记忆（`connection.conf`）——启动时自动询问并保存，下次直接回车复用。
- 编写 [多主机部署操作指南](多主机部署操作指南.md)。

---

## License

MIT
