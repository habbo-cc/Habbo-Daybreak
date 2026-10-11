package com.eu.habbo.habbohotel.wired;

/**
 * Availability values exposed by July's variable editors. USER_IN_ROOM is the
 * User Variable editor's "while user is in room"; which codes each variable
 * type may use is checked by WiredVariableDefinition.
 */
public enum WiredVariableAvailability {
    USER_IN_ROOM(0),
    ROOM_ACTIVE(1),
    PERMANENT(10),
    SHARED_PERMANENT(11);

    public final int code;

    WiredVariableAvailability(int code) {
        this.code = code;
    }

    public boolean isPersistent() {
        return this == PERMANENT || this == SHARED_PERMANENT;
    }

    public static WiredVariableAvailability fromCode(int code) {
        for (WiredVariableAvailability availability : values()) {
            if (availability.code == code) {
                return availability;
            }
        }
        return null;
    }
}
