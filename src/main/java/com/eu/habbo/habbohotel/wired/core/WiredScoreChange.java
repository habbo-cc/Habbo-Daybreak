package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import java.util.Objects;

/** Team and totals captured at the score mutation, independent of later game state. */
public record WiredScoreChange(GameTeamColors team, long previous, long current) {
    public WiredScoreChange {
        Objects.requireNonNull(team, "team");
    }

    public boolean crosses(int threshold) {
        return previous < threshold && current >= threshold && current > previous;
    }
}
