package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Exact July four-int/one-variable foundation for conditions 45 and 46. */
abstract class WiredConditionChestBase extends InteractionWiredCondition {
    private static final int VERSION = 1;

    protected int amount;
    protected int referenceMode;
    protected int referenceTarget;
    protected int comparison = 1;
    protected String variableId = "";
    protected int quantifier;
    protected final List<HabboItem> selected = new ArrayList<>();
    protected final List<HabboItem> selected2 = new ArrayList<>();

    WiredConditionChestBase(ResultSet set, Item item) throws SQLException { super(set, item); }
    WiredConditionChestBase(int id, int userId, Item item, String extraData,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }

    protected abstract int furniSlots();
    protected abstract boolean validSelections(ChestManager manager);
    protected abstract List<HabboItem> resolvedChests(WiredContext context, ChestManager manager);
    protected abstract long valueFor(ChestManager manager, HabboItem chest, WiredContext context);

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings == null ? null : settings.getIntParams();
        if (params == null || params.length != 4 || params[0] < 0 || params[0] > 1_000_000
                || (params[1] != 0 && params[1] != 1)
                || !ChestWiredSupport.validTarget(params[2])
                || params[3] < 0 || params[3] > 5
                || settings.getVariableIds().length != 1
                || (params[1] == 1 && settings.getVariableIds()[0].isBlank())
                || !settings.getStringParam().isEmpty()
                || settings.getFurniSourceTypes().length != furniSlots()
                || settings.getUserSourceTypes().length != 1
                || settings.getDelay() != 0) {
            return false;
        }
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null) return false;
        this.selected.clear();
        this.selected.addAll(load(room, settings.getFurniIds(), false));
        this.selected2.clear();
        this.selected2.addAll(load(room, settings.getFurniIds2(), false));
        if (!validSelections(Emulator.getGameEnvironment().getChestManager())) {
            onPickUp();
            return false;
        }
        this.amount = params[0];
        this.referenceMode = params[1];
        this.referenceTarget = params[2];
        this.comparison = params[3];
        this.variableId = params[1] == 1 ? settings.getVariableIds()[0] : "";
        this.quantifier = settings.getQuantifierCode() == 1 ? 1 : 0;
        setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
        return true;
    }

    @Override
    public boolean evaluate(WiredContext context) {
        if (context == null || !WiredFeatureCapabilityGuard.isRuntimeReady(this)) {
            return false;
        }
        ChestManager manager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> chests = resolvedChests(context, manager);
        if (chests.isEmpty()) return false;
        long reference = this.amount;
        if (this.referenceMode == 1) {
            Collection<HabboItem> furni = resolveFurniSource(context,
                    getWiredFurniSourceTypes(), furniSlots() - 1, List.of(), List.of());
            Collection<RoomUnit> users = resolveUserSource(
                    context, getWiredUserSourceTypes(), 0);
            Integer value = ChestWiredSupport.resolveValue(
                    context, this.variableId, this.referenceTarget, furni, users);
            if (value == null) return false;
            reference = value;
        }
        boolean any = false;
        for (HabboItem chest : chests) {
            boolean matched = ChestWiredSupport.compare(
                    valueFor(manager, chest, context), reference, this.comparison);
            if (this.quantifier == 1 && matched) return true;
            if (this.quantifier == 0 && !matched) return false;
            any |= matched;
        }
        return this.quantifier == 0 || any;
    }

    @Override public WiredConditionOperator operator() { return WiredConditionOperator.AND; }
    @Override public void serializeWiredData(ServerMessage message, Room room) {
        serializeWiredDataV2(message, room);
    }
    @Override public String getWiredData() {
        return WiredManager.getGson().toJson(new Data(VERSION, this.amount,
                this.referenceMode, this.referenceTarget, this.comparison,
                this.variableId, this.quantifier,
                this.selected.stream().map(HabboItem::getId).toList(),
                this.selected2.stream().map(HabboItem::getId).toList(),
                getWiredFurniSourceTypes(), getWiredUserSourceTypes()));
    }
    @Override public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            Data data = WiredManager.getGson().fromJson(
                    set == null ? null : set.getString("wired_data"), Data.class);
            if (data == null || data.version != VERSION || data.amount < 0
                    || data.amount > 1_000_000
                    || (data.referenceMode != 0 && data.referenceMode != 1)
                    || !ChestWiredSupport.validTarget(data.referenceTarget)
                    || data.comparison < 0 || data.comparison > 5
                    || (data.referenceMode == 1
                    && (data.variableId == null || data.variableId.isBlank()))) return;
            this.amount = data.amount;
            this.referenceMode = data.referenceMode;
            this.referenceTarget = data.referenceTarget;
            this.comparison = data.comparison;
            this.variableId = data.referenceMode == 1 ? data.variableId : "";
            this.quantifier = data.quantifier == 1 ? 1 : 0;
            this.selected.addAll(loadDatabase(room, data.selectedIds));
            this.selected2.addAll(loadDatabase(room, data.selectedIds2));
            setWiredSourceTypes(data.furniSources, data.userSources);
            if (!validSelections(Emulator.getGameEnvironment().getChestManager())) onPickUp();
        } catch (RuntimeException ignored) { onPickUp(); }
    }
    @Override public void onPickUp() {
        this.amount = 0; this.referenceMode = 0; this.referenceTarget = 0;
        this.comparison = 1; this.variableId = ""; this.quantifier = 0;
        this.selected.clear(); this.selected2.clear();
        setWiredSourceTypes(new int[0], new int[0]);
    }
    @Deprecated @Override public boolean execute(RoomUnit unit, Room room, Object[] stuff) {
        return false;
    }
    @Override protected Collection<HabboItem> getSelectedItems() { return this.selected; }
    @Override protected Collection<HabboItem> getSelectedItems2() { return this.selected2; }
    @Override protected int[] getWiredIntParams() {
        return new int[] {this.amount, this.referenceMode, this.referenceTarget, this.comparison};
    }
    @Override protected String[] getWiredVariableIds() {
        return new String[] {this.referenceMode == 1 ? this.variableId : ""};
    }
    @Override protected int getWiredQuantifierCode() { return this.quantifier; }
    @Override protected int getFurniSourceSlotCount() { return furniSlots(); }
    @Override protected int getUserSourceSlotCount() { return 1; }
    @Override protected boolean supportsFurniPicking() { return true; }
    @Override protected boolean isWiredAdvancedMode() { return true; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_PICKED_1, FURNI_SOURCE_PICKED_2,
                FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
    }
    @Override protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }
    @Override protected int getDefaultFurniSourceForSlot(int slot) {
        return slot == 1 && furniSlots() > 2 ? FURNI_SOURCE_PICKED_2
                : (slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_TRIGGERING_ITEM);
    }

    protected static List<HabboItem> load(Room room, int[] ids, boolean databaseIds) {
        List<HabboItem> result = new ArrayList<>();
        if (room != null && ids != null) for (int id : ids) {
            HabboItem item = databaseIds ? room.getHabboItemByDatabaseId(id) : room.getHabboItem(id);
            if (item != null) result.add(item);
        }
        return result;
    }
    private static List<HabboItem> loadDatabase(Room room, List<Integer> ids) {
        if (ids == null) return List.of();
        return load(room, ids.stream().filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).toArray(), true);
    }

    private static final class Data {
        int version, amount, referenceMode, referenceTarget, comparison, quantifier;
        String variableId;
        List<Integer> selectedIds, selectedIds2;
        int[] furniSources, userSources;
        Data(int version, int amount, int referenceMode, int referenceTarget,
                int comparison, String variableId, int quantifier,
                List<Integer> selectedIds, List<Integer> selectedIds2,
                int[] furniSources, int[] userSources) {
            this.version=version; this.amount=amount; this.referenceMode=referenceMode;
            this.referenceTarget=referenceTarget; this.comparison=comparison;
            this.variableId=variableId; this.quantifier=quantifier;
            this.selectedIds=selectedIds; this.selectedIds2=selectedIds2;
            this.furniSources=furniSources; this.userSources=userSources;
        }
    }
}
