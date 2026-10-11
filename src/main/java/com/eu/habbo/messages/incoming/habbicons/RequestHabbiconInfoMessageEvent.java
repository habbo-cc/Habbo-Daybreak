package com.eu.habbo.messages.incoming.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestHabbiconInfoMessageEvent extends MessageHandler {
    @Override
    public void handle() {
        if (this.packet.bytesAvailable() < 4) {
            return;
        }

        int habbiconId = this.packet.readInt();
        HabbiconManager manager = Emulator.getGameEnvironment().getHabbiconManager();
        if (this.client.getHabbo() == null || manager == null) {
            return;
        }

        manager.sendInfo(this.client.getHabbo(), habbiconId);
    }
}
