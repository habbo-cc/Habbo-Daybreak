package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.messages.outgoing.Outgoing;

/** Owner-only request to add a game-scoped bot to one team. */
public final class BattleBallAddBotEvent extends BattleBallHandler {
    @Override public void handle() {
        int instanceId = this.packet.readRequiredInt();
        int teamId = this.packet.readRequiredInt();
        this.requireEnd();
        if (this.habbo() != null) this.failure(Outgoing.BattleBallInstanceNotAvailableComposer,
                instanceId, this.manager().addBot(this.habbo(), instanceId, teamId));
    }
}
