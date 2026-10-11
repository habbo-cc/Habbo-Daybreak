package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallManager;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameLocationComposer;
public final class BattleBallSetJoinParametersEvent extends BattleBallHandler {
    @Override public void handle() { int id = this.packet.readRequiredInt(); int team = this.packet.readRequiredInt(); this.requireEnd(); if (this.habbo() == null) return;
        BattleBallManager.Result result = this.manager().join(this.habbo(), id, team);
        this.failure(Outgoing.BattleBallJoinFailedComposer, id, result.failure());
        if (result.success()) this.client.sendResponse(new BattleBallGameLocationComposer(result.game(), false)); }
}
