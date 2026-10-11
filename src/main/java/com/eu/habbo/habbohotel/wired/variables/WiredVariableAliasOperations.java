package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;

import java.util.HashSet;
import java.util.Set;

/**
 * Bounded operational view of a Reference/Echo chain. Callers never receive a
 * writable target through a read-only edge, and cyclic durable edges fail
 * closed without resetting the visited set.
 */
public final class WiredVariableAliasOperations {
    private WiredVariableAliasOperations() {
    }

    public static Resolved resolve(Room room, String variableId) {
        Resolved active = fromActive(
                WiredVariableAliasResolver.resolve(room, variableId), false);
        return active != null
                ? active
                : resolveDurable(room, variableId, new HashSet<>(), 0, false);
    }

    public static boolean isAlias(Room room, String variableId) {
        if (room == null || variableId == null) {
            return false;
        }
        Boolean activeAlias = activeAlias(room, variableId);
        if (activeAlias != null) {
            return activeAlias;
        }
        WiredCrossRoomAliasRepository.Entry entry =
                WiredCrossRoomAliasRepository.instance()
                        .loadByAlias(room.getId(), variableId);
        return entry != null && entry.sourceRoomId() != null;
    }

    private static Resolved resolveDurable(
            Room room,
            String variableId,
            Set<String> visited,
            int depth,
            boolean readOnly) {
        if (room == null || variableId == null
                || depth >= WiredVariableAliasResolver.MAX_DEPTH
                || !visited.add(key(room.getId(), variableId))) {
            return null;
        }

        if (variableId.startsWith("room:")) {
            try {
                var item = room.getRoomSpecialTypes().getVariable(Integer.parseInt(variableId.substring(5)));
                if (item instanceof WiredVariableEcho echo)
                    return resolveDurable(room, echo.sourceId(), visited, depth + 1, readOnly);
            } catch (NumberFormatException ignored) { }
        }

        WiredCrossRoomAliasRepository.Entry alias =
                WiredCrossRoomAliasRepository.instance()
                        .loadByAlias(room.getId(), variableId);
        if (alias != null && alias.sourceRoomId() != null) {
            var environment = Emulator.getGameEnvironment();
            if (environment == null) {
                return null;
            }
            Room sourceRoom = environment.getRoomManager().getRoom(alias.sourceRoomId());
            if (sourceRoom == null) {
                return null;
            }
            return resolveDurable(
                    sourceRoom,
                    alias.sourceVariableId(),
                    visited,
                    depth + 1,
                    readOnly || alias.readOnly());
        }

        WiredVariableAliasResolver.Resolved active =
                WiredVariableAliasResolver.resolve(room, variableId);
        return fromActive(active, readOnly);
    }

    public static Integer read(Room room, String variableId, WiredVariableHolder holder) {
        WiredVariableValue value = readValue(room, variableId, holder);
        return value == null ? null : value.value();
    }

    public static WiredVariableValue readValue(
            Room room, String variableId, WiredVariableHolder holder) {
        if (room == null || holder == null) {
            return null;
        }
        Resolved active = resolve(room, variableId);
        if (active != null) {
            if (active.variableId().startsWith("internal:")) {
                Integer value = acceptsHolder(room.getId(), active, holder)
                        ? WiredInternalVariableRuntime.read(active.manager().roomId() == room.getId() ? room : sourceRoom(active), active.variableId(), holder) : null;
                return value == null ? null : new WiredVariableValue(variableId, holder, value, 0, 0, 0);
            }
            if (active.variableId().contains(".value.")) {
                return acceptsHolder(room.getId(), active, holder)
                        ? WiredGeneratedVariableRuntime.read(active.manager().roomId() == room.getId() ? room : sourceRoom(active), active.variableId(), holder) : null;
            }
            return acceptsHolder(room.getId(), active, holder)
                    ? active.manager().get(active.variableId(), holder)
                    : null;
        }
        return readUnloaded(
                room.getId(), room.getId(), variableId, holder,
                new HashSet<>(), 0);
    }

    private static WiredVariableValue readUnloaded(
            int originRoomId,
            int roomId,
            String variableId,
            WiredVariableHolder holder,
            Set<String> visited,
            int depth) {
        if (roomId <= 0 || variableId == null
                || depth >= WiredVariableAliasResolver.MAX_DEPTH
                || !visited.add(key(roomId, variableId))) {
            return null;
        }
        WiredCrossRoomAliasRepository repository =
                WiredCrossRoomAliasRepository.instance();
        WiredCrossRoomAliasRepository.Entry entry =
                repository.loadByAlias(roomId, variableId);
        if (entry == null) {
            return null;
        }
        if (entry.sourceRoomId() != null) {
            return readUnloaded(
                    originRoomId,
                    entry.sourceRoomId(),
                    entry.sourceVariableId(),
                    holder,
                    visited,
                    depth + 1);
        }

        if (holder.scope() == WiredVariableHolder.Scope.FURNI
                && roomId != originRoomId) {
            return null;
        }
        WiredCrossRoomAliasRepository.PersistedValue value =
                repository.loadPersistedValue(roomId, variableId, holder);
        if (value == null || value.scope() != holder.scope().code) {
            return null;
        }
        return new WiredVariableValue(
                variableId,
                holder,
                value.value(),
                value.createdAt(),
                value.updatedAt(),
                value.revision());
    }

