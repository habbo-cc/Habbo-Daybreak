package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Tells one client that its player membership was forcibly removed. */
public final class BattleBallPlayerRemovedComposer extends MessageComposer {
    private final int instanceId;
    private final int reason;

    public BattleBallPlayerRemovedComposer(int instanceId, int reason) {
        this.instanceId = instanceId;
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallPlayerRemovedComposer);
        this.response.appendInt(this.instanceId);
        this.response.appendInt(this.reason);
        return this.response;
    }
}
