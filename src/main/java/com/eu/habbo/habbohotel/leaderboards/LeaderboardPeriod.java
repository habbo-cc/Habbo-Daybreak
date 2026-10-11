package com.eu.habbo.habbohotel.leaderboards;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public enum LeaderboardPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ALLTIME;

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    public static LeaderboardPeriod fromString(String value) {
        if (value == null || value.trim().isEmpty()) return WEEKLY;

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "DAY", "DAILY", "TODAY" -> DAILY;
            case "MONTH", "MONTHLY", "THISMONTH" -> MONTHLY;
            case "ALL", "ALLTIME" -> ALLTIME;
            default -> WEEKLY;
        };
    }

    public long getCurrentPeriodStart() {
        LocalDate today = LocalDate.now(ZONE_ID);
        ZonedDateTime start = switch (this) {
            case DAILY -> today.atStartOfDay(ZONE_ID);
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE_ID);
            case MONTHLY -> today.withDayOfMonth(1).atStartOfDay(ZONE_ID);
            case ALLTIME -> null;
        };
        return start == null ? 0L : start.toEpochSecond();
    }
}
