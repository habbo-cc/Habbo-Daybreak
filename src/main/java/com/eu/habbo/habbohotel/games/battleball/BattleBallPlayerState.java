package com.eu.habbo.habbohotel.games.battleball;

public enum BattleBallPlayerState {
    NORMAL(0), STUNNED(1), TURBO_BOOST(2), HIGH_JUMPS(3), CLEANING_TILES(4),
    COLORING_FOR_OPPONENT(5), CLIMBING_INTO_CANNON(6), FLYING_THROUGH_AIR(7),
    BALL_BROKEN(8);

    private final int id;

    BattleBallPlayerState(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }
}
