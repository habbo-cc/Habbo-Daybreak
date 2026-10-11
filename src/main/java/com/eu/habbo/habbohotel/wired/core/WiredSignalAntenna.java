package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.users.HabboItem;

/** Identifies official signal antenna furniture without introducing a new interaction type. */
public final class WiredSignalAntenna {
    private static final String ANTENNA_PREFIX = "wf_antenna";

    private WiredSignalAntenna() {
    }

    public static boolean isAntenna(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        String itemName = item.getBaseItem().getName();
        if (itemName != null && itemName.startsWith(ANTENNA_PREFIX)) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();
        return interactionName != null && interactionName.startsWith(ANTENNA_PREFIX);
    }
}
