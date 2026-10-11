package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorUsersWithHanditem extends WiredSelectorConfigBase {
    public WiredSelectorUsersWithHanditem(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorUsersWithHanditem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.USERS_WITH_HANDITEM; }
    @Override public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 1 || settings.getIntParams()[0] < 0) {
            return false;
        }
        return super.saveData(settings);
    }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        int handItem = this.intParams.length > 0 ? this.intParams[0] : 0;
        for (RoomUnit unit : allRoomUnits(room)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && unit.getHandItem() == handItem) {
                targets.addUser(unit);
            }
        }
        return targets;
    }
}
