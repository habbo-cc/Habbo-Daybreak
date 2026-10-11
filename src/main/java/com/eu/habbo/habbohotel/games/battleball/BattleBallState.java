package com.eu.habbo.habbohotel.games.battleball;

public enum BattleBallState {
    LOBBY(0), COUNTDOWN(1), RUNNING(2), ENDING(3), DISPOSED(4);

    private final int id;

    BattleBallState(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }
}
