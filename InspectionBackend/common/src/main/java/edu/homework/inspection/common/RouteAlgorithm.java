package edu.homework.inspection.common;

public enum RouteAlgorithm {
    ASTAR("0"),
    BIDIRECTIONAL_ASTAR("1"),
    DIJKSTRA("2");

    private final String code;

    RouteAlgorithm(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static RouteAlgorithm fromRedis(String value) {
        if (value == null) {
            return ASTAR;
        }
        for (RouteAlgorithm algorithm : values()) {
            if (algorithm.code.equals(value.trim())) {
                return algorithm;
            }
        }
        return ASTAR;
    }
}
