package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableActionMath;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.OptionalInt;

/** July AIR Core Variables action 41 ({@code wf_act_change_var_val}). */
public final class WiredEffectChangeVariableValue extends WiredEffectVariableBase {
    private static final int OPERAND_LITERAL = 0;
    private static final int OPERAND_VARIABLE = 1;

    private String variableId = "";
    private String operandVariableId = "";
    private int target = TARGET_FURNI;
    private int operation = WiredVariableActionMath.ASSIGN;
    private int operandMode = OPERAND_LITERAL;
    private int literalValue;
    private int operandTarget = TARGET_FURNI;

    public WiredEffectChangeVariableValue(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeVariableValue(int id, int userId, Item item, String extradata, int limitedStack,
                                          int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.CHANGE_VARIABLE;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 2;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 2;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        Room room = requireEditorRoom(settings, client);
        if (room == null) {
            return false;
        }
        if (settings.getIntParams() == null || settings.getIntParams().length != 6
                || settings.getVariableIds() == null || settings.getVariableIds().length != 2
                || settings.getFurniSourceTypes().length != 2 || settings.getUserSourceTypes().length != 2) {
            throw new WiredSaveException("Invalid Change Variable data");
        }
        int[] parameters = settings.getIntParams();
        if (!WiredVariableActionMath.isImplementedOperation(parameters[1])
                || (parameters[2] != OPERAND_LITERAL && parameters[2] != OPERAND_VARIABLE)
                || !validSignedIntParts(parameters[3], parameters[4])) {
            throw new WiredSaveException("Unsupported Change Variable operation");
        }
        boolean requiresOperand = WiredVariableActionMath.requiresOperand(parameters[1]);
        if (!requiresOperand && parameters[2] != OPERAND_LITERAL) {
            throw new WiredSaveException("This Change Variable operation has no operand");
        }
        String requestedId = settings.getVariableIds()[0];
        String requestedOperandId = settings.getVariableIds()[1];
        WiredInternalVariableRuntime.Definition internalTarget =
                WiredInternalVariableRuntime.definition(requestedId, parameters[0]);
        if (internalTarget != null) {
            if (!internalTarget.writable()) {
                throw new WiredSaveException("The selected internal variable is read-only");
            }
        } else {
            validateOperation(room, requestedId, parameters[0], true, false);
        }
        if (requiresOperand && parameters[2] == OPERAND_VARIABLE) {
            if (!WiredInternalVariableRuntime.matches(requestedOperandId, parameters[5])) {
                validateReadableDefinition(room, requestedOperandId, parameters[5], true);
            }
        }
        if (!requiresOperand || parameters[2] == OPERAND_LITERAL) {
            requestedOperandId = "";
        }
        saveSelections(room, settings.getFurniIds(), settings.getFurniIds2(), true);
        this.variableId = requestedId;
        this.operandVariableId = requestedOperandId;
        this.target = parameters[0];
        this.operation = parameters[1];
        this.operandMode = parameters[2];
        this.literalValue = parameters[4];
        this.operandTarget = parameters[5];
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || !WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || !canOperate(context.room(), this.variableId, this.target, false)) {
            return;
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null) {
            return;
        }
        OptionalInt resolvedOperand = resolveOperand(manager, context);
        if (resolvedOperand.isEmpty()) {
            return;
        }
        int operand = resolvedOperand.getAsInt();
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(this.variableId, this.target);
        if (internal == null) {
            var source = WiredVariableAliasOperations.resolve(context.room(), this.variableId);
            if (source != null && !source.readOnly()) internal =
                    WiredInternalVariableRuntime.definition(source.variableId(), this.target);
        }
        if (internal != null) {
            executeInternal(context, internal, operand);
            return;
        }
        if (this.target == TARGET_CONTEXT) {
            Integer current = context.contextVariables().get(this.variableId);
            WiredVariableActionMath.apply(this.operation, current == null ? 0 : current, operand)
                    .ifPresent(value -> context.contextVariables().set(this.variableId, value));
            return;
        }
        List<WiredVariableHolder> destinations = resolveHolders(context, this.target, 0);
        consumeTargets(context, destinations);
        for (WiredVariableHolder destination : destinations) {
            WiredVariableAliasOperations.Resolved resolved = WiredVariableAliasOperations.resolve(context.room(), this.variableId);
            if (resolved != null && resolved.readOnly()) {
                continue;
            }
            WiredVariableAliasOperations.MutationTarget alias = WiredVariableAliasOperations.writable(
                    context.room(), this.variableId, destination);
            if (alias != null && (alias.manager() != manager || !alias.variableId().equals(this.variableId))) {
                WiredVariableValue current = alias.manager().get(alias.variableId(), destination);
                WiredVariableActionMath.apply(this.operation, current == null ? 0 : current.value(), operand)
                        .ifPresent(value -> alias.manager().setAndDispatch(alias.variableId(), destination, value,
                                mutationDispatcher(context, this.variableId,
                                        WiredVariableMutation.CHANGE_ORIGIN_ANOTHER_ROOM)));
                continue;
            }
            if (WiredVariableAliasOperations.isAlias(context.room(), this.variableId)) {
                var mutation = WiredVariableAliasOperations.transformUnloaded(
                        context.room(),
                        this.variableId,
                        destination,
                        current -> WiredVariableActionMath.apply(
                                this.operation, current, operand));
                mutationDispatcher(context, this.variableId).accept(mutation);
                continue;
            }
            WiredVariableValue current = manager.get(this.variableId, destination);
            OptionalInt next = WiredVariableActionMath.apply(this.operation,
                    current == null ? 0 : current.value(), operand);
            if (next.isPresent()) {
                manager.setAndDispatch(this.variableId, destination, next.getAsInt(), mutationDispatcher(context));
            }
        }
    }

