package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

abstract class WiredConditionConfigBase extends InteractionWiredCondition {
    protected final List<HabboItem> items = new ArrayList<>();
    protected int[] intParams = new int[0];
    protected String stringParam = "";
    protected int[] furniSourceTypes = new int[0];
    protected int[] userSourceTypes = new int[0];
    protected String[] variableIds = new String[0];
    protected int quantifierCode;

    protected WiredConditionConfigBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredConditionConfigBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        this.intParams = settings.getIntParams() != null ? settings.getIntParams() : new int[0];
        this.stringParam = settings.getStringParam() != null ? settings.getStringParam() : "";
        this.furniSourceTypes = settings.getFurniSourceTypes() != null ? settings.getFurniSourceTypes() : new int[0];
        this.userSourceTypes = settings.getUserSourceTypes() != null ? settings.getUserSourceTypes() : new int[0];
        this.variableIds = settings.getVariableIds() != null ? settings.getVariableIds() : new String[0];
        this.quantifierCode = settings.getQuantifierCode() == 1 ? 1 : 0;
        this.items.clear();
        if (settings.getFurniIds() != null) {
            for (int visibleId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(visibleId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }
        return true;
    }

    @Override
    public String getWiredData() {
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId());
        return WiredManager.getGson().toJson(new JsonData(
                this.intParams,
                this.stringParam,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSourceTypes,
                this.userSourceTypes,
                this.variableIds,
                this.quantifierCode));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.variableIds = new String[0];
        this.quantifierCode = 0;
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        this.intParams = data.intParams != null ? data.intParams : new int[0];
        this.stringParam = data.stringParam != null ? data.stringParam : "";
        this.furniSourceTypes = data.furniSourceTypes != null ? data.furniSourceTypes : new int[0];
        this.userSourceTypes = data.userSourceTypes != null ? data.userSourceTypes : new int[0];
        this.variableIds = data.variableIds != null ? data.variableIds : new String[0];
        this.quantifierCode = data.quantifierCode == 1 ? 1 : 0;
        if (data.itemIds != null) {
            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItemByDatabaseId(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.variableIds = new String[0];
        this.quantifierCode = 0;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.items;
    }

    @Override
    protected int[] getWiredIntParams() {
        return this.intParams;
    }

    @Override
    protected String getWiredStringParam() {
        return this.stringParam;
    }

    @Override
    protected int[] getWiredFurniSourceTypes() {
        return this.furniSourceTypes;
    }

    @Override
    protected int[] getWiredUserSourceTypes() {
        return this.userSourceTypes;
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableIds;
    }

    @Override
    protected int getWiredQuantifierCode() {
        return this.quantifierCode;
    }

    @Override
    protected boolean supportsFurniPicking() {
        return !this.items.isEmpty() || supportsFurniPickingWhenEmpty();
    }

    protected boolean supportsFurniPickingWhenEmpty() {
        return false;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return getFurniSourceSlotCount() > 0 || getUserSourceSlotCount() > 0;
    }

    protected Collection<HabboItem> sourceItems(WiredContext ctx) {
        return resolveFurniSource(ctx, this.furniSourceTypes, 0, this.items, null);
    }

    protected Collection<RoomUnit> sourceUsers(WiredContext ctx) {
        return resolveUserSource(ctx, this.userSourceTypes, 0);
    }

    protected boolean compare(int lhs, int rhs, int operator) {
        switch (operator) {
            case 1:
                return lhs >= rhs;
            case 2:
                return lhs <= rhs;
            case 0:
            default:
                return lhs == rhs;
        }
    }

    static class JsonData {
        int[] intParams;
        String stringParam;
        List<Integer> itemIds;
        int[] furniSourceTypes;
        int[] userSourceTypes;
        String[] variableIds;
        int quantifierCode;

        JsonData(int[] intParams, String stringParam, List<Integer> itemIds,
                 int[] furniSourceTypes, int[] userSourceTypes, String[] variableIds, int quantifierCode) {
            this.intParams = intParams;
            this.stringParam = stringParam;
            this.itemIds = itemIds;
            this.furniSourceTypes = furniSourceTypes;
            this.userSourceTypes = userSourceTypes;
            this.variableIds = variableIds;
            this.quantifierCode = quantifierCode;
        }
    }
}
