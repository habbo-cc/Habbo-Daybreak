package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.chests.ChestContractPlan;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Exact July S2C 3650 payload on local header 7129. */
public final class WiredTradeInitiateComposer extends MessageComposer {
    private final ChestType chestType;
    private final ChestContractPlan contract;
    private final boolean showRequirementsImmediately;
    private final boolean overridePreviousTrade;
    private final int timeoutSeconds;

    public WiredTradeInitiateComposer(ChestType chestType,
            boolean overridePreviousTrade, int timeoutSeconds) {
        this.chestType = chestType;
        this.contract = null;
        this.showRequirementsImmediately = false;
        this.overridePreviousTrade = overridePreviousTrade;
        this.timeoutSeconds = Math.max(0, timeoutSeconds);
    }

    public WiredTradeInitiateComposer(
            ChestContractPlan contract,
            boolean showRequirementsImmediately,
            boolean overridePreviousTrade,
            int timeoutSeconds) {
        if (contract == null) {
            throw new IllegalArgumentException("contract");
        }
        this.chestType = null;
        this.contract = contract;
        this.showRequirementsImmediately = showRequirementsImmediately;
        this.overridePreviousTrade = overridePreviousTrade;
        this.timeoutSeconds = Math.max(0, Math.min(86_400, timeoutSeconds));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeInitiateComposer);
        if (this.contract == null) {
            // TradeRequirement: credit-furni only for coin chests, normal furni
            // only for furniture chests. Manual deposits have no reward rules.
            this.response.appendInt(this.chestType == ChestType.COINS ? 0 : 1);
            this.response.appendString("");
            this.response.appendString("generic");
        } else {
            appendContract();
        }
        this.response.appendBoolean(this.showRequirementsImmediately);
        this.response.appendBoolean(this.overridePreviousTrade);
        this.response.appendInt(this.timeoutSeconds);
        return this.response;
    }

    private void appendContract() {
        if (this.contract.giveRules() == null && this.contract.getRule() == null) {
            // July's "anything" payment contract uses the simple discriminator.
            // A type-4 payload without a give rule is rejected by the AIR client.
            this.response.appendInt(2);
            this.response.appendString(this.contract.receiveText());
            this.response.appendString(this.contract.layoutType());
            return;
        }
        this.response.appendInt(4);
        this.response.appendString(this.contract.receiveText());
        this.response.appendString(this.contract.layoutType());
        this.response.appendBoolean(this.contract.giveRules() != null);
        if (this.contract.giveRules() != null) {
            this.response.appendInt(this.contract.giveRules().size());
            for (ChestContractPlan.Rule rule : this.contract.giveRules()) {
                appendRule(rule);
            }
        }
        this.response.appendBoolean(this.contract.getRule() != null);
        if (this.contract.getRule() != null) {
            appendRule(this.contract.getRule());
        }
        this.response.appendInt(this.contract.multiplierMode());
        if (this.contract.multiplierMode() == ChestContractPlan.MULTIPLIER_FIXED
                || this.contract.multiplierMode() == ChestContractPlan.MULTIPLIER_AUTO) {
            this.response.appendInt(this.contract.multiplier());
        }
    }

    private void appendRule(ChestContractPlan.Rule rule) {
        this.response.appendInt(rule.nodes().size());
        for (ChestContractPlan.Node node : rule.nodes()) {
            this.response.appendByte(node.type());
            this.response.appendInt(node.amount());
            if (node.type() == 1) {
                this.response.appendBoolean(node.itemType().wall());
                this.response.appendInt(node.itemType().typeId());
                this.response.appendString(node.itemType().poster());
            }
        }
    }
}
