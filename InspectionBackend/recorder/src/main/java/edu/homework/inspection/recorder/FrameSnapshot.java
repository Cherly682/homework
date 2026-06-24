package edu.homework.inspection.recorder;

import java.util.ArrayList;
import java.util.List;

/**
 *   FrameSnapshot
 *   ├─ width: 20
 *   ├─ height: 20
 *   ├─ mapViewBase64:     ← 探索位图的 Base64 编码
 *   │   "AAAAAAEAAA..."
 *   ├─ blockViewBase64:   ← 障碍物位图的 Base64 编码
 *   │   "AAAAAgAAAA..."
 *   └─ cars: [            ← 所有小车的状态列表
 *       CarSnapshot { key="Cars:1", fields={x:5,y:3,...} },
 *       CarSnapshot { key="Cars:2", fields={x:8,y:6,...} },
 *       ...
 *     ]
 */
public class FrameSnapshot {
    private int width;
    private int height;
    private String mapViewBase64;
    private String blockViewBase64;
    private List<CarSnapshot> cars = new ArrayList<>();
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
