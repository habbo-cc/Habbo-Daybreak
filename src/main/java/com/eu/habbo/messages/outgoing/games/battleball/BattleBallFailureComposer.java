package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public final class BattleBallFailureComposer extends MessageComposer {
    private final int header, instanceId, reason; private final boolean includeInstance;
    public BattleBallFailureComposer(int header, int reason) { this(header, 0, reason, false); }
    public BattleBallFailureComposer(int header, int instanceId, int reason) { this(header, instanceId, reason, true); }
    private BattleBallFailureComposer(int header, int instanceId, int reason, boolean includeInstance) {
        this.header = header; this.instanceId = instanceId; this.reason = reason; this.includeInstance = includeInstance;
    }
    @Override protected ServerMessage composeInternal() {
        this.response.init(this.header); if (this.includeInstance) this.response.appendInt(this.instanceId); this.response.appendInt(this.reason); return this.response;
    }
}
