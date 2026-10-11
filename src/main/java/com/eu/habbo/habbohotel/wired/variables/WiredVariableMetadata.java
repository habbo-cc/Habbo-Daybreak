package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariablePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.messages.ServerMessage;

import java.util.Map;

/** Serializes one July WiredVariable metadata record. */
public final class WiredVariableMetadata {
    public static final int STORED_VARIABLE_TYPE = 0;
    public static final int ALIAS_VARIABLE_TYPE = 2;
    public static final int DYNAMIC_VARIABLE_TYPE = 3;
    public static final int GLOBAL_TARGET = -10;

    private final String variableId;
    private final int variableType;
    private final String variableName;
    private final int availability;
    private final int variableTarget;
    private final boolean alwaysAvailable;
    private final boolean canCreateAndDelete;
    private final boolean hasValue;
    private final boolean canWriteValue;
    private final boolean canInterceptChanges;
    private final boolean invisible;
    private final boolean canReadCreationTime;
    private final boolean canReadLastUpdateTime;
    private final Map<Long, String> textConnectorValues;

    private WiredVariableMetadata(String variableId, int variableType, String variableName,
                                  int availability, int variableTarget,
                                  boolean alwaysAvailable, boolean canCreateAndDelete,
                                  boolean hasValue, boolean canWriteValue,
                                  boolean canInterceptChanges, boolean invisible,
                                  boolean canReadCreationTime, boolean canReadLastUpdateTime,
                                  Map<Long, String> textConnectorValues) {
        this.variableId = variableId;
        this.variableType = variableType;
        this.variableName = variableName;
        this.availability = availability;
        this.variableTarget = variableTarget;
        this.alwaysAvailable = alwaysAvailable;
        this.canCreateAndDelete = canCreateAndDelete;
        this.hasValue = hasValue;
        this.canWriteValue = canWriteValue;
        this.canInterceptChanges = canInterceptChanges;
        this.invisible = invisible;
        this.canReadCreationTime = canReadCreationTime;
        this.canReadLastUpdateTime = canReadLastUpdateTime;
        this.textConnectorValues = textConnectorValues == null ? Map.of() : Map.copyOf(textConnectorValues);
    }

    /**
     * Adapter for the Global/Room Variable singleton.
     *
     * <p>The July client proves the field order, target {@code -10}, and
     * availability values, but client code cannot prove the server-generated ID
     * or capability flags. These values intentionally emulate the donor's
     * lifecycle behind a stable item-based ID. Replace this one factory when an
     * official open-editor capture becomes available.</p>
     */
    public static WiredVariableMetadata roomGlobal(int databaseItemId, String name, int availability) {
        return stored("room:" + databaseItemId, name, availability, GLOBAL_TARGET, true, Map.of());
    }

    public static WiredVariableMetadata roomGlobal(Room room, int databaseItemId, String name, int availability) {
        String variableId = "room:" + databaseItemId;
        return stored(variableId, name, availability, GLOBAL_TARGET, true,
                WiredAddonVariablePlaceholder.textConverterValues(room, variableId));
    }

    /**
     * Adapter for a room-visible stored definition.  The opaque variable ID is
     * retained from the persisted definition; it must never be re-derived from
     * a room-visible furniture ID.
     *
     * <p>The July parser proves this record's field order and holder-target
     * domain.  Its capability flags remain the documented emulation until an
     * official editor capture establishes the exact flags per definition.</p>
     */
    public static WiredVariableMetadata fromDefinition(WiredVariableDefinition definition) {
        return fromDefinition(null, definition);
    }

