package com.eu.habbo.habbohotel.games.snowstorm;

public enum SnowStormActivityState {
    NORMAL(0, true, true, true, true),
    CREATING(1, true, true, false, true),
    STUNNED(2, false, false, false, false),
    INVINCIBLE_AFTER_STUN(3, true, true, true, false);

    private final int id;
    private final boolean canWalk;
    private final boolean canThrow;
    private final boolean canMake;
    private final boolean avatarCollision;

    SnowStormActivityState(int id, boolean canWalk, boolean canThrow, boolean canMake, boolean avatarCollision) {
        this.id = id;
        this.canWalk = canWalk;
        this.canThrow = canThrow;
        this.canMake = canMake;
        this.avatarCollision = avatarCollision;
    }

    public int id() { return id; }
    public boolean canWalk() { return canWalk; }
    public boolean canThrow() { return canThrow; }
    public boolean canMake() { return canMake; }
    public boolean avatarCollision() { return avatarCollision; }

    public static SnowStormActivityState fromId(int id) {
        return switch (id) {
            case 0 -> NORMAL;
            case 1 -> CREATING;
            case 2 -> STUNNED;
            case 3 -> INVINCIBLE_AFTER_STUN;
            default -> throw new IllegalArgumentException("Unknown SnowStorm activity state " + id);
        };
    }
}
