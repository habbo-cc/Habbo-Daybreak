package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractData;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractItemType;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractNode;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.ContractRule;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCustomContract;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.util.ArrayList;
import java.util.List;

/** Immutable, execution-owned July contract definition. */
public final class ChestContractPlan {
    public static final int MULTIPLIER_NONE = 0;
    public static final int MULTIPLIER_FIXED = 1;
    public static final int MULTIPLIER_AUTO = 2;

    private final int contractId;
    private final int contractVisibleId;
    private final int contractType;
    private final List<Rule> giveRules;
    private final Rule getRule;
    private final String receiveText;
    private final String layoutType;
    private final int rewardCategory;
    private final boolean showDialog;
    private final String rewardText;
    private final int multiplierMode;
    private final int multiplier;

    private ChestContractPlan(
            int contractId,
            int contractVisibleId,
            int contractType,
            List<Rule> giveRules,
            Rule getRule,
            String receiveText,
            String layoutType,
            int rewardCategory,
            boolean showDialog,
            String rewardText,
            int multiplierMode,
            int multiplier) {
        this.contractId = contractId;
        this.contractVisibleId = contractVisibleId;
        this.contractType = contractType;
        this.giveRules = giveRules == null ? null : List.copyOf(giveRules);
        this.getRule = getRule;
        this.receiveText = safe(receiveText, 60);
        this.layoutType = "games".equals(layoutType) ? "games" : "generic";
        this.rewardCategory = rewardCategory == 13 ? 13 : 11;
        this.showDialog = showDialog;
        this.rewardText = safe(rewardText, 200);
        this.multiplierMode = multiplierMode;
        this.multiplier = multiplier;
        validate();
    }

    public static ChestContractPlan fromContract(
            InteractionChestContract contract, int multiplierMode, int multiplier) {
        if (contract == null) {
            return null;
        }
        ContractData data = contract.contractData();
        List<Rule> giveRules = null;
        if (data.giveRules != null && data.paymentMode == 1) {
            giveRules =
                    data.giveRules.stream()
                            .map(Rule::from)
                            .filter(java.util.Objects::nonNull)
                            .toList();
        }
        Rule getRule = Rule.from(data.getRule);
        int contractType = contract.contractType();
        if ((contractType == InteractionChestContract.TYPE_PAYMENT
                        && data.paymentMode == 1
                        && (giveRules == null || giveRules.isEmpty()))
                || (contractType == InteractionChestContract.TYPE_TRADE
                        && (giveRules == null || giveRules.isEmpty() || getRule == null))
                || (contractType == InteractionChestContract.TYPE_REWARD && getRule == null)) {
            return null;
        }
        return new ChestContractPlan(
                contract.getId(),
                contract.getRoomVisibleId(),
                contractType,
                giveRules,
                getRule,
                data.receiveText,
                data.layoutType,
                data.rewardCategory,
                data.showDialog,
                data.rewardText,
                multiplierMode,
                multiplier);
    }

    public static ChestContractPlan fromCustomAddon(
            WiredAddonCustomContract addon,
            WiredContext context,
            int multiplierMode,
            int multiplier) {
        if (addon == null || context == null) {
            return null;
        }
        WiredAddonCustomContract.ContractSide payment = addon.payment();
        WiredAddonCustomContract.ContractSide reward = addon.reward();
        Node paymentNode =
                payment.enabled()
                        ? customNode(
                                payment,
                                addon.resolvePaymentAmount(context),
                                addon.resolvePaymentFurniture(context))
                        : null;
        Node rewardNode =
                reward.enabled()
                        ? customNode(
                                reward,
                                addon.resolveRewardAmount(context),
                                addon.resolveRewardFurniture(context))
                        : null;
        if ((payment.enabled() && paymentNode == null)
                || (reward.enabled() && rewardNode == null)
                || (paymentNode == null && rewardNode == null)) {
            return null;
        }
        int type =
                paymentNode == null
                        ? InteractionChestContract.TYPE_REWARD
                        : rewardNode == null
                                ? InteractionChestContract.TYPE_PAYMENT
                                : InteractionChestContract.TYPE_TRADE;
        List<Rule> give = paymentNode == null ? null : List.of(new Rule(List.of(paymentNode)));
        Rule get = rewardNode == null ? null : new Rule(List.of(rewardNode));
        return new ChestContractPlan(
                addon.getId(),
                addon.getRoomVisibleId(),
                type,
                give,
                get,
                "",
                "generic",
                11,
                true,
                "",
                multiplierMode,
                multiplier);
    }

