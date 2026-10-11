package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Exact July S2C 1481 payload on local header 7126. */
public final class WiredTradeCancelledComposer extends MessageComposer {
    private final int reason;
    public WiredTradeCancelledComposer(int reason) {
        this.reason = reason;
    }
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeCancelledComposer);
        this.response.appendInt(this.reason);
        return this.response;
    }
}
