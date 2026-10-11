package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * July add-on 7: keep altitude, pass selected furniture/users, and block on
 * selected furniture. The two furniture source slots correspond to July's
 * "Move through furni" and "Blocked by furni" source pickers.
 */
public final class WiredAddonMovementPhysics extends WiredMovementAddon {
    private boolean keepAltitude;
    private boolean throughFurni;
    private boolean throughUsers;
    private boolean blockByFurni;
    private final List<HabboItem> throughItems = new ArrayList<>();
    private final List<HabboItem> blockingItems = new ArrayList<>();

    public WiredAddonMovementPhysics(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredAddonMovementPhysics(
            int id, int userId, Item item, String extra, int stack, int sells) {
        super(id, userId, item, extra, stack, sells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.MOVEMENT_PHYSICS;
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null
                || settings.getIntParams().length != 4
                || !settings.getStringParam().isEmpty()
                || settings.getVariableIds().length != 0
                || settings.getFurniSourceTypes().length != 2
                || settings.getUserSourceTypes().length != 1) {
            return false;
        }
        int[] parameters = settings.getIntParams();
        for (int value : parameters) {
            if (value != 0 && value != 1) {
                return false;
            }
        }

        Room room = null;
        if (settings.getFurniIds().length != 0 || settings.getFurniIds2().length != 0) {
            room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room == null) {
                return false;
            }
        }
        if (!loadItems(room, this.throughItems, settings.getFurniIds())
                || !loadItems(room, this.blockingItems, settings.getFurniIds2())) {
            return false;
        }

        this.keepAltitude = parameters[0] == 1;
        this.throughFurni = parameters[1] == 1;
        this.throughUsers = parameters[2] == 1;
        this.blockByFurni = parameters[3] == 1;
        return true;
    }

    @Override
    public String getWiredData() {
        validateItems(this.throughItems);
        validateItems(this.blockingItems);
        return json(new Data(
                1,
                this.keepAltitude,
                this.throughFurni,
                this.throughUsers,
                this.blockByFurni,
                databaseIds(this.throughItems),
                databaseIds(this.blockingItems)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        if (set == null) {
            return;
        }
        try {
            Data data = WiredManager.getGson().fromJson(set.getString("wired_data"), Data.class);
            if (data == null || data.v != 1) {
                return;
            }
            this.keepAltitude = data.keepAltitude;
            this.throughFurni = data.throughFurni;
            this.throughUsers = data.throughUsers;
            this.blockByFurni = data.blockByFurni;
            loadDatabaseItems(room, this.throughItems, data.throughItemIds);
            loadDatabaseItems(room, this.blockingItems, data.blockingItemIds);
        } catch (RuntimeException ignored) {
            onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.keepAltitude = false;
        this.throughFurni = false;
        this.throughUsers = false;
        this.blockByFurni = false;
        this.throughItems.clear();
        this.blockingItems.clear();
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {
                this.keepAltitude ? 1 : 0,
                this.throughFurni ? 1 : 0,
                this.throughUsers ? 1 : 0,
                this.blockByFurni ? 1 : 0
        };
    }

    @Override
    protected int getMaxFurniSelection() {
        return WiredManager.MAXIMUM_FURNI_SELECTION;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.throughItems;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems2() {
        return this.blockingItems;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 2;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 1;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return slot == 0
                ? new int[] {
                        FURNI_SOURCE_ROOM_FURNI,
                        FURNI_SOURCE_PICKED_1,
                        FURNI_SOURCE_SELECTOR,
                        FURNI_SOURCE_SIGNAL
                }
                : new int[] {
                        FURNI_SOURCE_ROOM_FURNI,
                        FURNI_SOURCE_PICKED_2,
                        FURNI_SOURCE_SELECTOR,
                        FURNI_SOURCE_SIGNAL
                };
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {
                USER_SOURCE_ROOM_USERS,
                USER_SOURCE_SELECTOR,
                USER_SOURCE_SIGNAL
        };
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_ROOM_FURNI;
    }

    @Override
    protected int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_ROOM_USERS;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    public boolean keepAltitude() {
        return this.keepAltitude;
    }

    public boolean throughFurni() {
        return this.throughFurni;
    }

    public boolean throughUsers() {
        return this.throughUsers;
    }

    public boolean blockByFurni() {
        return this.blockByFurni;
    }

    public Collection<HabboItem> throughFurniture(WiredContext context) {
        return resolveFurniSource(
                context, this.wiredFurniSourceTypes, 0, this.throughItems, this.blockingItems);
    }

    public Collection<HabboItem> blockingFurniture(WiredContext context) {
        return resolveFurniSource(
                context, this.wiredFurniSourceTypes, 1, this.throughItems, this.blockingItems);
    }

    public Collection<RoomUnit> throughUsers(WiredContext context) {
        return resolveUserSource(context, this.wiredUserSourceTypes, 0);
    }

    public boolean throughAllFurniture() {
        return source(this.wiredFurniSourceTypes, 0, FURNI_SOURCE_ROOM_FURNI)
                == FURNI_SOURCE_ROOM_FURNI;
    }

    public boolean blocksAllFurniture() {
        return source(this.wiredFurniSourceTypes, 1, FURNI_SOURCE_ROOM_FURNI)
                == FURNI_SOURCE_ROOM_FURNI;
    }

    public boolean throughAllUsers() {
        return source(this.wiredUserSourceTypes, 0, USER_SOURCE_ROOM_USERS)
                == USER_SOURCE_ROOM_USERS;
    }

    private static int source(int[] sources, int slot, int fallback) {
        return sources != null && slot >= 0 && slot < sources.length ? sources[slot] : fallback;
    }

    private static boolean loadItems(Room room, List<HabboItem> target, int[] databaseIds) {
        target.clear();
        if (databaseIds == null || databaseIds.length == 0) {
            return true;
        }
        if (room == null) {
            return false;
        }
        for (int databaseId : databaseIds) {
            HabboItem item = room.getHabboItemByDatabaseId(databaseId);
            if (item == null) {
                return false;
            }
            target.add(item);
        }
        return true;
    }

    private static void loadDatabaseItems(
            Room room, List<HabboItem> target, List<Integer> databaseIds) {
        if (room == null || databaseIds == null) {
            return;
        }
        for (Integer databaseId : databaseIds) {
            if (databaseId == null) {
                continue;
            }
            HabboItem item = room.getHabboItemByDatabaseId(databaseId);
            if (item != null) {
                target.add(item);
            }
        }
    }

    private void validateItems(List<HabboItem> items) {
        items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId());
    }

    private static List<Integer> databaseIds(List<HabboItem> items) {
        List<Integer> ids = new ArrayList<>(items.size());
        for (HabboItem item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    static final class Data {
        int v;
        boolean keepAltitude;
        boolean throughFurni;
        boolean throughUsers;
        boolean blockByFurni;
        List<Integer> throughItemIds = new ArrayList<>();
        List<Integer> blockingItemIds = new ArrayList<>();

        Data() {
        }

        Data(
                int version,
                boolean keepAltitude,
                boolean throughFurni,
                boolean throughUsers,
                boolean blockByFurni,
                List<Integer> throughItemIds,
                List<Integer> blockingItemIds) {
            this.v = version;
            this.keepAltitude = keepAltitude;
            this.throughFurni = throughFurni;
            this.throughUsers = throughUsers;
            this.blockByFurni = blockByFurni;
            this.throughItemIds = throughItemIds;
            this.blockingItemIds = blockingItemIds;
        }
    }
}
