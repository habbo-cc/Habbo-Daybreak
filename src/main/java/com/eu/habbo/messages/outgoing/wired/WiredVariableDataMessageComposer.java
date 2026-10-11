package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July-compatible Wired 2.0 variable editor payload (header 7102). */
public class WiredVariableDataMessageComposer extends MessageComposer {
    private final InteractionWiredVariable variable;
    private final Room room;

    public WiredVariableDataMessageComposer(InteractionWiredVariable variable, Room room) {
        this.variable = variable;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariableDataMessageComposer);
        this.variable.serializeWiredDataV2(this.response, this.room);
        this.variable.needsUpdate(true);
        return this.response;
    }
}
