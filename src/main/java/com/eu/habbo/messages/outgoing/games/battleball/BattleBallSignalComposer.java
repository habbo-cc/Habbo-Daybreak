package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public final class BattleBallSignalComposer extends MessageComposer {
    private final int header; private final int[] values;
    public BattleBallSignalComposer(int header, int... values) { this.header = header; this.values = values; }
    @Override protected ServerMessage composeInternal() {
        this.response.init(this.header); for (int value : this.values) this.response.appendInt(value); return this.response;
    }
}
