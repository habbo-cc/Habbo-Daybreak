package com.eu.habbo.habbohotel.leaderboards;

public record LeaderboardDefinition(
        int id,
        int roomId,
        String gameType,
        String metric,
        String titleLocalizationCode,
        String descriptionLocalizationCode,
        String menuLocalizationCode,
        int sortOrder) {
}
