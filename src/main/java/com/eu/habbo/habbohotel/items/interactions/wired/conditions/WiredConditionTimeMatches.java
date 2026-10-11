package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;

public class WiredConditionTimeMatches extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.TIME_MATCHES;

    public WiredConditionTimeMatches(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionTimeMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] values = settings == null ? null : settings.getIntParams();
        if (values == null || values.length != 9
                || !flag(values[0]) || !flag(values[1]) || !flag(values[2])
                || !range(values[3], values[4], 0, 59)
                || !range(values[5], values[6], 0, 59)
                || !range(values[7], values[8], 0, 23)) {
            return false;
        }
        settings.setStringParam(normalizeTimeZone(settings.getStringParam()));
        return super.saveData(settings);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Calendar now = Calendar.getInstance(safeTimeZone(this.stringParam));
        return inRange(this.intParams, 0, now.get(Calendar.SECOND), 3, 4)
                && inRange(this.intParams, 1, now.get(Calendar.MINUTE), 5, 6)
                && inRange(this.intParams, 2, now.get(Calendar.HOUR_OF_DAY), 7, 8);
    }

    private TimeZone safeTimeZone(String id) {
        if (id == null || id.trim().isEmpty()) return TimeZone.getTimeZone("UTC");
        TimeZone zone = TimeZone.getTimeZone(id.trim());
        if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(id.trim())
                && !id.trim().toUpperCase(java.util.Locale.ROOT).startsWith("GMT")) {
            return TimeZone.getTimeZone("UTC");
        }
        return zone;
    }

    private String normalizeTimeZone(String id) {
        TimeZone zone = safeTimeZone(id);
        return zone.getID();
    }

    private boolean flag(int value) {
        return value == 0 || value == 1;
    }

    private boolean range(int min, int max, int floor, int ceiling) {
        return min >= floor && max <= ceiling && min <= max;
    }

    private boolean inRange(int[] params, int enabledIndex, int value, int minIndex, int maxIndex) {
        if (params.length <= enabledIndex || params[enabledIndex] == 0) {
            return true;
        }
        int min = params.length > minIndex ? params[minIndex] : value;
        int max = params.length > maxIndex ? params[maxIndex] : value;
        return value >= min && value <= max;
    }
}
