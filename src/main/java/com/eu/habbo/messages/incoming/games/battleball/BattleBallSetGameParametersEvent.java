package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallManager;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPowerType;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallFailureComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameLocationComposer;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BattleBallSetGameParametersEvent extends BattleBallHandler {
    @Override public void handle() {
        String name = this.packet.readBoundedString(32).trim(); int mapId = this.packet.readRequiredInt();
        int roundSeconds = this.packet.readRequiredInt(); int teamCount = this.packet.readRequiredInt(); int teamCapacity = this.packet.readRequiredInt();
        int selectedTeam = this.packet.readRequiredInt(); boolean privateGame = this.packet.readRequiredBoolean();
        int count = this.packet.readBoundedCount(8, 4); Set<BattleBallPowerType> powers = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            BattleBallPowerType power = BattleBallPowerType.fromId(this.packet.readRequiredInt());
            if (power == null || power == BattleBallPowerType.QUESTION_MARK || !powers.add(power)) throw new MalformedPacketException("invalid or duplicate BattleBall power id");
        }
        this.requireEnd(); if (this.habbo() == null) return;
        BattleBallManager.Result result = this.manager().create(this.habbo(), new BattleBallManager.Creation(name, mapId, roundSeconds, teamCount, teamCapacity, selectedTeam, privateGame, powers));
        if (!result.success()) this.client.sendResponse(new BattleBallFailureComposer(Outgoing.BattleBallCreateFailedComposer, result.failure()));
        else this.client.sendResponse(new BattleBallGameLocationComposer(result.game(), false));
    }
}
