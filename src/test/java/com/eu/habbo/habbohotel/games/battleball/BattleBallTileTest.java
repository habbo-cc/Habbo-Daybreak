package com.eu.habbo.habbohotel.games.battleball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BattleBallTileTest {
    @Test
    void originalScoringAndStealRulesArePreserved() {
        BattleBallTile tile = new BattleBallTile(2, 3, true);

        assertTrue(tile.land(1, 0, 10, false, false));
        assertEquals(BattleBallTileState.CLICKED, tile.state());
        assertEquals(6, score(tile));

        assertTrue(tile.land(1, 0, 10, false, false));
        assertEquals(BattleBallTileState.PRESSED, tile.state());
        assertEquals(16, score(tile));

        assertTrue(tile.land(1, 1, 20, false, false));
        assertEquals(1, tile.scoreReferences().size(), "a steal replaces the previous team's references");
        assertEquals(8, score(tile));
    }

    @Test
    void barebonesNeedsFourLandingsAndHighJumpSealsImmediately() {
        BattleBallTile classic = new BattleBallTile(0, 0, true);
        classic.land(5, 0, 10, false, false);
        assertEquals(BattleBallTileState.TOUCHED, classic.state());
        classic.land(5, 0, 10, false, false);
        classic.land(5, 0, 10, false, false);
        classic.land(5, 0, 10, false, false);
        assertEquals(BattleBallTileState.SEALED, classic.state());

        BattleBallTile jump = new BattleBallTile(1, 1, true);
        assertTrue(jump.land(1, 2, 30, true, false));
        assertEquals(BattleBallTileState.SEALED, jump.state());
        assertEquals(14, score(jump));
        assertFalse(jump.land(1, 1, 20, false, false));
    }

    private static int score(BattleBallTile tile) {
        return tile.scoreReferences().stream().mapToInt(BattleBallScoreReference::points).sum();
    }
}