    private OptionalInt resolveOperand(WiredVariableManager manager, WiredContext context) {
        if (!WiredVariableActionMath.requiresOperand(this.operation)
                || this.operandMode == OPERAND_LITERAL) {
            return OptionalInt.of(this.literalValue);
        }
        if (this.operandTarget == TARGET_CONTEXT) {
            Integer value = WiredVariableAliasOperations.readContext(context, this.operandVariableId);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(
                        this.operandVariableId, this.operandTarget);
        if (internal != null) {
            return resolveInternalOperand(context, internal);
        }
        // July supplies one independent owner source for the operand. Resolve
        // multiple candidates deterministically by their stable holder order.
        for (WiredVariableHolder holder : resolveHolders(context, this.operandTarget, 1)) {
            WiredVariableValue value = WiredVariableAliasOperations.readValue(
                    context.room(), this.operandVariableId, holder);
            if (value != null) {
                return OptionalInt.of(value.value());
            }
        }
        return OptionalInt.empty();
    }

    private void executeInternal(
            WiredContext context,
            WiredInternalVariableRuntime.Definition definition,
            int operand) {
        Room room = context.room();
        if (definition.target() == TARGET_GLOBAL) {
            Integer current = WiredInternalVariableRuntime.globalValues(room)
                    .get(definition.name());
            if (current != null) {
                WiredVariableActionMath.apply(this.operation, current, operand)
                        .ifPresent(value -> {
                            WiredVariableHolder holder = WiredVariableHolder.room();
                            if (value != current && WiredInternalVariableRuntime.write(
                                    context, definition.variableId(), holder, value)) {
                                dispatchInternalMutation(
                                        context, definition, holder, current,
                                        WiredInternalVariableRuntime.read(room, definition.variableId(), holder));
                            }
                        });
            }
            return;
        }
        if (definition.target() == TARGET_FURNI) {
            var items = resolveFurniSource(context, getWiredFurniSourceTypes(), 0,
                    this.primaryItems, this.secondaryItems).stream()
                    .filter(item -> item != null
                            && item.getRoomId() == room.getId()
                            && room.getHabboItemByDatabaseId(item.getId()) == item)
                    .sorted(java.util.Comparator.comparingInt(item -> item.getId()))
                    .toList();
            context.budget().consumeTargets(items.size());
            for (var item : items) {
                WiredVariableHolder holder = WiredVariableHolder.furni(item.getId());
                Integer current = WiredInternalVariableRuntime.read(
                        room, definition.variableId(), holder);
                if (current != null) {
                    WiredVariableActionMath.apply(this.operation, current, operand)
                            .ifPresent(value -> {
                                if (value != current && WiredInternalVariableRuntime.write(
                                        context, definition.variableId(), holder, value)) {
                                    dispatchInternalMutation(
                                            context, definition, holder, current,
                                            WiredInternalVariableRuntime.read(room, definition.variableId(), holder));
                                }
                            });
                }
            }
            return;
        }
        List<RoomUnit> units = resolveUserSource(
                context, getWiredUserSourceTypes(), 0).stream()
                .filter(unit -> unit != null && unit.getRoom() == room
                        && unit.isInRoom() && room.getRoomUnits().contains(unit))
                .sorted(java.util.Comparator.comparingInt(RoomUnit::getId))
                .toList();
        context.budget().consumeTargets(units.size());
        for (RoomUnit unit : units) {
            Integer current = WiredInternalVariableRuntime.unitValues(room, unit)
                    .get(definition.name());
            if (current != null || definition.writable()) {
                Integer before = current;
                WiredVariableActionMath.apply(this.operation, current == null ? 0 : current, operand)
                        .ifPresent(value -> {
                            if (java.util.Objects.equals(value, before) || !WiredInternalVariableRuntime.writeUnit(
                                    context, unit, definition.name(), value)) {
                                return;
                            }
                            var habbo = room.getHabbo(unit);
                            int stableId = habbo != null && habbo.getHabboInfo() != null
                                    ? habbo.getHabboInfo().getId() : unit.getId();
                            dispatchInternalMutation(context, definition,
                                    WiredVariableHolder.user(stableId), before,
                                    WiredInternalVariableRuntime.unitValues(room, unit).get(definition.name()));
                        });
            }
        }
    }

    private void dispatchInternalMutation(
            WiredContext context,
            WiredInternalVariableRuntime.Definition definition,
            WiredVariableHolder holder,
            Integer before,
            Integer after) {
        var mutation = com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableObserver.transition(
                definition.variableId(), holder, before, after, this.getId(),
                WiredVariableMutation.CHANGE_ORIGIN_IN_ROOM);
        if (mutation == null) return;
        com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableObserver.record(context.room(), mutation);
        mutationDispatcher(context, this.variableId).accept(mutation);
    }

    private OptionalInt resolveInternalOperand(
            WiredContext context,
            WiredInternalVariableRuntime.Definition definition) {
        Room room = context.room();
        if (definition.target() == TARGET_GLOBAL) {
            Integer value = WiredInternalVariableRuntime.globalValues(room)
                    .get(definition.name());
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
        if (definition.target() == TARGET_FURNI) {
            return resolveFurniSource(context, getWiredFurniSourceTypes(), 1,
                    this.primaryItems, this.secondaryItems).stream()
                    .filter(item -> item != null
                            && item.getRoomId() == room.getId()
                            && room.getHabboItemByDatabaseId(item.getId()) == item)
                    .sorted(java.util.Comparator.comparingInt(item -> item.getId()))
                    .map(item -> WiredInternalVariableRuntime.furniValues(room, item)
                            .get(definition.name()))
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .findFirst();
        }
        return resolveUserSource(context, getWiredUserSourceTypes(), 1).stream()
                .filter(unit -> unit != null && unit.getRoom() == room
                        && unit.isInRoom() && room.getRoomUnits().contains(unit))
                .sorted(java.util.Comparator.comparingInt(RoomUnit::getId))
                .map(unit -> WiredInternalVariableRuntime.unitValues(room, unit)
                        .get(definition.name()))
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .findFirst();
    }

    @Override
    public String getWiredData() {
        return encode(encodeVariableId(this.variableId), encodeVariableId(this.operandVariableId),
                String.valueOf(this.target), String.valueOf(this.operation),
                String.valueOf(this.operandMode), String.valueOf(this.literalValue),
                String.valueOf(this.operandTarget), String.valueOf(getDelay()),
                encodeItemIds(this.primaryItems), encodeItemIds(this.secondaryItems));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        String[] fields = decode(set.getString("wired_data"), 11);
        if (fields == null) {
            return;
        }
        String loadedId = decodeVariableId(fields[1]);
        String loadedOperandId = decodeVariableId(fields[2]);
        try {
            int loadedTarget = Integer.parseInt(fields[3]);
            int loadedOperation = Integer.parseInt(fields[4]);
            int loadedMode = Integer.parseInt(fields[5]);
            int loadedLiteral = Integer.parseInt(fields[6]);
            int loadedOperandTarget = Integer.parseInt(fields[7]);
            int loadedDelay = Integer.parseInt(fields[8]);
            boolean requiresOperand = WiredVariableActionMath.requiresOperand(loadedOperation);
            if (loadedId == null || !WiredVariableActionMath.isImplementedOperation(loadedOperation)
                    || !validTarget(loadedTarget, true) || !validTarget(loadedOperandTarget, true)
                    || (loadedMode != OPERAND_LITERAL && loadedMode != OPERAND_VARIABLE)
                    || (requiresOperand && loadedMode == OPERAND_VARIABLE && loadedOperandId == null)
                    || (!requiresOperand && loadedMode != OPERAND_LITERAL)
                    || loadedDelay < 0 || loadedDelay > MAX_DELAY) {
                return;
            }
            this.variableId = loadedId;
            this.operandVariableId = requiresOperand && loadedMode == OPERAND_VARIABLE ? loadedOperandId : "";
            this.target = loadedTarget;
            this.operation = loadedOperation;
            this.operandMode = loadedMode;
            this.literalValue = loadedLiteral;
            this.operandTarget = loadedOperandTarget;
            this.setDelay(loadedDelay);
            decodeItemIds(room, fields[9], this.primaryItems);
            decodeItemIds(room, fields[10], this.secondaryItems);
        } catch (NumberFormatException ignored) {
            onPickUp();
        }
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {this.target, this.operation, this.operandMode,
                this.literalValue < 0 ? -1 : 0, this.literalValue, this.operandTarget};
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableId.isEmpty() ? new String[0]
                : new String[] {this.variableId, this.operandVariableId};
    }

    @Override
    void resetVariableConfiguration() {
        this.variableId = "";
        this.operandVariableId = "";
        this.target = TARGET_FURNI;
        this.operation = WiredVariableActionMath.ASSIGN;
        this.operandMode = OPERAND_LITERAL;
        this.literalValue = 0;
        this.operandTarget = TARGET_FURNI;
    }

    private static boolean validTarget(int target, boolean allowGlobal) {
        return target == TARGET_FURNI || target == TARGET_USER || target == TARGET_CONTEXT
                || (allowGlobal && target == TARGET_GLOBAL);
    }
}
