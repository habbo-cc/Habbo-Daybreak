package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Pure, deterministic set operations used by the July AIR Remote Selector. */
final class WiredRemoteSelectorSupport {
    static final int UNION = 0;
    static final int INTERSECTION = 1;

    private WiredRemoteSelectorSupport() {
    }

    static boolean isReady(boolean roomRuntimeReady, boolean clientRoomReady) {
        return roomRuntimeReady && clientRoomReady;
    }

    static boolean hasExactAirParameters(int[] intParams, int maximumRandomAmount) {
        return intParams != null
                && intParams.length == 2
                && (intParams[0] == UNION || intParams[0] == INTERSECTION)
                && intParams[1] >= 0
                && intParams[1] <= maximumRandomAmount;
    }

    static long stackKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    static long pathKey(int roomId, int selectorItemId) {
        return ((long) roomId << 32) ^ (selectorItemId & 0xffffffffL);
    }

    static long samplingSeed(UUID runId, int roomId, int selectorItemId, Collection<Long> stackKeys) {
        long seed = runId.getMostSignificantBits()
                ^ Long.rotateLeft(runId.getLeastSignificantBits(), 17)
                ^ pathKey(roomId, selectorItemId);
        if (stackKeys != null) {
            for (Long key : stackKeys) {
                if (key != null) {
                    seed = mix(seed ^ key);
                }
            }
        }
        return mix(seed);
    }

    static <T> List<T> sample(List<T> orderedValues, int amount, long seed) {
        if (orderedValues == null || orderedValues.isEmpty()) {
            return List.of();
        }

        List<T> copy = new ArrayList<>(orderedValues);
        if (amount <= 0 || amount >= copy.size()) {
            return List.copyOf(copy);
        }

        Random random = new Random(seed);
        for (int i = copy.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            Collections.swap(copy, i, swapIndex);
        }
        return List.copyOf(copy.subList(0, amount));
    }

    static <T> Set<T> aggregate(List<AxisContribution<T>> contributions, int aggregation) {
        LinkedHashSet<T> result = new LinkedHashSet<>();
        boolean initialized = false;

        if (contributions != null) {
            for (AxisContribution<T> contribution : contributions) {
                if (contribution == null || !contribution.present()) {
                    continue;
                }

                if (aggregation == INTERSECTION) {
                    if (!initialized) {
                        result.addAll(contribution.values());
                        initialized = true;
                    } else {
                        result.retainAll(contribution.values());
                    }
                } else {
                    result.addAll(contribution.values());
                    initialized = true;
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    record AxisContribution<T>(boolean present, Set<T> values) {
        AxisContribution {
            values = values == null
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ (value >>> 33);
    }
}
