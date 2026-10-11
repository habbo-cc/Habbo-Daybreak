package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.wired.core.WiredLimitException;
import com.eu.habbo.habbohotel.wired.core.WiredSafetyBudget;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredRemoteSelectorSupportTest {
    @Test
    void acceptsOnlyTheExactJulyTwoIntegerContract() {
        assertTrue(WiredRemoteSelectorSupport.hasExactAirParameters(new int[] {0, 0}, 64));
        assertTrue(WiredRemoteSelectorSupport.hasExactAirParameters(new int[] {1, 64}, 64));
        assertFalse(WiredRemoteSelectorSupport.hasExactAirParameters(new int[] {0}, 64));
        assertFalse(WiredRemoteSelectorSupport.hasExactAirParameters(
                new int[] {0, 1, 2, 0, 0, 100}, 64));
        assertFalse(WiredRemoteSelectorSupport.hasExactAirParameters(new int[] {2, 0}, 64));
        assertFalse(WiredRemoteSelectorSupport.hasExactAirParameters(new int[] {0, -1}, 64));
        assertFalse(WiredRemoteSelectorSupport.hasExactAirParameters(new int[] {0, 65}, 64));
    }

    @Test
    void capabilityReadinessRequiresBothRuntimeAndExactRoomNegotiation() {
        assertTrue(WiredRemoteSelectorSupport.isReady(true, true));
        assertFalse(WiredRemoteSelectorSupport.isReady(true, false));
        assertFalse(WiredRemoteSelectorSupport.isReady(false, true));
        assertFalse(WiredRemoteSelectorSupport.isReady(false, false));
    }

    @Test
    void unionCombinesOnlyAxesThatArePresent() {
        Set<Integer> result = WiredRemoteSelectorSupport.aggregate(List.of(
                new WiredRemoteSelectorSupport.AxisContribution<>(true, Set.of(1, 2)),
                new WiredRemoteSelectorSupport.AxisContribution<>(false, Set.of(99)),
                new WiredRemoteSelectorSupport.AxisContribution<>(true, Set.of(2, 3))),
                WiredRemoteSelectorSupport.UNION);

        assertEquals(Set.of(1, 2, 3), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(4));
    }

    @Test
    void intersectionIgnoresMissingAxesButHonoursPresentEmptySets() {
        assertEquals(Set.of(2), WiredRemoteSelectorSupport.aggregate(List.of(
                new WiredRemoteSelectorSupport.AxisContribution<>(true, Set.of(1, 2)),
                new WiredRemoteSelectorSupport.AxisContribution<>(false, Set.of()),
                new WiredRemoteSelectorSupport.AxisContribution<>(true, Set.of(2, 3))),
                WiredRemoteSelectorSupport.INTERSECTION));

        assertEquals(Set.of(), WiredRemoteSelectorSupport.aggregate(List.of(
                new WiredRemoteSelectorSupport.AxisContribution<>(true, Set.of(1, 2)),
                new WiredRemoteSelectorSupport.AxisContribution<>(true, Set.of())),
                WiredRemoteSelectorSupport.INTERSECTION));
    }

    @Test
    void zeroAndOversizedSamplesUseAllReferencesWhilePositiveNIsStable() {
        List<Integer> references = List.of(10, 20, 30, 40);
        assertEquals(references, WiredRemoteSelectorSupport.sample(references, 0, 123));
        assertEquals(references, WiredRemoteSelectorSupport.sample(references, 9, 123));

        List<Integer> first = WiredRemoteSelectorSupport.sample(references, 2, 123);
        List<Integer> second = WiredRemoteSelectorSupport.sample(references, 2, 123);
        assertEquals(2, first.size());
        assertEquals(first, second);
        assertNotEquals(first, WiredRemoteSelectorSupport.sample(references, 2, 456));
    }

    @Test
    void sampleSeedIsStableForOneRootExecutionAndReferenceGraph() {
        UUID runId = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        long first = WiredRemoteSelectorSupport.samplingSeed(runId, 5, 9, List.of(1L, 2L));
        long second = WiredRemoteSelectorSupport.samplingSeed(runId, 5, 9, List.of(1L, 2L));
        assertEquals(first, second);
        assertNotEquals(first,
                WiredRemoteSelectorSupport.samplingSeed(runId, 5, 10, List.of(1L, 2L)));
    }

    @Test
    void sharedExecutionPathRejectsDirectAndIndirectRemoteCycles() {
        WiredState state = new WiredState(20, new WiredSafetyBudget(20, 3, 3, 3, 20, 20));
        long first = WiredRemoteSelectorSupport.pathKey(1, 100);
        long second = WiredRemoteSelectorSupport.pathKey(1, 200);

        try (WiredSafetyBudget.PathLease ignoredFirst = state.enter(
                WiredSafetyBudget.PathKind.REMOTE_SELECTOR, first)) {
            assertThrows(WiredLimitException.class, () -> state.enter(
                    WiredSafetyBudget.PathKind.REMOTE_SELECTOR, first));
            try (WiredSafetyBudget.PathLease ignoredSecond = state.enter(
                    WiredSafetyBudget.PathKind.REMOTE_SELECTOR, second)) {
                assertThrows(WiredLimitException.class, () -> state.enter(
                        WiredSafetyBudget.PathKind.REMOTE_SELECTOR, first));
            }
        }
    }
}
