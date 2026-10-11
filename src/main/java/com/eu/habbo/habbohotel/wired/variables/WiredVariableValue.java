package com.eu.habbo.habbohotel.wired.variables;

import java.util.Objects;

/** Immutable signed-32 value record using stable holder identity. */
public final class WiredVariableValue {
    private final String variableId;
    private final WiredVariableHolder holder;
    private final int value;
    private final long createdAtMs;
    private final long updatedAtMs;
    private final long revision;

    public WiredVariableValue(String variableId, WiredVariableHolder holder, int value,
                              long createdAtMs, long updatedAtMs, long revision) {
        this.variableId = Objects.requireNonNull(variableId, "variableId");
        this.holder = Objects.requireNonNull(holder, "holder");
        if (createdAtMs < 0L || updatedAtMs < createdAtMs || revision < 0L) {
            throw new IllegalArgumentException("invalid value timestamps or revision");
        }
        this.value = value;
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
        this.revision = revision;
    }

    public String variableId() { return this.variableId; }
    public WiredVariableHolder holder() { return this.holder; }
    public int value() { return this.value; }
    public long createdAtMs() { return this.createdAtMs; }
    public long updatedAtMs() { return this.updatedAtMs; }
    public long revision() { return this.revision; }
}
