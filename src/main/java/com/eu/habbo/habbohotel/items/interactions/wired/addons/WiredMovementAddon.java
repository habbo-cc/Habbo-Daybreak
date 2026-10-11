package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.ResultSet; import java.sql.SQLException;

abstract class WiredMovementAddon extends InteractionWiredAddon {
    WiredMovementAddon(ResultSet set, Item item) throws SQLException { super(set, item); }
    WiredMovementAddon(int id, int userId, Item item, String extra, int stack, int sells) { super(id,userId,item,extra,stack,sells); }
    final boolean ownsOnly(WiredSettingsV2 s, int count) { return s != null && s.getIntParams().length == count && s.getStringParam().isEmpty() && s.getFurniIds().length == 0 && s.getFurniIds2().length == 0 && s.getVariableIds().length == 0 && s.getFurniSourceTypes().length == 0 && s.getUserSourceTypes().length == 0; }
    final String json(Object data) { return WiredManager.getGson().toJson(data); }
    @Override protected int getMaxFurniSelection() { return 0; }
}
