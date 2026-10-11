package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormPlayerRemovedComposer extends MessageComposer {
    private final int instanceId;
    private final int reason;

    public SnowStormPlayerRemovedComposer(int instanceId, int reason) {
        this.instanceId = instanceId;
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormPlayerRemovedComposer);
        response.appendInt(instanceId);
        response.appendByte(reason);
        return response;
    }
}
