package com.eu.habbo.messages.wired.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractData;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractItemType;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractNode;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractRule;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.ServerMessage;

import java.util.ArrayList;

/** Exact July AIR contract-rule wire codec with defensive packet bounds. */
public final class ChestContractWire {
    private static final int MAX_RULES = 64;
    private static final int MAX_NODES = 256;
    private static final int MAX_POSTER_LENGTH = 64;

    private ChestContractWire() {
    }

    public static ContractData readDefinition(ClientMessage packet, int contractType) {
        ContractData data = new ContractData();
        if (packet.readRequiredBoolean()) {
            int count = packet.readBoundedCount(MAX_RULES, Integer.BYTES);
            data.giveRules = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                data.giveRules.add(readRule(packet));
            }
        } else {
            data.giveRules = null;
        }
        data.getRule = packet.readRequiredBoolean() ? readRule(packet) : null;
        return data;
    }

    private static ContractRule readRule(ClientMessage packet) {
        int count = packet.readBoundedCount(MAX_NODES, 5);
        ContractRule rule = new ContractRule();
        for (int index = 0; index < count; index++) {
            ContractNode node = new ContractNode();
            node.type = packet.readRequiredByte();
            node.amount = packet.readRequiredInt();
            if (node.type == 1) {
                ContractItemType itemType = new ContractItemType();
                itemType.wall = packet.readRequiredBoolean();
                itemType.typeId = packet.readRequiredInt();
                itemType.poster = packet.readBoundedString(MAX_POSTER_LENGTH);
                node.itemType = itemType;
            } else if (node.type != 0) {
                throw new MalformedPacketException("invalid contract node type " + node.type);
            }
            rule.nodes.add(node);
        }
        return rule;
    }

    public static void appendDefinition(ServerMessage response, ContractData data) {
        response.appendBoolean(data.giveRules != null);
        if (data.giveRules != null) {
            response.appendInt(data.giveRules.size());
            for (ContractRule rule : data.giveRules) {
                appendRule(response, rule);
            }
        }
        response.appendBoolean(data.getRule != null);
        if (data.getRule != null) {
            appendRule(response, data.getRule);
        }
    }

    private static void appendRule(ServerMessage response, ContractRule rule) {
        response.appendInt(rule.nodes.size());
        for (ContractNode node : rule.nodes) {
            response.appendByte(node.type);
            response.appendInt(node.amount);
            if (node.type == 1) {
                response.appendBoolean(node.itemType.wall);
                response.appendInt(node.itemType.typeId);
                response.appendString(node.itemType.poster);
            }
        }
    }
}
