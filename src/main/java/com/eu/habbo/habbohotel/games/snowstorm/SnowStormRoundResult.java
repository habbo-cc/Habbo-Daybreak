package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable end-of-round state captured before the live game may reset. */
public record SnowStormRoundResult(
        int resultId,
        int instanceId,
        int sequence,
        int roundNumber,
        String name,
        int ownerUserId,
        String ownerName,
        int mapId,
        int teamCount,
        int lengthChoice,
        int roundSeconds,
        int endReason,
        boolean aborted,
        boolean ranked,
        int winningTeam,
        Map<Integer, TeamResult> teams,
        List<PlayerResult> players) {

    public static final int END_TIME_EXPIRED = 0;
    public static final int END_OWNER_BEFORE_CHARGE = 1;
    public static final int END_NOT_ENOUGH_OPPOSITION = 2;
    public static final int END_SERVER_SHUTDOWN = 3;
    public static final int END_INTERNAL_FAILURE = 4;
    public static final int NO_TEAM_WINNER = 255;

    public record TeamResult(int teamId, int score, int placement) {}
    public record PlayerResult(
            int userId,
            String name,
            int teamId,
            int score,
            int placement,
            boolean winner,
            boolean disconnected) {}

    public SnowStormRoundResult {
        teams = Map.copyOf(teams);
        players = List.copyOf(players);
    }

    public static SnowStormRoundResult capture(
            SnowStormGame game,
            int endReason,
            boolean aborted) {
        return game.captureRoundResult(endReason, aborted);
    }

    static SnowStormRoundResult capture(Capture capture, int endReason, boolean aborted) {
        SnowStormStartingRoster roster = capture.roster();
        Map<Integer, Integer> scoresByUser = capture.scoresByUser();
        Map<Integer, Integer> scoresByTeam = capture.scoresByTeam();

        List<Integer> orderedTeams = scoresByTeam.keySet().stream()
                .sorted(Comparator.<Integer>comparingInt(team -> scoresByTeam.getOrDefault(team, 0))
                        .reversed().thenComparingInt(Integer::intValue))
                .toList();
        Map<Integer, TeamResult> teams = new LinkedHashMap<>();
        for (int index = 0; index < orderedTeams.size(); index++) {
            int teamId = orderedTeams.get(index);
            teams.put(teamId, new TeamResult(teamId, scoresByTeam.getOrDefault(teamId, 0), index + 1));
        }

        int winningTeam = aborted || capture.teamCount() == 1 || orderedTeams.isEmpty()
                ? NO_TEAM_WINNER : orderedTeams.getFirst();
        List<SnowStormStartingRoster.Player> orderedPlayers = roster.players().stream()
                .sorted(Comparator.<SnowStormStartingRoster.Player>comparingInt(
                                player -> scoresByUser.getOrDefault(player.userId(), 0))
                        .reversed().thenComparingInt(SnowStormStartingRoster.Player::teamId)
                        .thenComparingInt(SnowStormStartingRoster.Player::userId))
                .toList();
        List<PlayerResult> players = new ArrayList<>(orderedPlayers.size());
        for (int index = 0; index < orderedPlayers.size(); index++) {
            SnowStormStartingRoster.Player player = orderedPlayers.get(index);
            boolean winner = !aborted && (capture.teamCount() == 1
                    ? index == 0 : player.teamId() == winningTeam);
            players.add(new PlayerResult(player.userId(), player.name(), player.teamId(),
                    scoresByUser.getOrDefault(player.userId(), 0), index + 1, winner,
                    capture.departedStartingUsers().contains(player.userId())));
        }

        return new SnowStormRoundResult(capture.resultId(), capture.instanceId(), capture.sequence(),
                capture.roundNumber(), capture.name(), capture.ownerUserId(), capture.ownerName(),
                capture.mapId(), capture.teamCount(), capture.lengthChoice(), capture.roundSeconds(),
                endReason, aborted, roster.ranked(), winningTeam, teams, players);
    }

    record Capture(
            int resultId,
            int instanceId,
            int sequence,
            int roundNumber,
            String name,
            int ownerUserId,
            String ownerName,
            int mapId,
            int teamCount,
            int lengthChoice,
            int roundSeconds,
            SnowStormStartingRoster roster,
            Map<Integer, Integer> scoresByUser,
            Map<Integer, Integer> scoresByTeam,
            Set<Integer> departedStartingUsers) {
        Capture {
            scoresByUser = Map.copyOf(scoresByUser);
            scoresByTeam = Map.copyOf(scoresByTeam);
            departedStartingUsers = Set.copyOf(departedStartingUsers);
        }
    }
}
