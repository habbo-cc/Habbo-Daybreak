package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormInstanceRemovedComposer extends MessageComposer {
    private final int rowKind;
    private final int id;

    public SnowStormInstanceRemovedComposer(int rowKind, int id) {
        this.rowKind = rowKind;
        this.id = id;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormInstanceRemovedComposer);
        response.appendByte(rowKind);
        response.appendInt(id);
        return response;
    }
}