    public static MutationTarget writable(
            Room room, String variableId, WiredVariableHolder holder) {
        Resolved resolved = resolve(room, variableId);
        return resolved == null || resolved.readOnly()
                || resolved.variableId().startsWith("internal:")
                || resolved.variableId().contains(".value.")
                || !acceptsHolder(room == null ? 0 : room.getId(), resolved, holder)
                ? null
                : new MutationTarget(
                        resolved.manager(), resolved.variableId(), holder);
    }

    public static boolean canCreateDelete(Room room, String variableId) {
        Resolved resolved = resolve(room, variableId);
        if (resolved == null) {
            var durable = durableCapabilities(room, variableId);
            return durable != null && !durable.readOnly() && durable.scope() != WiredVariableHolder.Scope.ROOM.code;
        }
        return resolved != null && !resolved.readOnly()
                && !resolved.variableId().startsWith("internal:")
                && !resolved.variableId().contains(".value.")
                && (resolved.definition().type() == com.eu.habbo.habbohotel.wired.WiredVariableType.FURNI
                || resolved.definition().type() == com.eu.habbo.habbohotel.wired.WiredVariableType.USER
                || resolved.definition().type() == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT);
    }

    public static boolean canWriteValue(Room room, String variableId) {
        Resolved resolved = resolve(room, variableId);
        if (resolved == null) {
            var durable = durableCapabilities(room, variableId);
            return durable != null && !durable.readOnly() && durable.hasValue();
        }
        return resolved != null && !resolved.readOnly() && resolved.definition().hasValue()
                && !WiredQuestVariableRuntime.isQuestBacked(resolved.definition());
    }

