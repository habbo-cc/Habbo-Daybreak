package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;

abstract class AbstractChestContractMessageEvent extends MessageHandler {
    protected Room currentContractRoom() {
        if (this.client == null || this.client.getHabbo() == null
                || this.client.getHabbo().getHabboInfo() == null) {
            return null;
        }
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || this.client.getHabbo().getRoomUnit() == null
                || !this.client.getHabbo().getRoomUnit().isInRoom()
                || !this.client.getWiredCapabilityState().supportsRoom(
                        WiredCapabilityService.CAPABILITY_CONTRACTS, room.getId())) {
            return null;
        }
        return room;
    }

    protected InteractionChestContract contract(Room room, int visibleId) {
        if (room == null || visibleId == Integer.MIN_VALUE) {
            return null;
        }
        HabboItem item = room.getHabboItem(Math.abs(visibleId));
        if (!(item instanceof InteractionChestContract contract)
                || !InteractionChestContract.canConfigure(this.client, room, item)) {
            return null;
        }
        return contract;
    }

    protected void requireEnd() {
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected chest contract packet payload");
        }
    }
}
