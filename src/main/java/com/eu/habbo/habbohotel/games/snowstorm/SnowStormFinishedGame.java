package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.List;
import java.util.Map;

/** Immutable persisted SnowStorm result used by live final scores and historical rows. */
public record SnowStormFinishedGame(
        int resultId,
        int instanceId,
        int roundNumber,
        String name,
        int ownerUserId,
        String ownerName,
        int mapId,
        int teamCount,
        int lengthChoice,
        int roundSeconds,
        int endedAt,
        int endReason,
        int winningTeam,
        boolean ranked,
        Map<Integer, SnowStormRoundResult.TeamResult> teams,
        List<SnowStormRoundResult.PlayerResult> players) {

    public SnowStormFinishedGame {
        teams = Map.copyOf(teams);
        players = List.copyOf(players);
    }
}
