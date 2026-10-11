package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormInstanceSummary;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormManager;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameInstanceComposer;

public final class SnowStormSetJoinParametersEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt();
        int teamId = requiredByte(0, 3); requireEnd();
        if (habbo() == null) return;
        SnowStormManager.Result result = manager().join(habbo(), instanceId, teamId);
        if (!result.success()) failure(SnowStormProtocol.OP_JOIN, instanceId, result.failure());
        else {
            client.sendResponse(new SnowStormGameInstanceComposer(
                    SnowStormInstanceSummary.live(result.game())));
        }
    }
}
