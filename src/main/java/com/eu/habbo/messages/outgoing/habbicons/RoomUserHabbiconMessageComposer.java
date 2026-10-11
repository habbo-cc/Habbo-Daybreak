package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomUserHabbiconMessageComposer extends MessageComposer {
    private final int roomIndex;
    private final int habbiconId;

    public RoomUserHabbiconMessageComposer(int roomIndex, int habbiconId) {
        this.roomIndex = roomIndex;
        this.habbiconId = habbiconId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserHabbiconMessageComposer);
        this.response.appendInt(this.roomIndex);
        this.response.appendInt(this.habbiconId);
        return this.response;
    }
}
