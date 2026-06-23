# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

变电站巡检仿真系统，基于**黑板架构（Blackboard Architecture）**的多车协作地图探索系统。Redis 是黑板（共享数据空间），RabbitMQ 是消息总线，Controller 是唯一节拍调度器，Navigator/Car/Recorder 是独立知识源。

两个子项目：
- **InspectionBackend**：Maven 多模块后端（Java 11），含 7 个子模块
- **View**：Swing 桌面前端（Java 19，`View/CREAZYTHURSDAY/com.Manny/`）

## 构建与运行

### 环境依赖

| 组件 | 版本要求 | 默认连接 |
|------|----------|----------|
| JDK | 11+（InspectionBackend）/ 19+（View） | - |
| Maven | 3.8+ | - |
| Redis | 5.x+ | `localhost:6379`，DB 9，无密码 |
| RabbitMQ | 3.x+ | `localhost:5672`，默认 `guest/guest`，vhost `/` |

配置在 `InspectionBackend/common/src/main/resources/application.properties`，可通过同名环境变量覆盖。

### 一键启动（推荐）

双击根目录的 **`start.bat`** 或运行：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File start.ps1
```

脚本自动完成：构建后端 fat JAR → 编译前端 → 初始化 Redis → 启动后端（4 个独立进程）→ 启动前端。日志输出到 `logs/backend.log` 和 `logs/frontend.log`。

登录凭据：`config / config123`（配置员）。

### 手动构建

```bash
# 后端（生成 6 个 fat JAR，位于各模块 target/ 目录下）
cd InspectionBackend
mvn clean package -DskipTests

# 前端
cd View/CREAZYTHURSDAY/com.Manny
mvn clean compile
```

### 手动启动

```bash
cd InspectionBackend

# 初始化（首次运行或清理后执行一次）
java -jar tools/target/tools.jar init
java -jar tools/target/tools.jar clean-runtime

# 方式一：一键启动（Launcher 以独立进程启动 4 个模块）
java -jar launcher/target/launcher.jar

# 方式二：分别独立启动（各开一个终端）
java -jar controller/target/controller.jar &
java -jar navigator/target/navigator.jar &
java -jar car/target/car.jar &
java -jar recorder/target/recorder.jar &

# 启动前端（另一个终端）
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

## 日志系统

前后端均使用 **SLF4J + Logback**。

- **配置文件**：`logback.xml`（分别位于 `InspectionBackend/common/src/main/resources/` 和 `View/CREAZYTHURSDAY/com.Manny/src/main/resources/`）
- **日志目录**：运行时通过 `-Dlog.dir=<dir>` 指定，默认 `logs/`
  - `backend.log`：后端全链路日志（Controller 节拍、Car 每步耗时、Navigator 路径规划、Recorder 录制）
  - `frontend.log`：前端渲染和用户操作日志（登录、地图渲染、按钮点击）
- **格式**：`yyyy-MM-dd HH:mm:ss.SSS [线程] LEVEL 类名 - 消息`
- **滚动策略**：单文件 10MB，按日期+序号滚动，保留 7 天
- **日志级别**：默认 INFO。关键操作 INFO，底层 Redis/MQ 读写 DEBUG

## 架构

### 模块结构（InspectionBackend）

```
InspectionBackend/
├── pom.xml              # 父 POM，定义 7 个子模块
├── common/              # 公共模块：Redis/RabbitMQ 配置、黑板 key、消息模型、logback.xml
├── controller/          # 唯一节拍调度器，监听 controller.start
├── navigator/           # 路径规划器，worker 监听 navigator.no1~noN
├── car/                 # 小车知识源，自动发现 Cars:1~N
├── recorder/            # 记录/回放，监听 save.start
├── tools/               # 初始化工具（写入用户、声明队列、清理运行时数据）
└── launcher/            # 一键启动（ProcessBuilder 启动 4 个模块为独立 JVM 进程）
```

### 核心组件职责

| 组件 | 类型 | 职责 |
|------|------|------|
| Controller | 调度器 | 按节拍循环：扫描空闲小车 → 分发导航任务 → 广播移动命令 → 检查探索完成 |
| Navigator | 知识源 | 接收导航任务，执行路径规划（A*/双向A*/Dijkstra），将路径写入小车任务队列 |
| Car | 知识源 | 接收节拍广播，消费路径队列移动一步，点亮 3×3 视野 |
| Recorder | 知识源 | 保存探索帧快照，支持回放恢复 |
| View | 显示/配置 | Swing GUI：登录、用户管理、地图配置、实时视图、回放分析、运行状态监控 |

### 黑板数据（Redis Key 设计）

