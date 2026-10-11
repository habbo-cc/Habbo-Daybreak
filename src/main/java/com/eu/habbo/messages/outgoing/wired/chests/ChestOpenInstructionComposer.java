package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July S2C 1174 shape on collision-checked local header 7120. */
public final class ChestOpenInstructionComposer extends MessageComposer {
    private final int chestVisibleId;

    public ChestOpenInstructionComposer(int chestVisibleId) {
        this.chestVisibleId = chestVisibleId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestOpenInstructionComposer);
        this.response.appendInt(this.chestVisibleId);
        return this.response;
    }
}
