package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

/** Decodes the May 2026 21x21 neighbourhood spiral stored after the three header parameters. */
final class WiredNeighborhoodMask {
    static final int RADIUS = 10;
    static final int DIMENSION = RADIUS * 2 + 1;
    static final int CELL_COUNT = DIMENSION * DIMENSION;
    static final int MASK_INT_COUNT = (CELL_COUNT + Integer.SIZE - 1) / Integer.SIZE;
    static final int PARAM_COUNT = 3 + MASK_INT_COUNT;

    private static final int[][] RANKS = createRanks();

    private WiredNeighborhoodMask() {
    }

    static boolean contains(int[] parameters, int targetX, int targetY, int anchorX, int anchorY) {
        if (parameters == null || parameters.length < PARAM_COUNT) {
            return false;
        }

        // The configured root cell is the point occupied by the selected source.
        int maskX = targetX - anchorX + parameters[1];
        int maskY = targetY - anchorY + parameters[2];
        int rank = rankAt(maskX, maskY);
        if (rank < 0) {
            return false;
        }

        int packed = parameters[3 + rank / Integer.SIZE];
        return (packed & (1 << (rank % Integer.SIZE))) != 0;
    }

    static int rankAt(int x, int y) {
        if (x < -RADIUS || x > RADIUS || y < -RADIUS || y > RADIUS) {
            return -1;
        }
        return RANKS[x + RADIUS][y + RADIUS];
    }

    private static int[][] createRanks() {
        int[][] ranks = new int[DIMENSION][DIMENSION];
        for (int x = 0; x < DIMENSION; x++) {
            java.util.Arrays.fill(ranks[x], -1);
        }

        int rank = 0;
        int x = 0;
        int y = 0;
        int directionX = 1;
        int directionY = 0;
        for (int runLength = 1; runLength <= DIMENSION && rank < CELL_COUNT; runLength++) {
            for (int turn = 0; turn < 2 && rank < CELL_COUNT; turn++) {
                for (int step = 0; step < runLength && rank < CELL_COUNT; step++) {
                    ranks[x + RADIUS][y + RADIUS] = rank++;
                    x += directionX;
                    y += directionY;
                }
                int nextX = directionY;
                directionY = -directionX;
                directionX = nextX;
            }
        }
        return ranks;
    }
}
