package com.eu.habbo.habbohotel.leaderboards;

import java.util.Collections;
import java.util.List;

public record LeaderboardData(
        LeaderboardDefinition selected,
        List<LeaderboardDefinition> available,
        LeaderboardPeriod period,
        int offset,
        int limit,
        int totalRows,
        List<LeaderboardRow> rows,
        LeaderboardRow ownRow) {

    public LeaderboardData {
        available = available == null ? Collections.emptyList() : List.copyOf(available);
        rows = rows == null ? Collections.emptyList() : List.copyOf(rows);
    }
}
