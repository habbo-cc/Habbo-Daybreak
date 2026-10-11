package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.chests.ChestRepository;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.wired.chests.ChestSettingsResultComposer;

/** July C2S 2905 shape on local header 7025. */
public final class ChestSaveNotificationsMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        int mode = this.packet.readRequiredInt();
        boolean full = this.packet.readRequiredBoolean();
        boolean donation = this.packet.readRequiredBoolean();
        boolean withdraw = this.packet.readRequiredBoolean();
        boolean empty = this.packet.readRequiredBoolean();
        boolean wired = this.packet.readRequiredBoolean();
        requireEnd();
        Room room = currentRoom();
        HabboItem chest = chest(room, visibleId, true);
        if (chest == null) {
            return;
        }
        ChestRepository.Result result = Emulator.getGameEnvironment().getChestManager()
                .saveNotifications(this.client, room, chest, mode, full, donation,
                        withdraw, empty, wired);
        if (result == ChestRepository.Result.OK) {
            this.client.sendResponse(new ChestSettingsResultComposer(visibleId, true));
        }
    }
}
