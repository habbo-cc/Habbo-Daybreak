package com.eu.habbo.habbohotel.games.battleball;

public enum BattleBallPowerType {
    LIGHT_BULB(1), SPRING(2), FLASHLIGHT(3), CANNON(4), BOX_OF_PINS(5),
    HARLEQUIN(6), BOMB(7), DRILL(8), QUESTION_MARK(9);

    private final int id;

    BattleBallPowerType(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }

    public static BattleBallPowerType fromId(int id) {
        for (BattleBallPowerType type : values()) {
            if (type.id == id) return type;
        }
        return null;
    }
}
