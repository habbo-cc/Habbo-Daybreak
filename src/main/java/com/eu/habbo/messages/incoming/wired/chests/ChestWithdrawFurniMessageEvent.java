package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

/** July C2S 873 shape on local header 7027. */
public final class ChestWithdrawFurniMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        boolean wall = this.packet.readRequiredBoolean();
        int typeId = this.packet.readRequiredInt();
        String legacyPosterId = this.packet.readBoundedString(64);
        int amount = this.packet.readRequiredInt();
        requireEnd();
        Room room = currentRoom();
        HabboItem chest = chest(room, visibleId, true);
        if (chest != null) {
            Emulator.getGameEnvironment().getChestManager().withdrawByType(
                    this.client, room, chest, wall, typeId, legacyPosterId, amount);
        }
    }
}
