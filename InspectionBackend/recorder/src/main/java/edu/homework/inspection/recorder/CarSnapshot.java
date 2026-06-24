package edu.homework.inspection.recorder;

import java.util.Map;

/**
 *   Key: "Cars:3"
 *     ├─ x: "5"
 *     ├─ y: "3"
 *     ├─ endx: "8"
 *     ├─ endy: "6"
 *     ├─ state: "1"
 *     └─ direction: "R"
 */
public class CarSnapshot {
    private String key;//键名
    private Map<String, String> fields;//值对

    public CarSnapshot() {
    }

    public CarSnapshot(String key, Map<String, String> fields) {
        this.key = key;
        this.fields = fields;
    }

    // Jackson 序列化需要 getter，反序列化需要 setter

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
