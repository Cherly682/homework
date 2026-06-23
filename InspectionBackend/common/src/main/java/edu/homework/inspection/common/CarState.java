package edu.homework.inspection.common;

public enum CarState {
    IDLE(0),
    ASSIGNING(1),
    RUNNING(2),
    BLOCKED(3);

    private final int code;

    CarState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static CarState fromCode(String code) {
        if (code == null) {
            return IDLE;
        }
        for (CarState state : values()) {
            if (String.valueOf(state.code).equals(code.trim())) {
                return state;
            }
        }
        return IDLE;
    }
}
