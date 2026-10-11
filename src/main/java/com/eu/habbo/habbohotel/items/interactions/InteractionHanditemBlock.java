package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.ConfigurationItemStatesMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July Handitem Blocker (`conf_handitem_block`). */
public class InteractionHanditemBlock extends HabboItem {
    public InteractionHanditemBlock(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionHanditemBlock(int id, int userId, Item item, String extradata,
                                    int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(this.isLimited() ? 256 : 0);
        serverMessage.appendString(normalizedState());
        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        boolean wiredToggle = objects != null && objects.length >= 2
                && objects[1] == WiredEffectType.TOGGLE_STATE;
        if (room == null || (client != null && !room.hasRights(client.getHabbo()) && !wiredToggle)) {
            return;
        }

        this.setExtradata(isEnabled() ? "0" : "1");
        this.needsUpdate(true);
        room.updateItemState(this);
        room.sendComposer(new ConfigurationItemStatesMessageComposer(room).compose());
        Emulator.getThreading().run(this);
        super.onClick(client, room, new Object[]{"TOGGLE_OVERRIDE"});
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        if (room != null) {
            room.sendComposer(new ConfigurationItemStatesMessageComposer(room).compose());
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
        super.onPickUp(room);
        if (room != null) {
            room.sendComposer(new ConfigurationItemStatesMessageComposer(room).compose());
        }
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    public boolean isEnabled() {
        return "1".equals(normalizedState());
    }

    private String normalizedState() {
        return "1".equals(this.getExtradata()) ? "1" : "0";
    }

    public static boolean isHanditemControlBlocked(Room room) {
        if (room == null) {
            return false;
        }
        for (HabboItem item : room.getFloorItems()) {
            if (item instanceof InteractionHanditemBlock
                    && ((InteractionHanditemBlock) item).isEnabled()) {
                return true;
            }
        }
        return false;
    }
}
