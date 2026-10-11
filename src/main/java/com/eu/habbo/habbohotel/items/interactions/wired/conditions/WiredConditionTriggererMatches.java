package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionTriggererMatches extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.TRIGGERER_MATCHES;

    public WiredConditionTriggererMatches(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionTriggererMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        RoomUnit actor = ctx.actor().orElse(null);
        if (actor == null) {
            return false;
        }

        int expectedType = this.intParams.length > 0 ? this.intParams[0] : actor.getRoomUnitType().getTypeId();
        if (actor.getRoomUnitType().getTypeId() != expectedType) {
            return false;
        }

        if (this.stringParam == null || this.stringParam.trim().isEmpty()) {
            return true;
        }

        Habbo habbo = ctx.room().getHabbo(actor);
        return habbo != null && this.stringParam.equalsIgnoreCase(habbo.getHabboInfo().getUsername());
    }
}
