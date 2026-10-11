package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;
import com.eu.habbo.messages.outgoing.wired.chests.ChestContractOpenComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** July AIR payment, trade and reward contract furniture. */
public final class InteractionChestContract extends InteractionDefault {
    public static final String PAYMENT = "wf_contract_payment";
    public static final String TRADE = "wf_contract_trade";
    public static final String REWARD = "wf_contract_reward";
    public static final int TYPE_PAYMENT = 0;
    public static final int TYPE_TRADE = 1;
    public static final int TYPE_REWARD = 2;

    public InteractionChestContract(ResultSet set, Item item) throws SQLException { super(set,item); }
    public InteractionChestContract(int id,int userId,Item item,String data,int stack,int sells) {
        super(id,userId,item,data,stack,sells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        if (!canConfigure(client, room, this)
                || !client.getWiredCapabilityState().supportsRoom(
                WiredCapabilityService.CAPABILITY_CONTRACTS, room.getId())) {
            return;
        }
        client.sendResponse(new ChestContractOpenComposer(getRoomVisibleId()));
    }

    public int contractType() {
        String name = getBaseItem() == null || getBaseItem().getInteractionType() == null
                ? "" : getBaseItem().getInteractionType().getName();
        if (TRADE.equalsIgnoreCase(name)) return TYPE_TRADE;
        if (REWARD.equalsIgnoreCase(name)) return TYPE_REWARD;
        return TYPE_PAYMENT;
    }

    public ContractData contractData() {
        try {
            ContractData data = WiredManager.getGson().fromJson(getExtradata(), ContractData.class);
            return sanitize(data, contractType());
        } catch (RuntimeException ignored) {
            return defaults(contractType());
        }
    }

    public void saveContractData(ContractData data, Room room) {
        setExtradata(WiredManager.getGson().toJson(sanitize(data, contractType())));
        needsUpdate(true);
        if (room != null) room.updateItem(this);
    }

    public static boolean canConfigure(GameClient client, Room room,
            com.eu.habbo.habbohotel.users.HabboItem item) {
        if (client == null
                || client.getHabbo() == null
                || client.getHabbo().getHabboInfo() == null
                || room == null
                || !(item instanceof InteractionChestContract)
                || client.getHabbo().getHabboInfo().getCurrentRoom() != room
                || client.getHabbo().getRoomUnit() == null
                || !client.getHabbo().getRoomUnit().isInRoom()
                || item.getRoomId() != room.getId()
                || room.getHabboItemByDatabaseId(item.getId()) != item) {
            return false;
        }
        return WiredMenuSettings.load(room.getId()).canModify(room, client.getHabbo());
    }

    public static boolean isContractItem(com.eu.habbo.habbohotel.users.HabboItem item) {
        return item instanceof InteractionChestContract;
    }

    public static ContractData sanitize(ContractData source, int type) {
        ContractData result = defaults(type);
        if (source == null) return result;
        result.giveRules = type == TYPE_REWARD ? null : sanitizeRules(source.giveRules);
        result.getRule = type == TYPE_PAYMENT ? null : sanitizeRule(source.getRule);
        result.paymentMode = source.paymentMode == 1 ? 1 : 0;
        result.receiveText = trim(source.receiveText, 60);
        result.layoutType = "games".equals(source.layoutType) ? "games" : "generic";
        result.rewardCategory = source.rewardCategory == 13 ? 13 : 11;
        result.showDialog = source.showDialog;
        result.rewardText = trim(source.rewardText, 200);
        if (type == TYPE_TRADE) result.paymentMode = 1;
        return result;
    }

    private static ContractData defaults(int type) {
        ContractData data = new ContractData();
        data.giveRules = type == TYPE_REWARD ? null : new ArrayList<>();
        data.getRule = type == TYPE_PAYMENT ? null : new ContractRule();
        return data;
    }

    private static List<ContractRule> sanitizeRules(List<ContractRule> rules) {
        List<ContractRule> result = new ArrayList<>();
        if (rules != null) for (ContractRule rule : rules) {
            ContractRule clean = sanitizeRule(rule);
            if (clean != null && !clean.nodes.isEmpty()) result.add(clean);
            if (result.size() >= 64) break;
        }
        return result;
    }

    private static ContractRule sanitizeRule(ContractRule rule) {
        if (rule == null) return null;
        ContractRule clean = new ContractRule();
        if (rule.nodes != null) for (ContractNode node : rule.nodes) {
            ContractNode next = sanitizeNode(node);
            if (next != null) clean.nodes.add(next);
            if (clean.nodes.size() >= 256) break;
        }
        return clean;
    }

    private static ContractNode sanitizeNode(ContractNode node) {
        if (node == null || (node.type != 0 && node.type != 1)
                || node.amount < 1 || node.amount > (node.type == 0 ? 100_000 : 500)) {
            return null;
        }
        ContractNode clean = new ContractNode();
        clean.type=node.type; clean.amount=node.amount;
        if (node.type == 1) {
            if (node.itemType == null || node.itemType.typeId <= 0) return null;
            clean.itemType = new ContractItemType();
            clean.itemType.wall=node.itemType.wall;
            clean.itemType.typeId=node.itemType.typeId;
            clean.itemType.poster=trim(node.itemType.poster, 64);
        }
        return clean;
    }

    private static String trim(String value,int max) {
        if (value == null) return "";
        value=value.replace('\0',' ').trim();
        return value.length() <= max ? value : value.substring(0,max);
    }

    public static final class ContractData {
        public List<ContractRule> giveRules;
        public ContractRule getRule;
        public int paymentMode;
        public String receiveText="";
        public String layoutType="generic";
        public int rewardCategory=11;
        public boolean showDialog=true;
        public String rewardText="";
    }
    public static final class ContractRule {
        public List<ContractNode> nodes=new ArrayList<>();
    }
    public static final class ContractNode {
        public int type;
        public int amount=1;
        public ContractItemType itemType;
    }
    public static final class ContractItemType {
        public boolean wall;
        public int typeId;
        public String poster="";
    }
}
