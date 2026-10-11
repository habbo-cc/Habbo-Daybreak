package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionFurniHasAltitude extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.FURNI_HAS_ALTITUDE;

    public WiredConditionFurniHasAltitude(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionFurniHasAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int height = this.intParams.length > 0 ? this.intParams[0] : 0;
        int operator = this.intParams.length > 1 ? this.intParams[1] : 0;
        if (this.items.isEmpty()) {
            return false;
        }
        for (HabboItem item : this.items) {
            if (!compare((int) Math.round(item.getZ() * 100.0), height, operator)) {
                return false;
            }
        }
        return true;
    }
}
