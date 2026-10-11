package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormRemoveBotEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt();
        int botUserId = requiredNegativeInt();
        requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_REMOVE_BOT, instanceId,
                manager().removeBot(habbo(), instanceId, botUserId));
    }
}
