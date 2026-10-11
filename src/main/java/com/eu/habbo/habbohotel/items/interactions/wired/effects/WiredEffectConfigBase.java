package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

abstract class WiredEffectConfigBase extends InteractionWiredEffect {
    protected final List<HabboItem> items = new ArrayList<>();
    protected final List<HabboItem> items2 = new ArrayList<>();
    protected int[] intParams = new int[0];
    protected String stringParam = "";
    protected int[] furniSourceTypes = new int[0];
    protected int[] userSourceTypes = new int[0];
    protected String[] variableIds = new String[0];

    protected WiredEffectConfigBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredEffectConfigBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        this.intParams = settings.getIntParams() != null ? settings.getIntParams() : new int[0];
        this.stringParam = settings.getStringParam() != null ? settings.getStringParam() : "";
        this.furniSourceTypes = settings.getFurniSourceTypes() != null ? settings.getFurniSourceTypes() : new int[0];
        this.userSourceTypes = settings.getUserSourceTypes() != null ? settings.getUserSourceTypes() : new int[0];
        this.variableIds = settings.getVariableIds() != null ? settings.getVariableIds() : new String[0];
        this.setDelay(settings.getDelay());
        loadItems(room, this.items, settings.getFurniIds());
        loadItems(room, this.items2, settings.getFurniIds2());
        return true;
    }

    private void loadItems(Room room, List<HabboItem> target, int[] databaseIds) {
        target.clear();
        if (databaseIds == null) {
            return;
        }
        for (int databaseId : databaseIds) {
            HabboItem item = room.getHabboItemByDatabaseId(databaseId);
            if (item != null) {
                target.add(item);
            }
        }
    }

    @Override
    public String getWiredData() {
        validateItems(this.items);
        validateItems(this.items2);
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.intParams,
                this.stringParam,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.items2.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSourceTypes,
                this.userSourceTypes,
                this.variableIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.items2.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.variableIds = new String[0];
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }
        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        this.setDelay(data.delay);
        this.intParams = data.intParams != null ? data.intParams : new int[0];
        this.stringParam = data.stringParam != null ? data.stringParam : "";
        this.furniSourceTypes = data.furniSourceTypes != null ? data.furniSourceTypes : new int[0];
        this.userSourceTypes = data.userSourceTypes != null ? data.userSourceTypes : new int[0];
        this.variableIds = data.variableIds != null ? data.variableIds : new String[0];
        if (data.itemIds != null) {
            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItemByDatabaseId(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }
        if (data.itemIds2 != null) {
            for (Integer id : data.itemIds2) {
                HabboItem item = room.getHabboItemByDatabaseId(id);
                if (item != null) {
                    this.items2.add(item);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.items2.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.variableIds = new String[0];
        this.setDelay(0);
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.items;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems2() {
        return this.items2;
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
        return resolveFurniSource(ctx, this.furniSourceTypes, 0, this.items, this.items2);
    }

    protected Collection<HabboItem> sourceItems(WiredContext ctx, int slot) {
        return resolveFurniSource(ctx, this.furniSourceTypes, slot, this.items, this.items2);
    }

    protected Collection<RoomUnit> sourceUsers(WiredContext ctx) {
        return resolveUserSource(ctx, this.userSourceTypes, 0);
    }

    static class JsonData {
        int delay;
        int[] intParams;
        String stringParam;
        List<Integer> itemIds;
        List<Integer> itemIds2;
        int[] furniSourceTypes;
        int[] userSourceTypes;
        String[] variableIds;

        JsonData(int delay, int[] intParams, String stringParam, List<Integer> itemIds, List<Integer> itemIds2,
                 int[] furniSourceTypes, int[] userSourceTypes, String[] variableIds) {
            this.delay = delay;
            this.intParams = intParams;
            this.stringParam = stringParam;
            this.itemIds = itemIds;
            this.itemIds2 = itemIds2;
            this.furniSourceTypes = furniSourceTypes;
            this.userSourceTypes = userSourceTypes;
            this.variableIds = variableIds;
        }
    }
}
