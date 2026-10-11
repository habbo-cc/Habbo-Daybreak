package com.eu.habbo.habbohotel.games.battleball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BattleBallRoundResultTest {
    @Test
    void snapshotIsImmutableAfterTheLiveGameChanges() {
        BattleBallGame game = game();
        game.addPlayer(1, "owner", "", "", "M", 0);
        game.addPlayer(2, "opponent", "", "", "F", 1);
        game.start(1, 1_000L, true);
        game.tick(4_000L);
        game.requestMove(1, 1, 1, 4_500L);
        game.tick(4_500L);

        BattleBallRoundResult snapshot = BattleBallRoundResult.capture(game, false);
        Map<Integer, Integer> capturedScores = snapshot.teamScores();
        List<BattleBallRoundResult.PlayerResult> capturedPlayers = snapshot.players();

        game.removePlayer(1);
        game.tile(1, 2).land(game.map().id(), 1, 2, false, false);

        assertEquals(capturedScores, snapshot.teamScores());
        assertEquals(capturedPlayers, snapshot.players());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.teamScores().put(9, 9));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.players().clear());
    }

    @Test
    void playerWhoLeavesForfeitsPersonalResultButTeamPaintRemains() {
        BattleBallGame game = game();
        game.addPlayer(1, "owner", "", "", "M", 0);
        game.addPlayer(2, "opponent", "", "", "F", 1);
        game.start(1, 1_000L, true);
        game.tick(4_000L);
        game.requestMove(1, 1, 1, 4_500L);
        game.tick(4_500L);
        int earnedTeamScore = game.scores().get(0);
        assertTrue(earnedTeamScore > 0);

        game.removePlayer(1);
        BattleBallRoundResult snapshot = BattleBallRoundResult.capture(game, true);

        assertEquals(earnedTeamScore, snapshot.teamScores().get(0));
        assertFalse(snapshot.players().stream().anyMatch(player -> player.userId() == 1));
        assertEquals(-1, snapshot.winningTeam());
        assertTrue(snapshot.aborted());
    }

    @Test
    void botOnlyOppositionIsCapturedAsUnranked() {
        BattleBallGame game = game();
        game.addPlayer(1, "owner", "", "", "M", 0);
        assertTrue(game.addBot(1));

        boolean ranked = game.rankedEligible(true);
        assertFalse(ranked);
        assertTrue(game.start(1, 1_000L, ranked));

        BattleBallRoundResult snapshot = BattleBallRoundResult.capture(game, false);
        assertFalse(snapshot.ranked());
        assertTrue(game.rankedEligible(false));
    }

    @Test
    void abortedRankedRoundDoesNotQualifyForAggregateStats() {
        BattleBallGame game = game();
        game.addPlayer(1, "owner", "", "", "M", 0);
        game.addPlayer(2, "opponent", "", "", "F", 1);

        boolean ranked = game.rankedEligible(true);
        assertTrue(ranked);
        assertTrue(game.start(1, 1_000L, ranked));
        game.removePlayer(2);

        BattleBallRoundResult result = BattleBallRoundResult.capture(game, true);
        assertTrue(result.ranked());
        assertTrue(result.aborted());
        assertFalse(BattleBallRepository.updatesRankedStats(result));
        assertTrue(BattleBallRepository.updatesRankedStats(new BattleBallRoundResult(
                result.instanceId(), result.roundNumber(), result.name(), result.ownerUserId(),
                result.ownerName(), result.mapId(), result.privateGame(), result.teamCount(),
                result.capacity(), result.roundSeconds(), false, true, -1,
                result.teamScores(), result.players())));
    }

    private static BattleBallGame game() {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        return new BattleBallGame(91, "snapshot", 1, "owner", map, 60, 2, 1, false,
                Set.of(BattleBallPowerType.LIGHT_BULB),
                Map.of(0, List.of(new BattleBallPosition(0, 0)),
                        1, List.of(new BattleBallPosition(2, 2))), 60, 15);
    }
}
