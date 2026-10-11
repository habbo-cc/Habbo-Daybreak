package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormFinishedGame;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormFinishedGameComposer extends MessageComposer {
    private final SnowStormFinishedGame result;

    public SnowStormFinishedGameComposer(SnowStormFinishedGame result) { this.result = result; }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormFinishedGameComposer);
        SnowStormWire.writeFinishedResult(response, result);
        return response;
    }
}
