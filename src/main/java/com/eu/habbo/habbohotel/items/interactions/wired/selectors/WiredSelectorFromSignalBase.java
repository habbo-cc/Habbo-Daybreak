package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared strict persistence for the two parameterless July signal selectors. */
abstract class WiredSelectorFromSignalBase extends InteractionWiredSelector {
    protected WiredSelectorFromSignalBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredSelectorFromSignalBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public final boolean saveData(WiredSettingsV2 settings) {
        if (settings == null
                || settings.getIntParams().length != 0
                || !settings.getStringParam().isEmpty()
                || settings.getFurniIds().length != 0
                || settings.getFurniIds2().length != 0
                || settings.getVariableIds().length != 0
                || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 0
                || settings.getDelay() != 0
                || settings.getQuantifierCode() != 0) {
            return false;
        }
        return this.saveBase(settings);
    }

    @Override
    public final String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.isFilter(), this.isInvert()));
    }

    @Override
    public final void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setSelectorFlags(false, false);
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.setSelectorFlags(data.filter, data.invert);
            }
        } catch (RuntimeException ignored) {
            this.setSelectorFlags(false, false);
        }
    }

    @Override
    public final void onPickUp() {
        this.setSelectorFlags(false, false);
    }

    static final class JsonData {
        boolean filter;
        boolean invert;

        JsonData(boolean filter, boolean invert) {
            this.filter = filter;
            this.invert = invert;
        }
    }
}
