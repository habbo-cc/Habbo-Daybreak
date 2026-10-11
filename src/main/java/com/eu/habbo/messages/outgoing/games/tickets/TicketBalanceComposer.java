package com.eu.habbo.messages.outgoing.games.tickets;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class TicketBalanceComposer extends MessageComposer {
    private final int balance;

    public TicketBalanceComposer(int balance) {
        this.balance = Math.max(0, balance);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TicketBalanceComposer);
        this.response.appendInt(this.balance);
        return this.response;
    }
}
