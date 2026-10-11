package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

abstract class WiredSelectorConfigBase extends InteractionWiredSelector {
    protected final List<HabboItem> items = new ArrayList<>();
    protected int[] intParams = new int[0];
    protected String stringParam = "";
    protected int[] furniSourceTypes = new int[0];
    protected int[] userSourceTypes = new int[0];
    protected String[] variableIds = new String[0];
    protected int[] furniIds2 = new int[0];

    protected WiredSelectorConfigBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredSelectorConfigBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null) {
            return false;
        }
        Room room = com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }
        saveBase(settings);
        this.intParams = settings.getIntParams() == null ? new int[0] : settings.getIntParams();
        this.stringParam = settings.getStringParam() == null ? "" : settings.getStringParam();
        this.furniSourceTypes = settings.getFurniSourceTypes() == null
                ? new int[0] : settings.getFurniSourceTypes();
        this.userSourceTypes = settings.getUserSourceTypes() == null
                ? new int[0] : settings.getUserSourceTypes();
        this.variableIds = settings.getVariableIds() == null
                ? new String[0] : settings.getVariableIds();
        this.furniIds2 = toDatabaseIds(room, settings.getFurniIds2());
        this.items.clear();
        for (int visibleId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(visibleId);
            if (item != null) {
                this.items.add(item);
            }
        }
        return true;
    }

    @Override
    public String getWiredData() {
        this.items.removeIf(item -> item == null || item.getRoomId() == 0);
        return WiredManager.getGson().toJson(new JsonData(isFilter(), isInvert(), intParams, stringParam,
                items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                furniSourceTypes, userSourceTypes, variableIds, furniIds2));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null) {
                return;
            }
            setSelectorFlags(data.filter, data.invert);
            this.intParams = data.intParams != null ? data.intParams : new int[0];
            this.stringParam = data.stringParam != null ? data.stringParam : "";
            this.furniSourceTypes = data.furniSourceTypes != null ? data.furniSourceTypes : new int[0];
            this.userSourceTypes = data.userSourceTypes != null ? data.userSourceTypes : new int[0];
            this.variableIds = data.variableIds != null ? data.variableIds : new String[0];
            this.furniIds2 = data.furniIds2 != null ? data.furniIds2 : new int[0];
            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItemByDatabaseId(id);
                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            onPickUp();
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
        this.furniIds2 = new int[0];
        setSelectorFlags(false, false);
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.items;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems2() {
        if (this.furniIds2.length == 0) {
            return List.of();
        }
        var environment = com.eu.habbo.Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return List.of();
        }
        return java.util.Arrays.stream(this.furniIds2)
                .mapToObj(room::getHabboItemByDatabaseId)
                .filter(java.util.Objects::nonNull)
                .toList();
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
    protected boolean sanitizeSelectedItems(Room room) {
        if (room == null) {
            return false;
        }
        boolean changed = pruneInvalidSelectedItems(room, this.items);
        if (this.furniIds2.length == 0) {
            return changed;
        }
        int[] currentIds = java.util.Arrays.stream(this.furniIds2)
                .filter(id -> isCurrentSelectedItem(room, room.getHabboItemByDatabaseId(id)))
                .toArray();
        if (!java.util.Arrays.equals(this.furniIds2, currentIds)) {
            this.furniIds2 = currentIds;
            changed = true;
        }
        return changed;
    }

    private int[] toDatabaseIds(Room room, int[] visibleIds) {
        if (visibleIds == null || visibleIds.length == 0) {
            return new int[0];
        }
        return java.util.Arrays.stream(visibleIds)
                .mapToObj(room::getHabboItem)
                .filter(java.util.Objects::nonNull)
                .mapToInt(HabboItem::getId)
                .distinct()
                .toArray();
    }

    protected boolean supportsFurniPickingWhenEmpty() {
        return false;
    }

    @Override
    protected boolean supportsFurniPicking() {
        return !this.items.isEmpty() || supportsFurniPickingWhenEmpty();
    }

    protected WiredTargets targets() {
        return new WiredTargets();
    }

    protected Collection<RoomUnit> allRoomUnits(Room room) {
        return room.getRoomUnits();
    }

    protected Collection<HabboItem> allFloorItems(Room room) {
        return room.getFloorItems();
    }

    protected void addUsersOnSelectedFurni(Room room, WiredTargets targets) {
        for (HabboItem item : this.items) {
            THashSet<RoomUnit> units = room.getHabbosAndBotsAt(item.getX(), item.getY());
            for (RoomUnit unit : units) {
                targets.addUser(unit);
            }
        }
    }

    protected boolean inArea(int x, int y) {
        if (this.intParams.length < 4) {
            return false;
        }
        int ax = this.intParams[0];
        int ay = this.intParams[1];
        int width = Math.max(1, this.intParams[2]);
        int height = Math.max(1, this.intParams[3]);
        return x >= ax && y >= ay && x < ax + width && y < ay + height;
    }

    protected boolean altitudeMatches(double z) {
        if (this.intParams.length < 2) {
            return false;
        }
        double expected = this.intParams[0] / 100.0;
        int op = this.intParams[1];
        if (op == 1) {
            return z < expected;
        }
        if (op == 2) {
            return z > expected;
        }
        return Math.abs(z - expected) < 0.01;
    }

    protected Set<String> names() {
        Set<String> names = new HashSet<>();
        for (String part : this.stringParam.split("[\\t\\r\\n]+")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    protected static String normalizeExtraData(String value) {
        return value == null ? "" : value.trim();
    }

    protected boolean groupMatches(Room room, RoomUnit unit) {
        if (this.stringParam == null || this.stringParam.trim().isEmpty()) {
            Habbo habbo = room.getHabbo(unit);
            return habbo != null && !habbo.getHabboStats().guilds.isEmpty();
        }
        try {
            int guildId = Integer.parseInt(this.stringParam.trim());
            Habbo habbo = room.getHabbo(unit);
            return habbo != null && habbo.getHabboStats().hasGuild(guildId);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    protected boolean unitTypeMatches(Room room, RoomUnit unit) {
        int mode = this.intParams.length > 0 ? this.intParams[0] : 1;
        if (mode == 2) {
            return unit.getRoomUnitType() == RoomUnitType.USER;
        }
        if (mode == 4) {
            Bot bot = room.getBot(unit);
            return bot != null || unit.getRoomUnitType() == RoomUnitType.BOT;
        }
        return true;
    }

    static class JsonData {
        boolean filter;
        boolean invert;
        int[] intParams;
        String stringParam;
        List<Integer> itemIds;
        int[] furniSourceTypes;
        int[] userSourceTypes;
        String[] variableIds;
        int[] furniIds2;

        JsonData(boolean filter, boolean invert, int[] intParams, String stringParam, List<Integer> itemIds,
                 int[] furniSourceTypes, int[] userSourceTypes, String[] variableIds, int[] furniIds2) {
            this.filter = filter;
            this.invert = invert;
            this.intParams = intParams;
            this.stringParam = stringParam;
            this.itemIds = itemIds;
            this.furniSourceTypes = furniSourceTypes;
            this.userSourceTypes = userSourceTypes;
            this.variableIds = variableIds;
            this.furniIds2 = furniIds2;
        }
    }
}
