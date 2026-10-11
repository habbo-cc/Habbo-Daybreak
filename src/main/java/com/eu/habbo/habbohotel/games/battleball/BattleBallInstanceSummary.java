package com.eu.habbo.habbohotel.games.battleball;

/** The common list-row shape shared by live games and persisted finished rounds. */
public record BattleBallInstanceSummary(
        int instanceId,
        String name,
        int ownerUserId,
        String ownerName,
        int mapId,
        int state,
        boolean privateGame,
        int teamCount,
        int playerCount,
        int capacity,
        int roundSeconds) {

    public static BattleBallInstanceSummary live(BattleBallGame game) {
        return new BattleBallInstanceSummary(game.id(), game.name(), game.ownerUserId(), game.ownerName(),
                game.map().id(), game.state().id(), game.privateGame(), game.teams().size(),
                game.players().size(), game.capacity(), game.roundSeconds());
    }

    public static BattleBallInstanceSummary finished(BattleBallFinishedGame game) {
        return new BattleBallInstanceSummary(game.wireId(), game.name(), game.ownerUserId(), game.ownerName(),
                game.mapId(), BattleBallState.ENDING.id(), game.privateGame(), game.teamCount(),
                game.playerCount(), game.capacity(), game.roundSeconds());
    }
}
