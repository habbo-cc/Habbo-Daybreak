package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.chests.ChestRepository;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.wired.chests.ChestSettingsResultComposer;

/** July C2S 3830 shape on local header 7023. */
public final class ChestSaveSettingsMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        String name = this.packet.readBoundedString(30);
        String description = this.packet.readBoundedString(200);
        boolean open = this.packet.readRequiredBoolean();
        boolean donate = this.packet.readRequiredBoolean();
        int state = this.packet.readRequiredInt();
        int previewMode = this.packet.readRequiredInt();
        int previewAmount = this.packet.readRequiredInt();
        boolean wiredEnabled = this.packet.readRequiredBoolean();
        requireEnd();
        Room room = currentRoom();
        HabboItem chest = chest(room, visibleId, true);
        if (chest == null) {
            return;
        }
        ChestRepository.Result result = Emulator.getGameEnvironment().getChestManager()
                .saveGeneral(this.client, room, chest, name, description, open, donate,
                        state, previewMode, previewAmount, wiredEnabled);
        if (result == ChestRepository.Result.OK) {
            this.client.sendResponse(new ChestSettingsResultComposer(visibleId, false));
        }
    }
}
