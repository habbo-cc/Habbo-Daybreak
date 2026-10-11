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

/** July AIR Core Variables action 40 ({@code wf_act_remove_var}). */
public final class WiredEffectRemoveVariable extends WiredEffectVariableBase {
    private String variableId = "";
    private int target = TARGET_FURNI;

    public WiredEffectRemoveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoveVariable(int id, int userId, Item item, String extradata, int limitedStack,
                                     int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.REMOVE_VARIABLE;
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
        if (settings.getIntParams() == null || settings.getIntParams().length != 1
                || settings.getVariableIds() == null || settings.getVariableIds().length != 1
                || settings.getFurniSourceTypes().length != 1 || settings.getUserSourceTypes().length != 1) {
            throw new WiredSaveException("Invalid Remove Variable data");
        }
        String requestedId = settings.getVariableIds()[0];
        int requestedTarget = settings.getIntParams()[0];
        validateOperation(room, requestedId, requestedTarget, false, true);
        saveSelections(room, settings.getFurniIds(), settings.getFurniIds2(), false);
        this.variableId = requestedId;
        this.target = requestedTarget;
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
            context.contextVariables().remove(this.variableId);
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
                alias.manager().removeValueAndDispatch(
                        alias.variableId(),
                        holder,
                        mutationDispatcher(context, this.variableId,
                                com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation
                                        .CHANGE_ORIGIN_ANOTHER_ROOM));
                continue;
            }
            if (WiredVariableAliasOperations.isAlias(context.room(), this.variableId)) {
                var mutation = WiredVariableAliasOperations.removeUnloaded(
                        context.room(), this.variableId, holder);
                mutationDispatcher(context, this.variableId).accept(mutation);
                continue;
            }
            manager.removeValueAndDispatch(this.variableId, holder, mutationDispatcher(context));
        }
    }

    @Override
    public String getWiredData() {
        return encode(encodeVariableId(this.variableId), String.valueOf(this.target),
                String.valueOf(getDelay()), encodeItemIds(this.primaryItems));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        String[] fields = decode(set.getString("wired_data"), 5);
        if (fields == null) {
            return;
        }
        String loadedId = decodeVariableId(fields[1]);
        try {
            int loadedTarget = Integer.parseInt(fields[2]);
            int loadedDelay = Integer.parseInt(fields[3]);
            if (loadedId == null || (loadedTarget != TARGET_FURNI && loadedTarget != TARGET_USER && loadedTarget != TARGET_CONTEXT)
                    || loadedDelay < 0 || loadedDelay > MAX_DELAY) {
                return;
            }
            this.variableId = loadedId;
            this.target = loadedTarget;
            this.setDelay(loadedDelay);
            decodeItemIds(room, fields[4], this.primaryItems);
        } catch (NumberFormatException ignored) {
            onPickUp();
        }
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {this.target};
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId};
    }

    @Override
    void resetVariableConfiguration() {
        this.variableId = "";
        this.target = TARGET_FURNI;
    }
}
