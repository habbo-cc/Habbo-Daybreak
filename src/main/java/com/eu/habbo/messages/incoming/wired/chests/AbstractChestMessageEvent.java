package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;

abstract class AbstractChestMessageEvent extends MessageHandler {
    protected Room currentRoom() {
        if (this.client == null || this.client.getHabbo() == null
                || this.client.getHabbo().getHabboInfo() == null) {
            return null;
        }
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || this.client.getHabbo().getRoomUnit() == null
                || !this.client.getHabbo().getRoomUnit().isInRoom()
                || !this.client.getWiredCapabilityState().supportsRoom(
                        WiredCapabilityService.CAPABILITY_CHESTS, room.getId())) {
            return null;
        }
        return room;
    }

    protected HabboItem chest(Room room, int visibleId, boolean mustBeOpen) {
        if (room == null) {
            return null;
        }
        HabboItem item = room.getHabboItem(Math.abs(visibleId));
        if (item == null
                || !com.eu.habbo.Emulator.getGameEnvironment().getChestManager().isChest(item)
                || (mustBeOpen && !this.client.isActiveChest(room.getId(), item.getId()))) {
            return null;
        }
        return item;
    }

    protected void requireEnd() {
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected chest packet payload");
        }
    }
}
