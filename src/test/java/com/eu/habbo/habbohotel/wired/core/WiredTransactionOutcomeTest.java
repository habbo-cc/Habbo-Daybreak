package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Golden checks for July's transaction event variables and failure-code namespace. */
class WiredTransactionOutcomeTest {
    @Test
    void completedOutcomeSeedsOnlyJulyCompletionVariables() {
        WiredContextVariableStore variables = new WiredContextVariableStore();

        WiredTransactionOutcome.completed(3, 4, 5, 6, 7).seed(variables);

        assertEquals(3, variables.get("@event.transaction_complete.multiplier"));
        assertEquals(4, variables.get("@event.transaction_complete.deposit.furni_count"));
        assertEquals(5, variables.get("@event.transaction_complete.deposit.coins_count"));
        assertEquals(6, variables.get("@event.transaction_complete.withdrawal.furni_count"));
        assertEquals(7, variables.get("@event.transaction_complete.withdrawal.coins_count"));
        assertNull(variables.get("@event.transaction_failed.reason"));
    }

    @Test
    void failedOutcomeSeedsOnlyJulyFailureReason() {
        WiredContextVariableStore variables = new WiredContextVariableStore();

        WiredTransactionOutcome.failed(ChestTransactionFailure.CAPACITY_EXCEEDED)
                .seed(variables);

        assertEquals(19, variables.get("@event.transaction_failed.reason"));
        assertNull(variables.get("@event.transaction_complete.multiplier"));
    }

    @Test
    void invalidOutcomeShapesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredTransactionOutcome.completed(0, 0, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WiredTransactionOutcome(
                                false,
                                0,
                                0,
                                0,
                                0,
                                0,
                                null));
    }

    @Test
    void failureCodesMatchJulyNamespaceExactly() {
        int[] expected = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            1000, 1001, 1002
        };
        int[] actual =
                Arrays.stream(ChestTransactionFailure.values())
                        .mapToInt(ChestTransactionFailure::code)
                        .toArray();

        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
