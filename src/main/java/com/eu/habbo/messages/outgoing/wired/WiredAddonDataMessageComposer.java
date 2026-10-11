package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July-compatible Wired 2.0 add-on editor payload (header 7101). */
public class WiredAddonDataMessageComposer extends MessageComposer {
    private final InteractionWiredAddon addon;
    private final Room room;

    public WiredAddonDataMessageComposer(InteractionWiredAddon addon, Room room) {
        this.addon = addon;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredAddonDataMessageComposer);
        this.addon.serializeWiredDataV2(this.response, this.room);
        this.addon.needsUpdate(true);
        return this.response;
    }
}
