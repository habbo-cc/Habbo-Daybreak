package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredSelectorDataMessageComposer extends MessageComposer {
    private final InteractionWiredSelector selector;
    private final Room room;

    public WiredSelectorDataMessageComposer(InteractionWiredSelector selector, Room room) {
        this.selector = selector;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredSelectorDataMessageComposer);
        this.selector.serializeWiredDataV2(this.response, this.room);
        this.selector.needsUpdate(true);
        return this.response;
    }
}
