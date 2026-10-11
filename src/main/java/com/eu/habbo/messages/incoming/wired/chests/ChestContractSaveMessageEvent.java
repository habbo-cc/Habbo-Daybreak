package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractData;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractRule;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.outgoing.wired.chests.ChestContractUpdateResultComposer;
import com.eu.habbo.messages.wired.chests.ChestContractWire;

/** Exact July save-contract payload on the local parity header. */
public final class ChestContractSaveMessageEvent extends AbstractChestContractMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        int type = this.packet.readRequiredShort();
        if (type < InteractionChestContract.TYPE_PAYMENT
                || type > InteractionChestContract.TYPE_REWARD) {
            throw new MalformedPacketException("invalid chest contract type " + type);
        }

        ContractData data = ChestContractWire.readDefinition(this.packet, type);
        if (type == InteractionChestContract.TYPE_PAYMENT) {
            data.paymentMode = this.packet.readRequiredShort();
            data.receiveText = this.packet.readBoundedString(60);
            data.layoutType = this.packet.readBoundedString(16);
        } else if (type == InteractionChestContract.TYPE_REWARD) {
            data.rewardCategory = this.packet.readRequiredShort();
            data.showDialog = this.packet.readRequiredBoolean();
            data.rewardText = this.packet.readBoundedString(200);
        }
        requireEnd();

        Room room = currentContractRoom();
        InteractionChestContract contract = contract(room, visibleId);
        if (contract == null || contract.contractType() != type) {
            return;
        }

        ContractData sanitized = InteractionChestContract.sanitize(data, type);
        if (!hasRequiredRules(sanitized, type)) {
            this.client.sendResponse(new ChestContractUpdateResultComposer(
                    contract.getRoomVisibleId(), false, "invalid_rules"));
            return;
        }
        contract.saveContractData(sanitized, room);
        this.client.sendResponse(new ChestContractUpdateResultComposer(
                contract.getRoomVisibleId(), true, ""));
    }

    static boolean hasRequiredRules(ContractData data, int type) {
        boolean requiresGiveRules =
                type == InteractionChestContract.TYPE_TRADE
                        || (type == InteractionChestContract.TYPE_PAYMENT
                                && data.paymentMode == 1);
        if (requiresGiveRules
                && (data.giveRules == null || data.giveRules.isEmpty())) {
            return false;
        }
        if (type != InteractionChestContract.TYPE_PAYMENT && !hasNodes(data.getRule)) {
            return false;
        }
        if (data.giveRules != null) {
            for (ContractRule rule : data.giveRules) {
                if (!hasNodes(rule)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasNodes(ContractRule rule) {
        return rule != null && rule.nodes != null && !rule.nodes.isEmpty();
    }
}
