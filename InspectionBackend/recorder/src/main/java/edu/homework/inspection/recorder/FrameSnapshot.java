package edu.homework.inspection.recorder;

import java.util.ArrayList;
import java.util.List;

/**
 *   FrameSnapshot
 *   ├─ width: 20          ← 地图宽度（格子数）
 *   ├─ height: 20         ← 地图高度（格子数）
 *   ├─ mapViewBase64:     ← 探索位图的 Base64 编码
 *   │   "AAAAAAEAAA..."      （哪些格子被小车探过了）
 *   ├─ blockViewBase64:   ← 障碍物位图的 Base64 编码
 *   │   "AAAAAgAAAA..."      （哪些格子是障碍物）
 *   └─ cars: [            ← 所有小车的状态列表
 *       CarSnapshot { key="Cars:1", fields={x:5,y:3,...} },
 *       CarSnapshot { key="Cars:2", fields={x:8,y:6,...} },
 *       ...
 *     ]
 *
 * Redis 的 bitmap 是二进制数据，不能直接放进 JSON 字符串中。
 *
 * 例如：二进制字节 0x8D → Base64 文本 "jQ=="
 *
 * 这样 JSON 可以安全地存储和传输位图数据，
 * 回放时再从 Base64 解码回原始二进制。
 *
 * 【类比理解】
 * FrameSnapshot 就像一张"拍立得照片"：
 * - width/height = 照片的尺寸
 * - mapViewBase64 = 照片上哪些地方有颜色（探索标记）
 * - blockViewBase64 = 照片上哪些地方有黑块（障碍物）
 * - cars = 照片上小车的位置和朝向
 *
 * 所有这些信息放在一起，就能完整还原那一刻的地图状态。
 */
public class FrameSnapshot {
    /** 地图宽度（格子数） */
    private int width;

    /** 地图高度（格子数） */
    private int height;

    /** 探索位图的 Base64 编码字符串 */
    private String mapViewBase64;

    /** 障碍物位图的 Base64 编码字符串 */
    private String blockViewBase64;

    /** 这一帧中所有小车的状态列表 */
    private List<CarSnapshot> cars = new ArrayList<>();

    // ==================== Getter / Setter ====================
    // Jackson 需要这些方法来序列化（对象→JSON）和反序列化（JSON→对象）

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getMapViewBase64() {
        return mapViewBase64;
    }

    public void setMapViewBase64(String mapViewBase64) {
        this.mapViewBase64 = mapViewBase64;
    }

    public String getBlockViewBase64() {
        return blockViewBase64;
    }

    public void setBlockViewBase64(String blockViewBase64) {
        this.blockViewBase64 = blockViewBase64;
    }

    public List<CarSnapshot> getCars() {
        return cars;
    }

    public void setCars(List<CarSnapshot> cars) {
        this.cars = cars;
    }
}
