package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class BattleBallLoungeInfoComposer extends MessageComposer {
    private final int ticketCost, skillLevel, ticketBalance;
    public BattleBallLoungeInfoComposer(int ticketCost, int skillLevel, int ticketBalance) {
        this.ticketCost = ticketCost; this.skillLevel = skillLevel;
        this.ticketBalance = Math.max(0, ticketBalance);
    }
    @Override protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallLoungeInfoComposer); this.response.appendInt(BattleBallWire.STATUS_VERSION);
        this.response.appendInt(this.ticketCost);
        this.response.appendInt(this.skillLevel); this.response.appendInt(this.ticketBalance); return this.response;
    }
}
