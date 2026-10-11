package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotHasHandItem extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.NOT_HAS_HANDITEM;

    public WiredConditionNotHasHandItem(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionNotHasHandItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        RoomUnit actor = ctx.actor().orElse(null);
        int handItem = this.intParams.length > 0 ? this.intParams[0] : 0;
        return actor != null && actor.getHandItem() != handItem;
    }
}
