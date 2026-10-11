package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Optional side-channel packet so the existing v2 status layout remains stable. */
public final class BattleBallSpectatorCountComposer extends MessageComposer {
    private final int instanceId;
    private final int spectatorCount;

    public BattleBallSpectatorCountComposer(int instanceId, int spectatorCount) {
        this.instanceId = instanceId;
        this.spectatorCount = Math.max(0, spectatorCount);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallSpectatorCountComposer);
        this.response.appendInt(this.instanceId);
        this.response.appendInt(this.spectatorCount);
        return this.response;
    }
}
