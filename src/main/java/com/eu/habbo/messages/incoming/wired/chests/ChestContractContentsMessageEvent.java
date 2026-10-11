package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.wired.chests.ChestContractContentsComposer;

/** July request-contract-contents packet on the local parity header. */
public final class ChestContractContentsMessageEvent extends AbstractChestContractMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        requireEnd();
        Room room = currentContractRoom();
        InteractionChestContract contract = contract(room, visibleId);
        if (contract != null) {
            this.client.sendResponse(new ChestContractContentsComposer(contract));
        }
    }
}
