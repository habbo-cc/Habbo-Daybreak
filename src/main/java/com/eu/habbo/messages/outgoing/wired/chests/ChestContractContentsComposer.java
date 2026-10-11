package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractData;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.wired.chests.ChestContractWire;

/** Exact July contract-contents payload on the local parity header. */
public final class ChestContractContentsComposer extends MessageComposer {
    private final InteractionChestContract contract;

    public ChestContractContentsComposer(InteractionChestContract contract) {
        this.contract = contract;
    }

    @Override
    protected ServerMessage composeInternal() {
        ContractData data = this.contract.contractData();
        int type = this.contract.contractType();
        this.response.init(Outgoing.ChestContractContentsComposer);
        this.response.appendInt(this.contract.getRoomVisibleId());
        this.response.appendShort(type);
        ChestContractWire.appendDefinition(this.response, data);
        if (type == InteractionChestContract.TYPE_PAYMENT) {
            this.response.appendShort(data.paymentMode);
            this.response.appendString(data.receiveText);
            this.response.appendString(data.layoutType);
        } else if (type == InteractionChestContract.TYPE_REWARD) {
            this.response.appendShort(data.rewardCategory);
            this.response.appendBoolean(data.showDialog);
            this.response.appendString(data.rewardText);
        }
        return this.response;
    }
}
