package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredNeighborhoodMaskTest {
    @Test
    void followsClientClockwiseSpiralRanks() {
        assertEquals(0, WiredNeighborhoodMask.rankAt(0, 0));
        assertEquals(1, WiredNeighborhoodMask.rankAt(1, 0));
        assertEquals(2, WiredNeighborhoodMask.rankAt(1, -1));
        assertEquals(3, WiredNeighborhoodMask.rankAt(0, -1));
        assertEquals(4, WiredNeighborhoodMask.rankAt(-1, -1));
        assertEquals(-1, WiredNeighborhoodMask.rankAt(11, 0));
    }

    @Test
    void mapsTheSourceOntoTheConfiguredRootCell() {
        int[] parameters = parametersWithCell(2, -3);
        parameters[1] = 2;
        parameters[2] = -3;

        assertTrue(WiredNeighborhoodMask.contains(parameters, 40, 50, 40, 50));
        assertFalse(WiredNeighborhoodMask.contains(parameters, 41, 50, 40, 50));
    }

    @Test
    void translatesThePatternAroundAnOffsetAnchor() {
        int[] parameters = parametersWithCell(-4, 6);
        parameters[1] = 2;
        parameters[2] = -1;

        assertTrue(WiredNeighborhoodMask.contains(parameters, 24, 37, 30, 30));
        assertFalse(WiredNeighborhoodMask.contains(parameters, 25, 37, 30, 30));
    }

    @Test
    void readsSignedPackedIntegersWithoutLosingBitThirtyOne() {
        int[] parameters = new int[WiredNeighborhoodMask.PARAM_COUNT];
        int x = 0;
        int y = 0;
        outer:
        for (int candidateX = -WiredNeighborhoodMask.RADIUS; candidateX <= WiredNeighborhoodMask.RADIUS; candidateX++) {
            for (int candidateY = -WiredNeighborhoodMask.RADIUS; candidateY <= WiredNeighborhoodMask.RADIUS; candidateY++) {
                if (WiredNeighborhoodMask.rankAt(candidateX, candidateY) == 31) {
                    x = candidateX;
                    y = candidateY;
                    break outer;
                }
            }
        }
        parameters[3] = Integer.MIN_VALUE;

        assertTrue(WiredNeighborhoodMask.contains(parameters, x, y, 0, 0));
    }

    @Test
    void rejectsIncompletePayloads() {
        assertFalse(WiredNeighborhoodMask.contains(new int[WiredNeighborhoodMask.PARAM_COUNT - 1], 0, 0, 0, 0));
    }

    private static int[] parametersWithCell(int x, int y) {
        int[] parameters = new int[WiredNeighborhoodMask.PARAM_COUNT];
        int rank = WiredNeighborhoodMask.rankAt(x, y);
        parameters[3 + rank / Integer.SIZE] |= 1 << (rank % Integer.SIZE);
        return parameters;
    }
}
