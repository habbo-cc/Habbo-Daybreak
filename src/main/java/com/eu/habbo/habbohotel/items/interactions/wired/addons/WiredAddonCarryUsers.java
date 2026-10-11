package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

/**
 * July add-on 8. Mode 0 carries users standing directly on the moving
 * furniture; mode 1 carries source users sharing its origin tile.
 */
public final class WiredAddonCarryUsers extends WiredMovementAddon {
    private static final int ON_FURNITURE = 0;
    private static final int ON_SAME_TILE = 1;

    private int mode;

    public WiredAddonCarryUsers(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredAddonCarryUsers(
            int id, int userId, Item item, String extra, int stack, int sells) {
        super(id, userId, item, extra, stack, sells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.CARRY_USERS;
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null
                || settings.getIntParams().length != 1
                || settings.getStringParam().length() != 0
                || settings.getFurniIds().length != 0
                || settings.getFurniIds2().length != 0
                || settings.getVariableIds().length != 0
                || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 1) {
            return false;
        }

        int value = settings.getIntParams()[0];
        if (value != ON_FURNITURE && value != ON_SAME_TILE) {
            return false;
        }
        this.mode = value;
        return true;
    }

    @Override
    public String getWiredData() {
        return json(new Data(1, this.mode));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        if (set == null) {
            return;
        }
        String raw = set.getString("wired_data");
        if (raw == null || !raw.startsWith("{")) {
            return;
        }
        try {
            Data data = WiredManager.getGson().fromJson(raw, Data.class);
            if (data != null
                    && data.v == 1
                    && (data.mode == ON_FURNITURE || data.mode == ON_SAME_TILE)) {
                this.mode = data.mode;
            }
        } catch (RuntimeException ignored) {
            onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.mode = ON_FURNITURE;
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {this.mode};
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 1;
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
    protected int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_ROOM_USERS;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    public int mode() {
        return this.mode;
    }

    public Collection<RoomUnit> users(WiredContext context) {
        return resolveUserSource(context, this.wiredUserSourceTypes, 0);
    }

    static final class Data {
        int v;
        int mode;

        Data() {
        }

        Data(int version, int mode) {
            this.v = version;
            this.mode = mode;
        }
    }
}
