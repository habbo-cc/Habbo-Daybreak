package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

public class WiredSelectorUsersByName extends WiredSelectorConfigBase {
    public WiredSelectorUsersByName(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorUsersByName(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.USERS_BY_NAME; }
    @Override public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null) {
            return false;
        }
        java.util.List<String> entries = splitNames(settings.getStringParam());
        String normalized = String.join("\t", entries);
        if (entries.isEmpty() || entries.size() > 20 || normalized.length() > 1000) {
            return false;
        }
        settings.setStringParam(normalized);
        return super.saveData(settings);
    }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        Set<String> names = names();
        for (RoomUnit unit : allRoomUnits(room)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && names.contains(habbo.getHabboInfo().getUsername().toLowerCase(Locale.ROOT))) {
                targets.addUser(unit);
            }
        }
        return targets;
    }

    private static java.util.List<String> splitNames(String value) {
        if (value == null) {
            return java.util.List.of();
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String part : value.split("[\\t\\r\\n]+")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }
}
