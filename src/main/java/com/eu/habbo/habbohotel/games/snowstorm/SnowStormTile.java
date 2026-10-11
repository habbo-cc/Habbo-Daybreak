package com.eu.habbo.habbohotel.games.snowstorm;

public record SnowStormTile(int x, int y) {
    public long distanceSquared(SnowStormTile other) {
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        return dx * dx + dy * dy;
    }
}
