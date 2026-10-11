package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class HabbiconStatusChangedMessageComposer extends MessageComposer {
    private final int habbiconId;
    private final int state;

    public HabbiconStatusChangedMessageComposer(int habbiconId, int state) {
        this.habbiconId = habbiconId;
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconStatusChangedMessageComposer);
        this.response.appendInt(this.habbiconId);
        this.response.appendInt(this.state);
        return this.response;
    }
}
