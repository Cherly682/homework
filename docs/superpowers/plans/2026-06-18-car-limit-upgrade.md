# 小车数量上限升级为地图格数 —— 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将小车数量上限从硬编码的 5 辆升级为动态等于地图格数，使配置员可添加最多 `mapSize × mapSize` 辆车。

**Architecture:** 后端 `controller.maxCars` 改为 400（覆盖默认 20×20 全格），前端 `MapModel.getMaxCars()` 改为返回 `area`，所有固定循环清理改为 Redis SCAN 动态扫描，消除硬编码 5。

**Tech Stack:** Java 11+ (backend), Java 19+ (frontend Swing), Redis, RabbitMQ

## Global Constraints

- 前端 `MapModel` 的 `getMaxCars()` 返回 `area`（地图总格数）
- 所有 "清理 N 辆小车/队列" 的固定循环改为 Redis SCAN 动态扫描
- 后端 `controller.maxCars` 覆盖默认 20×20 地图（400 格）
- 不改动 RabbitMQ 预声明架构（仍用固定池，池大小为配置值）
- 编译零错误

---

### Task 1: 前端 `MapModel` —— 小车上限改为 area，清理循环改为 SCAN

**Files:**
- Modify: `View/CREAZYTHURSDAY/com.Manny/src/main/java/Configurator/Model/MapModel.java`

- [ ] **Step 1: 删除 `MAX_CAR_NUMS` 常量，`getMaxCars()` 改为返回 `area`**

找到第 33 行：
```java
private final int MAX_CAR_NUMS = 5;
```
删除该行。

找到第 88 行：
```java
public int getMaxCars() { return MAX_CAR_NUMS; }
```
改为：
```java
public int getMaxCars() { return area; }
```

- [ ] **Step 2: `initialize()` 中任务队列清理循环改为 SCAN**

找到第 62-64 行：
```java
// reset task queues
for (int i = 1; i <= 5; i++) {
    jedis.del(i + TASK_KEY_);
}
```
改为：
```java
// reset task queues (dynamic scan)
for (String key : scanKeys("*" + TASK_KEY_)) {
    jedis.del(key);
}
```

- [ ] **Step 3: `addCar()` 递增逻辑保持不变（已用 `carCount` 计数），只需修复早退条件**

找到第 106 行：
```java
carCount++;
if (carCount > MAX_CAR_NUMS) { return; }
```
改为：
```java
carCount++;
if (carCount > area) { return; }
```

- [ ] **Step 4: `autoAddCar()` 修复上限判断**

找到第 139 行：
```java
if (carCount >= MAX_CAR_NUMS) { return; }
```
改为：
```java
if (carCount >= area) { return; }
```

- [ ] **Step 5: 编译验证**

```bash
cd "E:\Desktop\homework\View\CREAZYTHURSDAY\com.Manny" && mvn compile -q
```

---

### Task 2: 前端 `PlaybackModel` —— 清理循环改为 SCAN

**Files:**
- Modify: `View/CREAZYTHURSDAY/com.Manny/src/main/java/Analyst/Model/PlaybackModel.java`

- [ ] **Step 1: 删除 `MAX_CARS_COUNT` 常量，`resetCars()` 改为 SCAN**

找到第 20 行：
```java
private final int MAX_CARS_COUNT = 5;
```
删除该行。

找到第 89-92 行：
```java
private void resetCars() {
    for (int i = 1; i <= MAX_CARS_COUNT; i++) {
        jedis.del(CARS_KEY + i);
    }
}
```
改为：
```java
private void resetCars() {
    for (String key : scanKeys(CARS_KEY + "*")) {
        jedis.del(key);
    }
}
```

- [ ] **Step 2: 添加 `scanKeys` 工具方法**（与 `MapModel` 中一致的实现）

