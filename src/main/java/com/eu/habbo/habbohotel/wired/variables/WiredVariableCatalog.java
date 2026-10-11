package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import java.util.*;

/** Room catalog combining stored definitions with calculated July child variables. */
public final class WiredVariableCatalog {
    private WiredVariableCatalog() { }

    /** Shared definition visibility policy for editor context, hash and diff responses. */
    public static boolean isRoomVisibleStoredDefinition(WiredVariableType type) {
        return type == WiredVariableType.FURNI || type == WiredVariableType.USER
                || type == WiredVariableType.ROOM || type == WiredVariableType.QUEST
                || type == WiredVariableType.QUEST_CHAIN || type == WiredVariableType.CONTEXT;
    }

    public static WiredVariableManager.Snapshot visibleSnapshot(WiredVariableManager manager) {
        return manager == null ? null : manager.snapshot(definition -> !definition.invisible()
                && isRoomVisibleStoredDefinition(definition.type()));
    }

    /** Callers must already have authorized room/editor access. */
    public static Catalog forRoom(Room room) {
        return build(room, room == null || room.getRoomSpecialTypes() == null ? null
                : visibleSnapshot(room.getRoomSpecialTypes().getWiredVariableManager()));
    }

    public static Catalog build(Room room, WiredVariableManager.Snapshot snapshot) {
        if (room == null || snapshot == null || !snapshot.available()) return new Catalog(0, Map.of());
        List<Entry> ordered = new ArrayList<>();
        for (WiredVariableManager.VariableSnapshot variable : snapshot.variables().values()) {
            WiredVariableMetadata metadata =
                    WiredVariableMetadata.fromDefinition(room, variable.definition());
            if (metadata.invisible()) continue;
            ordered.add(new Entry(
                    variable.definition().variableId(),
                    catalogHash(variable.hash(), metadata),
                    metadata));
            // Alias descriptors are cloned at bind time; their current source
            // can subsequently become valueless. Use effective capabilities.
            if (!metadata.hasValue()) continue;
            for (WiredGeneratedVariableRuntime.GeneratedDefinition child
                    : WiredGeneratedVariableRuntime.definitions(room, variable.definition())) {
                WiredVariableMetadata childMetadata = WiredVariableMetadata.generated(
                        room, child.variableId(), child.variableName(), child.parent());
                ordered.add(new Entry(
                        child.variableId(),
                        catalogHash(child.hash(), childMetadata),
                        childMetadata));
            }
        }
        // Execution-local values deliberately stay out of manager storage, but
        // their furniture-backed definitions still belong in the picker catalog.
        for (var item : room.getRoomSpecialTypes().getVariables(WiredVariableType.CONTEXT)) {
            if (!(item instanceof WiredVariableContext context)
                    || !WiredVariableName.isValid(context.variableName())
                    || snapshot.variables().containsKey(context.variableId())) continue;
            WiredVariableMetadata metadata = WiredVariableMetadata.context(
                    context.variableId(), context.variableName(), context.hasValue());
            ordered.add(new Entry(context.variableId(), metadata.catalogHash(), metadata));
        }
        for (WiredInternalVariableRuntime.Definition internal
                : WiredInternalVariableRuntime.definitions()) {
            WiredVariableMetadata metadata = WiredVariableMetadata.internal(internal);
            ordered.add(new Entry(
                    internal.variableId(),
                    catalogHash(internal.hash(), metadata),
                    metadata));
        }
        ordered.sort(Comparator.comparing(Entry::variableId));
        LinkedHashMap<String,Entry> entries = new LinkedHashMap<>();
        int aggregate = 1;
        for (Entry entry : ordered) {
            entries.put(entry.variableId(), entry);
            aggregate = 31 * aggregate + entry.variableId().hashCode();
            aggregate = 31 * aggregate + entry.hash();
        }
        return new Catalog(aggregate, entries);
    }
    private static int catalogHash(int sourceHash, WiredVariableMetadata metadata) {
        return 31 * sourceHash + metadata.catalogHash();
    }
    public record Entry(String variableId, int hash, WiredVariableMetadata metadata) { }
    public record Catalog(int aggregateHash, Map<String,Entry> entries) {
        public Catalog { entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries)); }
    }
}
