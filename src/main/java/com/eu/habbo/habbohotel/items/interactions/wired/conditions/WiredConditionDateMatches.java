package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;

public class WiredConditionDateMatches extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.DATE_MATCHES;

    public WiredConditionDateMatches(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionDateMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] values = settings == null ? null : settings.getIntParams();
        if (values == null || values.length != 8
                || !flag(values[0]) || !flag(values[1])
                || values[2] < 0 || values[2] > 0x7f
                || values[5] < 0 || values[5] > 0xfff
                || !range(values[3], values[4], 1, 31)
                || !range(values[6], values[7], 0, 9999)) {
            return false;
        }
        settings.setStringParam(safeTimeZone(settings.getStringParam()).getID());
        return super.saveData(settings);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Calendar now = Calendar.getInstance(safeTimeZone(this.stringParam));
        int day = now.get(Calendar.DAY_OF_MONTH);
        int month = now.get(Calendar.MONTH) + 1;
        int year = now.get(Calendar.YEAR);
        int weekday = ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1;

        if (this.intParams.length > 0 && this.intParams[0] != 0) {
            int min = this.intParams.length > 3 ? this.intParams[3] : day;
            int max = this.intParams.length > 4 ? this.intParams[4] : day;
            if (day < min || day > max) {
                return false;
            }
        }

        if (this.intParams.length > 2 && this.intParams[2] != 0 && !maskContains(this.intParams[2], weekday)) {
            return false;
        }

        if (this.intParams.length > 5 && this.intParams[5] != 0 && !maskContains(this.intParams[5], month)) {
            return false;
        }

        if (this.intParams.length > 1 && this.intParams[1] != 0) {
            int min = this.intParams.length > 6 ? this.intParams[6] : year;
            int max = this.intParams.length > 7 ? this.intParams[7] : year;
            return year >= min && year <= max;
        }

        return true;
    }

    private boolean maskContains(int mask, int oneBasedValue) {
        return (mask & (1 << Math.max(0, oneBasedValue - 1))) != 0;
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

    private boolean flag(int value) {
        return value == 0 || value == 1;
    }

    private boolean range(int min, int max, int floor, int ceiling) {
        return min >= floor && max <= ceiling && min <= max;
    }
}
