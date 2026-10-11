package com.eu.habbo.messages.incoming.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestHabbiconShopDataMessageEvent extends MessageHandler {
    @Override
    public void handle() {
        HabbiconManager manager = Emulator.getGameEnvironment().getHabbiconManager();
        if (this.client.getHabbo() == null || manager == null) {
            return;
        }

        manager.sendShopData(this.client.getHabbo());
    }
}
