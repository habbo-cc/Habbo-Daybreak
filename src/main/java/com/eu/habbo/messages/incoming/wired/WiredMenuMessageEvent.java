package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuPreferences;
import com.eu.habbo.habbohotel.wired.menu.WiredRoomMonitor;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.wired.WiredMenuMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomForwardMessageComposer;
import com.eu.habbo.messages.outgoing.users.AccountPreferencesMessageComposer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * Authenticated room boundary for July's Wired Menu protocol. The numeric
 * headers are NGH-local, while field order follows the July AIR client.
 */
public final class WiredMenuMessageEvent extends MessageHandler {
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public int getRatelimit() {
        return 150;
    }

    @Override
    public Object getRatelimitKey(int messageId) {
        return messageId;
    }

    @Override
    public void handle() {
        Context context = resolve();
        if (context == null) {
            return;
        }
        permissions(context);
        if (!context.read) {
            return;
        }
        switch (this.packet.getMessageId()) {
            case Incoming.WiredMenuRequestVariableHoldersMessageEvent -> holders(context);
            case Incoming.WiredMenuClearErrorsMessageEvent -> clearErrors(context);
            case Incoming.WiredMenuUpdatePreferencesMessageEvent -> preferences(context);
            case Incoming.WiredMenuRequestRoomStatsMessageEvent -> stats(context);
            case Incoming.WiredMenuUpdateRoomSettingsMessageEvent -> updateSettings(context);
            case Incoming.WiredMenuInspectObjectMessageEvent -> inspect(context);
            case Incoming.WiredMenuRequestErrorsMessageEvent -> errors(context);
            case Incoming.WiredMenuReloadOrRollbackMessageEvent -> reloadOrRollback(context);
            case Incoming.WiredMenuRequestRoomSettingsMessageEvent -> settings(context);
            case Incoming.WiredMenuModifyVariableMessageEvent -> modify(context, false);
            case Incoming.WiredMenuRequestLogsMessageEvent -> logs(context);
            case Incoming.WiredMenuRequestUserVariablesMessageEvent -> userVariables(context);
            case Incoming.WiredMenuMutatePermanentVariableMessageEvent -> modify(context, true);
            case Incoming.WiredMenuRequestPermanentVariablesMessageEvent ->
                    permanentVariables(context);
            default -> throw new MalformedPacketException("unknown Wired Menu request");
        }
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected trailing Wired Menu payload");
        }
    }

    private Context resolve() {
        if (this.client == null || this.client.getHabbo() == null
                || this.client.getHabbo().getHabboInfo() == null) {
            return null;
        }
        Habbo habbo = this.client.getHabbo();
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getRoomUnit() == null || !habbo.getRoomUnit().isInRoom()
                || !this.client.getWiredCapabilityState().supportsRoom(
                WiredCapabilityService.CAPABILITY_WIRED_MENU, room.getId())) {
            return null;
        }
        boolean ownerOrStaff = room.getOwnerId() == habbo.getHabboInfo().getId()
                || habbo.hasPermission(Permission.ACC_SUPERWIRED);
        WiredMenuSettings settings = WiredMenuSettings.load(room.getId());
        boolean write = settings.canModify(room, habbo);
        boolean read = settings.canRead(room, habbo);
        return new Context(room, ownerOrStaff, write, read);
    }

    private void permissions(Context context) {
        send(Outgoing.WiredMenuPermissionsMessageComposer, response -> {
            response.appendBoolean(context.write);
            response.appendBoolean(context.read);
        });
    }

    private void settings(Context context) {
        requireEmpty();
        this.client.sendResponse(new AccountPreferencesMessageComposer(
                this.client.getHabbo()));
        sendSettings(WiredMenuSettings.load(context.room.getId()));
    }

    private void updateSettings(Context context) {
        WiredMenuSettings value = new WiredMenuSettings(
                this.packet.readRequiredInt(),
                this.packet.readRequiredInt(),
                this.packet.readBoundedString(64));
        if (context.ownerOrStaff && value.save(context.room.getId())) {
            sendSettings(value);
        }
    }

    private void sendSettings(WiredMenuSettings value) {
        send(Outgoing.WiredMenuRoomSettingsMessageComposer, response -> {
            response.appendInt(value.modifyMask());
            response.appendInt(value.readMask());
            response.appendString(value.timezone());
        });
    }

    private void stats(Context context) {
        requireEmpty();
        double executionCost = WiredManager.getUsageTracker().getCurrentUsage(context.room);
        double executionCap = WiredManager.getUsageTracker().getUsageLimit();
        WiredVariableManager manager =
                context.room.getRoomSpecialTypes().getWiredVariableManager();
        WiredVariableManager.Snapshot variables = manager == null
                ? null : manager.publicSnapshot();
        int permanentFurni = countPermanent(variables, WiredVariableType.FURNI);
        int permanentUsers = countPermanent(variables, WiredVariableType.USER);
        int permanentGlobals = countPermanent(variables, WiredVariableType.ROOM);
        send(Outgoing.WiredMenuRoomStatsMessageComposer, response -> {
            response.appendDouble(executionCost);
            response.appendDouble(executionCap);
            response.appendBoolean(WiredManager.getUsageTracker().isHeavy(context.room));
            response.appendInt(context.room.getFloorItems().size());
            response.appendInt(Emulator.getConfig().getInt("hotel.room.furni.max", 2500));
            response.appendInt(context.room.getWallItems().size());
            response.appendInt(Emulator.getConfig().getInt("hotel.room.wallfurni.max", 2500));
            response.appendInt(permanentFurni);
            response.appendInt(Emulator.getConfig().getInt("hotel.room.furni.variable.max", 100));
            response.appendInt(permanentUsers);
            response.appendInt(Emulator.getConfig().getInt("hotel.room.user.variable.max", 100));
            response.appendInt(permanentGlobals);
            response.appendInt(Emulator.getConfig().getInt("hotel.room.global.variable.max", 100));
        });
    }

    private int countPermanent(WiredVariableManager.Snapshot snapshot,
                               WiredVariableType type) {
        if (snapshot == null || !snapshot.available()) {
            return 0;
        }
        return (int) snapshot.variables().values().stream()
                .map(WiredVariableManager.VariableSnapshot::definition)
                .filter(definition -> definition.type() == type
                        && definition.persistsValues())
                .count();
    }

    private void errors(Context context) {
        requireEmpty();
        List<WiredRoomMonitor.ErrorEntry> errors = WiredRoomMonitor.errors(context.room);
        send(Outgoing.WiredMenuErrorsMessageComposer, response -> {
            response.appendInt(errors.size());
            for (WiredRoomMonitor.ErrorEntry error : errors) {
                response.appendInt(error.id());
                response.appendString(error.name());
                response.appendString(error.category());
                response.appendInt(error.count());
                response.appendLong(error.msSinceLastOccurrence());
            }
        });
    }

    private void clearErrors(Context context) {
        requireEmpty();
        if (context.write) {
            WiredRoomMonitor.clearErrors(context.room);
            errors(context);
        }
    }

    private void inspect(Context context) {
        int type = this.packet.readRequiredInt();
        int visibleId = this.packet.readRequiredInt();
        InspectionTarget target = inspectionTarget(context, type, visibleId);
        if (target == null) {
            sendInspectionError(0);
            return;
        }
        sendInspection(context, target);
    }

    private void sendInspection(Context context, InspectionTarget target) {
        Map<String, Value> merged = new LinkedHashMap<>();
        if (target.holder() != null) {
            for (Value value : values(context, target.holder())) {
                merged.put(value.variableId(), value);
            }
        }
        Map<String, Integer> internal = target.item() != null
                ? WiredInternalVariableRuntime.inspectionValues(
                        WiredInternalVariableRuntime.TARGET_FURNI,
                        WiredInternalVariableRuntime.furniValues(
                                context.room, target.item()))
                : target.unit() != null
                ? WiredInternalVariableRuntime.inspectionValues(
                        WiredInternalVariableRuntime.TARGET_USER,
                        WiredInternalVariableRuntime.unitValues(
                                context.room, target.unit()))
                : WiredInternalVariableRuntime.inspectionValues(
                        WiredInternalVariableRuntime.TARGET_GLOBAL,
                        WiredInternalVariableRuntime.globalValues(context.room));
        internal.forEach((id, value) ->
                merged.put(id, new Value(id, value, 0L, 0L)));
        List<Value> inspectionValues = merged.values().stream()
                .sorted(Comparator.comparing(Value::variableId)).toList();
        List<Integer> references = target.item() == null
                ? List.of() : configuredWiredReferences(context.room, target.item());
        send(Outgoing.WiredMenuObjectInspectionMessageComposer, response -> {
            response.appendInt(target.type());
            // July only includes an object identifier for furni and users.
            // Global inspection continues directly with the value count.
            if (target.type() == WiredInternalVariableRuntime.TARGET_FURNI
                    || target.type() == WiredInternalVariableRuntime.TARGET_USER) {
                response.appendInt(target.visibleId());
            }
            response.appendInt(inspectionValues.size());
            for (Value value : inspectionValues) {
                response.appendString(value.variableId());
                response.appendInt(value.value());
            }
            if (target.type() == 0) {
                response.appendInt(references.size());
                for (int wiredId : references) {
                    response.appendInt(wiredId);
                }
            }
        });
    }

    private void holders(Context context) {
        String variableId = this.packet.readBoundedString(255);
        WiredInternalVariableRuntime.Definition internal =
                internalDefinition(variableId);
        if (internal != null) {
            internalHolders(context, internal);
            return;
        }
        WiredVariableManager.VariableSnapshot variable = variable(context, variableId);
        if (variable == null) {
            return;
        }
        List<WiredVariableValue> visibleValues = authoritativeValues(context, variableId, variable).stream()
                .filter(value -> value.holder().scope()
                        == WiredVariableHolder.Scope.ROOM
                        || visibleHolderId(context, value.holder()) != 0)
                .toList();
        send(Outgoing.WiredMenuVariableHoldersMessageComposer, response -> {
            response.appendInt(0);
            WiredVariableMetadata.fromDefinition(
                    context.room, variable.definition()).serialize(response);
            response.appendInt(visibleValues.size());
            for (WiredVariableValue value : visibleValues) {
                response.appendInt(visibleHolderId(context, value.holder()));
                response.appendInt(value.value());
            }
        });
    }

    private List<WiredVariableValue> authoritativeValues(Context context, String id,
            WiredVariableManager.VariableSnapshot variable) {
        var source = WiredVariableAliasOperations.resolve(context.room, id);
        if (source == null) return List.of();
        var candidates = new java.util.LinkedHashSet<WiredVariableHolder>();
        var stored = source.manager().variableSnapshot(source.variableId());
        if (stored != null) stored.values().forEach(value -> candidates.add(value.holder()));
        else {
            var parent = com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime.parentDefinition(
                    WiredVariableAliasOperations.sourceRoom(source), source.variableId());
            var parentValues = parent == null ? null : source.manager().variableSnapshot(parent.variableId());
            if (parentValues != null) parentValues.values().forEach(value -> candidates.add(value.holder()));
            else if (variable.definition().type() == WiredVariableType.ROOM) candidates.add(WiredVariableHolder.room());
            else if (variable.definition().type() == WiredVariableType.FURNI) {
                for (var item : context.room.getFloorItems()) candidates.add(WiredVariableHolder.furni(item.getId()));
                for (var item : context.room.getWallItems()) candidates.add(WiredVariableHolder.furni(item.getId()));
            } else if (variable.definition().type() == WiredVariableType.USER)
                context.room.getCurrentHabbos().values().forEach(habbo ->
                        candidates.add(WiredVariableHolder.user(habbo.getHabboInfo().getId())));
        }
        var result = new ArrayList<WiredVariableValue>();
        for (var holder : candidates) {
            var value = WiredVariableAliasOperations.readValue(context.room, id, holder);
            if (value != null) result.add(value);
        }
        return result;
    }

    private void internalHolders(
            Context context, WiredInternalVariableRuntime.Definition definition) {
        List<HolderValue> values = new ArrayList<>();
        if (definition.target() == WiredInternalVariableRuntime.TARGET_FURNI) {
            Collection<HabboItem> all = new ArrayList<>(context.room.getFloorItems());
            all.addAll(context.room.getWallItems());
            all.stream().filter(item -> item != null && item.getBaseItem() != null)
                    .sorted(Comparator.comparingInt(HabboItem::getId))
                    .limit(1_000)
                    .forEach(item -> {
                        Integer value = WiredInternalVariableRuntime
                                .furniValues(context.room, item)
                                .get(definition.name());
                        if (value != null) {
                            int visibleId = item.getBaseItem().getType() == FurnitureType.WALL
                                    ? -Math.abs(item.getRoomVisibleId())
                                    : item.getRoomVisibleId();
                            values.add(new HolderValue(visibleId, value));
                        }
                    });
        } else if (definition.target() == WiredInternalVariableRuntime.TARGET_USER) {
            context.room.getRoomUnits().stream()
                    .filter(unit -> unit != null && unit.isInRoom())
                    .sorted(Comparator.comparingInt(RoomUnit::getId))
                    .limit(1_000)
                    .forEach(unit -> {
                        Integer value = WiredInternalVariableRuntime
                                .unitValues(context.room, unit)
                                .get(definition.name());
                        if (value != null) {
                            values.add(new HolderValue(unit.getId(), value));
                        }
                    });
        } else if (definition.target()
                == WiredInternalVariableRuntime.TARGET_GLOBAL) {
            Integer value = WiredInternalVariableRuntime.globalValues(context.room)
                    .get(definition.name());
            if (value != null) {
                values.add(new HolderValue(0, value));
            }
        }
        send(Outgoing.WiredMenuVariableHoldersMessageComposer, response -> {
            response.appendInt(0);
            WiredVariableMetadata.internal(definition).serialize(response);
            response.appendInt(values.size());
            for (HolderValue value : values) {
                response.appendInt(value.visibleId());
                response.appendInt(value.value());
            }
        });
    }

    private int visibleHolderId(Context context, WiredVariableHolder holder) {
        if (holder.scope() == WiredVariableHolder.Scope.USER) {
            Habbo habbo = context.room.getHabbo(holder.stableId());
            return habbo == null || habbo.getRoomUnit() == null
                    ? 0 : habbo.getRoomUnit().getId();
        }
        if (holder.scope() == WiredVariableHolder.Scope.FURNI) {
            HabboItem item =
                    context.room.getHabboItemByDatabaseId(holder.stableId());
            if (item == null || item.getBaseItem() == null) {
                return 0;
            }
            return item.getBaseItem().getType() == FurnitureType.WALL
                    ? -Math.abs(item.getRoomVisibleId())
                    : item.getRoomVisibleId();
        }
        return 0;
    }

    private void modify(Context context, boolean permanent) {
        int type = this.packet.readRequiredInt();
        int visibleId = this.packet.readRequiredInt();
        String variableId = this.packet.readBoundedString(255);
        int value = this.packet.readRequiredInt();
        int operation = this.packet.readRequiredInt();
        InspectionTarget target = permanent
                ? null : inspectionTarget(context, type, visibleId);
        WiredVariableHolder holder = permanent
                ? permanentHolder(type, visibleId)
                : target == null ? null : target.holder();
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(variableId, type);
        if (internal == null) {
            var alias = WiredVariableAliasOperations.resolve(context.room, variableId);
            if (alias != null && !alias.readOnly()) internal =
                    WiredInternalVariableRuntime.definition(alias.variableId(), type);
        }
        boolean internalSuccess = context.write && !permanent && target != null
                && internal != null && operation == 0
                && writeInternal(context, target, internal, variableId, value);
        WiredVariableManager.MutationResult result =
                internal == null && context.write && holder != null
                        ? mutate(context, holder, variableId, value, operation, permanent)
                        : WiredVariableManager.MutationResult.INVALID;
        boolean success = internalSuccess || mutationSucceeded(result);
        if (permanent) {
            send(Outgoing.WiredMenuPermanentMutationResultMessageComposer,
                    response -> response.appendBoolean(success));
        } else if (success && target != null) {
            sendInspection(context, target);
        } else {
            sendInspectionError(target == null ? 0 : 2);
        }
    }

    private boolean writeInternal(
            Context context, InspectionTarget target,
            WiredInternalVariableRuntime.Definition definition, String exposedId, int value) {
        if (!definition.writable()) {
            return false;
        }
        Integer before = target.item() != null
                ? WiredInternalVariableRuntime.furniValues(
                        context.room, target.item()).get(definition.name())
                : target.unit() != null
                ? WiredInternalVariableRuntime.unitValues(
                        context.room, target.unit()).get(definition.name())
                : WiredInternalVariableRuntime.globalValues(
                        context.room).get(definition.name());
        WiredVariableHolder eventHolder = target.holder();
        if (eventHolder == null && target.unit() != null) {
            eventHolder = WiredVariableHolder.user(target.unit().getId());
        }
        boolean written;
        if (target.holder() != null) {
            written = WiredInternalVariableRuntime.write(
                    context.room, definition.variableId(), target.holder(), value);
        } else {
            written = target.unit() != null
                    && definition.target() == WiredInternalVariableRuntime.TARGET_USER
                    && WiredInternalVariableRuntime.writeUnit(
                            context.room, target.unit(), definition.name(), value);
        }
        if (written && eventHolder != null) {
            Integer after = target.item() != null
                    ? WiredInternalVariableRuntime.furniValues(context.room, target.item()).get(definition.name())
                    : target.unit() != null ? WiredInternalVariableRuntime.unitValues(context.room, target.unit()).get(definition.name())
                    : WiredInternalVariableRuntime.globalValues(context.room).get(definition.name());
            var mutation = com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableObserver.transition(
                    definition.variableId(), eventHolder, before, after, 0,
                    WiredVariableMutation.CHANGE_ORIGIN_CREATOR_TOOL);
            if (mutation != null) {
                com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableObserver.record(context.room, mutation);
                WiredManager.triggerVariableChanged(context.room, this.client.getHabbo().getRoomUnit(),
                        new WiredVariableMutation(exposedId, eventHolder, before, after,
                                mutation.kind(), mutation.revision(), 0, mutation.changeOrigin()));
            }
        }
        return written;
    }

    private WiredVariableManager.MutationResult mutate(
            Context context, WiredVariableHolder holder,
            String variableId, int value, int operation, boolean permanentOnly) {
        return com.eu.habbo.habbohotel.wired.variables.WiredVariableMenuMutation.apply(
                context.room, holder, variableId, value, operation, permanentOnly,
                mutation -> WiredManager.triggerVariableChanged(
                        context.room, this.client.getHabbo().getRoomUnit(), mutation));
    }

    private boolean mutationSucceeded(WiredVariableManager.MutationResult result) {
        return result == WiredVariableManager.MutationResult.CREATED
                || result == WiredVariableManager.MutationResult.CHANGED
                || result == WiredVariableManager.MutationResult.REMOVED
                || result == WiredVariableManager.MutationResult.UNCHANGED;
    }

    private void sendInspectionError(int code) {
        send(Outgoing.WiredMenuErrorMessageComposer,
                response -> response.appendShort(Math.max(0, Math.min(2, code))));
    }

    private void logs(Context context) {
        int page = page(this.packet.readRequiredInt());
        int amount = amount(this.packet.readRequiredInt());
        int level = boundedLogLevel(this.packet.readRequiredInt());
        int source = boundedLogSource(this.packet.readRequiredInt());
        String query = this.packet.readBoundedString(64);
        WiredRoomMonitor.LogPage result =
                WiredRoomMonitor.logs(context.room, page, amount, level, source, query);
        send(Outgoing.WiredMenuLogsPageMessageComposer, response -> {
            response.appendInt(result.total());
            response.appendInt(page);
            response.appendInt(amount);
            response.appendInt(result.entries().size());
            for (WiredRoomMonitor.LogEntry entry : result.entries()) {
                response.appendLong(entry.id());
                response.appendByte(entry.level());
                response.appendByte(entry.source());
                response.appendString(entry.message());
                response.appendLong(entry.timestamp());
                response.appendString(entry.timestampText());
            }
            response.appendBoolean(level >= 0);
            if (level >= 0) {
                response.appendByte(level);
            }
            response.appendBoolean(source >= 0);
            if (source >= 0) {
                response.appendByte(source);
            }
            response.appendBoolean(!query.isEmpty());
            if (!query.isEmpty()) {
                response.appendString(query);
            }
        });
    }

    private void userVariables(Context context) {
        String variableId = this.packet.readBoundedString(255);
        int page = page(this.packet.readRequiredInt());
        int amount = amount(this.packet.readRequiredInt());
        int sortFilter = boundedUserVariableSort(this.packet.readRequiredInt());
        int userFilter = boundedUserTypeFilter(this.packet.readRequiredInt());
        WiredVariableManager.VariableSnapshot variable = variable(context, variableId);
        List<WiredVariableValue> all = variable == null
                || variable.definition().type() != WiredVariableType.USER
                || !variable.definition().persistsValues()
                || (userFilter != -1 && userFilter != 1) ? List.of()
                : variable.values().stream()
                .filter(value -> value.holder().scope() == WiredVariableHolder.Scope.USER)
                .sorted(userVariableComparator(sortFilter))
                .toList();
        int from = Math.min(all.size(), (page - 1) * amount);
        List<WiredVariableValue> result =
                all.subList(from, Math.min(all.size(), from + amount));
        send(Outgoing.WiredMenuUserVariablesPageMessageComposer, response -> {
            response.appendString(variableId);
            response.appendInt(all.size());
            response.appendInt(page);
            response.appendInt(amount);
            response.appendInt(result.size());
            for (WiredVariableValue value : result) {
                HabboInfo user = Emulator.getGameEnvironment().getHabboManager()
                        .getHabboInfo(value.holder().stableId());
                response.appendInt(1);
                response.appendInt(value.holder().stableId());
                response.appendString(user == null ? "" : user.getUsername());
                appendStorage(response, value, false);
            }
            response.appendInt(userFilter);
            response.appendInt(sortFilter);
        });
    }

    private void permanentVariables(Context context) {
        int type = this.packet.readRequiredInt();
        int visibleId = this.packet.readRequiredInt();
        WiredVariableHolder holder = permanentHolder(type, visibleId);
        if (holder == null) {
            return;
        }
        HabboInfo user = type == 1
                ? Emulator.getGameEnvironment().getHabboManager()
                    .getHabboInfo(visibleId)
                : null;
        if (type == 1 && user == null) {
            return;
        }
        List<Value> values = values(context, holder, true);
        send(Outgoing.WiredMenuPermanentVariablesMessageComposer, response -> {
            response.appendInt(type);
            response.appendInt(visibleId);
            response.appendString(user == null ? "" : user.getUsername());
            response.appendString(user == null ? "" : user.getLook());
            if (type != 1) {
                response.appendInt(context.room.getOwnerId());
                response.appendString(context.room.getOwnerName());
                response.appendString("");
            }
            response.appendInt(values.size());
            for (Value value : values) {
                response.appendString(value.variableId());
                response.appendInt(value.value());
                response.appendLong(value.createdAt());
                response.appendString(formatTimestamp(value.createdAt()));
                response.appendLong(value.updatedAt());
                response.appendString(formatTimestamp(value.updatedAt()));
            }
        });
    }

    private void preferences(Context context) {
        boolean menuButton = this.packet.readRequiredBoolean();
        boolean inspectButton = this.packet.readRequiredBoolean();
        boolean playtest = this.packet.readRequiredBoolean();
        this.packet.readRequiredInt();
        boolean whisperDisabled = this.packet.readRequiredBoolean();
        boolean allNotifications = this.packet.readRequiredBoolean();
        String style = this.packet.readBoundedString(32);
        new WiredMenuPreferences(menuButton, inspectButton, playtest,
                whisperDisabled, allNotifications, style).save(
                this.client.getHabbo().getHabboInfo().getId());
    }

    private void reloadOrRollback(Context context) {
        boolean rollback = this.packet.readRequiredBoolean();
        if (rollback) {
            if (!context.ownerOrStaff) {
                return;
            }
            context.room.rollbackFurniLoadSnapshot();
            stats(context);
            return;
        }
        if (!context.write) {
            return;
        }
        int roomId = context.room.getId();
        Collection<Habbo> occupants =
                new ArrayList<>(context.room.getCurrentHabbos().values());
        Emulator.getThreading().run(() -> {
            Room loaded = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
            if (loaded == null) {
                return;
            }
            Emulator.getGameEnvironment().getRoomManager().unloadRoom(loaded);
            loaded = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
            if (loaded == null) {
                return;
            }
            ServerMessage forward = new RoomForwardMessageComposer(roomId).compose();
            for (Habbo habbo : occupants) {
                if (habbo != null && habbo.getClient() != null) {
                    habbo.getClient().sendResponse(forward);
                }
            }
        }, 100);
    }

    private WiredVariableManager.VariableSnapshot variable(Context context, String id) {
        WiredVariableManager manager =
                context.room.getRoomSpecialTypes().getWiredVariableManager();
        var value = manager == null ? null : manager.publicSnapshot().variables().get(id);
        return value != null && !WiredVariableMetadata.fromDefinition(context.room, value.definition()).invisible()
                ? value : null;
    }

    private List<Value> values(Context context, WiredVariableHolder holder) {
        return values(context, holder, false);
    }

    private List<Value> values(Context context, WiredVariableHolder holder,
                               boolean permanentOnly) {
        WiredVariableManager manager =
                context.room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null) {
            return List.of();
        }
        List<Value> result = new ArrayList<>();
        for (WiredVariableManager.VariableSnapshot variable :
                manager.publicSnapshot().variables().values()) {
            if (WiredVariableMetadata.fromDefinition(context.room, variable.definition()).invisible()) continue;
            if (permanentOnly && !variable.definition().persistsValues()) {
                continue;
            }
            var value = WiredVariableAliasOperations.readValue(context.room,
                    variable.definition().variableId(), holder);
            if (value != null) result.add(new Value(variable.definition().variableId(), value.value(),
                    value.createdAtMs(), value.updatedAtMs()));
        }
        result.sort(Comparator.comparing(Value::variableId));
        return List.copyOf(result);
    }

    private InspectionTarget inspectionTarget(
            Context context, int type, int visibleId) {
        if (context == null) {
            return null;
        }
        if (type == WiredInternalVariableRuntime.TARGET_FURNI) {
            int itemVisibleId = visibleId == Integer.MIN_VALUE
                    ? 0 : Math.abs(visibleId);
            HabboItem item = context.room.getHabboItem(itemVisibleId);
            return item == null ? null : new InspectionTarget(
                    type, visibleId, WiredVariableHolder.furni(item.getId()),
                    item, null);
        }
        if (type == WiredInternalVariableRuntime.TARGET_USER) {
            RoomUnit unit = null;
            for (RoomUnit candidate : context.room.getRoomUnits()) {
                if (candidate != null && candidate.getId() == visibleId
                        && candidate.isInRoom()) {
                    unit = candidate;
                    break;
                }
            }
            if (unit == null) {
                return null;
            }
            Habbo habbo = context.room.getHabbo(unit);
            WiredVariableHolder holder = habbo == null
                    || habbo.getHabboInfo() == null ? null
                    : WiredVariableHolder.user(habbo.getHabboInfo().getId());
            return new InspectionTarget(type, visibleId, holder, null, unit);
        }
        if (type == WiredInternalVariableRuntime.TARGET_GLOBAL && visibleId == 0) {
            return new InspectionTarget(type, visibleId,
                    WiredVariableHolder.room(), null, null);
        }
        return null;
    }

    private List<Integer> configuredWiredReferences(Room room, HabboItem inspected) {
        if (room == null || inspected == null) {
            return List.of();
        }
        return room.getFloorItems().stream()
                .filter(InteractionWired.class::isInstance)
                .map(InteractionWired.class::cast)
                .filter(wired -> wired.referencesConfiguredFurni(inspected.getId()))
                .sorted(Comparator.comparingInt(InteractionWired::getId))
                .limit(1_000)
                .map(InteractionWired::getRoomVisibleId)
                .toList();
    }

    private WiredInternalVariableRuntime.Definition internalDefinition(String id) {
        if (id == null) {
            return null;
        }
        for (WiredInternalVariableRuntime.Definition definition
                : WiredInternalVariableRuntime.definitions()) {
            if (definition.variableId().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    private WiredVariableHolder permanentHolder(int type, int stableId) {
        if (type == 1 && stableId > 0
                && Emulator.getGameEnvironment().getHabboManager()
                    .getHabboInfo(stableId) != null) {
            return WiredVariableHolder.user(stableId);
        }
        return null;
    }

    private void appendStorage(com.eu.habbo.messages.ServerMessage response,
                               WiredVariableValue value, boolean includeId) {
        if (includeId) {
            response.appendString(value.variableId());
        }
        response.appendInt(value.value());
        response.appendLong(value.createdAtMs());
        response.appendString(formatTimestamp(value.createdAtMs()));
        response.appendLong(value.updatedAtMs());
        response.appendString(formatTimestamp(value.updatedAtMs()));
    }

    private String formatTimestamp(long timestamp) {
        return timestamp <= 0L ? "" : Instant.ofEpochMilli(timestamp).toString();
    }

    private int page(int value) {
        return Math.max(1, Math.min(100_000, value));
    }

    private int amount(int value) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, value));
    }

    private int boundedLogLevel(int value) {
        return value >= WiredRoomMonitor.LEVEL_INFO
                && value <= WiredRoomMonitor.LEVEL_DEBUG ? value : -1;
    }

    private int boundedLogSource(int value) {
        return value == WiredRoomMonitor.SOURCE_SYSTEM
                || value == WiredRoomMonitor.SOURCE_WIRED ? value : -1;
    }

    private int boundedUserTypeFilter(int value) {
        return value == 1 || value == 2 || value == 4 ? value : -1;
    }

    private int boundedUserVariableSort(int value) {
        return value >= 0 && value <= 5 ? value : 0;
    }

    private Comparator<WiredVariableValue> userVariableComparator(int sort) {
        Comparator<WiredVariableValue> comparator = switch (sort) {
            case 1 -> Comparator.comparingInt(WiredVariableValue::value);
            case 2 -> Comparator.comparingLong(WiredVariableValue::createdAtMs);
            case 3 -> Comparator.comparingLong(WiredVariableValue::createdAtMs).reversed();
            case 4 -> Comparator.comparingLong(WiredVariableValue::updatedAtMs);
            case 5 -> Comparator.comparingLong(WiredVariableValue::updatedAtMs).reversed();
            default -> Comparator.comparingInt(WiredVariableValue::value).reversed();
        };
        return comparator.thenComparingInt(value -> value.holder().stableId());
    }

    private void requireEmpty() {
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("expected empty Wired Menu request");
        }
    }

    private void send(int header,
                      java.util.function.Consumer<com.eu.habbo.messages.ServerMessage> payload) {
        this.client.sendResponse(new WiredMenuMessageComposer(header, payload));
    }

    private record Context(Room room, boolean ownerOrStaff, boolean write, boolean read) {
    }

    private record Value(String variableId, int value,
                         long createdAt, long updatedAt) {
    }

    private record HolderValue(int visibleId, int value) {
    }

    private record InspectionTarget(
            int type, int visibleId, WiredVariableHolder holder,
            HabboItem item, RoomUnit unit) {
    }
}
