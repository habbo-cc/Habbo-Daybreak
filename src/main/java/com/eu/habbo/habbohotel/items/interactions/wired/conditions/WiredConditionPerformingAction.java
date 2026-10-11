package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredUserAction;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionPerformingAction extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.PERFORMING_ACTION;

    public WiredConditionPerformingAction(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionPerformingAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        RoomUnit actor = ctx.actor().orElse(null);
        if (actor == null) {
            return false;
        }

        int action = this.intParams.length > 0 ? this.intParams[0] : WiredUserAction.WAVE;
        return WiredUserAction.matches(actor, ctx, action, this.stringParam);
    }
}
