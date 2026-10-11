package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredClickUserResponseMessageComposer extends MessageComposer {
    private final int roomUnitId;
    private final boolean openMenu;

    public WiredClickUserResponseMessageComposer(int roomUnitId, boolean openMenu) {
        this.roomUnitId = roomUnitId;
        this.openMenu = openMenu;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickUserResponseMessageComposer);
        this.response.appendInt(this.roomUnitId);
        this.response.appendBoolean(this.openMenu);
        return this.response;
    }
}
