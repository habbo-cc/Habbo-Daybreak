package com.eu.habbo.habbohotel.games.snowstorm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormRoundResultTest {
    @Test
    void capturesStableTeamPlacementsAndMarksDepartedStartingPlayers() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertTrue(game.beginChargedRound(roster, 77, 0));
        assertTrue(game.beginRunning(SnowStormGame.COUNTDOWN_MS));

        assertTrue(game.removePlayer(10) != null);
        game.advance(SnowStormGame.COUNTDOWN_MS);
        SnowStormRoundResult result = SnowStormRoundResult.capture(
                game, SnowStormRoundResult.END_TIME_EXPIRED, false);

        assertEquals(77, result.resultId());
        assertEquals(0, result.winningTeam());
        assertEquals(1, result.teams().get(0).placement());
        assertEquals(2, result.teams().get(1).placement());
        assertEquals(10, result.players().get(0).userId());
        assertEquals(1, result.players().get(0).placement());
        assertTrue(result.players().get(0).winner());
        assertTrue(result.players().get(0).disconnected());
        assertFalse(result.players().get(1).winner());
    }

    @Test
    void oneTeamFreeForAllUsesStablePlayerTieBreakWithoutATeamWinner() {
        SnowStormGame game = game(1);
        assertTrue(game.addPlayer(20, "Later", "hd-180-2", "F", 0));
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertTrue(game.beginChargedRound(roster, 78, 0));

        SnowStormRoundResult result = SnowStormRoundResult.capture(
                game, SnowStormRoundResult.END_TIME_EXPIRED, false);

        assertEquals(SnowStormRoundResult.NO_TEAM_WINNER, result.winningTeam());
        assertEquals(10, result.players().get(0).userId());
        assertTrue(result.players().get(0).winner());
        assertFalse(result.players().get(1).winner());
    }

    @Test
    void abortedResultsNeverAwardWinners() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertTrue(game.beginChargedRound(roster, 79, 0));

        SnowStormRoundResult result = SnowStormRoundResult.capture(
                game, SnowStormRoundResult.END_INTERNAL_FAILURE, true);

        assertEquals(SnowStormRoundResult.NO_TEAM_WINNER, result.winningTeam());
        assertTrue(result.players().stream().noneMatch(SnowStormRoundResult.PlayerResult::winner));
    }

    @Test
    void inactivityDepartureProducesAnAbortedOutcomeWithoutWinners() {
        SnowStormGame game = game(2);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertTrue(game.beginChargedRound(roster, 80, 0));

        assertTrue(game.removePlayer(20) != null);
        SnowStormRoundResult result = SnowStormRoundResult.capture(
                game, SnowStormRoundResult.END_NOT_ENOUGH_OPPOSITION, true);

        assertTrue(result.aborted());
        assertEquals(SnowStormRoundResult.NO_TEAM_WINNER, result.winningTeam());
        assertTrue(result.players().stream().noneMatch(SnowStormRoundResult.PlayerResult::winner));
        assertTrue(result.players().stream()
                .filter(player -> player.userId() == 20)
                .allMatch(SnowStormRoundResult.PlayerResult::disconnected));
    }

    private static SnowStormGame game(int teamCount) {
        return new SnowStormGame(1, "SnowStorm", 10, "Owner",
                SnowStormMapCatalog.loadGenerated().get(1), 1, teamCount, 20);
    }
}
