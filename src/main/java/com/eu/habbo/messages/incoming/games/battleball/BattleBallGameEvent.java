package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.messages.MalformedPacketException;
public final class BattleBallGameEvent extends BattleBallHandler {
    // Movement and activation have separate user/game buckets in BattleBallManager.
    @Override public int getRatelimit() { return 0; }
    @Override public void handle() { int id = this.packet.readRequiredInt(); int subtype = this.packet.readRequiredInt(); if (this.habbo() == null) return;
        if (subtype == 2) { int x = this.packet.readRequiredInt(); int y = this.packet.readRequiredInt(); this.requireEnd(); this.manager().move(this.habbo(), id, x, y); }
        else if (subtype == 4) { int powerId = this.packet.readRequiredInt(); this.requireEnd(); this.manager().activate(this.habbo(), id, powerId); }
        else throw new MalformedPacketException("unknown BattleBall game event subtype"); }
}
