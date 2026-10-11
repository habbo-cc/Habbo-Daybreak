package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormDeleteEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt(); requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_DELETE, instanceId,
                manager().delete(habbo(), instanceId));
    }
}
