package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared merged-source anchoring for both neighbourhood selector output axes. */
abstract class WiredSelectorNeighborhoodBase extends WiredSelectorConfigBase {
    protected WiredSelectorNeighborhoodBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredSelectorNeighborhoodBase(int id, int userId, Item item, String extradata,
                                            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 1;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] { FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR };
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] { USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR };
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_TRIGGERING_ITEM;
    }

    @Override
    protected int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_TRIGGERING_USER;
    }

    protected boolean isInNeighborhood(int targetX, int targetY, WiredContext ctx) {
        if (this.intParams.length < WiredNeighborhoodMask.PARAM_COUNT) {
            return false;
        }

        boolean useUserSource = this.intParams[0] != 0;
        if (useUserSource) {
            for (RoomUnit anchor : resolveUserSource(ctx, this.userSourceTypes, 0)) {
                if (WiredNeighborhoodMask.contains(this.intParams, targetX, targetY, anchor.getX(), anchor.getY())) {
                    return true;
                }
            }
        } else {
            for (HabboItem anchor : resolveFurniSource(ctx, this.furniSourceTypes, 0, this.items, null)) {
                if (WiredNeighborhoodMask.contains(this.intParams, targetX, targetY, anchor.getX(), anchor.getY())) {
                    return true;
                }
            }
        }
        return false;
    }
}
