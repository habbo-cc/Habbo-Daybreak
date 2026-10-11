package com.eu.habbo.habbohotel.wired.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WiredSafetyBudgetTest {
    @Test
    void totalStepsAreSharedAndBounded() {
        WiredSafetyBudget budget = new WiredSafetyBudget(2, 2, 2, 2, 3, 4);

        budget.step();
        budget.step();

        assertEquals(2, budget.totalSteps());
        assertEquals(0, budget.remainingSteps());
        assertThrows(WiredLimitException.class, budget::step);
    }

    @Test
    void selfAndMultiNodeCyclesArePathScoped() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 3, 3, 3, 10, 10);
        WiredState state = new WiredState(10, budget);

        try (WiredSafetyBudget.PathLease ignored = state.enter(WiredSafetyBudget.PathKind.STACK, 1)) {
            assertThrows(WiredLimitException.class,
                    () -> state.enter(WiredSafetyBudget.PathKind.STACK, 1));
            try (WiredSafetyBudget.PathLease ignoredAgain = state.enter(WiredSafetyBudget.PathKind.STACK, 2)) {
                assertThrows(WiredLimitException.class,
                        () -> state.enter(WiredSafetyBudget.PathKind.STACK, 1));
            }
        }

        assertDoesNotThrow(() -> {
            try (WiredSafetyBudget.PathLease ignored = state.enter(WiredSafetyBudget.PathKind.STACK, 1)) {
                // A sibling visit after the previous path unwinds is legal.
            }
        });
    }

    @Test
    void depthGuardsReleaseAfterExceptions() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 1, 1, 1, 10, 10);
        WiredState state = new WiredState(10, budget);

        assertThrows(IllegalStateException.class, () -> {
            try (WiredSafetyBudget.PathLease ignored = state.enter(WiredSafetyBudget.PathKind.SIGNAL, 7)) {
                throw new IllegalStateException("test");
            }
        });

        assertDoesNotThrow(() -> {
            try (WiredSafetyBudget.PathLease ignored = state.enter(WiredSafetyBudget.PathKind.SIGNAL, 8)) {
            }
        });
    }

    @Test
    void fanOutAndTargetsUseCumulativeLimits() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 2, 2, 2, 3, 4);

        budget.consumeFanOut(2);
        budget.consumeTargets(3);
        budget.consumeFanOut(1);
        budget.consumeTargets(1);

        assertEquals(3, budget.totalFanOut());
        assertEquals(4, budget.totalTargets());
        assertThrows(WiredLimitException.class, () -> budget.consumeFanOut(1));
        assertThrows(WiredLimitException.class, () -> budget.consumeTargets(1));
        assertThrows(WiredLimitException.class, () -> budget.consumeTargets(-1));
    }

    @Test
    void depthLimitsAreIndependent() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 1, 1, 1, 10, 10);
        WiredState state = new WiredState(10, budget);

        try (WiredSafetyBudget.PathLease signal = state.enter(WiredSafetyBudget.PathKind.SIGNAL, 1);
             WiredSafetyBudget.PathLease remote = state.enter(WiredSafetyBudget.PathKind.REMOTE_SELECTOR, 1);
             WiredSafetyBudget.PathLease trigger = state.enter(WiredSafetyBudget.PathKind.TRIGGER_STACK, 1)) {
            assertThrows(WiredLimitException.class,
                    () -> state.enter(WiredSafetyBudget.PathKind.SIGNAL, 2));
            assertThrows(WiredLimitException.class,
                    () -> state.enter(WiredSafetyBudget.PathKind.REMOTE_SELECTOR, 2));
            assertThrows(WiredLimitException.class,
                    () -> state.enter(WiredSafetyBudget.PathKind.TRIGGER_STACK, 2));
        }
    }

    @Test
    void forkedBranchesShareCountersButNotMutablePathLeases() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 2, 2, 2, 10, 10);
        WiredState parent = new WiredState(10, budget);

        try (WiredSafetyBudget.PathLease ignored = parent.enter(WiredSafetyBudget.PathKind.STACK, 1)) {
            WiredState child = parent.fork();
            assertThrows(WiredLimitException.class,
                    () -> child.enter(WiredSafetyBudget.PathKind.STACK, 1));
        }

        WiredState sibling = new WiredState(10, budget);
        assertDoesNotThrow(() -> {
            try (WiredSafetyBudget.PathLease ignored = sibling.enter(WiredSafetyBudget.PathKind.STACK, 1)) {
            }
        });
    }

    @Test
    void simultaneousSiblingBranchesDoNotShareActiveAncestry() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 2, 2, 2, 10, 10);
        WiredState first = new WiredState(10, budget);
        WiredState second = new WiredState(10, budget);

        assertDoesNotThrow(() -> {
            try (WiredSafetyBudget.PathLease firstLease = first.enter(WiredSafetyBudget.PathKind.STACK, 1);
                 WiredSafetyBudget.PathLease secondLease = second.enter(WiredSafetyBudget.PathKind.STACK, 1)) {
            }
        });
    }

    @Test
    void aggregateCountersAreThreadSafe() throws Exception {
        WiredSafetyBudget budget = new WiredSafetyBudget(1_000, 2, 2, 2, 1_000, 1_000);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 1_000; index++) {
                tasks.add(() -> {
                    budget.step();
                    return null;
                });
            }
            executor.invokeAll(tasks).forEach(future -> assertDoesNotThrow(() -> {
                future.get();
            }));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertEquals(1_000, budget.totalSteps());
    }
}
