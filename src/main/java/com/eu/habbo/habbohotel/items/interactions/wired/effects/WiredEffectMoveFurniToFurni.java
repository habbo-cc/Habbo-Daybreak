package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectMoveFurniToFurni extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_TO_FURNI;

    public WiredEffectMoveFurniToFurni(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectMoveFurniToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected int getFurniSourceSlotCount() { return 2; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) { return new int[] { slot == 0 ? FURNI_SOURCE_PICKED_2 : FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR }; }
    @Override protected int getDefaultFurniSourceForSlot(int slot) { return slot == 0 ? FURNI_SOURCE_PICKED_2 : FURNI_SOURCE_PICKED_1; }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        HabboItem target = sourceItems(ctx, 1).stream().findFirst().orElse(null);
        if (target == null) return;
        RoomTile tile = room.getLayout().getTile(target.getX(), target.getY());
        if (tile == null) return;
        for (HabboItem item : WiredMovementAddonRuntime.furniTargets(ctx, sourceItems(ctx, 0))) {
            if (item != target) {
                RoomTile from = room.getLayout().getTile(item.getX(), item.getY());
                double fromZ = item.getZ();
                if (from != null && WiredMovementAddonRuntime.move(
                        ctx, room, item, tile, item.getRotation(), false, false)
                        == FurnitureMovementError.NONE) {
                    WiredMovementAddonRuntime.moved(ctx, room, item, from, fromZ, tile);
                }
            }
        }
    }
}
