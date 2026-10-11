package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorUsersInGroup extends WiredSelectorConfigBase {
    public WiredSelectorUsersInGroup(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorUsersInGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.USERS_IN_GROUP; }
    @Override public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null) {
            return false;
        }
        String raw = settings.getStringParam();
        String group = raw == null ? "" : raw.trim();
        if (!group.isEmpty()) {
            try {
                if (Integer.parseInt(group) <= 0) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        settings.setStringParam(group);
        return super.saveData(settings);
    }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        for (RoomUnit unit : allRoomUnits(room)) {
            if (groupMatches(room, unit)) {
                targets.addUser(unit);
            }
        }
        return targets;
    }
}
