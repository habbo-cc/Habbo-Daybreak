package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Set;

public class PurchasableChatStylesMessageComposer extends MessageComposer {
    private final Set<Integer> styleIds;

    public PurchasableChatStylesMessageComposer(Set<Integer> styleIds) {
        this.styleIds = styleIds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PurchasableChatStylesMessageComposer);
        this.response.appendInt(this.styleIds.size());

        for (Integer styleId : this.styleIds) {
            this.response.appendInt(styleId);
        }

        return this.response;
    }
}
