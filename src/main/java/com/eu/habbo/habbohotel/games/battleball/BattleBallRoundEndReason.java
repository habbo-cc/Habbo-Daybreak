package com.eu.habbo.habbohotel.games.battleball;

/** Why the current BattleBall round entered its ending state. */
public enum BattleBallRoundEndReason {
    NONE(false),
    TIME_EXPIRED(false),
    INSUFFICIENT_ACTIVE_TEAMS(true),
    HUMAN_OPPOSITION_LOST(true);

    private final boolean aborted;

    BattleBallRoundEndReason(boolean aborted) {
        this.aborted = aborted;
    }

    public boolean aborted() {
        return this.aborted;
    }
}
