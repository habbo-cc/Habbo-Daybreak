package com.eu.habbo.habbohotel.games.snowstorm;

public enum SnowStormTrajectory {
    QUICK(0),
    SHORT_LOB(1),
    LONG_LOB(2);

    private final int id;

    SnowStormTrajectory(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static SnowStormTrajectory fromId(int id) {
        return switch (id) {
            case 0 -> QUICK;
            case 1 -> SHORT_LOB;
            case 2 -> LONG_LOB;
            default -> throw new IllegalArgumentException("Unknown SnowStorm trajectory " + id);
        };
    }
}
