package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Observes ordinary gameplay changes at the room's 500ms cycle boundary. */
public final class WiredInternalVariableObserver {
    private static final Map<Room, Map<Key, Integer>> STATES = new WeakHashMap<>();
    private WiredInternalVariableObserver() { }

    public static WiredVariableMutation transition(String id, WiredVariableHolder holder,
            Integer before, Integer after, int boxId, int origin) {
        if (Objects.equals(before, after)) return null;
        return new WiredVariableMutation(id, holder, before, after,
                before == null ? WiredVariableMutation.Kind.CREATED
                        : after == null ? WiredVariableMutation.Kind.DELETED
                        : WiredVariableMutation.Kind.VALUE_CHANGED,
                Math.max(1L, System.currentTimeMillis()), boxId, origin);
    }

    /** Explicit publishers advance the baseline before dispatch, avoiding duplicates. */
    public static void record(Room room, WiredVariableMutation mutation) {
        if (room == null || mutation == null || !mutation.variableId().startsWith("internal:")) return;
        synchronized (STATES) {
            STATES.computeIfAbsent(room, ignored -> new HashMap<>())
                    .put(new Key(mutation.variableId(), mutation.holder()), mutation.afterValue());
        }
    }

    public static void observe(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null
                || room.getRoomSpecialTypes().getWiredVariableManager() == null
                || !room.getRoomSpecialTypes().getWiredVariableManager().isOperational()) return;
        var subscribed = new java.util.HashSet<String>();
        for (var trigger : room.getRoomSpecialTypes().getTriggers(
                com.eu.habbo.habbohotel.wired.WiredTriggerType.VARIABLE_CHANGED)) {
            if (trigger instanceof com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged changed) {
                var source = WiredVariableAliasResolver.resolve(room, changed.selectedVariableId());
                if (source != null && source.variableId().startsWith("internal:")) subscribed.add(source.variableId());
            }
        }
        if (subscribed.isEmpty()) {
            synchronized (STATES) { STATES.remove(room); }
            return;
        }
        Map<Key, Integer> sample = new HashMap<>();
        Map<WiredVariableHolder, com.eu.habbo.habbohotel.rooms.RoomUnit> actors = new HashMap<>();
        boolean furni = subscribed.stream().anyMatch(id -> id.startsWith("internal:0:"));
        boolean user = subscribed.stream().anyMatch(id -> id.startsWith("internal:1:"));
        boolean global = subscribed.stream().anyMatch(id -> id.startsWith("internal:-10:"));
        if (furni) {
            var furniture = new ArrayList<>(room.getFloorItems());
            furniture.addAll(room.getWallItems());
            for (var item : furniture) {
                var values = WiredInternalVariableRuntime.furniValues(room, item);
                collect(sample, subscribed, WiredInternalVariableRuntime.TARGET_FURNI,
                        WiredVariableHolder.furni(item.getId()), values);
            }
        }
        if (user) for (var unit : room.getRoomUnits()) {
            if (unit == null || !unit.isInRoom() || unit.getRoom() != room) continue;
            var habbo = room.getHabbo(unit);
            int stableId = habbo != null && habbo.getHabboInfo() != null
                    ? habbo.getHabboInfo().getId() : unit.getId();
            if (stableId > 0) {
                var holder = WiredVariableHolder.user(stableId);
                actors.put(holder, unit);
                collect(sample, subscribed, WiredInternalVariableRuntime.TARGET_USER,
                        holder, WiredInternalVariableRuntime.unitValues(room, unit));
            }
        }
        if (global) collect(sample, subscribed, WiredInternalVariableRuntime.TARGET_GLOBAL,
                WiredVariableHolder.room(), WiredInternalVariableRuntime.globalValues(room));
        var changes = new ArrayList<WiredVariableMutation>();
        synchronized (STATES) {
            Map<Key, Integer> before = STATES.put(room, sample);
            if (before == null) return; // First sample establishes room/holder baselines.
            for (var entry : sample.entrySet()) {
                if (!before.containsKey(entry.getKey())) continue;
                var change = transition(entry.getKey().id(), entry.getKey().holder(),
                        before.get(entry.getKey()), entry.getValue(), 0,
                        WiredVariableMutation.CHANGE_ORIGIN_EXTERNAL);
                if (change != null) changes.add(change);
            }
        }
        for (var change : changes) {
            WiredManager.triggerVariableChanged(room, actors.get(change.holder()), change);
        }
    }

    private static void collect(Map<Key, Integer> sample, java.util.Set<String> subscribed, int target,
            WiredVariableHolder holder, Map<String, Integer> values) {
        for (var definition : WiredInternalVariableRuntime.definitions())
            if (definition.target() == target
                    && subscribed.contains(definition.variableId())
                    && (definition.writable() || definition.runtimeObservable()))
                sample.put(new Key(definition.variableId(), holder), values.get(definition.name()));
    }

    private record Key(String id, WiredVariableHolder holder) { }
}
