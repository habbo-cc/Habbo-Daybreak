package com.eu.habbo.habbohotel.items.chests;

/** Failure codes exposed by July AIR as {@code @event.transaction_failed.reason}. */
public enum ChestTransactionFailure {
    USER_CANCELLED(0),
    INVALID_TRADE(1),
    TIMEOUT(2),
    TRADE_CANCELLED(3),
    ALREADY_TRADING(4),
    WIRED_MISCONFIGURATION(5),
    INSUFFICIENT_FUNDS(6),
    FUNDS_UNAVAILABLE(7),
    USER_CANNOT_TRADE(8),
    CHEST_OWNER_CANNOT_TRADE(9),
    EMPTY_TRANSACTION(10),
    CHEST_FULL(11),
    FEATURE_DISABLED(12),
    CHEST_NOT_IN_ROOM(13),
    TOO_MANY_CHESTS(14),
    NO_AVAILABLE_CHESTS(15),
    CANNOT_GIVE_ALL_TO_MULTIPLE_USERS(16),
    TOO_MANY_WIRED_OFFERS(17),
    RATE_LIMITED(18),
    CAPACITY_EXCEEDED(19),
    INTERNAL_ERROR(1000),
    DATABASE_ERROR(1001),
    DATABASE_RELOAD_REQUIRED(1002);

    private final int code;

    ChestTransactionFailure(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }

    public static ChestTransactionFailure fromCode(int code) {
        for (ChestTransactionFailure failure : values()) {
            if (failure.code == code) {
                return failure;
            }
        }
        return INTERNAL_ERROR;
    }
}