| Key | 类型 | 说明 |
|-----|------|------|
| `map_width` / `map_height` | String | 地图尺寸 |
| `MapView` | Bitmap | 探索视野，1=已探索 |
| `blockview` | Bitmap | 障碍物标记 |
| `Cars:1..N` | Hash | 小车信息：x, y, endx, endy, state, direction |
| `1_task_queue..N_task_queue` | List | 小车路径队列，元素为 `x,y` |
| `Algorithm` | String | 路径算法：0=A*, 1=双向A*, 2=Dijkstra |
| `Users:admin` 等 | Hash | 用户（password, role） |
| `Save` | Hash | 录制/回放元数据（file_num, order_view, last_view, triple_speed） |
| `Record:N:F` | Hash | 录制帧快照（N=文件号, F=帧号），含 `snapshot` JSON |
| `exploration_result` | String | 探索完成标记：`complete` 或 `partial`（部分不可达） |
| `car_reserve:X:Y` | String (TTL 5s) | 移动目标格原子抢占锁，防两车碰撞 |
| `controller:lock` | String (TTL 30s) | Controller 分布式单实例锁（SETNX，含定时续约） |

### 消息队列（RabbitMQ）

| 队列/交换器 | 类型 | 用途 |
|-------------|------|------|
| `controller.start` | Queue（绑定到 controller Fanout） | 启停 Controller |
| `navigator.messagesent` | Direct Exchange | 按 routing key `.no1~.noN` 分发导航任务 |
| `car.broadcast` | Fanout Exchange | 广播节拍移动命令给所有小车 |
| `save.start` | Queue（绑定到 save Fanout） | 启停 Recorder/回放 |

### 路径规划算法（Navigator）

通过 `PathFinder` 接口支持三种算法，由 `Algorithm` 配置项切换：
- **A\***（0）：八方向启发式搜索，曼哈顿距离启发函数
- **双向 A\***（1）：从起点和目标同时搜索
- **Dijkstra**（2）：全局最短路径

### 小车状态机

`IDLE → ASSIGNING → (路径就绪) → (收到节拍 MOVING) → IDLE/ASSIGNING`

小车处理流程：peek 路径下一步 → 检查障碍 → 原子抢占目标格 → pop 路径 → 更新位置 → 点亮 3×3（含视线阻挡检测）→ 递增步数。受阻、碰撞或路径走完后重置为空闲。

## 默认账号

- 管理员：`admin / admin123`
- 配置员：`config / config123`
- 分析员：`analyst / analyst123`

## View 前端模块说明

```
View/CREAZYTHURSDAY/com.Manny/src/main/java/
├── Login/          # 登录模块（MVC: LoginView, LoginController, UserModel）
├── Admin/          # 管理员模块（用户管理 CRUD）
├── Analyst/        # 分析员模块（回放查看）
├── Configurator/   # 配置员模块（地图、小车、障碍物配置 + 启动巡检）
└── Utils/          # 工具类（RedisConnect, StartProducer, ImageRequest）
```

View 直接读写 Redis 获取地图和小车状态，通过 RabbitMQ 发送启停命令。

## 项目文档

| 文档 | 路径 | 用途 |
|------|------|------|
| README | `README.md` | 项目说明、课设要求对照、功能概览、快速开始 |
| 使用说明书 | `分布式变电站多车巡检系统使用说明书.md` | 课设答辩用：构件/连接件/黑板详细设计、数据字典、接口说明 |
| 架构参考 | `参考架构plan_all_v3.md` | v3 架构设计参考文档 |
| 启动脚本 | `start.bat` / `start.ps1` | 一键启动 |

## 已完成的修复与改进

> 面向非程序员读者，用通俗语言描述每次改动解决了什么问题、带来了什么效果。

