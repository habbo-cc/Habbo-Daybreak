package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormAddBotEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt();
        int teamId = requiredByte(0, 3);
        requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_ADD_BOT, instanceId,
                manager().addBot(habbo(), instanceId, teamId));
    }
}
