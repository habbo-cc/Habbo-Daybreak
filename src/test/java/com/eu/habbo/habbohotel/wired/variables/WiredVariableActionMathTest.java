package com.eu.habbo.habbohotel.wired.variables;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableActionMathTest {
    @Test
    void implementsOnlyBoundedAirOperationsWithoutUnknownAssignFallback() {
        assertEquals(12, WiredVariableActionMath.apply(WiredVariableActionMath.ADD, 7, 5).orElseThrow());
        assertEquals(7, WiredVariableActionMath.apply(WiredVariableActionMath.SET_MINIMUM, 7, 4).orElseThrow());
        assertEquals(4, WiredVariableActionMath.apply(WiredVariableActionMath.SET_MAXIMUM, 7, 4).orElseThrow());
        assertEquals(3, WiredVariableActionMath.apply(WiredVariableActionMath.BIT_COUNT, 0b1011, 99).orElseThrow());
        assertFalse(WiredVariableActionMath.apply(999, 7, 5).isPresent());
        assertTrue(WiredVariableActionMath.isImplementedOperation(WiredVariableActionMath.POWER));
    }

    @Test
    void rejectsInvalidAndOverflowingArithmeticWithoutMutationCandidate() {
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.DIVIDE, 5, 0).isPresent());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.MODULO, 5, 0).isPresent());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.ADD, Integer.MAX_VALUE, 1).isPresent());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.ABSOLUTE, Integer.MIN_VALUE, 0).isPresent());
        assertTrue(WiredVariableActionMath.requiresOperand(WiredVariableActionMath.ADD));
        assertFalse(WiredVariableActionMath.requiresOperand(WiredVariableActionMath.BITWISE_NOT));
    }

    @Test
    void appliesBoundedPowerRandomAndShiftOperations() {
        assertEquals(81, WiredVariableActionMath.apply(WiredVariableActionMath.POWER, 3, 4).orElseThrow());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.POWER, 2, 31).isPresent());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.POWER, 2, -1).isPresent());
        assertEquals(0, WiredVariableActionMath.apply(
                WiredVariableActionMath.RANDOM_UPPER_BOUND, 99, 0).orElseThrow());
        int random = WiredVariableActionMath.apply(
                WiredVariableActionMath.RANDOM_UPPER_BOUND, 99, 3).orElseThrow();
        assertTrue(random >= 0 && random <= 3);
        assertEquals(8, WiredVariableActionMath.apply(WiredVariableActionMath.LEFT_SHIFT, 1, 3).orElseThrow());
        assertEquals(-2, WiredVariableActionMath.apply(WiredVariableActionMath.RIGHT_SHIFT, -4, 1).orElseThrow());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.LEFT_SHIFT, 1, 32).isPresent());
    }

    @Test
    void navigatesAndMutatesSigned32BitValuesByBitIndex() {
        int value = 0b1010;
        assertEquals(0, WiredVariableActionMath.apply(WiredVariableActionMath.NEXT_LOW_BIT, value, 0).orElseThrow());
        assertEquals(1, WiredVariableActionMath.apply(WiredVariableActionMath.NEXT_HIGH_BIT, value, 0).orElseThrow());
        assertEquals(2, WiredVariableActionMath.apply(WiredVariableActionMath.PREVIOUS_LOW_BIT, value, 3).orElseThrow());
        assertEquals(3, WiredVariableActionMath.apply(WiredVariableActionMath.PREVIOUS_HIGH_BIT, value, 3).orElseThrow());
        assertEquals(1, WiredVariableActionMath.apply(WiredVariableActionMath.GET_BIT, value, 3).orElseThrow());
        assertEquals(0b1011, WiredVariableActionMath.apply(WiredVariableActionMath.SET_BIT, value, 0).orElseThrow());
        assertEquals(0b0010, WiredVariableActionMath.apply(WiredVariableActionMath.CLEAR_BIT, value, 3).orElseThrow());
        assertEquals(0b1000, WiredVariableActionMath.apply(WiredVariableActionMath.TOGGLE_BIT, value, 1).orElseThrow());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.GET_BIT, value, -1).isPresent());
        assertFalse(WiredVariableActionMath.apply(WiredVariableActionMath.NEXT_HIGH_BIT, 0, 0).isPresent());
    }
}