- **日志系统**：程序现在会自动记录运行情况和错误信息，方便排查问题。
- **一键启动脚本**：双击即可自动完成构建、初始化和启动后端+前端，启动前自动清理上次残留的运行数据。
- **修好了启动时日志文件冲突**：前端和后端不再共用同一个日志文件，各自独立记录，避免互相覆盖导致启动失败。
- **修好了界面图片不显示**：图片文件路径修正，所有图标和背景图恢复正常渲染。
- **修好了点"开始巡检"后小车不动**：原因是上次异常关闭的旧进程占用了消息通道，启动脚本现自动清理所有残留进程。
- **关闭前端窗口时自动停止后端**：不再需要手动到任务管理器终止进程。
- **界面全中文化**：所有按钮、标签、弹窗均翻译为中文，字体统一为"微软雅黑"，消除英文和乱码。
- **修好了回放界面所有按钮点击无效**：选记录时前端把英文字母当数字传给了后端，改为正确传数字编号。
- **修好了回放时地图不动的综合问题**：后端共 4 处缺陷——开始回放错误地先调了停止功能、空数据静默跳过导致无限循环、无帧数据时死循环、进度条与定时器数值竞争。
- **回放记录列表显示格式化时间**（月-日 时:分），选中记录后立即加载初始帧，展示地图全貌、障碍物位置和小车位置。
- **回放支持任意大小地图**：地图尺寸从录制帧中动态解析，不再固定为 20×20。
- **回放速度按钮生效**：后端耗时计算接入速度参数，前端定时器同步响应速度变化。
- **回放进度可拖拽**：将无用的"分析"按钮改为"暂停/继续"，新增进度滑块支持拖拽跳转到任意帧。
- **回放重置功能**：将"停止回放"按钮改为"重置"，清空画面和缓存，允许选择其他录像。
- **回放地图渲染完整**：原来只画了小车周围一小块区域，改为从 Redis 读取完整地图数据逐格还原。
- **小车不再随机乱跑**：导航策略改为贪心选择最近的未探索格子，不再随机抽取，探索效率大幅提升。
- **修好了障碍物包围导致小车卡住**：引入 BFS 可达性检查——小车能自动识别并跳过被障碍物围住的死区，探索完可达区域后自动停止并提示"部分地块无法进入"。
- **修好了小车车头朝向和移动方向不一致**：方向定义与屏幕坐标系的上下左右对齐。
- **防止两车相撞**：为目标格增加原子抢占锁——每辆小车移动前先"锁住"目标格，抢占失败就原地让路。
- **修好了小车隔墙看东西**：小车点亮对角格子前会检查两侧是否有墙挡住视线，有阻挡就不点。
- **重启系统后旧回放记录自动清理**：启动脚本增加 clean-runtime 步骤，每次启动都是干净状态。
- **放置小车时自动避开障碍物和其他小车**：不会出现小车和障碍物、小车和小车重叠的情况。
- **随机放置障碍物自动避开小车位置**：不管先放车还是先放障碍物，都不会互相重叠。
- **打开回放界面自动清空旧画面**：先清掉上一次巡检留下的探索痕迹和障碍物，保证回放界面初始是空的。
- **前后端回放数据保持同步**：共用同一个 Redis 连接，选记录、拖进度条时数据即时一致。
- **拖动回放进度条后画面立刻更新**：拖动滑块瞬间从录像读取对应帧并刷新画面，不会跳到不对的帧。
- **回放帧号前后端统一记法**：进度条始终与播放帧一一对应，不再出现跳帧或错位。
- **切换不同大小地图回放正常**：看完大地图再看小地图自动重建画面缓存，不会错乱或空白。
- **每次开始回放从第一帧播放**：后端计数器每次重置，不再跳过开头。
- **回放地图颜色和障碍物位置正确**：修正了位图读取方向，画面 100% 还原巡检过程。
- **小车数量上限从 5 辆升到地图格数**（如 20×20=400 辆），支持大规模多车实验。
- **三种用户统一导航栏**：移除"首页"按钮，"退出"增加二次确认弹窗。
- **地图渲染速度大幅提升**：地图数据读取方式从逐格查询改为一次性批量拉取（数千次网络请求变为 1 次），画面刷新从几秒降到几毫秒。
- **各模块编译为独立可执行程序**（JAR 包）：每个模块都能单独打包成可双击运行的程序，不再依赖 Maven 命令行启动——像普通应用程序一样可以独立运行。
- **启动方式从"四合一"改为"四独立"**：Controller、Navigator、Car、Recorder 现在各跑各的，互不干扰——一个模块出问题不会拖垮其他模块，升级其中一个也不需要重启全部。
- **控制器加了"唯一锁"**：系统保证同一台电脑上只能运行一个 Controller，不小心多开会自动退掉多余的——防止两个控制器同时指挥小车导致冲突。
- **前端新增运行状态面板**：在配置界面可以看到 Controller 是否在线、Navigator 有几个在工作、小车数量、探索进度——不用看日志就能知道整个系统跑得怎么样。
- **启动脚本改用 java -jar 直接启动**：不再依赖 Maven 运行时环境，启动更稳定更快速。
- **修好了启动器找不到模块程序的问题**：启动器能自动根据自身位置找到各模块的程序文件，不再依赖工作目录——随便从哪个目录启动都能正常运行。
- **重写项目说明文档（README）**：面向新用户，讲清楚项目是什么、怎么用、课设各项要求是否满足、答辩可能被问到哪些问题。
- **撰写系统使用说明书**：面向课设答辩，内容涵盖构件设计、连接件设计、黑板数据字典、核心接口和特色设计，可作为实验报告的附录补充。