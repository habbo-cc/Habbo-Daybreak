package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormInstanceSummary;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public final class SnowStormInstanceListComposer extends MessageComposer {
    private final List<SnowStormInstanceSummary> values;

    public SnowStormInstanceListComposer(List<SnowStormInstanceSummary> values) {
        if (values == null || values.size() > SnowStormProtocol.MAX_INSTANCE_ROWS) {
            throw new IllegalArgumentException("Invalid SnowStorm instance-list size");
        }
        this.values = List.copyOf(values);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ShockwaveSnowStormInstanceListComposer);
        this.response.appendShort(this.values.size());
        this.values.forEach(value -> SnowStormWire.writeInstance(this.response, value));
        return this.response;
    }
}
