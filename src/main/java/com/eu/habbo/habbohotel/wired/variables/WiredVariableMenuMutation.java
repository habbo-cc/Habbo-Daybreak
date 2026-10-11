package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.function.Consumer;

/** Creator-tool stored mutations share the authoritative alias policy of Wired effects. */
public final class WiredVariableMenuMutation {
    private WiredVariableMenuMutation() { }

    public static WiredVariableManager.MutationResult apply(Room room, WiredVariableHolder holder,
            String variableId, int value, int operation, boolean permanentOnly,
            Consumer<WiredVariableMutation> dispatcher) {
        if (room == null || holder == null || operation < 0 || operation > 2)
            return WiredVariableManager.MutationResult.INVALID;
        var source = WiredVariableAliasOperations.resolve(room, variableId);
        var destination = WiredVariableAliasOperations.writable(room, variableId, holder);
        if (source == null || source.readOnly() || destination == null)
            return WiredVariableManager.MutationResult.INVALID;
        var definition = source.definition();
        var localDefinition = room.getRoomSpecialTypes().getWiredVariableManager().definition(variableId);
        if (definition.invisible() || localDefinition != null
                && WiredVariableMetadata.fromDefinition(room, localDefinition).invisible()
                || !definition.accepts(holder) || permanentOnly && !definition.persistsValues()
                || (operation == 0 ? !WiredVariableAliasOperations.canWriteValue(room, variableId)
                : !WiredVariableAliasOperations.canCreateDelete(room, variableId)))
            return WiredVariableManager.MutationResult.INVALID;
        var manager = destination.manager();
        var id = destination.variableId();
        var current = manager.get(id, holder);
        Consumer<WiredVariableMutation> expose = mutation -> {
            if (dispatcher != null) dispatcher.accept(new WiredVariableMutation(variableId,
                    mutation.holder(), mutation.beforeValue(), mutation.afterValue(), mutation.kind(),
                    mutation.revision(), mutation.boxId(), WiredVariableMutation.CHANGE_ORIGIN_CREATOR_TOOL));
        };
        return switch (operation) {
            case 0 -> current == null ? WiredVariableManager.MutationResult.INVALID
                    : manager.setAndDispatch(id, holder, value, expose);
            case 1 -> current != null ? WiredVariableManager.MutationResult.INVALID
                    : manager.setIfAbsentAndDispatch(id, holder, definition.hasValue() ? value : 0, expose);
            case 2 -> current == null ? WiredVariableManager.MutationResult.INVALID
                    : manager.removeValueAndDispatch(id, holder, expose);
            default -> WiredVariableManager.MutationResult.INVALID;
        };
    }
}
