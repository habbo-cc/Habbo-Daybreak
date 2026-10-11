package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR selector 10: users carried by the current signal. */
public final class WiredSelectorUsersFromSignal extends WiredSelectorFromSignalBase {
    public WiredSelectorUsersFromSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorUsersFromSignal(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return WiredSelectorType.USERS_FROM_SIGNAL;
    }

    @Override
    public WiredTargets resolve(Room room, WiredContext context) {
        WiredTargets targets = new WiredTargets();
        if (!WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || context == null
                || context.eventType() != WiredEvent.Type.RECEIVE_SIGNAL) {
            return targets;
        }
        for (RoomUnit user : context.event().getSignalPayload().users(room)) {
            targets.addUser(user);
        }
        return targets;
    }
}
