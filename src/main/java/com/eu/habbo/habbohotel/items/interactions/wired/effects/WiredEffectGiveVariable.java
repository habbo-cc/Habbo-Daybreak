package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** July AIR Core Variables action 39 ({@code wf_act_give_var}). */
public final class WiredEffectGiveVariable extends WiredEffectVariableBase {
    private String variableId = "";
    private int target = TARGET_FURNI;
    private int initialValue;
    private boolean overrideExisting;

    public WiredEffectGiveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveVariable(int id, int userId, Item item, String extradata, int limitedStack,
                                   int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.GIVE_VARIABLE;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 1;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        Room room = requireEditorRoom(settings, client);
        if (room == null) {
            return false;
        }
        if (settings.getIntParams() == null || settings.getIntParams().length != 4
                || settings.getVariableIds() == null || settings.getVariableIds().length != 1
                || settings.getFurniSourceTypes().length != 1 || settings.getUserSourceTypes().length != 1
                || !validSignedIntParts(settings.getIntParams()[1], settings.getIntParams()[2])
                || (settings.getIntParams()[3] != 0 && settings.getIntParams()[3] != 1)) {
            throw new WiredSaveException("Invalid Give Variable data");
        }
        String requestedId = settings.getVariableIds()[0];
        int requestedTarget = settings.getIntParams()[0];
        var definition = validateOperation(room, requestedId, requestedTarget, false, true);
        saveSelections(room, settings.getFurniIds(), settings.getFurniIds2(), false);
        this.variableId = requestedId;
        this.target = requestedTarget;
        this.initialValue = definition.hasValue() ? settings.getIntParams()[2] : 0;
        this.overrideExisting = settings.getIntParams()[3] == 1;
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || !WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || !canOperate(context.room(), this.variableId, this.target, true)) {
            return;
        }
        if (this.target == TARGET_CONTEXT) {
            if (this.overrideExisting
                    || !context.contextVariables().contains(this.variableId)) {
                context.contextVariables().set(this.variableId, this.initialValue);
            }
            return;
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null) {
            return;
        }
        List<WiredVariableHolder> holders = resolveHolders(context, this.target, 0);
        consumeTargets(context, holders);
        for (WiredVariableHolder holder : holders) {
            WiredVariableAliasOperations.Resolved resolved =
                    WiredVariableAliasOperations.resolve(
                            context.room(), this.variableId);
            if (resolved != null && resolved.readOnly()) {
                continue;
            }
            WiredVariableAliasOperations.MutationTarget alias =
                    WiredVariableAliasOperations.writable(
                            context.room(), this.variableId, holder);
            if (alias != null
                    && (alias.manager() != manager
                    || !alias.variableId().equals(this.variableId))) {
                if (this.overrideExisting) {
                    alias.manager().setAndDispatch(
                            alias.variableId(), holder, this.initialValue,
                            mutationDispatcher(context, this.variableId,
                                    com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation
                                            .CHANGE_ORIGIN_ANOTHER_ROOM));
                } else {
                    alias.manager().setIfAbsentAndDispatch(
                            alias.variableId(), holder, this.initialValue,
                            mutationDispatcher(context, this.variableId,
                                    com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation
                                            .CHANGE_ORIGIN_ANOTHER_ROOM));
                }
                continue;
            }
            if (WiredVariableAliasOperations.isAlias(context.room(), this.variableId)) {
                var mutation = WiredVariableAliasOperations.setUnloaded(
                        context.room(),
                        this.variableId,
                        holder,
                        this.initialValue,
                        !this.overrideExisting);
                mutationDispatcher(context, this.variableId).accept(mutation);
                continue;
            }
            if (this.overrideExisting) {
                manager.setAndDispatch(this.variableId, holder, this.initialValue, mutationDispatcher(context));
            } else {
                manager.setIfAbsentAndDispatch(this.variableId, holder, this.initialValue,
                        mutationDispatcher(context));
            }
        }
    }

    @Override
    public String getWiredData() {
        return encode(encodeVariableId(this.variableId), String.valueOf(this.target),
                String.valueOf(this.initialValue), this.overrideExisting ? "1" : "0",
                String.valueOf(getDelay()), encodeItemIds(this.primaryItems));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        String[] fields = decode(set.getString("wired_data"), 7);
        if (fields == null) {
            return;
        }
        String loadedId = decodeVariableId(fields[1]);
        try {
            int loadedTarget = Integer.parseInt(fields[2]);
            int loadedValue = Integer.parseInt(fields[3]);
            int loadedOverride = Integer.parseInt(fields[4]);
            int loadedDelay = Integer.parseInt(fields[5]);
            if (loadedId == null || (loadedTarget != TARGET_FURNI && loadedTarget != TARGET_USER && loadedTarget != TARGET_CONTEXT)
                    || (loadedOverride != 0 && loadedOverride != 1)
                    || loadedDelay < 0 || loadedDelay > MAX_DELAY) {
                return;
            }
            this.variableId = loadedId;
            this.target = loadedTarget;
            this.initialValue = loadedValue;
            this.overrideExisting = loadedOverride == 1;
            this.setDelay(loadedDelay);
            decodeItemIds(room, fields[6], this.primaryItems);
        } catch (NumberFormatException ignored) {
            onPickUp();
        }
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {this.target, this.initialValue < 0 ? -1 : 0, this.initialValue,
                this.overrideExisting ? 1 : 0};
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId};
    }

    @Override
    void resetVariableConfiguration() {
        this.variableId = "";
        this.target = TARGET_FURNI;
        this.initialValue = 0;
        this.overrideExisting = false;
    }
}
