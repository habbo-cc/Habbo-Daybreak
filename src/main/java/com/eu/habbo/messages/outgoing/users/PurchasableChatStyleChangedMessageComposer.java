package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class PurchasableChatStyleChangedMessageComposer extends MessageComposer {
    private final int styleId;
    private final boolean owned;

    public PurchasableChatStyleChangedMessageComposer(int styleId, boolean owned) {
        this.styleId = styleId;
        this.owned = owned;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PurchasableChatStyleChangedMessageComposer);
        this.response.appendInt(this.styleId);
        this.response.appendBoolean(this.owned);
        return this.response;
    }
}
