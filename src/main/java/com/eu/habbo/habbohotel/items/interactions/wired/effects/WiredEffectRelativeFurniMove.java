package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectRelativeFurniMove extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.RELATIVE_FURNI_MOVE;

    public WiredEffectRelativeFurniMove(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectRelativeFurniMove(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        int dx = this.intParams.length > 0 ? this.intParams[0] : 0;
        int dy = this.intParams.length > 1 ? this.intParams[1] : 0;
        for (HabboItem item : WiredMovementAddonRuntime.furniTargets(ctx, sourceItems(ctx))) {
            RoomTile tile = room.getLayout().getTile((short) (item.getX() + dx), (short) (item.getY() + dy));
            if (tile != null) {
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
