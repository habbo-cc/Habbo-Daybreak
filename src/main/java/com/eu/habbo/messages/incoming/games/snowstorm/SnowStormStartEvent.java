package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormStartEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt(); requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_START, instanceId,
                manager().start(habbo(), instanceId));
    }
}
