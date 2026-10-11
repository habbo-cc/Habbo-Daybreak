package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionCanPerformMove extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.CAN_PERFORM_MOVE;

    public WiredConditionCanPerformMove(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionCanPerformMove(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (this.items.isEmpty()) {
            return true;
        }
        for (HabboItem item : this.items) {
            RoomTile tile = ctx.room().getLayout().getTile(item.getX(), item.getY());
            if (tile == null || ctx.room().furnitureFitsAt(tile, item, item.getRotation(), true) != FurnitureMovementError.NONE) {
                return false;
            }
        }
        return true;
    }
}
