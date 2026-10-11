package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.messages.outgoing.Outgoing;
public final class BattleBallWatchEvent extends BattleBallHandler {
    @Override public void handle() { int id = this.packet.readRequiredInt(); this.requireEnd(); if (this.habbo() != null) this.failure(Outgoing.BattleBallWatchFailedComposer, id, this.manager().watch(this.habbo(), id)); }
}
