package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormInstanceSummary;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormManager;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameInstanceComposer;

public final class SnowStormSetGameParametersEvent extends SnowStormHandler {
    @Override public void handle() {
        String name = packet.readBoundedString(32).trim();
        int mapId = requiredByte(1, 7);
        int lengthChoice = requiredByte(1, 3);
        int teamCount = requiredByte(1, 4);
        int selectedTeam = requiredByte(0, teamCount - 1);
        requireEnd();
        if (habbo() == null) return;
        SnowStormManager.Result result = manager().create(habbo(), new SnowStormManager.Creation(
                name, mapId, lengthChoice, teamCount, selectedTeam));
        if (!result.success()) failure(SnowStormProtocol.OP_CREATE, 0, result.failure());
        else client.sendResponse(new SnowStormGameInstanceComposer(
                SnowStormInstanceSummary.live(result.game())));
    }
}
