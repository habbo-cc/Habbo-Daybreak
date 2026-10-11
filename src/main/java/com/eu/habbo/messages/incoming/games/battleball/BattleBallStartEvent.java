package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.messages.outgoing.Outgoing;
public final class BattleBallStartEvent extends BattleBallHandler {
    @Override public void handle() { int id = this.packet.readRequiredInt(); this.requireEnd(); if (this.habbo() != null) this.failure(Outgoing.BattleBallStartFailedComposer, id, this.manager().start(this.habbo(), id)); }
}
