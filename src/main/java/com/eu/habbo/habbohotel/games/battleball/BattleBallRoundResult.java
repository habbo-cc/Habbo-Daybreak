package com.eu.habbo.habbohotel.games.battleball;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Immutable end-of-round data safe to persist after the live game has reset or been disposed. */
public record BattleBallRoundResult(
        int instanceId,
        int roundNumber,
        String name,
        int ownerUserId,
        String ownerName,
        int mapId,
        boolean privateGame,
        int teamCount,
        int capacity,
        int roundSeconds,
        boolean aborted,
        boolean ranked,
        int winningTeam,
        Map<Integer, Integer> teamScores,
        List<PlayerResult> players) {

    public record PlayerResult(int userId, String name, int teamId, int score, boolean winner) {}

    public BattleBallRoundResult {
        teamScores = Map.copyOf(teamScores);
        players = List.copyOf(players);
    }

    public static BattleBallRoundResult capture(BattleBallGame game, boolean aborted) {
        Map<Integer, Integer> teamScores = game.scores();
        Map<Integer, Integer> playerScores = game.playerScores();
        int best = teamScores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        long winnerCount = teamScores.values().stream().filter(score -> score == best).count();
        int winningTeam = !aborted && winnerCount == 1
                ? teamScores.entrySet().stream().filter(entry -> entry.getValue() == best)
                        .findFirst().orElseThrow().getKey()
                : -1;
        List<PlayerResult> players = new ArrayList<>();
        for (BattleBallPlayer player : game.players()) {
            players.add(new PlayerResult(player.userId(), player.name(), player.teamId(),
                    playerScores.getOrDefault(player.userId(), 0), player.teamId() == winningTeam));
        }
        return new BattleBallRoundResult(game.id(), game.roundNumber(), game.name(), game.ownerUserId(),
                game.ownerName(), game.map().id(), game.privateGame(), game.teams().size(), game.capacity(),
                game.roundSeconds(), aborted, game.rankedRound(), winningTeam, teamScores, players);
    }
}
