package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormManager;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormJoinParametersComposer;

public final class SnowStormInitiateJoinEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt(); requireEnd();
        if (habbo() == null) return;
        SnowStormManager.Result result = manager().initiateJoin(habbo(), instanceId);
        if (!result.success()) failure(SnowStormProtocol.OP_INITIATE_JOIN, instanceId, result.failure());
        else {
            int selectedTeam = Math.max(0, result.game().playerTeam(habbo().getHabboInfo().getId()));
            client.sendResponse(new SnowStormJoinParametersComposer(result.game(), selectedTeam));
        }
    }
}
