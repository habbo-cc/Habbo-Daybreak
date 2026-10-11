package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormRequestFinishedGameEvent extends SnowStormHandler {
    @Override public void handle() {
        int resultId = requiredPositiveInt(); requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_FINISHED_RESULT, resultId,
                manager().requestFinished(habbo(), resultId));
    }
}
