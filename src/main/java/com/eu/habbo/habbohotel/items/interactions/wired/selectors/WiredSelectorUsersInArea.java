package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorUsersInArea extends WiredSelectorConfigBase {
    public WiredSelectorUsersInArea(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorUsersInArea(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.USERS_IN_AREA; }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        for (RoomUnit unit : allRoomUnits(room)) {
            if (inArea(unit.getX(), unit.getY())) {
                targets.addUser(unit);
            }
        }
        return targets;
    }
}