    /** Read persisted shared-source policy without loading or hydrating the room. */
    private static DurableCapabilities durableCapabilities(Room room, String id) {
        if (room == null || id == null) return null;
        int roomId = room.getId();
        boolean readOnly = false;
        boolean crossedAlias = false;
        var visited = new HashSet<String>();
        for (int depth = 0; depth < WiredVariableAliasResolver.MAX_DEPTH; depth++) {
            if (!visited.add(key(roomId, id))) return null;
            if (roomId == room.getId() && id.startsWith("room:")) {
                try {
                    var item = room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5)));
                    if (item instanceof WiredVariableEcho echo) {
                        crossedAlias = true;
                        id = echo.sourceId();
                        continue;
                    }
                } catch (NumberFormatException ignored) { }
            }
            var entry = WiredCrossRoomAliasRepository.instance().loadByAlias(roomId, id);
            if (entry == null) return null;
            readOnly |= entry.readOnly();
            if (entry.sourceRoomId() != null) {
                crossedAlias = true;
                roomId = entry.sourceRoomId();
                id = entry.sourceVariableId();
                continue;
            }
            if (!crossedAlias || entry.availability() != 11
                    || entry.type() != com.eu.habbo.habbohotel.wired.WiredVariableType.FURNI
                    && entry.type() != com.eu.habbo.habbohotel.wired.WiredVariableType.USER
                    && entry.type() != com.eu.habbo.habbohotel.wired.WiredVariableType.ROOM) return null;
            return new DurableCapabilities(readOnly, entry.hasValue(), entry.scope());
        }
        return null;
    }

    private record DurableCapabilities(boolean readOnly, boolean hasValue, int scope) { }

    public static Integer readContext(com.eu.habbo.habbohotel.wired.core.WiredContext context, String id) {
        if (context == null) return null;
        Resolved resolved = resolve(context.room(), id);
        return context.contextVariables().get(resolved == null ? id : resolved.variableId());
    }

    public static Room sourceRoom(Resolved resolved) {
        var environment = Emulator.getGameEnvironment();
        return resolved == null || environment == null ? null
                : environment.getRoomManager().getRoom(resolved.manager().roomId());
    }

    /** One source mutation is exposed under every active alias in this room. */
    public static java.util.List<WiredVariableMutation> notifications(Room room, WiredVariableMutation mutation) {
        if (room == null || mutation == null) return java.util.List.of();
        var terminal = WiredVariableAliasResolver.resolve(room, mutation.variableId());
        if (terminal == null) return java.util.List.of(mutation);
        var ids = new java.util.LinkedHashSet<String>();
        if (terminal.room().getId() == room.getId()) ids.add(terminal.variableId());
        ids.add(mutation.variableId());
        for (var item : room.getRoomSpecialTypes().getVariables()) {
            if (!(item instanceof WiredVariableEcho) && !(item instanceof WiredVariableReference)) continue;
            String id = "room:" + item.getId();
            var alias = WiredVariableAliasResolver.resolve(room, id);
            if (alias != null && alias.room().getId() == terminal.room().getId()
                    && alias.variableId().equals(terminal.variableId())) ids.add(id);
        }
        return ids.stream().map(id -> id.equals(mutation.variableId()) ? mutation
                : new WiredVariableMutation(id, mutation.holder(), mutation.beforeValue(),
                        mutation.afterValue(), mutation.kind(), mutation.revision(),
                        mutation.boxId(), mutation.changeOrigin())).toList();
    }

    public static java.util.List<Notification> notificationsAcrossRooms(Room origin, WiredVariableMutation mutation) {
        if (origin == null || mutation == null) return java.util.List.of();
        var terminal = WiredVariableAliasResolver.resolve(origin, mutation.variableId());
        if (terminal == null) return java.util.List.of(new Notification(origin, mutation));
        var rooms = new java.util.LinkedHashMap<Integer, Room>();
        rooms.put(origin.getId(), origin);
        rooms.put(terminal.room().getId(), terminal.room());
        var environment = Emulator.getGameEnvironment();
        // Remote References can only target shared-permanent definitions.
        if (terminal.definition().availabilityCode() == 11 && environment != null)
            for (Room active : environment.getRoomManager().getActiveRooms()) rooms.put(active.getId(), active);
        var result = new java.util.LinkedHashMap<String, Notification>();
        addNotification(result, terminal.room(), terminal.variableId(), mutation);
        addNotification(result, origin, mutation.variableId(), mutation);
        for (Room room : rooms.values()) {
            for (var trigger : room.getRoomSpecialTypes().getTriggers(
                    com.eu.habbo.habbohotel.wired.WiredTriggerType.VARIABLE_CHANGED)) {
                if (!(trigger instanceof com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged changed)) continue;
                String id = changed.selectedVariableId();
                var source = WiredVariableAliasResolver.resolve(room, id);
                if (source != null && source.room().getId() == terminal.room().getId()
                        && source.variableId().equals(terminal.variableId())) addNotification(result, room, id, mutation);
            }
        }
        return java.util.List.copyOf(result.values());
    }

    private static void addNotification(java.util.Map<String, Notification> result, Room room,
            String id, WiredVariableMutation mutation) {
        var exposed = id.equals(mutation.variableId()) ? mutation : new WiredVariableMutation(id,
                mutation.holder(), mutation.beforeValue(), mutation.afterValue(), mutation.kind(),
                mutation.revision(), mutation.boxId(), mutation.changeOrigin());
        result.putIfAbsent(room.getId() + ":" + id, new Notification(room, exposed));
    }

    public record Notification(Room room, WiredVariableMutation mutation) { }

    public static WiredVariableMutation transformUnloaded(
            Room room,
            String variableId,
            WiredVariableHolder holder,
            WiredCrossRoomAliasRepository.ValueTransform transform) {
        return room == null ? null
                : WiredCrossRoomAliasRepository.instance().transformAliasValue(
                room.getId(), variableId, holder, transform);
    }

    public static WiredVariableMutation setUnloaded(
            Room room,
            String variableId,
            WiredVariableHolder holder,
            int value,
            boolean onlyIfAbsent) {
        return room == null ? null
                : WiredCrossRoomAliasRepository.instance().setAliasValue(
                room.getId(), variableId, holder, value, onlyIfAbsent);
    }

    public static WiredVariableMutation removeUnloaded(
            Room room, String variableId, WiredVariableHolder holder) {
        return room == null ? null
                : WiredCrossRoomAliasRepository.instance().removeAliasValue(
                room.getId(), variableId, holder);
    }

    private static boolean acceptsHolder(
            int originRoomId, Resolved resolved, WiredVariableHolder holder) {
        return resolved != null
                && resolved.definition().accepts(holder)
                && (holder.scope() != WiredVariableHolder.Scope.FURNI
                || resolved.manager().roomId() == originRoomId);
    }

    private static String key(int roomId, String variableId) {
        return roomId + ":" + variableId;
    }

    private static Resolved fromActive(
            WiredVariableAliasResolver.Resolved active, boolean readOnly) {
        if (active == null) {
            return null;
        }
        WiredVariableManager manager =
                active.room().getRoomSpecialTypes().getWiredVariableManager();
        return manager == null
                ? null
                : new Resolved(
                        manager,
                        active.variableId(),
                        active.definition(),
                        readOnly || active.readOnly());
    }

    /**
     * Avoids a registry query for ordinary active-room variables.  A null
     * result means the item cannot be identified safely and retains the
     * durable lookup fallback.
     */
    private static Boolean activeAlias(Room room, String variableId) {
        if (!variableId.startsWith("room:")) {
            return null;
        }
        try {
            InteractionWiredVariable item =
                    room.getRoomSpecialTypes().getVariable(
                            Integer.parseInt(variableId.substring(5)));
            return item == null
                    ? null
                    : item instanceof WiredVariableReference
                    || item instanceof WiredVariableEcho;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record Resolved(
            WiredVariableManager manager,
            String variableId,
            WiredVariableDefinition definition,
            boolean readOnly) {
    }

    public record MutationTarget(
            WiredVariableManager manager,
            String variableId,
            WiredVariableHolder holder) {
    }
}
