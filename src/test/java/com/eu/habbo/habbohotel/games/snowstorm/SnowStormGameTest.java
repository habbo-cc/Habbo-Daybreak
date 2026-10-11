package com.eu.habbo.habbohotel.games.snowstorm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormGameTest {
    @Test
    void enforcesOriginalTeamCapacitiesAndWaitingOnlyMembership() {
        assertEquals(12, SnowStormGame.capacityForTeamCount(1));
        assertEquals(6, SnowStormGame.capacityForTeamCount(2));
        assertEquals(4, SnowStormGame.capacityForTeamCount(3));
        assertEquals(3, SnowStormGame.capacityForTeamCount(4));

        SnowStormGame game = game(4);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(11, "Two", "hd-180-1", "F", 0));
        assertTrue(game.addPlayer(12, "Three", "hd-180-1", "M", 0));
        assertFalse(game.addPlayer(13, "Full", "hd-180-1", "F", 0));
        assertTrue(game.addPlayer(13, "Other", "hd-180-1", "F", 1));
        assertFalse(game.changeTeam(13, 0));
    }

    @Test
    void freezesStableRosterAndRejectsAStaleChargeTransition() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));

        SnowStormStartingRoster prepared = game.prepareStart(10, true);
        assertNotNull(prepared);
        assertTrue(prepared.ranked());
        assertEquals(List.of(10, 20), prepared.players().stream()
                .map(SnowStormStartingRoster.Player::userId).toList());
        assertEquals(List.of(1, 2), prepared.players().stream()
                .map(SnowStormStartingRoster.Player::objectId).toList());

        assertTrue(game.addPlayer(30, "Late roster change", "hd-180-3", "M", 1));
        assertFalse(game.beginChargedRound(prepared, 1_000));
        SnowStormStartingRoster refreshed = game.prepareStart(10, true);
        assertNotNull(refreshed);
        assertTrue(refreshed.ranked());
        assertTrue(game.beginChargedRound(refreshed, 1_000));
        assertEquals(SnowStormState.COUNTDOWN, game.state());
        assertFalse(game.addPlayer(40, "Late", "hd-180-4", "M", 1));
        assertTrue(game.addSpectator(40));
    }

    @Test
    void gameModelTracksOwnerDepartureForManagerLifecycleHandling() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertNotNull(roster);
        assertTrue(roster.ranked());
        assertTrue(game.beginChargedRound(roster, 0));

        assertNotNull(game.removePlayer(10));
        assertEquals(SnowStormState.COUNTDOWN, game.state());
        assertTrue(game.beginRunning(SnowStormGame.COUNTDOWN_MS));
        SnowStormSimulation.TurnResult turn = game.advance(SnowStormGame.COUNTDOWN_MS);
        assertNotNull(turn);
        assertTrue(turn.subturns().get(0).stream().anyMatch(event ->
                event instanceof SnowStormSimulationEvent.RemoveObject removed
                        && removed.objectId() == 1));
        assertFalse(turn.objects().stream().anyMatch(object -> object.objectId() == 1));
        assertEquals(SnowStormState.RUNNING, game.state());
    }

    @Test
    void requiresOppositionToStartAndRanksOnlyHumanOpposition() {
        SnowStormGame game = game(1);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertFalse(game.canStart(10));
        assertFalse(game.rankedEligible(true));
        assertNull(game.prepareStart(10, true));
        assertNull(game.prepareStart(99, true));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 0));
        assertTrue(game.canStart(10));
        assertTrue(game.rankedEligible(true));
    }

    @Test
    void teamStartGateRequiresTwoOccupiedTeamsAndTheOwnerInLobby() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Same side", "hd-180-2", "F", 0));
        assertFalse(game.canStart(10));
        assertFalse(game.canStart(20));
        assertTrue(game.changeTeam(20, 1));
        assertTrue(game.canStart(10));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertNotNull(roster);
        assertTrue(game.beginChargedRound(roster, 77, 1));
        assertFalse(game.canStart(10));
    }

    @Test
    void botCountsForStartButNotRankedHumanOpposition() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addBot(1));
        assertTrue(game.canStart(10));
        assertFalse(game.rankedEligible(true));
        assertTrue(game.rankedEligible(false));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertNotNull(roster);
        assertFalse(roster.ranked());
        assertEquals(1, roster.players().stream().filter(SnowStormStartingRoster.Player::bot).count());
    }

    @Test
    void idleSweepNeverRemovesBots() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addBot(1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertNotNull(roster);
        assertTrue(game.beginChargedRound(roster, 77, 1));
        assertTrue(game.beginRunning(1 + SnowStormGame.COUNTDOWN_MS));

        game.checkIdle(1 + SnowStormGame.COUNTDOWN_MS + 61_000L);

        assertTrue(game.players().stream().anyMatch(SnowStormPlayer::isBot));
        assertFalse(game.players().stream().anyMatch(player -> player.userId() == 10));
        assertFalse(game.hasHumanPlayer());
    }

    @Test
    void humanRosterPredicateStaysTrueWhileAnyHumanRemains() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        assertTrue(game.addBot(1));

        assertTrue(game.hasHumanPlayer());
        assertNotNull(game.removePlayer(20));
        assertTrue(game.hasHumanPlayer());
        assertNotNull(game.removePlayer(10));
        assertFalse(game.hasHumanPlayer());
    }

    @Test
    void botUsesTheAuthoritativeIntentionPipeline() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addBot(1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertNotNull(roster);
        int botObjectId = roster.players().stream().filter(SnowStormStartingRoster.Player::bot)
                .mapToInt(SnowStormStartingRoster.Player::objectId).findFirst().orElseThrow();
        assertTrue(game.beginChargedRound(roster, 77, 1));
        long runningAt = 1 + SnowStormGame.COUNTDOWN_MS;
        assertTrue(game.beginRunning(runningAt));

        SnowStormSimulation.TurnResult turn = game.advance(runningAt + 1_200L);

        assertNotNull(turn);
        assertTrue(turn.subturns().stream().flatMap(List::stream).anyMatch(event ->
                (event instanceof SnowStormSimulationEvent.AvatarMove move
                        && move.objectId() == botObjectId
                        || event instanceof SnowStormSimulationEvent.AvatarThrow throwing
                        && throwing.objectId() == botObjectId)));
    }

    @Test
    void acceptsOnlyMonotonicAuthoritativeIntentionsAndRejectsFriendlyTargets() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Friend", "hd-180-2", "F", 0));
        assertTrue(game.addPlayer(30, "Enemy", "hd-180-3", "M", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertNotNull(roster);
        assertTrue(game.beginChargedRound(roster, 0));
        assertTrue(game.beginRunning(SnowStormGame.COUNTDOWN_MS));
        int friendObjectId = roster.players().stream().filter(player -> player.userId() == 20)
                .mapToInt(SnowStormStartingRoster.Player::objectId).findFirst().orElseThrow();
        int enemyObjectId = roster.players().stream().filter(player -> player.userId() == 30)
                .mapToInt(SnowStormStartingRoster.Player::objectId).findFirst().orElseThrow();

        assertFalse(game.submitIntention(10, 0,
                new SnowStormIntention.ThrowAvatar(SnowStormTrajectory.QUICK, friendObjectId)));
        assertTrue(game.submitIntention(10, 0,
                new SnowStormIntention.ThrowAvatar(SnowStormTrajectory.QUICK, enemyObjectId)));
        assertFalse(game.submitIntention(10, 0,
                new SnowStormIntention.Walk(0, 0)));
        assertFalse(game.submitIntention(10, -1,
                new SnowStormIntention.MakeSnowball()));
    }

    @Test
    void idleWarningsAndRemovalsAreEmittedOnceForManagerLifecycleHandling() {
        SnowStormGame game = new SnowStormGame(1, "SnowStorm", 10, "Owner",
                SnowStormMapCatalog.loadGenerated().get(1), 1, 2, 20, 30, 5);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertTrue(game.beginChargedRound(roster, 1_000));
        assertTrue(game.beginRunning(1_000 + SnowStormGame.COUNTDOWN_MS));

        game.checkIdle(26_001);
        assertEquals(Map.of(10, 5, 20, 5), game.consumeIdleWarnings());
        assertTrue(game.consumeIdleWarnings().isEmpty());
        game.checkIdle(31_001);
        assertEquals(Set.of(10, 20), game.consumeIdleRemovals());
        assertTrue(game.consumeIdleRemovals().isEmpty());
        assertEquals(SnowStormState.RUNNING, game.state());
    }

    private static SnowStormGame game(int teamCount) {
        return new SnowStormGame(1, "SnowStorm", 10, "Owner",
                SnowStormMapCatalog.loadGenerated().get(1), 1, teamCount, 20);
    }
}