在 `resetCars()` 之后添加：
```java
private List<String> scanKeys(String pattern) {
    List<String> keys = new ArrayList<>();
    String cursor = "0";
    redis.clients.jedis.ScanParams params = new redis.clients.jedis.ScanParams().match(pattern).count(100);
    do {
        redis.clients.jedis.ScanResult<String> result = jedis.scan(cursor, params);
        keys.addAll(result.getResult());
        cursor = result.getCursor();
    } while (!"0".equals(cursor));
    return keys;
}
```

需要在文件头部添加 import：
```java
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 3: 编译验证**

```bash
cd "E:\Desktop\homework\View\CREAZYTHURSDAY\com.Manny" && mvn compile -q
```

---

### Task 3: 前端 `MainView` —— 标签文本动态化

**Files:**
- Modify: `View/CREAZYTHURSDAY/com.Manny/src/main/java/Configurator/View/MainView.java`

- [ ] **Step 1: 修改 `carStatusLabel` 初始文本，去掉硬编码 "5"**

找到第 179 行：
```java
carStatusLabel = new JLabel("已添加: 0 / 5 辆小车");
```
改为（使用 `mapPanel.getModel().getMaxCars()` 获取动态上限）：
```java
// MaxCars 改为动态，label 在 setCarCount 调用时动态更新，此处用占位
carStatusLabel = new JLabel("已添加: 0 辆小车");
```

- [ ] **Step 2: 修改 `setCarCount()` 方法，显示动态上限**

找到第 377-379 行：
```java
public void setCarCount(int current, int max) {
    carStatusLabel.setText("已添加: " + current + " / " + max + " 辆小车");
}
```
该方法已是参数化的，无需修改——它已接收 `current` 和 `max` 参数，标签文本会由 `MainController.updateStatusLabels()` 驱动动态更新。

确认 `MainController.updateStatusLabels()`（`MainController.java` 第 283-285 行）调用：
```java
mainView.setCarCount(mapModel.getCarCount(), mapModel.getMaxCars());
```
这里 `getMaxCars()` 现在返回 `area`，标签会自动显示正确值。

- [ ] **Step 3: 编译验证**

```bash
cd "E:\Desktop\homework\View\CREAZYTHURSDAY\com.Manny" && mvn compile -q
```

---

### Task 4: 后端 `application.properties` —— 提升 `controller.maxCars`

**Files:**
- Modify: `InspectionBackend/common/src/main/resources/application.properties`

- [ ] **Step 1: 修改配置值**

找到：
```properties
controller.maxCars=5
```
改为：
```properties
controller.maxCars=400
```

> 400 覆盖默认 20×20 地图（400 格）的全格占用。如使用更大自定义地图，需设置环境变量 `CONTROLLER_MAX_CARS` 覆盖。

- [ ] **Step 2: 编译验证后端**

```bash
cd E:\Desktop\homework\InspectionBackend && mvn compile -q
```

---

### Task 5: 全量编译验证

- [ ] **Step 1: 编译前端**

```bash
cd "E:\Desktop\homework\View\CREAZYTHURSDAY\com.Manny" && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 编译后端**

```bash
cd E:\Desktop\homework\InspectionBackend && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行后端测试**

```bash
cd E:\Desktop\homework\InspectionBackend && mvn test -q
```
Expected: All tests pass (if any exist for modified modules)

---

## 变更影响总结

| 组件 | 原值 | 新值 | 说明 |
|------|------|------|------|
| `MapModel.MAX_CAR_NUMS` | 5 | 删除 | `getMaxCars()` → `area` |
| `MapModel.initialize()` 任务队列清理 | `for i=1..5` | `SCAN *_task_queue` | 不再限 5 个队列 |
| `PlaybackModel.MAX_CARS_COUNT` | 5 | 删除 | `resetCars()` → `SCAN Cars:*` |
| `MainView` 标签 | "0 / 5" | `setCarCount()` 动态参数 | 已有参数化方法 |
| `controller.maxCars` | 5 | 400 | 覆盖 20×20 地图 |
| `RabbitProvider` | `for i=1..5` | `for i=1..400` | 自动跟随配置 |
| `CarManager` | `for i=1..5` | `for i=1..400` | 自动跟随配置 |
