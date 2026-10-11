package com.eu.habbo.habbohotel.games.battleball;

public enum BattleBallTileState {
    DEFAULT(0), TOUCHED(1), CLICKED(2), PRESSED(3), SEALED(4);

    private final int id;

    BattleBallTileState(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }

    public BattleBallTileState next() {
        return this == SEALED ? SEALED : values()[this.ordinal() + 1];
    }
}
