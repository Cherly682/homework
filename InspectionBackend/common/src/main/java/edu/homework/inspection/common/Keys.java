package edu.homework.inspection.common;

public final class Keys {
    public static final String MAP_VIEW = "MapView";
    public static final String BLOCK_VIEW = "blockview";
    public static final String MAP_WIDTH = "map_width";
    public static final String MAP_HEIGHT = "map_height";
    public static final String ALGORITHM = "Algorithm";
    public static final String SAVE = "Save";
    public static final String NAVIGATOR_STATUS = "navigator_status";
    public static final String USERS_PREFIX = "Users:";
    public static final String CARS_PREFIX = "Cars:";
    public static final String RECORDER_FRAME_PREFIX = "Record:";
    public static final String CONTROLLER_LOCK = "controller:lock";

    /** 回放命名空间前缀：隔离分析员回放数据与配置员实时数据 */
    public static final String PLAYBACK_PREFIX = "playback:";

    private Keys() {
    }

    public static String carKey(int id) {
        return CARS_PREFIX + id;
    }

    public static String carTaskQueue(int id) {
        return id + "_task_queue";
    }

    public static String recorderFrameKey(int fileNo, int frameNo) {
        return RECORDER_FRAME_PREFIX + fileNo + ":" + frameNo;
    }
}
