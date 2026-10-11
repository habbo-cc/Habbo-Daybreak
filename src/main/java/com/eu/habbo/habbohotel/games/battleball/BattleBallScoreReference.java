package com.eu.habbo.habbohotel.games.battleball;

public record BattleBallScoreReference(int points, int teamId, int userId) {
    public BattleBallScoreReference {
        // Real users have positive ids while game-scoped bots deliberately use negative ids. Both
        // may own points for the lifetime of a match; only zero means that no scoring player exists.
        // Persistence filters bots before touching account-backed statistics.
        if (points < 0 || teamId < 0 || userId == 0) {
            throw new IllegalArgumentException("Invalid BattleBall score reference");
        }
    }
}
