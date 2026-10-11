package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wired 2.0 add-on code 17: execute every effect in deterministic stack order.
 *
 * <p>The add-on has no configurable fields. Runtime ordering is applied by the
 * room stack index and {@code WiredEngine}; this furniture remains an inert
 * {@link com.eu.habbo.habbohotel.items.interactions.InteractionWired} item by
 * itself.</p>
 */
public final class WiredAddonExecuteInOrder extends InteractionWiredAddon {
    public WiredAddonExecuteInOrder(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredAddonExecuteInOrder(int id, int userId, Item item, String extradata,
                                    int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.EXECUTE_IN_ORDER;
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        return settings != null
                && settings.getIntParams().length == 0
                && settings.getStringParam().isEmpty()
                && settings.getFurniIds().length == 0
                && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0;
    }

    @Override
    public String getWiredData() {
        return "";
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        // Code 17 has no persisted configuration beyond its category/type identity.
    }

    @Override
    public void onPickUp() {
        // No runtime state to clear.
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }
}
