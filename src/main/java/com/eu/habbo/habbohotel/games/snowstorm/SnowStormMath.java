package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.data.SnowStormGeneratedRuntimeTables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/** Exact signed-integer primitives shared by the authoritative SnowStorm simulation. */
public final class SnowStormMath {
    public static final int TURN_MS = 300;
    public static final int SUBTURNS_PER_TURN = 5;
    public static final int SUBTURN_MS = 60;
    public static final int WORLD_UNITS_PER_TILE = 3200;
    public static final int AVATAR_MOVEMENT_PER_SUBTURN = 640;
    private static final int[][] DIRECTION_DELTAS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    private SnowStormMath() {
    }

    public static int iterateSeed(int seed) {
        if (seed == 0) {
            seed = -1;
        }
        seed ^= seed << 13;
        seed ^= seed >> 17;
        seed ^= seed << 5;
        return seed;
    }

    public static int checksumContribution(List<Integer> insertionOrderedValues) {
        int checksum = 0;
        int counter = 1;
        for (int value : insertionOrderedValues) {
            checksum += value * counter;
            counter++;
        }
        return checksum;
    }

    public static int checksum(int turnNumber, Collection<ChecksumObject> objects) {
        int checksum = iterateSeed(turnNumber);
        List<ChecksumObject> ordered = new ArrayList<>(objects);
        ordered.sort(Comparator.comparingInt(ChecksumObject::objectId));
        for (ChecksumObject object : ordered) {
            checksum = checksum * 31 + object.type();
            checksum = checksum * 31 + object.objectId();
            for (int value : object.insertionOrderedValues()) {
                checksum = checksum * 31 + value;
            }
        }
        return checksum;
    }

    public static int directorDiv(int numerator, int denominator) {
        if (denominator == 0) {
            throw new ArithmeticException("SnowStorm integer division by zero");
        }
        return numerator / denominator;
    }

    public static int directorMod(int value, int divisor) {
        return value - directorDiv(value, divisor) * divisor;
    }

    public static int validateDirection360(int value) {
        if (value > 359) {
            value = directorMod(value, 360);
        } else if (value < 0) {
            value = 360 + directorMod(value, 360);
        }
        return value;
    }

    public static int validateDirection8(int value) {
        if (value > 7) {
            value = directorMod(value, 8);
        } else if (value < 0) {
            value = directorMod(8 + directorMod(value, 8), 8);
        }
        return value;
    }

    public static int direction360To8(int value) {
        return validateDirection8(directorDiv(validateDirection360(value - 22), 45) + 1);
    }

    public static int fastSquareRoot(int value) {
        if (value < 0) {
            return -1;
        }
        if (value >= 1_073_741_824) return SnowStormGeneratedRuntimeTables.squareRoot(value / 16_777_216) * 256;
        if (value >= 268_435_456) return SnowStormGeneratedRuntimeTables.squareRoot(value / 4_194_304) * 128;
        if (value >= 67_108_864) return SnowStormGeneratedRuntimeTables.squareRoot(value / 1_048_576) * 64;
        if (value >= 16_777_216) return SnowStormGeneratedRuntimeTables.squareRoot(value / 262_144) * 32;
        if (value >= 4_194_304) return SnowStormGeneratedRuntimeTables.squareRoot(value / 65_536) * 16;
        if (value >= 1_048_576) return SnowStormGeneratedRuntimeTables.squareRoot(value / 16_384) * 8;
        if (value >= 262_144) return SnowStormGeneratedRuntimeTables.squareRoot(value / 4_096) * 4;
        if (value >= 65_536) return SnowStormGeneratedRuntimeTables.squareRoot(value / 1_024) * 2;
        if (value >= 16_384) return SnowStormGeneratedRuntimeTables.squareRoot(value / 256);
        if (value >= 4_096) return SnowStormGeneratedRuntimeTables.squareRoot(value / 64) / 2;
        if (value >= 1_024) return SnowStormGeneratedRuntimeTables.squareRoot(value / 16) / 4;
        if (value >= 256) return SnowStormGeneratedRuntimeTables.squareRoot(value / 4) / 8;
        return SnowStormGeneratedRuntimeTables.squareRoot(value) / 16;
    }

    public static int componentAngle(int xComponent, int yComponent) {
        int x = xComponent;
        int y = yComponent;
        if (Math.abs(x) <= Math.abs(y)) {
            if (y == 0) {
                y = 1;
            }
            int scaledX = x * 256;
            int index = Math.min(Math.abs(directorDiv(scaledX, y)), 255);
            int angle = SnowStormGeneratedRuntimeTables.componentAngle(index);
            if (y < 0) {
                return scaledX > 0 ? angle : 360 - angle;
            }
            return scaledX > 0 ? 180 - angle : 180 + angle;
        }
        if (x == 0) {
            x = 1;
        }
        int scaledY = y * 256;
        int index = Math.min(Math.abs(directorDiv(scaledY, x)), 255);
        int angle = SnowStormGeneratedRuntimeTables.componentAngle(index);
        if (scaledY < 0) {
            return x > 0 ? 90 - angle : 270 + angle;
        }
        return x > 0 ? 90 + angle : 270 - angle;
    }

    public static int velocityX(int direction) {
        return SnowStormGeneratedRuntimeTables.velocityX(validateDirection360(direction));
    }

    public static int velocityY(int direction) {
        return SnowStormGeneratedRuntimeTables.velocityY(validateDirection360(direction));
    }

    public static SnowStormTile directionDelta(int direction) {
        if (direction < 0 || direction >= DIRECTION_DELTAS.length) {
            throw new IllegalArgumentException("SnowStorm direction must be between 0 and 7");
        }
        return new SnowStormTile(DIRECTION_DELTAS[direction][0], DIRECTION_DELTAS[direction][1]);
    }

    public static OptionalInt worldToTile(int worldCoordinate) {
        if (worldCoordinate < -(WORLD_UNITS_PER_TILE / 2)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(directorDiv(worldCoordinate + WORLD_UNITS_PER_TILE / 2, WORLD_UNITS_PER_TILE));
    }

    public static int tileToWorld(int tileCoordinate) {
        return tileCoordinate * WORLD_UNITS_PER_TILE;
    }

    public static int stepAxis(int current, int target) {
        int delta = target - current;
        if (-AVATAR_MOVEMENT_PER_SUBTURN < delta && delta < AVATAR_MOVEMENT_PER_SUBTURN) {
            return target;
        }
        if (delta < 0) {
            return current - AVATAR_MOVEMENT_PER_SUBTURN;
        }
        if (delta > 0) {
            return current + AVATAR_MOVEMENT_PER_SUBTURN;
        }
        return current;
    }

    public record ChecksumObject(int type, int objectId, List<Integer> insertionOrderedValues) {
        public ChecksumObject {
            insertionOrderedValues = List.copyOf(insertionOrderedValues);
        }
    }
}
