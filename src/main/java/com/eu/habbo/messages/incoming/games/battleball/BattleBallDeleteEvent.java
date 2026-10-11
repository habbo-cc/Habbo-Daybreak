package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.messages.outgoing.Outgoing;
public final class BattleBallDeleteEvent extends BattleBallHandler {
    @Override public void handle() { int id = this.packet.readRequiredInt(); this.requireEnd(); if (this.habbo() != null) this.failure(Outgoing.BattleBallInstanceNotAvailableComposer, id, this.manager().delete(this.habbo(), id)); }
}
