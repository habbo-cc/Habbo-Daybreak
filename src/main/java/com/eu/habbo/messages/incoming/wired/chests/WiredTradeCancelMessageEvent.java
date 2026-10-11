package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;

/** July C2S 2646 is an empty payload; local header 7032. */
public final class WiredTradeCancelMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        requireEnd();
        Emulator.getGameEnvironment().getChestManager()
                .abortTrade(this.client, true, 0);
    }
}
