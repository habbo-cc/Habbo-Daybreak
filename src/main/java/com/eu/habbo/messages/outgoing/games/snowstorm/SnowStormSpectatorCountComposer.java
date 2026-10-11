package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormSpectatorCountComposer extends MessageComposer {
    private final int instanceId;
    private final int count;

    public SnowStormSpectatorCountComposer(int instanceId, int count) {
        this.instanceId = instanceId;
        this.count = count;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormSpectatorCountComposer);
        response.appendInt(instanceId);
        response.appendShort(Math.max(0, Math.min(20, count)));
        return response;
    }
}
