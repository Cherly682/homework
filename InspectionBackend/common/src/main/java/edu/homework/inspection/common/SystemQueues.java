package edu.homework.inspection.common;

public final class SystemQueues {
    public static final String EXCHANGE_CONTROLLER = "controller";
    public static final String EXCHANGE_SAVE = "save";
    public static final String EXCHANGE_CAR_BROADCAST = "car.broadcast";
    public static final String EXCHANGE_NAVIGATOR = "navigator.messagesent";

    public static final String QUEUE_CONTROLLER_START = "controller.start";
    public static final String QUEUE_NAVIGATOR_START = "navigator.start";
    public static final String QUEUE_SAVE_START = "save.start";

    private SystemQueues() {
    }

    public static String carQueue(int id) {
        return "car.no" + id;
    }

    public static String navigatorQueue(int id) {
        return "navigator.no" + id;
    }

    public static String navigatorRoutingKey(int id) {
        return ".no" + id;
    }
}
