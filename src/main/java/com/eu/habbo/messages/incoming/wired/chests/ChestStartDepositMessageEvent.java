package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

/** July C2S 3514 shape on local header 7031. */
public final class ChestStartDepositMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        requireEnd();
        Room room = currentRoom();
        HabboItem chest = chest(room, visibleId, true);
        if (chest != null) {
            Emulator.getGameEnvironment().getChestManager()
                    .startTrade(this.client, room, chest);
        }
    }
}
