package com.eu.habbo.habbohotel.wired.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WiredStateTest {
    @Test
    void childStatesShareRunIdentityAndTotalSteps() {
        WiredSafetyBudget budget = WiredSafetyBudget.forSteps(3);
        WiredState first = new WiredState(3, budget);
        WiredState second = new WiredState(3, budget);

        first.step();
        second.step();
        first.step();

        assertEquals(first.runId(), second.runId());
        assertSame(budget, first.budget());
        assertEquals(3, budget.totalSteps());
        assertFalse(second.canStep());
        assertThrows(WiredLimitException.class, second::step);
    }

    @Test
    void perStackLimitStillAppliesBeforeSharedLimit() {
        WiredSafetyBudget budget = WiredSafetyBudget.forSteps(10);
        WiredState state = new WiredState(1, budget);

        state.step();

        assertThrows(WiredLimitException.class, state::step);
        assertEquals(1, budget.totalSteps());
    }

    @Test
    void localResetCannotResetSharedBudget() {
        WiredSafetyBudget budget = WiredSafetyBudget.forSteps(1);
        WiredState state = new WiredState(1, budget);
        state.step();

        state.reset();

        assertThrows(WiredLimitException.class, state::step);
        assertEquals(1, budget.totalSteps());
    }
}
