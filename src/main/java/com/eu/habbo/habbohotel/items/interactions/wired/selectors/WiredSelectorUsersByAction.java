package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.WiredUserAction;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorUsersByAction extends WiredSelectorConfigBase {
    public WiredSelectorUsersByAction(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorUsersByAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override
    public WiredSelectorType getType() {
        return WiredSelectorType.USERS_PERFORMING_ACTION;
    }

    @Override
    public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        int action = this.intParams.length > 0 ? this.intParams[0] : WiredUserAction.WAVE;
        for (RoomUnit unit : allRoomUnits(room)) {
            if (WiredUserAction.matches(unit, ctx, action, this.stringParam)) {
                targets.addUser(unit);
            }
        }
        return targets;
    }
}
