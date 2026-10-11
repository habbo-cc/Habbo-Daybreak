package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;

/** July C2S 2818 shape on local header 7033. */
public final class WiredTradeAcceptMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        boolean confirm = this.packet.readRequiredBoolean();
        requireEnd();
        Emulator.getGameEnvironment().getChestManager()
                .acceptTrade(this.client, confirm);
    }
}
