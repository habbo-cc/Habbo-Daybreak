package com.eu.habbo.habbohotel.wired.variables;

import java.util.Objects;

/**
 * Immutable description of a value mutation that has already committed to the
 * room variable store.  This is deliberately a runtime record, not a wire
 * representation: AIR trigger 22 identifies the definition by variable ID.
 */
public record WiredVariableMutation(
        String variableId,
        WiredVariableHolder holder,
        Integer beforeValue,
        Integer afterValue,
        Kind kind,
        long revision,
        int boxId,
        int changeOrigin) {

    public static final int CHANGE_ORIGIN_IN_ROOM = 0;
    public static final int CHANGE_ORIGIN_ANOTHER_ROOM = 1;
    public static final int CHANGE_ORIGIN_CREATOR_TOOL = 2;
    public static final int CHANGE_ORIGIN_EXTERNAL = 3;

    public enum Kind {
        CREATED,
        VALUE_CHANGED,
        DELETED
    }

    public WiredVariableMutation(
            String variableId,
            WiredVariableHolder holder,
            Integer beforeValue,
            Integer afterValue,
            Kind kind,
            long revision) {
        this(variableId, holder, beforeValue, afterValue, kind, revision,
                0, CHANGE_ORIGIN_EXTERNAL);
    }

    public WiredVariableMutation {
        Objects.requireNonNull(variableId, "variableId");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(kind, "kind");
        if (variableId.isBlank()) {
            throw new IllegalArgumentException("variable ID must not be blank");
        }
        if (revision <= 0L) {
            throw new IllegalArgumentException("a mutation must have a committed revision");
        }
        if (boxId < 0) {
            throw new IllegalArgumentException("box ID must not be negative");
        }
        if (changeOrigin < CHANGE_ORIGIN_IN_ROOM
                || changeOrigin > CHANGE_ORIGIN_EXTERNAL) {
            throw new IllegalArgumentException("unsupported mutation origin");
        }
        if (kind == Kind.CREATED && (beforeValue != null || afterValue == null)) {
            throw new IllegalArgumentException("created mutations require only an after value");
        }
        if (kind == Kind.VALUE_CHANGED && (beforeValue == null || afterValue == null)) {
            throw new IllegalArgumentException("value changes require before and after values");
        }
        if (kind == Kind.DELETED && (beforeValue == null || afterValue != null)) {
            throw new IllegalArgumentException("deleted mutations require only a before value");
        }
    }

    public WiredVariableMutation withProvenance(int sourceBoxId, int origin) {
        return new WiredVariableMutation(variableId, holder, beforeValue, afterValue,
                kind, revision, Math.max(0, sourceBoxId), origin);
    }

    public int valueChangeMaskBit() {
        if (this.kind != Kind.VALUE_CHANGED) {
            return 0;
        }
        return this.afterValue > this.beforeValue ? 1
                : this.afterValue < this.beforeValue ? 2 : 4;
    }
}
