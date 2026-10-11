package com.eu.habbo.habbohotel.games.battleball;

/** Internal mutation/presentation event consumed into compact authoritative delta operations. */
public record BattleBallEvent(int type, int objectId, int secondaryId, int value, int x, int y) {
    public static BattleBallEvent spawn(int objectId) { return new BattleBallEvent(0, objectId, -1, 0, 0, 0); }
    public static BattleBallEvent despawn(int objectId) { return new BattleBallEvent(1, objectId, -1, 0, 0, 0); }
    public static BattleBallEvent move(int playerObjectId, int x, int y) { return new BattleBallEvent(2, playerObjectId, -1, 0, x, y); }
    public static BattleBallEvent pickup(int playerObjectId, int powerObjectId, int powerType) { return new BattleBallEvent(3, playerObjectId, powerObjectId, powerType, 0, 0); }
    public static BattleBallEvent state(int playerObjectId, int playerState, int durationMs) { return new BattleBallEvent(4, playerObjectId, -1, playerState, durationMs, 0); }
    public static BattleBallEvent activation(int playerObjectId, int powerObjectId, int direction, int powerType) { return new BattleBallEvent(5, playerObjectId, powerObjectId, powerType, direction, 0); }
    public static BattleBallEvent update(int objectId) { return new BattleBallEvent(6, objectId, -1, 0, 0, 0); }
}
