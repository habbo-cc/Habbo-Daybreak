package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormIdleWarningComposer extends MessageComposer {
    private final int instanceId;
    private final int secondsRemaining;

    public SnowStormIdleWarningComposer(int instanceId, int secondsRemaining) {
        this.instanceId = instanceId;
        this.secondsRemaining = secondsRemaining;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormIdleWarningComposer);
        response.appendInt(instanceId);
        response.appendInt(Math.max(0, secondsRemaining));
        return response;
    }
}
