package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableLevelUp;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableTimeUtility;
import com.eu.habbo.habbohotel.rooms.Room;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** July generated subvariables: read-only virtual views over a stored parent value. */
public final class WiredGeneratedVariableRuntime {
    private static final String MARKER = ".value.";

    private WiredGeneratedVariableRuntime() { }

    public static List<GeneratedDefinition> definitions(Room room, WiredVariableDefinition parent) {
        if (room == null || parent == null || !parent.hasValue()) return List.of();
        InteractionWiredVariable item = room.getRoomSpecialTypes().getVariable(parent.definitionItemId());
        if (item == null) return List.of();
        Map<String, GeneratedDefinition> result = new LinkedHashMap<>();
        for (InteractionWiredAddon addon : variableAddons(room, item)) {
            if (addon instanceof WiredAddonVariableTimeUtility time)
                for (String name : time.enabledNames()) {
                    GeneratedDefinition generated =
                            generated(parent, name, time.getWiredData());
                    result.putIfAbsent(generated.variableId(), generated);
                }
            else if (addon instanceof WiredAddonVariableLevelUp level)
                for (String name : level.enabledNames()) {
                    GeneratedDefinition generated =
                            generated(parent, name, level.getWiredData());
                    result.putIfAbsent(generated.variableId(), generated);
                }
        }
        return List.copyOf(result.values());
    }

    public static WiredVariableValue read(Room room, String generatedId, WiredVariableHolder holder) {
        Parsed parsed = parse(generatedId);
        if (room == null || parsed == null || holder == null) return null;
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) return null;
        WiredVariableDefinition parent = manager.runtimeDefinition(parsed.parentId);
        if (parent == null || !parent.accepts(holder)) return null;
        var effectiveParent = WiredVariableAliasResolver.resolve(room, parsed.parentId);
        if (effectiveParent == null || !effectiveParent.definition().hasValue()) return null;
        WiredVariableValue source = WiredVariableAliasOperations.readValue(room, parsed.parentId, holder);
        if (source == null) return null;
        InteractionWiredVariable item =
                room.getRoomSpecialTypes().getVariable(parent.definitionItemId());
        if (item == null) return null;
        Long derived = null;
        for (InteractionWiredAddon addon : variableAddons(room, item)) {
            Map<String, Long> values = addon instanceof WiredAddonVariableTimeUtility time
                    ? time.derive(time.sourceValue(source))
                    : addon instanceof WiredAddonVariableLevelUp level ? level.derive(source.value()) : Map.of();
            if (values.containsKey(parsed.childName)) { derived = values.get(parsed.childName); break; }
        }
        if (derived == null) return null;
        int bounded = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, derived));
        return new WiredVariableValue(generatedId, holder, bounded,
                source.createdAtMs(), source.updatedAtMs(), source.revision());
    }

    public static WiredVariableDefinition parentDefinition(Room room, String generatedId) {
        Parsed parsed = parse(generatedId);
        if (room == null || parsed == null) return null;
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) return null;
        WiredVariableDefinition parent = manager.runtimeDefinition(parsed.parentId);
        if (parent == null) return null;
        return definitions(room, parent).stream()
                .anyMatch(value -> value.variableId.equals(generatedId)) ? parent : null;
    }

    private static GeneratedDefinition generated(WiredVariableDefinition parent, String child,
                                                 String configuration) {
        String id = parent.variableId() + MARKER + child;
        return new GeneratedDefinition(id, parent.name() + MARKER + child, parent, child,
                Objects.hash(id, configuration));
    }

    private static List<InteractionWiredAddon> variableAddons(
            Room room, InteractionWiredVariable item) {
        if (room == null || item == null) {
            return List.of();
        }
        return room.getRoomSpecialTypes().getAddons(item.getX(), item.getY()).stream()
                .filter(addon -> addon instanceof WiredAddonVariableTimeUtility
                        || addon instanceof WiredAddonVariableLevelUp)
                .sorted(Comparator.comparingInt(InteractionWiredAddon::getId))
                .toList();
    }

    private static Parsed parse(String id) {
        if (id == null) return null;
        int marker = id.indexOf(MARKER);
        if (marker <= 0 || marker + MARKER.length() >= id.length()) return null;
        return new Parsed(id.substring(0, marker), id.substring(marker + MARKER.length()));
    }

    public record GeneratedDefinition(String variableId, String variableName,
                                      WiredVariableDefinition parent, String childName, int hash) { }
    private record Parsed(String parentId, String childName) { }
}
