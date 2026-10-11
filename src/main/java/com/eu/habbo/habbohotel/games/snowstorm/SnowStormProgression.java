package com.eu.habbo.habbohotel.games.snowstorm;

/**
 * Original SnowStorm score progression recovered from the period server data used by Havana.
 *
 * <p>The r26 client deliberately receives the current skill name and score range from the server;
 * it does not calculate arbitrary levels locally. NGH keeps the frozen v1 numeric field and maps
 * the four original bands to stable values 1..4.</p>
 */
final class SnowStormProgression {
    static final int BEGINNER = 1;
    static final int AMATEUR = 2;
    static final int INTERMEDIATE = 3;
    static final int EXPERT = 4;

    private SnowStormProgression() {
    }

    static int skillLevel(long allTimePoints) {
        long points = Math.max(0L, allTimePoints);
        if (points <= 10_000L) return BEGINNER;
        if (points <= 100_000L) return AMATEUR;
        if (points <= 500_000L) return INTERMEDIATE;
        return EXPERT;
    }

    static int wirePoints(long allTimePoints) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, allTimePoints));
    }
}
