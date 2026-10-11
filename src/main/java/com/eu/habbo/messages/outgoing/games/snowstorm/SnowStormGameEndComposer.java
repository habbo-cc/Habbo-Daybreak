package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormRoundResult;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormGameEndComposer extends MessageComposer {
    private final SnowStormRoundResult result;
    private final int nextGameCountdownMs;

    public SnowStormGameEndComposer(SnowStormRoundResult result, int nextGameCountdownMs) {
        this.result = result;
        this.nextGameCountdownMs = nextGameCountdownMs;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormGameEndComposer);
        SnowStormWire.writeLiveResult(response, result, nextGameCountdownMs);
        return response;
    }
}
