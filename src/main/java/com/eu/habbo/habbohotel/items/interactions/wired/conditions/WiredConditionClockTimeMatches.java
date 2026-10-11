package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionClockTimeMatches extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.CLOCK_TIME_MATCHES;

    public WiredConditionClockTimeMatches(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionClockTimeMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int seconds = this.intParams.length > 0 ? this.intParams[0] : 0;
        int minutes = this.intParams.length > 1 ? this.intParams[1] : 0;
        int half = this.intParams.length > 2 ? this.intParams[2] : 0;
        int operator = this.intParams.length > 3 ? this.intParams[3] : 0;
        int targetHalfSeconds = (minutes * 120) + (seconds * 2) + half;
        return compare(WiredManager.getRoomClock(ctx.room()).getTotalHalfSeconds(), targetHalfSeconds, operator);
    }
}
