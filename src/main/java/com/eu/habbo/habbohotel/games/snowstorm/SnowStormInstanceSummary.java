package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.List;

/** Lobby-row projection shared by live instances and persisted finished rounds. */
public record SnowStormInstanceSummary(
        int rowKind,
        int id,
        String name,
        int ownerUserId,
        String ownerName,
        int mapId,
        int state,
        int teamCount,
        int playerCount,
        int spectatorCount,
        int lengthChoice,
        int resultId,
        List<String> playerNames) {

    public static final int LIVE = 0;
    public static final int FINISHED = 1;

    public SnowStormInstanceSummary {
        playerNames = List.copyOf(playerNames);
    }

    public static SnowStormInstanceSummary live(SnowStormGame game) {
        List<String> playerNames = game.players().stream().map(SnowStormPlayer::name).toList();
        return new SnowStormInstanceSummary(LIVE, game.id(), game.name(), game.ownerUserId(),
                game.ownerName(), game.map().mapId(), game.state().id(), game.teamCount(),
                playerNames.size(), game.spectators().size(), game.lengthChoice(), -1, playerNames);
    }

    public static SnowStormInstanceSummary finished(SnowStormFinishedGame game) {
        List<String> playerNames = game.players().stream()
                .sorted(java.util.Comparator.comparingInt(SnowStormRoundResult.PlayerResult::placement)
                        .thenComparingInt(SnowStormRoundResult.PlayerResult::userId))
                .map(SnowStormRoundResult.PlayerResult::name).toList();
        return new SnowStormInstanceSummary(FINISHED, game.instanceId(), game.name(),
                game.ownerUserId(), game.ownerName(), game.mapId(), SnowStormState.ENDING.id(),
                game.teamCount(), playerNames.size(), 0, game.lengthChoice(), game.resultId(), playerNames);
    }
}
