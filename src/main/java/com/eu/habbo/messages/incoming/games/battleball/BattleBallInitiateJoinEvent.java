package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallManager;
import com.eu.habbo.habbohotel.games.battleball.BattleBallState;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallFailureComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallJoinParametersComposer;
public final class BattleBallInitiateJoinEvent extends BattleBallHandler {
    @Override public void handle() { int id = this.packet.readRequiredInt(); this.requireEnd(); if (this.habbo() == null) return; BattleBallGame game = this.manager().discoverableGame(id, this.habbo().getHabboInfo().getId());
        if (game == null) this.client.sendResponse(new BattleBallFailureComposer(Outgoing.BattleBallJoinFailedComposer,
                id, BattleBallManager.FAILURE_NOT_FOUND));
        else if (game.state() != BattleBallState.LOBBY) this.client.sendResponse(new BattleBallFailureComposer(
                Outgoing.BattleBallJoinFailedComposer, id, BattleBallManager.FAILURE_BAD_STATE));
        else this.client.sendResponse(new BattleBallJoinParametersComposer(game)); }
}
