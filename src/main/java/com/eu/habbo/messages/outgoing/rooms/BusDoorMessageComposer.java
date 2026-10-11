package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Tells the park client whether the Infobus doors are open. The park visualization swaps the
 * "bus" / "bus_oviopen_hidden" layout elements based on this flag.
 */
public class BusDoorMessageComposer extends MessageComposer {
    private final boolean open;

    public BusDoorMessageComposer(boolean open) {
        this.open = open;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BusDoorMessageComposer);
        this.response.appendBoolean(this.open);
        return this.response;
    }
}
