package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** July add-on 1002: exposes selected calendar/duration subvariables. */
public final class WiredAddonVariableTimeUtility extends InteractionWiredAddon {
    public static final int SOURCE_VALUE = 0;
    public static final int SOURCE_CREATED_AT = 1;
    public static final int SOURCE_UPDATED_AT = 2;

    private static final int VERSION = 1;
    private static final int ALLOWED_MASK = 0x07F007FE; // July IDs 1..10 and 20..26.
    private static final String[] STANDARD_NAMES = {
            "milliseconds_of_seconds", "seconds_of_minute", "minute_of_hour", "hour_of_day",
            "day_of_week", "day_of_month", "day_of_year", "week_of_year", "month_of_year", "year"
    };
    private static final String[] ADVANCED_NAMES = {
            "millisecond", "second", "minute", "hour", "day", "week", "month"
    };

    private int mask;
    private int sourceMode;

    public WiredAddonVariableTimeUtility(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredAddonVariableTimeUtility(int id, int userId, Item item, String extradata,
                                         int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.VARIABLE_TIME_UTILITY;
    }

    @Override
    public synchronized boolean saveData(WiredSettingsV2 settings) {
        if (!ownsOnlyTwoInts(settings)) {
            return false;
        }
        int requestedMask = settings.getIntParams()[0];
        int requestedMode = settings.getIntParams()[1];
        if ((requestedMask & ~ALLOWED_MASK) != 0 || requestedMode < SOURCE_VALUE || requestedMode > SOURCE_UPDATED_AT) {
            return false;
        }
        this.mask = requestedMask;
        this.sourceMode = requestedMode;
        return true;
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(new Data(VERSION, this.mask, this.sourceMode));
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        if (set == null) {
            return;
        }
        try {
            Data data = WiredManager.getGson().fromJson(set.getString("wired_data"), Data.class);
            if (data != null && data.version == VERSION && (data.mask & ~ALLOWED_MASK) == 0
                    && data.sourceMode >= SOURCE_VALUE && data.sourceMode <= SOURCE_UPDATED_AT) {
                this.mask = data.mask;
                this.sourceMode = data.sourceMode;
            }
        } catch (RuntimeException ignored) {
            // Corrupt data leaves the safe empty default.
        }
    }

    @Override
    public synchronized void onPickUp() {
        this.mask = 0;
        this.sourceMode = SOURCE_VALUE;
    }

    @Override
    protected synchronized int[] getWiredIntParams() {
        return new int[] {this.mask, this.sourceMode};
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }

    public synchronized int mask() { return this.mask; }
    public synchronized int sourceMode() { return this.sourceMode; }

    public synchronized List<String> enabledNames() {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < STANDARD_NAMES.length; index++)
            if ((this.mask & (1 << (index + 1))) != 0) names.add(STANDARD_NAMES[index]);
        for (int index = 0; index < ADVANCED_NAMES.length; index++)
            if ((this.mask & (1 << (index + 20))) != 0) names.add(ADVANCED_NAMES[index]);
        return List.copyOf(names);
    }

    /** Resolves the configured July source from our authoritative variable value. */
    public synchronized long sourceValue(WiredVariableValue value) {
        if (value == null) {
            return 0L;
        }
        return switch (this.sourceMode) {
            case SOURCE_CREATED_AT -> value.createdAtMs();
            case SOURCE_UPDATED_AT -> value.updatedAtMs();
            default -> value.value();
        };
    }

    /** Returns only the subvariables whose July bit IDs are enabled. */
    public synchronized Map<String, Long> derive(long milliseconds) {
        if (this.mask == 0) {
            return Collections.emptyMap();
        }
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Long> values = new LinkedHashMap<>();
        for (int index = 0; index < STANDARD_NAMES.length; index++) {
            if ((this.mask & (1 << (index + 1))) != 0) {
                values.put(STANDARD_NAMES[index], standardValue(index, milliseconds, dateTime));
            }
        }
        for (int index = 0; index < ADVANCED_NAMES.length; index++) {
            if ((this.mask & (1 << (index + 20))) != 0) {
                values.put(ADVANCED_NAMES[index], advancedValue(index, milliseconds, dateTime));
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static long standardValue(int index, long milliseconds, LocalDateTime dateTime) {
        return switch (index) {
            case 0 -> Math.floorMod(milliseconds, 1000L);
            case 1 -> Math.floorMod(Math.floorDiv(milliseconds, 1000L), 60L);
            case 2 -> Math.floorMod(Math.floorDiv(milliseconds, 60000L), 60L);
            case 3 -> Math.floorMod(Math.floorDiv(milliseconds, 3600000L), 24L);
            case 4 -> dateTime.getDayOfWeek().getValue();
            case 5 -> dateTime.getDayOfMonth();
            case 6 -> dateTime.getDayOfYear();
            case 7 -> dateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case 8 -> dateTime.getMonthValue();
            case 9 -> dateTime.getYear();
            default -> 0L;
        };
    }

    private static long advancedValue(int index, long milliseconds, LocalDateTime dateTime) {
        return switch (index) {
            case 0 -> milliseconds;
            case 1 -> Math.floorDiv(milliseconds, 1000L);
            case 2 -> Math.floorDiv(milliseconds, 60000L);
            case 3 -> Math.floorDiv(milliseconds, 3600000L);
            case 4 -> Math.floorDiv(milliseconds, 86400000L);
            case 5 -> Math.floorDiv(milliseconds, 604800000L);
            case 6 -> ((long) dateTime.getYear() - 1970L) * 12L + dateTime.getMonthValue() - 1L;
            default -> 0L;
        };
    }

    private static boolean ownsOnlyTwoInts(WiredSettingsV2 settings) {
        return settings != null && settings.getIntParams().length == 2
                && settings.getStringParam().isEmpty()
                && settings.getFurniIds().length == 0 && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0;
    }

    private static final class Data {
        int version;
        int mask;
        int sourceMode;
        Data() { }
        Data(int version, int mask, int sourceMode) {
            this.version = version; this.mask = mask; this.sourceMode = sourceMode;
        }
    }
}
