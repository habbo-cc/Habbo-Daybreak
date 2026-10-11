package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectMoveUser extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.MOVE_USER;

    public WiredEffectMoveUser(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectMoveUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override public boolean requiresTriggeringUser() { return true; }
    @Override protected boolean supportsUserPicking() { return true; }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        int move = this.intParams.length > 0 ? this.intParams[0] : -1;
        int rotate = this.intParams.length > 1 ? this.intParams[1] : -1;
        for (RoomUnit unit : sourceUsers(ctx)) {
            if (rotate >= 0 && rotate < 8) {
                unit.setRotation(RoomUserRotation.fromValue(rotate));
                room.sendComposer(new UserUpdateMessageComposer(unit).compose());
            }
            if (move >= 0 && move < 8) {
                RoomTile tile = room.getLayout().getTileInFront(unit.getCurrentLocation(), move, 0);
                if (tile != null) unit.setGoalLocation(tile);
            }
        }
    }
}
