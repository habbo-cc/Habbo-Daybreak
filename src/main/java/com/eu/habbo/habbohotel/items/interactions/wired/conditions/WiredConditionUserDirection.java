package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public class WiredConditionUserDirection extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.USER_DIRECTION;

    public WiredConditionUserDirection(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionUserDirection(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override protected boolean supportsUserPicking() { return true; }

    @Override protected byte getWiredQuantifierType() { return 2; }

    @Override
    protected int[] getWiredUserSourceTypes() {
        return this.userSourceTypes.length > 0 ? this.userSourceTypes : new int[] { getDefaultUserSourceForSlot(0) };
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        super.loadWiredData(set, room);
        if (wiredData != null && wiredData.startsWith("{") && !wiredData.contains("\"quantifierCode\"") &&
                this.intParams.length > 0 && this.intParams[0] >= 0 && this.intParams[0] < 8) {
            this.intParams[0] = 1 << this.intParams[0];
        }
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int directionMask = this.intParams.length > 0 ? (this.intParams[0] & 0xFF) : 0;
        if (directionMask == 0) {
            return false;
        }

        Collection<RoomUnit> users = sourceUsers(ctx);
        if (users.isEmpty()) {
            return false;
        }

        if (this.quantifierCode == 1) {
            return users.stream().anyMatch(user -> matchesDirection(user, directionMask));
        }
        return users.stream().allMatch(user -> matchesDirection(user, directionMask));
    }

    private boolean matchesDirection(RoomUnit user, int directionMask) {
        return user != null && (directionMask & (1 << user.getBodyRotation().getValue())) != 0;
    }
}
