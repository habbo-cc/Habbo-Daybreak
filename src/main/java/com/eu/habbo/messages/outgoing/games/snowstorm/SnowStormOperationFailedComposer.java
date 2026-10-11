package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormOperationFailedComposer extends MessageComposer {
    private final int operation;
    private final int instanceId;
    private final int reason;

    public SnowStormOperationFailedComposer(int operation, int instanceId, int reason) {
        this.operation = operation;
        this.instanceId = instanceId;
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormOperationFailedComposer);
        response.appendByte(operation);
        response.appendInt(instanceId);
        response.appendByte(reason);
        return response;
    }
}
