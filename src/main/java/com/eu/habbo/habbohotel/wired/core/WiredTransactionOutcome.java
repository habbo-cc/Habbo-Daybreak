package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;

/**
 * Immutable payload for July's transaction-completed and transaction-failed triggers.
 *
 * <p>Keeping the values on the event prevents asynchronous chest completion from depending on a
 * mutable Wired execution state that may already have ended.</p>
 */
public record WiredTransactionOutcome(
        boolean completed,
        int multiplier,
        int depositedFurnitureCount,
        int depositedCoinCount,
        int withdrawnFurnitureCount,
        int withdrawnCoinCount,
        ChestTransactionFailure failure) {

    public WiredTransactionOutcome {
        if (completed) {
            if (multiplier < 1
                    || depositedFurnitureCount < 0
                    || depositedCoinCount < 0
                    || withdrawnFurnitureCount < 0
                    || withdrawnCoinCount < 0
                    || failure != null) {
                throw new IllegalArgumentException("invalid completed transaction outcome");
            }
        } else if (failure == null
                || multiplier != 0
                || depositedFurnitureCount != 0
                || depositedCoinCount != 0
                || withdrawnFurnitureCount != 0
                || withdrawnCoinCount != 0) {
            throw new IllegalArgumentException("invalid failed transaction outcome");
        }
    }

    public static WiredTransactionOutcome completed(
            int multiplier,
            int depositedFurnitureCount,
            int depositedCoinCount,
            int withdrawnFurnitureCount,
            int withdrawnCoinCount) {
        return new WiredTransactionOutcome(
                true,
                multiplier,
                depositedFurnitureCount,
                depositedCoinCount,
                withdrawnFurnitureCount,
                withdrawnCoinCount,
                null);
    }

    public static WiredTransactionOutcome failed(ChestTransactionFailure failure) {
        return new WiredTransactionOutcome(false, 0, 0, 0, 0, 0, failure);
    }

    void seed(WiredContextVariableStore variables) {
        if (variables == null) {
            return;
        }
        if (this.completed) {
            variables.set("@event.transaction_complete.multiplier", this.multiplier);
            variables.set(
                    "@event.transaction_complete.deposit.furni_count",
                    this.depositedFurnitureCount);
            variables.set(
                    "@event.transaction_complete.deposit.coins_count", this.depositedCoinCount);
            variables.set(
                    "@event.transaction_complete.withdrawal.furni_count",
                    this.withdrawnFurnitureCount);
            variables.set(
                    "@event.transaction_complete.withdrawal.coins_count", this.withdrawnCoinCount);
        } else {
            variables.set("@event.transaction_failed.reason", this.failure.code());
        }
    }
}
