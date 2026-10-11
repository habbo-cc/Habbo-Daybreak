package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Exact July contract-save result payload on the local parity header. */
public final class ChestContractUpdateResultComposer extends MessageComposer {
    private final int contractId;
    private final boolean success;
    private final String failCode;

    public ChestContractUpdateResultComposer(int contractId, boolean success, String failCode) {
        this.contractId = contractId;
        this.success = success;
        this.failCode = failCode == null ? "" : failCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestContractUpdateResultComposer);
        this.response.appendInt(this.contractId);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.failCode);
        return this.response;
    }
}
