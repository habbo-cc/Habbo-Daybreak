package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

import java.util.Objects;

/** Immutable server definition. Values and holders are intentionally separate. */
public final class WiredVariableDefinition {
    private final int roomId;
    private final int definitionItemId;
    private final String variableId;
    private final WiredVariableType type;
    private final String name;
    private final int availabilityCode;
    private final boolean hasValue;
    private final boolean invisible;

    private WiredVariableDefinition(int roomId, int definitionItemId, String variableId,
                                    WiredVariableType type, String name,
                                    int availabilityCode, boolean hasValue, boolean invisible) {
        this.roomId = roomId;
        this.definitionItemId = definitionItemId;
        this.variableId = variableId;
        this.type = type;
        this.name = name;
        this.availabilityCode = availabilityCode;
        this.hasValue = hasValue;
        this.invisible = invisible;
    }

    public static WiredVariableDefinition create(int roomId, int definitionItemId,
                                                 WiredVariableType type, String name,
                                                 WiredVariableAvailability availability,
                                                 boolean invisible) {
        if (roomId <= 0 || definitionItemId <= 0) {
            throw new IllegalArgumentException("room and definition item IDs must be positive");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(availability, "availability");
        return create(roomId, definitionItemId, type, name, availability.code, invisible);
    }

    public static WiredVariableDefinition create(int roomId, int definitionItemId,
                                                 WiredVariableType type, String name,
                                                 int availabilityCode, boolean invisible) {
        return create(roomId, definitionItemId, type, name, availabilityCode, true, invisible);
    }

    public static WiredVariableDefinition create(int roomId, int definitionItemId,
                                                 WiredVariableType type, String name,
                                                 int availabilityCode, boolean hasValue,
                                                 boolean invisible) {
        if (roomId <= 0 || definitionItemId <= 0) {
            throw new IllegalArgumentException("room and definition item IDs must be positive");
        }
        Objects.requireNonNull(type, "type");
        if (!isVerifiedAvailability(type, availabilityCode)) {
            throw new IllegalArgumentException("unsupported availability for variable type");
        }
        String normalizedName = WiredVariableName.normalize(name);
        if (!WiredVariableName.isValid(normalizedName)) {
            throw new IllegalArgumentException("invalid variable name");
        }

        // This format preserves the existing Global Variable identity. The exact
        // official July string format remains capture-dependent; storage and
        // references must treat this value as opaque.
        String variableId = "room:" + definitionItemId;
        return new WiredVariableDefinition(roomId, definitionItemId, variableId,
                type, normalizedName, availabilityCode, hasValue, invisible);
    }

    public int roomId() {
        return this.roomId;
    }

    /** Non-persisted source descriptor; its opaque ID need not identify furniture. */
    public static WiredVariableDefinition runtimeView(int roomId, String variableId,
            WiredVariableType type, String name, int availability, boolean hasValue) {
        if (roomId <= 0 || variableId == null || variableId.isBlank())
            throw new IllegalArgumentException("invalid runtime source");
        return new WiredVariableDefinition(roomId, 0, variableId, type, name,
                availability, hasValue, false);
    }

    public int definitionItemId() {
        return this.definitionItemId;
    }

    public String variableId() {
        return this.variableId;
    }

    public WiredVariableType type() {
        return this.type;
    }

    public String name() {
        return this.name;
    }

    public int availabilityCode() {
        return this.availabilityCode;
    }

    public boolean hasValue() {
        return this.hasValue;
    }

    public boolean invisible() {
        return this.invisible;
    }

    public boolean persistsValues() {
        return this.availabilityCode == WiredVariableAvailability.PERMANENT.code
                || this.availabilityCode == WiredVariableAvailability.SHARED_PERMANENT.code;
    }

    public WiredVariableHolder.Scope holderScope() {
        return switch (this.type) {
            case ROOM -> WiredVariableHolder.Scope.ROOM;
            case USER, QUEST, QUEST_CHAIN -> WiredVariableHolder.Scope.USER;
            case FURNI -> WiredVariableHolder.Scope.FURNI;
            default -> null;
        };
    }

    public boolean accepts(WiredVariableHolder holder) {
        return holder != null && holder.scope() == holderScope();
    }

    public WiredVariableDefinition renamed(String newName) {
        return create(this.roomId, this.definitionItemId, this.type, newName,
                this.availabilityCode, this.hasValue, this.invisible);
    }

    private static boolean isVerifiedAvailability(WiredVariableType type, int code) {
        return switch (type) {
            case FURNI -> code == 1 || code == 10;
            case USER -> code == 0 || code == 10 || code == 11;
            case ROOM -> code == 1 || code == 10 || code == 11;
            // Quest-backed values are read-only, user-scoped views refreshed
            // from the authoritative quest service while the user is present.
            case QUEST, QUEST_CHAIN -> code == 0;
            // Context values are execution-local; zero is an internal marker and
            // is not serialized as an invented editor field.
            case CONTEXT -> code == 0;
            default -> false;
        };
    }
}
