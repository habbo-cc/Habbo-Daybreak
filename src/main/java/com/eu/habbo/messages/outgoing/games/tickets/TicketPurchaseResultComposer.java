package com.eu.habbo.messages.outgoing.games.tickets;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class TicketPurchaseResultComposer extends MessageComposer {
    private final int status;
    private final String recipient;
    private final int ticketsAdded;

    public TicketPurchaseResultComposer(int status, String recipient, int ticketsAdded) {
        this.status = status;
        this.recipient = recipient == null ? "" : recipient;
        this.ticketsAdded = ticketsAdded;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TicketPurchaseResultComposer);
        this.response.appendInt(this.status);
        this.response.appendString(this.recipient);
        this.response.appendInt(this.ticketsAdded);
        return this.response;
    }
}
