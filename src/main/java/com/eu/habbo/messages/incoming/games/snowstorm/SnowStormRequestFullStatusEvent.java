package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormRequestFullStatusEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt();
        int lastSequence = requiredSequenceCursor();
        int reason = requiredByte(0, 3); requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_FULL_STATUS, instanceId,
                manager().requestFull(habbo(), instanceId, lastSequence, reason));
    }
}
