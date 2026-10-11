package com.eu.habbo.messages.incoming.games.battleball;

public final class BattleBallRequestFullStatusEvent extends BattleBallHandler {
    // The manager applies an access-checked per-user/game recovery backoff.
    @Override public int getRatelimit() { return 0; }
    @Override public void handle() { int id = this.packet.readRequiredInt(); this.requireEnd(); if (this.habbo() != null) this.manager().requestFull(this.habbo(), id); }
}
