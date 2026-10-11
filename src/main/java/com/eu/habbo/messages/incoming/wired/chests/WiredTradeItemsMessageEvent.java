package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;

import java.util.ArrayList;
import java.util.List;

/** July C2S 3111 shape on local header 7034. */
public final class WiredTradeItemsMessageEvent extends AbstractChestMessageEvent {
    private static final int MAX_ITEMS = 1500;

    @Override
    public void handle() {
        boolean remove = this.packet.readRequiredBoolean();
        int count = this.packet.readBoundedCount(MAX_ITEMS, Integer.BYTES);
        List<Integer> itemIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            itemIds.add(this.packet.readRequiredInt());
        }
        requireEnd();
        Emulator.getGameEnvironment().getChestManager()
                .updateTradeItems(this.client, remove, itemIds);
    }
}
