package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;

/** July C2S 1630 shape on local header 7022. */
public final class ChestSetRoomLocksMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        boolean locked = this.packet.readRequiredBoolean();
        boolean allChests = this.packet.readRequiredBoolean();
        requireEnd();
        Room room = currentRoom();
        if (room != null) {
            Emulator.getGameEnvironment().getChestManager()
                    .setRoomChestLocks(this.client, room, locked, allChests);
        }
    }
}
