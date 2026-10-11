package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

public class WiredConditionNotFurniTypeMatch extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.NOT_STUFF_IS;

    public WiredConditionNotFurniTypeMatch(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionNotFurniTypeMatch(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected int getFurniSourceSlotCount() { return 2; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return slot == 0
                ? new int[] { FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL }
                : new int[] { FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL };
    }
    @Override protected int getDefaultFurniSourceForSlot(int slot) {
        return slot == 0 ? FURNI_SOURCE_TRIGGERING_ITEM : FURNI_SOURCE_PICKED_1;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Collection<HabboItem> left = resolveFurniSource(ctx, this.furniSourceTypes, 0, this.items, null);
        Collection<HabboItem> right = new ArrayList<>(
                resolveFurniSource(ctx, this.furniSourceTypes, 1, this.items, null));
        if (left.isEmpty() || right.isEmpty()) return true;
        for (HabboItem a : left) for (HabboItem b : right) {
            if (a != null && b != null && a.getBaseItem().getId() == b.getBaseItem().getId()) return false;
        }
        return true;
    }
}