    public static WiredVariableMetadata fromDefinition(Room room, WiredVariableDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition is required");
        }
        if (room != null && room.getRoomSpecialTypes() != null) {
            var item = room.getRoomSpecialTypes().getVariable(definition.definitionItemId());
            if (item instanceof WiredVariableReference || item instanceof WiredVariableEcho) {
                var source = WiredVariableAliasOperations.resolve(room, definition.variableId());
                WiredVariableMetadata effective = source == null
                        ? ordinaryDefinition(room, definition)
                        : sourceMetadata(room, source);
                WiredVariableMetadata alias = effective.alias(definition.variableId(), definition.name(),
                        source == null || source.readOnly(),
                        WiredAddonVariablePlaceholder.textConverterValues(room, definition.variableId()));
                return source == null ? alias.unavailableAlias() : alias;
            }
        }
        return ordinaryDefinition(room, definition);
    }

    private static WiredVariableMetadata sourceMetadata(Room room, WiredVariableAliasOperations.Resolved source) {
        var internal = WiredInternalVariableRuntime.definitions().stream()
                .filter(value -> value.variableId().equals(source.variableId())).findFirst().orElse(null);
        if (internal != null) return internal(internal);
        // Synthetic generated definitions preserve the source target for runtime
        // operations; metadata must preserve its dynamic category and presence.
        var environment = com.eu.habbo.Emulator.getGameEnvironment();
        Room sourceRoom = source.manager().roomId() == room.getId() ? room
                : environment == null ? null : environment.getRoomManager().getRoom(source.manager().roomId());
        WiredVariableDefinition parent = WiredGeneratedVariableRuntime.parentDefinition(sourceRoom, source.variableId());
        if (parent != null) return generated(sourceRoom, source.variableId(), source.definition().name(), parent);
        return ordinaryDefinition(sourceRoom, source.definition());
    }

    private static WiredVariableMetadata ordinaryDefinition(Room room, WiredVariableDefinition definition) {
        int target = switch (definition.type()) {
            case FURNI -> 0;
            case USER, QUEST, QUEST_CHAIN -> 1;
            case ROOM -> GLOBAL_TARGET;
            case CONTEXT -> WiredInternalVariableRuntime.TARGET_CONTEXT;
            default -> throw new IllegalArgumentException(
                    "definition is not room-visible: " + definition.type());
        };
        if (definition.type() == WiredVariableType.QUEST
                || definition.type() == WiredVariableType.QUEST_CHAIN) {
            return readOnlyStored(definition.variableId(), definition.name(),
                    definition.availabilityCode(), target).visibility(definition.invisible());
        }
        if (definition.type() == WiredVariableType.CONTEXT) {
            return context(definition.variableId(), definition.name(), definition.hasValue())
                    .visibility(definition.invisible());
        }
        return stored(definition.variableId(), definition.name(), definition.availabilityCode(), target,
                definition.hasValue(), WiredAddonVariablePlaceholder.textConverterValues(room, definition.variableId()))
                .visibility(definition.invisible());
    }

    public static WiredVariableMetadata context(String variableId, String name, boolean hasValue) {
        return new WiredVariableMetadata(variableId, STORED_VARIABLE_TYPE, name, 0,
                WiredInternalVariableRuntime.TARGET_CONTEXT, false, true, hasValue, hasValue,
                false, false, false, false, Map.of());
    }

    /** Alias identity is local, while all capabilities follow its authoritative source. */
    public WiredVariableMetadata alias(String variableId, String name, boolean readOnly,
                                       Map<Long, String> connectorValues) {
        return new WiredVariableMetadata(variableId, ALIAS_VARIABLE_TYPE, name, this.availability,
                this.variableTarget, this.alwaysAvailable, this.canCreateAndDelete && !readOnly,
                this.hasValue, this.canWriteValue && !readOnly, this.canInterceptChanges,
                this.invisible, this.canReadCreationTime, this.canReadLastUpdateTime, connectorValues);
    }

    private WiredVariableMetadata unavailableAlias() {
        return new WiredVariableMetadata(this.variableId, this.variableType, this.variableName,
                this.availability, this.variableTarget, false, false, this.hasValue, false,
                false, this.invisible, false, false, this.textConnectorValues);
    }

    private WiredVariableMetadata visibility(boolean invisible) {
        return new WiredVariableMetadata(this.variableId, this.variableType, this.variableName,
                this.availability, this.variableTarget, this.alwaysAvailable, this.canCreateAndDelete,
                this.hasValue, this.canWriteValue, this.canInterceptChanges, invisible,
                this.canReadCreationTime, this.canReadLastUpdateTime, this.textConnectorValues);
    }

    public boolean invisible() { return this.invisible; }
    public boolean canCreateAndDelete() { return this.canCreateAndDelete; }
    public boolean canWriteValue() { return this.canWriteValue; }
    public boolean canInterceptChanges() { return this.canInterceptChanges; }
    public boolean hasValue() { return this.hasValue; }
    public boolean alwaysAvailable() { return this.alwaysAvailable; }
    public boolean canReadCreationTime() { return this.canReadCreationTime; }
    public boolean canReadLastUpdateTime() { return this.canReadLastUpdateTime; }

    public static WiredVariableMetadata generated(String variableId, String variableName,
                                                   WiredVariableDefinition parent) {
        return generated(null, variableId, variableName, parent);
    }

    public static WiredVariableMetadata generated(Room room, String variableId, String variableName,
                                                   WiredVariableDefinition parent) {
        if (parent == null) throw new IllegalArgumentException("parent is required");
        WiredVariableMetadata parentMetadata = fromDefinition(room, parent);
        return new WiredVariableMetadata(variableId, DYNAMIC_VARIABLE_TYPE, variableName,
                parentMetadata.availability, parentMetadata.variableTarget, parentMetadata.alwaysAvailable,
                false, true, false, false,
                parentMetadata.invisible, false, false, Map.of());
    }

    /** July variable type 1: calculated engine value exposed in the Internal tab. */
    public static WiredVariableMetadata internal(
            WiredInternalVariableRuntime.Definition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("internal definition is required");
        }
        return new WiredVariableMetadata(
                definition.variableId(),
                1,
                definition.name(),
                WiredInternalVariableRuntime.AVAILABILITY_INTERNAL,
                definition.target(),
                definition.alwaysAvailable(),
                false,
                true,
                definition.writable(),
                definition.writable() || definition.runtimeObservable(),
                false,
                false,
                false,
                Map.of());
    }

    /**
     * Stable local cache token for the exact metadata serialized to AIR.
     *
     * <p>July treats the per-variable hash as opaque.  Our emulated token must
     * nevertheless change whenever any serialized field changes; in
     * particular, placeholder add-ons can alter the text-connector map without
     * changing the underlying variable definition.</p>
     */
    int catalogHash() {
        int hash = 17;
        hash = 31 * hash + this.variableId.hashCode();
        hash = 31 * hash + this.variableType;
        hash = 31 * hash + this.variableName.hashCode();
        hash = 31 * hash + this.availability;
        hash = 31 * hash + this.variableTarget;
        hash = 31 * hash + Boolean.hashCode(this.alwaysAvailable);
        hash = 31 * hash + Boolean.hashCode(this.canCreateAndDelete);
        hash = 31 * hash + Boolean.hashCode(this.hasValue);
        hash = 31 * hash + Boolean.hashCode(this.canWriteValue);
        hash = 31 * hash + Boolean.hashCode(this.canInterceptChanges);
        hash = 31 * hash + Boolean.hashCode(this.invisible);
        hash = 31 * hash + Boolean.hashCode(this.canReadCreationTime);
        hash = 31 * hash + Boolean.hashCode(this.canReadLastUpdateTime);
        hash = 31 * hash + this.textConnectorValues.size();
        for (Map.Entry<Long, String> entry : this.textConnectorValues.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            hash = 31 * hash + Long.hashCode(entry.getKey());
            hash = 31 * hash + entry.getValue().hashCode();
        }
        return hash;
    }

    private static WiredVariableMetadata stored(String variableId, String name, int availability,
                                                int target) {
        return stored(variableId, name, availability, target, true, Map.of());
    }

    private static WiredVariableMetadata stored(String variableId, String name, int availability,
                                                int target, boolean hasValue,
                                                Map<Long, String> textConnectorValues) {
        return new WiredVariableMetadata(
                variableId,
                STORED_VARIABLE_TYPE,
                name == null ? "" : name,
                availability,
                target,
                target == GLOBAL_TARGET,
                target != GLOBAL_TARGET,
                hasValue,
                hasValue,
                true,
                false,
                true,
                true,
                textConnectorValues);
    }

    private static WiredVariableMetadata readOnlyStored(String variableId, String name,
                                                        int availability, int target) {
        return new WiredVariableMetadata(
                variableId,
                STORED_VARIABLE_TYPE,
                name == null ? "" : name,
                availability,
                target,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                Map.of());
    }

    /** Exact July WiredVariable field order, including the absent text-connector flag. */
    public void serialize(ServerMessage message) {
        message.appendString(this.variableId);
        message.appendInt(this.variableType);
        message.appendString(this.variableName);
        message.appendInt(this.availability);
        message.appendInt(this.variableTarget);
        message.appendBoolean(this.alwaysAvailable);
        message.appendBoolean(this.canCreateAndDelete);
        message.appendBoolean(this.hasValue);
        message.appendBoolean(this.canWriteValue);
        message.appendBoolean(this.canInterceptChanges);
        message.appendBoolean(this.invisible);
        message.appendBoolean(this.canReadCreationTime);
        message.appendBoolean(this.canReadLastUpdateTime);
        message.appendBoolean(!this.textConnectorValues.isEmpty());
        if (!this.textConnectorValues.isEmpty()) {
            message.appendInt(this.textConnectorValues.size());
            this.textConnectorValues.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        message.appendInt(entry.getKey().intValue());
                        message.appendString(entry.getValue());
                    });
        }
    }
}
