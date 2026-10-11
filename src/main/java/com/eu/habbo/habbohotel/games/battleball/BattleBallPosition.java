package com.eu.habbo.habbohotel.games.battleball;

public record BattleBallPosition(int x, int y, double z, int direction) {
    public BattleBallPosition(int x, int y) {
        this(x, y, 0.0, 0);
    }

    public int distanceSquared(BattleBallPosition other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return dx * dx + dy * dy;
    }
}
