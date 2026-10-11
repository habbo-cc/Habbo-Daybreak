package com.eu.habbo.habbohotel.wired.variables;

import java.util.Objects;

/**
 * Stable server-side identity for one variable value holder.
 *
 * <p>Furniture holders use database item IDs and user holders use Habbo IDs.
 * These IDs must only be translated to room-visible IDs at the packet boundary.</p>
 */
public final class WiredVariableHolder implements Comparable<WiredVariableHolder> {
    public static final int ROOM_ID = 0;

    public enum Scope {
        ROOM(0),
        USER(1),
        FURNI(2);

        public final int code;

        Scope(int code) {
            this.code = code;
        }

        public static Scope fromCode(int code) {
            for (Scope scope : values()) {
                if (scope.code == code) {
                    return scope;
                }
            }
            return null;
        }
    }

    private static final WiredVariableHolder ROOM = new WiredVariableHolder(Scope.ROOM, ROOM_ID);

    private final Scope scope;
    private final int stableId;

    private WiredVariableHolder(Scope scope, int stableId) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.stableId = stableId;
    }

    public static WiredVariableHolder room() {
        return ROOM;
    }

    public static WiredVariableHolder user(int habboId) {
        return positive(Scope.USER, habboId);
    }

    public static WiredVariableHolder furni(int databaseItemId) {
        if (databaseItemId == 0) {
            throw new IllegalArgumentException("furni holder ID must be non-zero");
        }
        return new WiredVariableHolder(Scope.FURNI, databaseItemId);
    }

    public static WiredVariableHolder of(Scope scope, int stableId) {
        Objects.requireNonNull(scope, "scope");
        if (scope == Scope.ROOM) {
            if (stableId != ROOM_ID) {
                throw new IllegalArgumentException("room holder ID must be zero");
            }
            return ROOM;
        }
        if (scope == Scope.FURNI) {
            return furni(stableId);
        }
        return positive(scope, stableId);
    }

    private static WiredVariableHolder positive(Scope scope, int stableId) {
        if (stableId <= 0) {
            throw new IllegalArgumentException("holder ID must be positive");
        }
        return new WiredVariableHolder(scope, stableId);
    }

    public Scope scope() {
        return this.scope;
    }

    public int stableId() {
        return this.stableId;
    }

    /** Negative furniture IDs identify room-local temporary Wired spawns. */
    public boolean isTransientFurni() {
        return this.scope == Scope.FURNI && this.stableId < 0;
    }

    public String canonicalId() {
        return this.scope.name().toLowerCase() + ":" + this.stableId;
    }

    @Override
    public int compareTo(WiredVariableHolder other) {
        int scopeOrder = Integer.compare(this.scope.code, other.scope.code);
        return scopeOrder != 0 ? scopeOrder : Integer.compare(this.stableId, other.stableId);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof WiredVariableHolder other
                && this.scope == other.scope
                && this.stableId == other.stableId;
    }

    @Override
    public int hashCode() {
        return 31 * this.scope.code + this.stableId;
    }

    @Override
    public String toString() {
        return canonicalId();
    }
}
