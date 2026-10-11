package com.eu.habbo.habbohotel.games.snowstorm;

public enum SnowStormState {
    LOBBY(0), COUNTDOWN(1), RUNNING(2), ENDING(3), DISPOSED(4);

    private final int id;

    SnowStormState(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
