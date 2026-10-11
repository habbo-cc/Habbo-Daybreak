package com.eu.habbo.habbohotel.games.battleball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BattleBallGameTest {
    @Test
    void requiresTwoNonEmptyTeamsAndEnforcesCapacity() {
        BattleBallGame game = game(1);
        assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
        assertFalse(game.addPlayer(3, "overflow", "", "", "M", 0));
        assertFalse(game.canStart(1));
        assertTrue(game.addPlayer(2, "opponent", "", "", "F", 1));
        assertTrue(game.canStart(1));
    }

    @Test
    void countdownMovementScoringAndInstanceStateAreAuthoritative() {
        BattleBallGame game = game(10);
        game.addPlayer(1, "owner", "", "", "M", 0);
        game.addPlayer(2, "opponent", "", "", "F", 1);
        assertTrue(game.start(1, 1_000, true));
        assertEquals(BattleBallState.COUNTDOWN, game.state());
        game.tick(4_000);
        assertEquals(BattleBallState.RUNNING, game.state());
        assertTrue(game.requestMove(1, 2, 1, 4_500));
        game.tick(4_500);
        game.tick(5_000);
        BattleBallPosition destination = game.players().stream()
                .filter(player -> player.userId() == 1).findFirst().orElseThrow().position();
        assertEquals(2, destination.x());
        assertEquals(1, destination.y());
        assertEquals(18, game.scores().get(0));
        assertEquals(18, game.playerScores().get(1));
    }

    @Test
    void simultaneousMatchesDoNotShareTilesOrSequences() {
        BattleBallGame first = game(20);
        BattleBallGame second = game(21);
        first.addPlayer(1, "a", "", "", "M", 0); first.addPlayer(2, "b", "", "", "F", 1);
        second.addPlayer(3, "c", "", "", "M", 0); second.addPlayer(4, "d", "", "", "F", 1);
        first.start(1, 0, true); second.start(3, 0, true); first.tick(3_000); second.tick(3_000);
        first.requestMove(1, 1, 0, 3_500);
        first.tick(3_500);
        assertEquals(BattleBallTileState.CLICKED, first.tile(1, 0).state());
        assertEquals(BattleBallTileState.DEFAULT, second.tile(1, 0).state());
    }

    @Test
    void ownerCanStartAgainstBotsAndBotsMayOwnScoreReferences() {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        BattleBallGame game = new BattleBallGame(30, "bots", 1, "owner", map, 60, 2, 3, false,
                Set.of(BattleBallPowerType.LIGHT_BULB),
                Map.of(
                        0, List.of(new BattleBallPosition(0, 0), new BattleBallPosition(1, 0), new BattleBallPosition(0, 1)),
                        1, List.of(new BattleBallPosition(2, 2), new BattleBallPosition(1, 2), new BattleBallPosition(2, 1))),
                60, 15);

        assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
        assertTrue(game.addBot(0));
        assertTrue(game.addBot(1));
        assertTrue(game.canStart(1));
        assertTrue(assertDoesNotThrow(() -> game.start(1, 1_000, true)));
        assertEquals(BattleBallState.COUNTDOWN, game.state());
        assertTrue(game.playerScores().keySet().stream().anyMatch(userId -> userId < 0));

    }

    @Test
    void botStrategyPrefersReachableEnemyGround() {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        BattleBallGame game = new BattleBallGame(31, "bot target", 1, "owner", map, 60, 2, 2,
                false, Set.of(),
                Map.of(0, List.of(new BattleBallPosition(0, 0), new BattleBallPosition(0, 1)),
                        1, List.of(new BattleBallPosition(2, 2), new BattleBallPosition(2, 1))),
                60, 15);
        assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
        assertTrue(game.addBot(1));
        BattleBallPlayer bot = game.players().stream().filter(BattleBallPlayer::isBot)
                .findFirst().orElseThrow();
        game.tile(1, 1).land(map.id(), 0, 1, false, false);

        BattleBallPosition target = new BattleBallBotStrategy().chooseTarget(game, bot);

        assertEquals(new BattleBallPosition(1, 1), target);
    }

    @Test
    void allOriginalPowerIdentifiersRemainDistinct() {
        assertEquals(Set.of(1,2,3,4,5,6,7,8,9),
                java.util.Arrays.stream(BattleBallPowerType.values()).map(BattleBallPowerType::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void losingTheOnlyActiveOpposingTeamAbortsTheRound() {
        BattleBallGame game = twoPerTeamGame(40);
        game.addPlayer(1, "owner", "", "", "M", 0);
        game.addPlayer(2, "teammate", "", "", "F", 0);
        game.addPlayer(3, "opponent", "", "", "M", 1);
        assertTrue(game.start(1, 1_000, true, true));

        game.removePlayer(3);
        game.tick(1_500);

        assertEquals(BattleBallState.ENDING, game.state());
        assertTrue(game.roundAborted());
        assertEquals(BattleBallRoundEndReason.INSUFFICIENT_ACTIVE_TEAMS, game.roundEndReason());
    }

    @Test
    void rankedRoundAbortsWhenOnlyBotOppositionRemains() {
        BattleBallGame game = twoPerTeamGame(41);
        game.addPlayer(1, "owner", "", "", "M", 0);
        game.addPlayer(2, "opponent", "", "", "F", 1);
        assertTrue(game.addBot(1));
        assertTrue(game.start(1, 1_000, true, true));

        game.removePlayer(2);
        game.tick(1_500);

        assertEquals(BattleBallState.ENDING, game.state());
        assertEquals(BattleBallRoundEndReason.HUMAN_OPPOSITION_LOST, game.roundEndReason());
    }

    @Test
    void unrankedBotPracticeContinuesWithTwoPopulatedTeams() {
        BattleBallGame game = twoPerTeamGame(42);
        game.addPlayer(1, "owner", "", "", "M", 0);
        assertTrue(game.addBot(1));
        assertTrue(game.start(1, 1_000, false, true));

        game.tick(1_500);

        assertEquals(BattleBallState.COUNTDOWN, game.state());
        assertFalse(game.roundAborted());
    }

    @Test
    void idleRemovalsAreReportedExactlyOnce() {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        BattleBallGame game = new BattleBallGame(43, "idle", 1, "owner", map, 60, 2, 1, false,
                Set.of(), Map.of(0, List.of(new BattleBallPosition(0, 0)),
                        1, List.of(new BattleBallPosition(2, 2))), 1, 1);
        assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
        assertTrue(game.addPlayer(2, "opponent", "", "", "F", 1));
        assertTrue(game.start(1, 1_000, true));
        game.tick(4_000);
        game.tick(4_001);
        game.tick(34_002);

        assertEquals(Set.of(1, 2), game.consumeIdleRemovals());
        assertTrue(game.consumeIdleRemovals().isEmpty());
    }

    private static BattleBallGame game(int id) {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        return new BattleBallGame(id, "test", 1, "owner", map, 60, 2, 1, false,
                Set.of(BattleBallPowerType.LIGHT_BULB, BattleBallPowerType.SPRING),
                Map.of(0, List.of(new BattleBallPosition(0, 0)), 1, List.of(new BattleBallPosition(2, 2))), 60, 15);
    }

    private static BattleBallGame twoPerTeamGame(int id) {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        return new BattleBallGame(id, "roster", 1, "owner", map, 60, 2, 2, false,
                Set.of(BattleBallPowerType.LIGHT_BULB),
                Map.of(0, List.of(new BattleBallPosition(0, 0), new BattleBallPosition(0, 1)),
                        1, List.of(new BattleBallPosition(2, 2), new BattleBallPosition(2, 1))), 60, 15);
    }
}
