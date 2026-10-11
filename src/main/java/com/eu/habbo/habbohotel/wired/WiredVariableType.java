package com.eu.habbo.habbohotel.wired;

/**
 * Variable definition codes used by the July Wired 2.0 protocol.
 *
 * <p>These values are protocol identities, not enabled runtime semantics.</p>
 */
public enum WiredVariableType {
    FURNI(0),
    USER(1),
    /** July GLOBAL_VARIABLE code 2: one room/global scalar definition. Donor code 1 is incompatible. */
    ROOM(2),
    CONTEXT(3),
    REFERENCE(4),
    QUEST(5),
    QUEST_CHAIN(6),
    ECHO(7),
    DAILY_TASK(8),
    UNKNOWN(-1);

    public final int code;

    WiredVariableType(int code) {
        this.code = code;
    }

    public static WiredVariableType fromCode(int code) {
        for (WiredVariableType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
