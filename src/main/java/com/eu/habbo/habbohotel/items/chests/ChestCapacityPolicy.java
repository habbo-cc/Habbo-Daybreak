package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboItem;

/** July capacity/upgrade rules with official production defaults. */
public final class ChestCapacityPolicy {
    private ChestCapacityPolicy() {
    }

    public static boolean isStarter(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return false;
        }
        String infix = configString("wired.chests_starter_infix", "_starter");
        return !infix.isBlank() && item.getBaseItem().getName().contains(infix);
    }

    public static int capacity(HabboItem item, ChestType type, int level) {
        if (type == null) {
            return 0;
        }
        if (isStarter(item)) {
            return configInt(key(type, "starter_capacity"), starterDefault(type));
        }
        int safeLevel = Math.max(0, Math.min(level, maxUpgrades(type)));
        int initial = configInt(key(type, "initial_capacity"), initialDefault(type));
        int perLevel = configInt(key(type, "upgrade_capacity"), upgradeDefault(type));
        return Math.max(0, initial + perLevel * safeLevel);
    }

    public static int maxUpgrades(ChestType type) {
        return Math.max(0, configInt(key(type, "max_upgrades"),
                type == ChestType.COINS ? 19 : 9));
    }

    public static boolean validUpgrade(HabboItem item, ChestType type, int currentLevel, int levels) {
        return type != null
                && !isStarter(item)
                && levels > 0
                && currentLevel >= 0
                && currentLevel + levels <= maxUpgrades(type);
    }

    private static String key(ChestType type, String suffix) {
        return "wired." + type.configPrefix() + "_chest." + suffix;
    }

    private static int initialDefault(ChestType type) {
        return type == ChestType.COINS ? 5000 : 1000;
    }

    private static int starterDefault(ChestType type) {
        return type == ChestType.COINS ? 500 : 100;
    }

    private static int upgradeDefault(ChestType type) {
        return type == ChestType.COINS ? 5000 : 1000;
    }

    private static int configInt(String key, int fallback) {
        try {
            return Emulator.getConfig().getInt(key, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String configString(String key, String fallback) {
        try {
            String value = Emulator.getConfig().getValue(key);
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
