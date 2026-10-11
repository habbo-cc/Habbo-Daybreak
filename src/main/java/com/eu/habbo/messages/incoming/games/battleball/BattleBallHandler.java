package com.eu.habbo.messages.incoming.games.battleball;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.battleball.BattleBallManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallFailureComposer;

abstract class BattleBallHandler extends MessageHandler {
    protected final BattleBallManager manager() { return Emulator.getGameEnvironment().getBattleBallManager(); }
    protected final Habbo habbo() { return this.client.getHabbo(); }
    protected final void requireEnd() { if (this.packet.bytesAvailable() != 0) throw new MalformedPacketException("unexpected trailing BattleBall payload"); }
    protected final void failure(int header, int instanceId, int reason) {
        if (reason >= 0) this.client.sendResponse(new BattleBallFailureComposer(header, instanceId, reason));
    }
    @Override public int getRatelimit() { return 100; }
}
