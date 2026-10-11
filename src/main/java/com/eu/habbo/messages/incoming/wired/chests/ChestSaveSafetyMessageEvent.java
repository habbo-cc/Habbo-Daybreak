package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

/** July C2S 2907 shape on local header 7026. */
public final class ChestSaveSafetyMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        boolean locked = this.packet.readRequiredBoolean();
        boolean autoLock = this.packet.readRequiredBoolean();
        int capacity = this.packet.readRequiredInt();
        requireEnd();
        Room room = currentRoom();
        HabboItem chest = chest(room, visibleId, true);
        if (chest != null) {
            Emulator.getGameEnvironment().getChestManager().saveSafety(
                    this.client, room, chest, locked, autoLock, capacity);
        }
    }
}
