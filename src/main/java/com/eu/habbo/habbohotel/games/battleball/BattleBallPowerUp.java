package com.eu.habbo.habbohotel.games.battleball;

public final class BattleBallPowerUp {
    private final int objectId;
    private BattleBallPowerType type;
    private final BattleBallPosition position;
    private long expiresAt;
    private int holderObjectId = -1;

    BattleBallPowerUp(int objectId, BattleBallPowerType type, BattleBallPosition position, long expiresAt) {
        this.objectId = objectId;
        this.type = type;
        this.position = position;
        this.expiresAt = expiresAt;
    }

    public int objectId() { return this.objectId; }
    public BattleBallPowerType type() { return this.type; }
    public BattleBallPosition position() { return this.position; }
    public long expiresAt() { return this.expiresAt; }
    public int holderObjectId() { return this.holderObjectId; }

    void collect(BattleBallPlayer player, BattleBallPowerType resolvedType, long expiresAt) {
        this.type = resolvedType;
        this.holderObjectId = player.objectId();
        this.expiresAt = expiresAt;
    }
}
