package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.messages.outgoing.Outgoing;

/** Owner-only request to remove a negative-id bot from a game lobby. */
public final class BattleBallRemoveBotEvent extends BattleBallHandler {
    @Override public void handle() {
        int instanceId = this.packet.readRequiredInt();
        int botUserId = this.packet.readRequiredInt();
        this.requireEnd();
        if (this.habbo() != null) this.failure(Outgoing.BattleBallInstanceNotAvailableComposer,
                instanceId, this.manager().removeBot(this.habbo(), instanceId, botUserId));
    }
}
