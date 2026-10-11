package com.eu.habbo.messages.incoming.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class BuyHabbiconCollectionMessageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        if (this.packet.bytesAvailable() < 4) {
            return;
        }

        int collectionId = this.packet.readInt();
        HabbiconManager manager = Emulator.getGameEnvironment().getHabbiconManager();
        if (this.client.getHabbo() == null || manager == null) {
            return;
        }

        manager.buyCollection(this.client.getHabbo(), collectionId);
    }
}
