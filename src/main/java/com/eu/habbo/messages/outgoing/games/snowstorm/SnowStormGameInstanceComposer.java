package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormInstanceSummary;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormGameInstanceComposer extends MessageComposer {
    private final SnowStormInstanceSummary value;

    public SnowStormGameInstanceComposer(SnowStormInstanceSummary value) {
        this.value = value;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormGameInstanceComposer);
        SnowStormWire.writeInstance(response, value);
        return response;
    }
}
