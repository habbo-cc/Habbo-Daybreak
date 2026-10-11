package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.users.HabboItem;

/** The two chest families present in the July AIR client. */
public enum ChestType {
    FURNI(0, "furni"),
    COINS(1, "coins");

    private final int wireCode;
    private final String configPrefix;

    ChestType(int wireCode, String configPrefix) {
        this.wireCode = wireCode;
        this.configPrefix = configPrefix;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public String configPrefix() {
        return this.configPrefix;
    }

    public static ChestType fromItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return null;
        }
        String interaction = item.getBaseItem().getInteractionType() == null
                ? "" : item.getBaseItem().getInteractionType().getName();
        String className = item.getBaseItem().getName();
        String value = (interaction + " " + className).toLowerCase();
        if (value.contains("wf_storage_coins")) {
            return COINS;
        }
        return value.contains("wf_storage_furni") ? FURNI : null;
    }
}
