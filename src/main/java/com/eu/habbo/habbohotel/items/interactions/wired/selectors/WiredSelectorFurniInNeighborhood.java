package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorFurniInNeighborhood extends WiredSelectorNeighborhoodBase {
    public WiredSelectorFurniInNeighborhood(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorFurniInNeighborhood(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override
    public WiredSelectorType getType() {
        return WiredSelectorType.FURNI_IN_NEIGHBORHOOD;
    }

    @Override
    public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        for (HabboItem item : allFloorItems(room)) {
            if (isInNeighborhood(item.getX(), item.getY(), ctx)) {
                targets.addItem(item);
            }
        }
        return targets;
    }
}
