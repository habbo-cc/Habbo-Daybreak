package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredTriggerDataMessageComposer extends MessageComposer {
    private final InteractionWiredTrigger trigger;
    private final Room room;

    public WiredTriggerDataMessageComposer(InteractionWiredTrigger trigger, Room room) {
        this.trigger = trigger;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTriggerDataMessageComposer);
        this.trigger.serializeWiredDataV2(this.response, this.room);
        this.trigger.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredTrigger getTrigger() {
        return trigger;
    }

    public Room getRoom() {
        return room;
    }
}
