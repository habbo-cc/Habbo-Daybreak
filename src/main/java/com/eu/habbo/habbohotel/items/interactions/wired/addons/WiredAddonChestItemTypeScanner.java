package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** July AIR add-on 18 ({@code wf_xtra_scan_chest_furni_by_type}). */
public final class WiredAddonChestItemTypeScanner extends InteractionWiredAddon {
    private static final int VERSION = 1;
    private static final int SCAN_ALL = 0;
    private static final int SCAN_PREVIEW = 1;

    private int mode;
    private String variableId = "";
    private final List<HabboItem> itemTypes = new ArrayList<>();
    private final List<HabboItem> chests = new ArrayList<>();

    public WiredAddonChestItemTypeScanner(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }
    public WiredAddonChestItemTypeScanner(int id, int userId, Item item, String extraData,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }

    @Override public WiredAddonType getType() {
        return WiredAddonType.CHEST_ITEM_TYPE_SCANNER;
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 1
                || (settings.getIntParams()[0] != SCAN_ALL
                && settings.getIntParams()[0] != SCAN_PREVIEW)
                || settings.getVariableIds().length != 1
                || settings.getVariableIds()[0].isBlank()
                || settings.getStringParam() == null || !settings.getStringParam().isEmpty()
                || settings.getFurniSourceTypes().length != 2
                || settings.getUserSourceTypes().length != 0
                || settings.getDelay() != 0) {
            return false;
        }
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null || !WiredAddonVariablePlaceholder.definitionMatches(
                room, settings.getVariableIds()[0], -20)) {
            return false;
        }
        List<HabboItem> first = loadDatabaseIds(room, settings.getFurniIds());
        List<HabboItem> second = loadDatabaseIds(room, settings.getFurniIds2());
        if (first.size() != settings.getFurniIds().length
                || second.size() != settings.getFurniIds2().length
                || first.stream().anyMatch(
                Emulator.getGameEnvironment().getChestManager()::isChest)
                || second.stream().anyMatch(item ->
                ChestType.fromItem(item) != ChestType.FURNI)) {
            return false;
        }
        this.mode = settings.getIntParams()[0];
        this.variableId = settings.getVariableIds()[0];
        this.itemTypes.clear();
        this.itemTypes.addAll(first);
        this.chests.clear();
        this.chests.addAll(second);
        setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
        return true;
    }

    public void scan(WiredContext context) {
        if (context == null || context.room() == null || this.variableId.isBlank()) {
            return;
        }
        ChestManager manager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> types = resolveItemTypes(context);
        long count = 0;
        for (HabboItem chest : resolveChests(context)) {
            List<HabboItem> source = this.mode == SCAN_PREVIEW
                    ? manager.wiredPreviewFurniture(chest)
                    : manager.wiredStoredFurniture(chest);
            count += source.stream().filter(item -> matches(item, types)).count();
        }
        context.contextVariables().set(this.variableId,
                (int) Math.min(Integer.MAX_VALUE, count));
    }

    public List<HabboItem> resolveItemTypes(WiredContext context) {
        return ChestWiredSupport.nonChests(Emulator.getGameEnvironment().getChestManager(),
                resolveFurniSource(context, getWiredFurniSourceTypes(), 0,
                        this.itemTypes, this.chests));
    }

    public List<HabboItem> resolveChests(WiredContext context) {
        return ChestWiredSupport.chests(Emulator.getGameEnvironment().getChestManager(),
                resolveFurniSource(context, getWiredFurniSourceTypes(), 1,
                        this.chests, this.itemTypes), ChestType.FURNI);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new Data(VERSION, this.mode, this.variableId,
                this.itemTypes.stream().map(HabboItem::getId).toList(),
                this.chests.stream().map(HabboItem::getId).toList(),
                getWiredFurniSourceTypes()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            Data data = WiredManager.getGson().fromJson(
                    set == null ? null : set.getString("wired_data"), Data.class);
            if (data == null || data.version != VERSION
                    || (data.mode != SCAN_ALL && data.mode != SCAN_PREVIEW)
                    || !WiredAddonVariablePlaceholder.definitionMatches(
                    room, data.variableId, -20)) {
                return;
            }
            this.mode = data.mode;
            this.variableId = data.variableId;
            this.itemTypes.addAll(loadDatabaseIds(room, data.itemTypeIds));
            this.chests.addAll(loadDatabaseIds(room, data.chestIds));
            setWiredSourceTypes(data.furniSources, new int[0]);
        } catch (RuntimeException ignored) {
            onPickUp();
        }
    }

    @Override public void onPickUp() {
        this.mode = SCAN_ALL;
        this.variableId = "";
        this.itemTypes.clear();
        this.chests.clear();
        setWiredSourceTypes(new int[0], new int[0]);
    }
    @Override protected Collection<HabboItem> getSelectedItems() { return this.itemTypes; }
    @Override protected Collection<HabboItem> getSelectedItems2() { return this.chests; }
    @Override protected int[] getWiredIntParams() { return new int[] {this.mode}; }
    @Override protected String[] getWiredVariableIds() {
        return this.variableId.isBlank() ? new String[0] : new String[] {this.variableId};
    }
    @Override protected int getFurniSourceSlotCount() { return 2; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_PICKED_1, FURNI_SOURCE_PICKED_2,
                FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
    }
    @Override protected int getDefaultFurniSourceForSlot(int slot) {
        return slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_PICKED_2;
    }
    @Override protected boolean supportsFurniPicking() { return true; }
    @Override protected boolean isWiredAdvancedMode() { return true; }

    private static List<HabboItem> loadDatabaseIds(Room room, int[] ids) {
        List<HabboItem> result = new ArrayList<>();
        if (room != null && ids != null) {
            for (int id : ids) {
                HabboItem item = room.getHabboItemByDatabaseId(id);
                if (item != null) result.add(item);
            }
        }
        return result;
    }

    private static List<HabboItem> loadDatabaseIds(Room room, List<Integer> ids) {
        List<HabboItem> result = new ArrayList<>();
        if (room != null && ids != null) {
            for (Integer id : ids) {
                HabboItem item = id == null ? null : room.getHabboItemByDatabaseId(id);
                if (item != null) result.add(item);
            }
        }
        return result;
    }

    private static boolean matches(HabboItem item, List<HabboItem> types) {
        if (types == null || types.isEmpty()) return true;
        if (item == null || item.getBaseItem() == null) return false;
        for (HabboItem type : types) {
            if (type != null && type.getBaseItem() != null
                    && type.getBaseItem().getType() == item.getBaseItem().getType()
                    && type.getBaseItem().getSpriteId() == item.getBaseItem().getSpriteId()
                    && (type.getExtradata() == null || type.getExtradata().isEmpty()
                    || type.getExtradata().equals(item.getExtradata()))) {
                return true;
            }
        }
        return false;
    }

    private static final class Data {
        int version;
        int mode;
        String variableId;
        List<Integer> itemTypeIds;
        List<Integer> chestIds;
        int[] furniSources;

        Data(int version, int mode, String variableId, List<Integer> itemTypeIds,
                List<Integer> chestIds, int[] furniSources) {
            this.version = version;
            this.mode = mode;
            this.variableId = variableId;
            this.itemTypeIds = itemTypeIds;
            this.chestIds = chestIds;
            this.furniSources = furniSources;
        }
    }
}
