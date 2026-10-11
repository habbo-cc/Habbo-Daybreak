package com.eu.habbo.habbohotel.wired;

/**
 * Add-on definition codes used by the July Wired 2.0 protocol.
 *
 * <p>These values describe the wire contract only. Their presence does not
 * advertise or enable any add-on runtime implementation.</p>
 */
public enum WiredAddonType {
    CONDITION_EVALUATION(0),
    /** Random effect selection: skip-count then pick-count. */
    EFFECT_PICK_AND_SKIP(1),
    /** Parameterless unseen-effect marker. */
    EFFECT_MARKER(2),
    EXECUTION_LIMIT(5),
    NO_MOVE_ANIMATION(6),
    MOVEMENT_PHYSICS(7),
    CARRY_USERS(8),
    ANIMATION_TIME(9),
    FURNI_SELECTOR_FILTER(10),
    USER_SELECTOR_FILTER(11),
    FURNI_VARIABLE_FILTER(12),
    USER_VARIABLE_FILTER(13),
    USERNAME_PLACEHOLDER(14),
    VARIABLE_PLACEHOLDER(15),
    VARIABLE_CAPTURER(16),
    EXECUTE_IN_ORDER(17),
    CHEST_ITEM_TYPE_SCANNER(18),
    FURNI_NAME_PLACEHOLDER(19),
    CUSTOM_CONTRACT(20),
    PROJECTILE(21),
    JUMP_STRENGTH(22),
    VARIABLE_TEXT_CONVERTER(1000),
    VARIABLE_LEVEL_UP(1001),
    VARIABLE_TIME_UTILITY(1002),
    GLOBAL_PLACEHOLDER(2000),
    ACHIEVEMENT_ENABLER(2001),
    UNKNOWN(-1);

    public final int code;

    WiredAddonType(int code) {
        this.code = code;
    }

    public static WiredAddonType fromCode(int code) {
        for (WiredAddonType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