    private static Node customNode(
            WiredAddonCustomContract.ContractSide side,
            Integer amount,
            java.util.Collection<HabboItem> furniture) {
        if (side == null || amount == null || amount < 1 || amount > 100_000) {
            return null;
        }
        if (side.type() == WiredAddonCustomContract.TYPE_COINS) {
            return new Node(0, amount, null);
        }
        HabboItem sample =
                furniture == null
                        ? null
                        : furniture.stream()
                                .filter(item -> item != null && item.getBaseItem() != null)
                                .findFirst()
                                .orElse(null);
        if (sample == null) {
            return null;
        }
        ItemType type =
                new ItemType(
                        sample.getBaseItem().getType() == FurnitureType.WALL,
                        sample.getBaseItem().getSpriteId(),
                        posterId(sample));
        return new Node(1, amount, type);
    }

    private static String posterId(HabboItem item) {
        if (item == null
                || item.getBaseItem() == null
                || item.getBaseItem().getType() != FurnitureType.WALL
                || item.getBaseItem().getName() == null
                || !item.getBaseItem().getName().startsWith("poster")) {
            return "";
        }
        String data = item.getExtradata();
        return data == null ? "" : safe(data, 64);
    }

    private void validate() {
        if (this.contractId <= 0
                || this.contractVisibleId == 0
                || this.contractType < InteractionChestContract.TYPE_PAYMENT
                || this.contractType > InteractionChestContract.TYPE_REWARD
                || this.multiplierMode < MULTIPLIER_NONE
                || this.multiplierMode > MULTIPLIER_AUTO
                || this.multiplier < 1
                || this.multiplier > 500
                || (this.contractType == InteractionChestContract.TYPE_PAYMENT
                        && this.getRule != null)
                || (this.contractType == InteractionChestContract.TYPE_REWARD
                        && this.giveRules != null)) {
            throw new IllegalArgumentException("invalid chest contract plan");
        }
    }

    private static String safe(String value, int max) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\0', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    public int contractId() {
        return this.contractId;
    }

    public int contractVisibleId() {
        return this.contractVisibleId;
    }

    public int contractType() {
        return this.contractType;
    }

    public List<Rule> giveRules() {
        return this.giveRules;
    }

    public Rule getRule() {
        return this.getRule;
    }

    public String receiveText() {
        return this.receiveText;
    }

    public String layoutType() {
        return this.layoutType;
    }

    public int rewardCategory() {
        return this.rewardCategory;
    }

    public boolean showDialog() {
        return this.showDialog;
    }

    public String rewardText() {
        return this.rewardText;
    }

    public int multiplierMode() {
        return this.multiplierMode;
    }

    public int multiplier() {
        return this.multiplier;
    }

    public record ItemType(boolean wall, int typeId, String poster) {
        public ItemType {
            if (typeId <= 0) {
                throw new IllegalArgumentException("invalid contract furniture type");
            }
            poster = safe(poster, 64);
        }

        static ItemType from(ContractItemType type) {
            return type == null ? null : new ItemType(type.wall, type.typeId, type.poster);
        }
    }

    public record Node(int type, int amount, ItemType itemType) {
        public Node {
            if ((type != 0 && type != 1)
                    || amount < 1
                    || amount > 100_000
                    || (type == 1) != (itemType != null)) {
                throw new IllegalArgumentException("invalid contract node");
            }
        }

        static Node from(ContractNode node) {
            return node == null ? null : new Node(node.type, node.amount, ItemType.from(node.itemType));
        }
    }

    public record Rule(List<Node> nodes) {
        public Rule {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            if (nodes.isEmpty() || nodes.size() > 256 || nodes.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("invalid contract rule");
            }
        }

        static Rule from(ContractRule rule) {
            if (rule == null) {
                return null;
            }
            List<Node> nodes = new ArrayList<>();
            for (ContractNode node : rule.nodes) {
                Node converted = Node.from(node);
                if (converted != null) {
                    nodes.add(converted);
                }
            }
            return nodes.isEmpty() ? null : new Rule(nodes);
        }
    }
}
