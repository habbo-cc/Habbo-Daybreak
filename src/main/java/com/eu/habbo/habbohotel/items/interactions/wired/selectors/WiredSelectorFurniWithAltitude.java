package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorFurniWithAltitude extends WiredSelectorConfigBase {
    public WiredSelectorFurniWithAltitude(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorFurniWithAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.FURNI_WITH_ALTITUDE; }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        for (HabboItem item : allFloorItems(room)) {
            if (altitudeMatches(item.getZ())) {
                targets.addItem(item);
            }
        }
        return targets;
    }
}
