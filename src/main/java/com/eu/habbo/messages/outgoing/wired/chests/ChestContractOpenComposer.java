package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Exact July contract-open payload on the local parity header. */
public final class ChestContractOpenComposer extends MessageComposer {
    private final int contractId;

    public ChestContractOpenComposer(int contractId) {
        this.contractId = contractId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestContractOpenComposer);
        this.response.appendInt(this.contractId);
        return this.response;
    }
}
