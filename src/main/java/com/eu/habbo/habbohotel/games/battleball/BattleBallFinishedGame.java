package com.eu.habbo.habbohotel.games.battleball;

import java.util.List;
import java.util.Map;

/** Immutable persisted round shown in the lounge after its live game has ended. */
public record BattleBallFinishedGame(
        int resultId,
        int instanceId,
        int roundNumber,
        String name,
        int ownerUserId,
        String ownerName,
        int mapId,
        boolean privateGame,
        int teamCount,
        int playerCount,
        int capacity,
        int roundSeconds,
        int endedAt,
        int winningTeam,
        Map<Integer, Integer> teamScores,
        List<PlayerResult> players) {

    public record PlayerResult(int userId, String name, int teamId, int score, boolean winner) {}

    public BattleBallFinishedGame {
        teamScores = Map.copyOf(teamScores);
        players = List.copyOf(players);
    }

    /** Historical ids occupy the negative half of the existing signed instance-id field. */
    public int wireId() { return -this.resultId; }

    public boolean visibleTo(int userId) {
        return !this.privateGame || this.ownerUserId == userId
                || this.players.stream().anyMatch(player -> player.userId() == userId);
    }
}
