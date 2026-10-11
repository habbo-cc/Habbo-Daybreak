package com.eu.habbo.messages.incoming.wired.chests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractData;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractNode;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestContractSaveMessageEventTest {
    @Test
    void anythingPaymentAllowsAnEmptyGiveRuleList() {
        ContractData data = new ContractData();
        data.paymentMode = 0;
        data.giveRules = new ArrayList<>();

        assertTrue(
                ChestContractSaveMessageEvent.hasRequiredRules(
                        data, InteractionChestContract.TYPE_PAYMENT));
    }

    @Test
    void specificPaymentStillRequiresAGiveRule() {
        ContractData data = new ContractData();
        data.paymentMode = 1;
        data.giveRules = new ArrayList<>();

        assertFalse(
                ChestContractSaveMessageEvent.hasRequiredRules(
                        data, InteractionChestContract.TYPE_PAYMENT));

        data.giveRules = List.of(rule());
        assertTrue(
                ChestContractSaveMessageEvent.hasRequiredRules(
                        data, InteractionChestContract.TYPE_PAYMENT));
    }

    @Test
    void tradeStillRequiresBothSidesOfTheContract() {
        ContractData data = new ContractData();
        data.giveRules = List.of(rule());

        assertFalse(
                ChestContractSaveMessageEvent.hasRequiredRules(
                        data, InteractionChestContract.TYPE_TRADE));

        data.getRule = rule();
        assertTrue(
                ChestContractSaveMessageEvent.hasRequiredRules(
                        data, InteractionChestContract.TYPE_TRADE));
    }

    private static ContractRule rule() {
        ContractNode node = new ContractNode();
        node.type = 0;
        node.amount = 1;
        ContractRule rule = new ContractRule();
        rule.nodes.add(node);
        return rule;
    }
}
