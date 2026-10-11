package com.eu.habbo.habbohotel.games.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnowStormProgressionTest {
    @Test
    void mapsOriginalScoreBandBoundaries() {
        assertEquals(SnowStormProgression.BEGINNER, SnowStormProgression.skillLevel(-1));
        assertEquals(SnowStormProgression.BEGINNER, SnowStormProgression.skillLevel(0));
        assertEquals(SnowStormProgression.BEGINNER, SnowStormProgression.skillLevel(10_000));
        assertEquals(SnowStormProgression.AMATEUR, SnowStormProgression.skillLevel(10_001));
        assertEquals(SnowStormProgression.AMATEUR, SnowStormProgression.skillLevel(100_000));
        assertEquals(SnowStormProgression.INTERMEDIATE, SnowStormProgression.skillLevel(100_001));
        assertEquals(SnowStormProgression.INTERMEDIATE, SnowStormProgression.skillLevel(500_000));
        assertEquals(SnowStormProgression.EXPERT, SnowStormProgression.skillLevel(500_001));
        assertEquals(SnowStormProgression.EXPERT, SnowStormProgression.skillLevel(Long.MAX_VALUE));
    }

    @Test
    void clampsAllTimePointsToFrozenWireInteger() {
        assertEquals(0, SnowStormProgression.wirePoints(-1));
        assertEquals(123, SnowStormProgression.wirePoints(123));
        assertEquals(Integer.MAX_VALUE, SnowStormProgression.wirePoints((long) Integer.MAX_VALUE + 1));
    }
}
