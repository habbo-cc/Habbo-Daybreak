package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AreaHideMessageComposer extends MessageComposer {
    private final InteractionAreaHide item;

    public AreaHideMessageComposer(InteractionAreaHide item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AreaHideMessageComposer);
        this.response.appendInt(this.item.getRoomVisibleId());
        this.response.appendBoolean(this.item.isEnabled());
        this.response.appendInt(this.item.getRootX());
        this.response.appendInt(this.item.getRootY());
        this.response.appendInt(this.item.getAreaWidth());
        this.response.appendInt(this.item.getAreaLength());
        this.response.appendBoolean(this.item.isInverted());
        return this.response;
    }
}
