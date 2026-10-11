package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July S2C 2721 shape on local header 7125; success is result code zero. */
public final class ChestUpgradeResultComposer extends MessageComposer {
    private final int chestVisibleId;
    private final int resultCode;

    public ChestUpgradeResultComposer(int chestVisibleId, int resultCode) {
        this.chestVisibleId = chestVisibleId;
        this.resultCode = resultCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestUpgradeResultComposer);
        this.response.appendInt(this.chestVisibleId);
        this.response.appendInt(this.resultCode);
        return this.response;
    }
}
