package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredEffectMoveFurniToUser extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_TO_USER;

    public WiredEffectMoveFurniToUser(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectMoveFurniToUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override public boolean requiresTriggeringUser() { return false; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected boolean supportsUserPicking() { return true; }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        List<HabboItem> movingItems = WiredMovementAddonRuntime.furniTargets(ctx, sourceItems(ctx));
        for (RoomUnit unit : sourceUsers(ctx)) {
            RoomTile tile = unit.getCurrentLocation();
            if (tile == null) continue;
            for (HabboItem item : movingItems) {
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
