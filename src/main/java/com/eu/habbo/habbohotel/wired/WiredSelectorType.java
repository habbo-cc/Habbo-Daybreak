package com.eu.habbo.habbohotel.wired;

public enum WiredSelectorType {
    FURNI_BY_TYPE(0, true, false),
    FURNI_CHOOSER(1, true, false),
    USERS_BY_TYPE(2, false, true),
    USERS_IN_TEAM(3, false, true),
    FURNI_ON_FURNI(4, true, false),
    FURNI_FROM_SIGNAL(5, true, false),
    FURNI_IN_NEIGHBORHOOD(6, true, false),
    FURNI_IN_AREA(7, true, false),
    USERS_ON_FURNI(8, false, true),
    USERS_PERFORMING_ACTION(9, false, true),
    USERS_FROM_SIGNAL(10, false, true),
    USERS_BY_NAME(11, false, true),
    USERS_IN_NEIGHBORHOOD(12, false, true),
    USERS_IN_AREA(13, false, true),
    USERS_WITH_HANDITEM(14, false, true),
    USERS_IN_GROUP(15, false, true),
    FURNI_WITH_ALTITUDE(16, true, false),
    FURNI_WITH_VARIABLE(17, true, false),
    USERS_WITH_VARIABLE(18, false, true),
    REMOTE_SELECTOR(19, true, true),
    UNKNOWN(-1, false, false);

    public final int code;
    public final boolean isFurni;
    public final boolean isUser;

    WiredSelectorType(int code, boolean isFurni, boolean isUser) {
        this.code = code;
        this.isFurni = isFurni;
        this.isUser = isUser;
    }

    public static WiredSelectorType fromCode(int code) {
        for (WiredSelectorType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
