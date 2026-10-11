package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormKickEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt();
        int targetUserId = requiredPositiveInt(); requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_KICK, instanceId,
                manager().kick(habbo(), instanceId, targetUserId));
    }
}
