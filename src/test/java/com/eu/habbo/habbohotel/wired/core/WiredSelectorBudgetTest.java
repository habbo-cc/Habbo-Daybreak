package com.eu.habbo.habbohotel.wired.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WiredSelectorBudgetTest {
    @Test
    void inversionChargesTheRoomWideExpansionAfterTheRawResult() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 2, 2, 2, 10, 100);

        budget.consumeTargets(1);
        budget.consumeTargets(WiredEngine.additionalTargetsAfterTransform(1, 100));

        assertEquals(100, budget.totalTargets());
    }

    @Test
    void inversionCannotExpandPastTheSharedTargetLimit() {
        WiredSafetyBudget budget = new WiredSafetyBudget(10, 2, 2, 2, 10, 99);

        budget.consumeTargets(1);
        assertThrows(WiredLimitException.class, () -> budget.consumeTargets(
                WiredEngine.additionalTargetsAfterTransform(1, 100)));
    }

    @Test
    void shrinkingTransformDoesNotDoubleChargeTargets() {
        assertEquals(0, WiredEngine.additionalTargetsAfterTransform(50, 10));
        assertThrows(IllegalArgumentException.class,
                () -> WiredEngine.additionalTargetsAfterTransform(-1, 10));
    }
}
