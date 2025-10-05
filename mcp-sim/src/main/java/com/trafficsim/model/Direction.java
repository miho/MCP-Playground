package com.trafficsim.model;

/**
 * Represents the four cardinal directions for traffic approaches.
 */
public enum Direction {
    NORTH("N"),
    SOUTH("S"),
    EAST("E"),
    WEST("W");

    private final String code;

    Direction(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Direction fromCode(String code) {
        for (Direction dir : values()) {
            if (dir.code.equals(code)) {
                return dir;
            }
        }
        throw new IllegalArgumentException("Unknown direction code: " + code);
    }
}
