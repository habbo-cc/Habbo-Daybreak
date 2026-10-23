package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Code 28 has no box-specific settings and never invents a triggering user. */
public class WiredTriggerRoomInitialized extends InteractionWiredTrigger {
    public WiredTriggerRoomInitialized(ResultSet row, Item item) throws SQLException { super(row, item); }
    public WiredTriggerRoomInitialized(int id, int user, Item item, String data, int stack, int sells) {
        super(id, user, item, data, stack, sells);
    }
    @Override public WiredTriggerType getType() { return WiredTriggerType.ROOM_INITIALIZED; }
    @Override public boolean matches(HabboItem item, WiredEvent event) {
        return event.getType() == WiredEvent.Type.ROOM_INITIALIZED;
    }
    @Override public boolean isTriggeredByRoomUnit() { return false; }
    @Override public String getWiredData() { return ""; }
    @Override public void loadWiredData(ResultSet row, Room room) { }
    @Override public void onPickUp() { }
    @Override public boolean saveData(WiredSettings settings) {
        return settings != null && settings.getIntParams() != null && settings.getIntParams().length == 0
                && (settings.getStringParam() == null || settings.getStringParam().isEmpty());
    }
    @Deprecated @Override public boolean execute(RoomUnit unit, Room room, Object[] data) { return false; }

    @Override public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(getBaseItem().getSpriteId());
        message.appendInt(getRoomVisibleId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(getType().code);
        message.appendInt(0);
    }
}
