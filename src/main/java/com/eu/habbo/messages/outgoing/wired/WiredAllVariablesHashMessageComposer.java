package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR-shaped aggregate variable-catalog hash response on local header 7106. */
public final class WiredAllVariablesHashMessageComposer extends MessageComposer {
    private final int aggregateHash;

    public WiredAllVariablesHashMessageComposer(int aggregateHash) {
        this.aggregateHash = aggregateHash;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredAllVariablesHashMessageComposer);
        this.response.appendInt(this.aggregateHash);
        return this.response;
    }
}
