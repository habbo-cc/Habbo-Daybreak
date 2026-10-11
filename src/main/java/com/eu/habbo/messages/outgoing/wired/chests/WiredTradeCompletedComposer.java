package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Exact July S2C 2137 payload on local header 7127. */
public final class WiredTradeCompletedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeCompletedComposer);
        return this.response;
    }
}
