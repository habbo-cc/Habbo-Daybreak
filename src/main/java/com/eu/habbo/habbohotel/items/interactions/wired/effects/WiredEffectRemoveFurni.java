package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectRemoveFurni extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.REMOVE_FURNI;

    public WiredEffectRemoveFurni(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectRemoveFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
    }
    @Override protected int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_SELECTOR;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        for (HabboItem item : sourceItems(ctx).toArray(new HabboItem[0])) {
            if (item != null && item.getId() < 0) {
                room.removeTemporaryFloorFurni(item);
            }
        }
    }
}
