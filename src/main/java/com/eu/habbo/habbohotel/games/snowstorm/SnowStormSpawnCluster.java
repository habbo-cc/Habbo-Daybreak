package com.eu.habbo.habbohotel.games.snowstorm;

public record SnowStormSpawnCluster(
        int id,
        int centerX,
        int centerY,
        int radius,
        int minimumSeparation,
        int applicableTeamCountMask,
        String teamAssignment,
        String fallbackRule) {

    public boolean supportsTeamCount(int teamCount) {
        return teamCount >= 1 && teamCount <= 4
                && (applicableTeamCountMask & (1 << (teamCount - 1))) != 0;
    }
}
