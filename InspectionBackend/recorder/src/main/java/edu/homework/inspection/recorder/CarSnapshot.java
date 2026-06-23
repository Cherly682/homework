package edu.homework.inspection.recorder;

import java.util.Map;

/**
 * ============================================================
 * 【小车快照 —— CarSnapshot】
 * ============================================================
 *
 * 【这个类是干什么的？】
 * 这是录制系统中的一个"数据容器"（DTO = Data Transfer Object，数据传输对象）。
 *
 * 录制时，每辆小车有自己当前的 x、y 坐标、朝向（U/D/L/R）、
 * 状态（空闲/移动中）等信息。CarSnapshot 就是把这些信息打包成一个
 * 方便传输和存储的小包裹。
 *
 * 一辆小车在 Redis 中的数据长这样：
 *   Key: "Cars:3"
 *     ├─ x: "5"
 *     ├─ y: "3"
 *     ├─ endx: "8"
 *     ├─ endy: "6"
 *     ├─ state: "1"
 *     └─ direction: "R"
 *
 * CarSnapshot 用两个字段来保存这些信息：
 * - key：Redis 中的键名（如 "Cars:3"）
 * - fields：所有的字段-值对（如 {"x":"5", "y":"3", ...}）
 *
 * 【为什么需要专门的快照类？】
 * 录制时要把小车状态保存到 JSON 格式的快照中，
 * 回放时要从 JSON 中恢复小车状态。
 * 使用专门的类可以让 JSON 序列化/反序列化变得简单——Jackson 库
 * 可以自动把 CarSnapshot 对象转成 JSON，再从 JSON 转回来。
 *
 * 【类比理解】
 * 就像搬家时用的纸箱：
 * - key = 纸箱上的标签（"厨房用品"）
 * - fields = 纸箱里面装的东西
 * 搬家工人（JSON 序列化器）看到标签就知道这个箱子是什么，
 * 不需要打开检查。
 */
public class CarSnapshot {
    private String key;
    private Map<String, String> fields;

    /**
     * Jackson 反序列化时需要一个无参构造方法来创建对象，
     * 然后用 setter 方法填充字段。
     * 如果你不写这个无参构造方法，Jackson 会报错。
     */
    public CarSnapshot() {
    }

    public CarSnapshot(String key, Map<String, String> fields) {
        this.key = key;
        this.fields = fields;
    }

    // ==================== Getter / Setter ====================
    // Jackson 序列化时需要 getter（转 JSON），反序列化时需要 setter（从 JSON 恢复）

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }
}
