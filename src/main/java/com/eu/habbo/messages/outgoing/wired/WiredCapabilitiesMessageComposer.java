package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.core.WiredCapabilityState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Revision-1 Wired 2.0 capability response (header 7199). */
public class WiredCapabilitiesMessageComposer extends MessageComposer {
    private final WiredCapabilityState state;

    public WiredCapabilitiesMessageComposer(WiredCapabilityState state) {
        this.state = state == null ? WiredCapabilityState.unsupported() : state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredCapabilitiesMessageComposer);
        this.response.appendInt(this.state.negotiatedRevision());
        this.response.appendInt(this.state.connectionCapabilityMask());
        this.response.appendInt(this.state.roomId());
        this.response.appendInt(this.state.roomCapabilityMask());
        return this.response;
    }
}
