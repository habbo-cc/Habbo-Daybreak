package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestContractPlan;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.chests.WiredTransactionSuccessComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** July's common six-field contract for chest reward actions 45 and 46. */
abstract class WiredEffectGiveFromChestBase extends WiredEffectConfigBase {
    static final int MODE_AMOUNT = 0;
    static final int MODE_ALL = 1;
    static final int MAX_AMOUNT = 1_000_000;

    WiredEffectGiveFromChestBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    WiredEffectGiveFromChestBase(int id, int userId, Item item, String extraData,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }

    protected abstract ChestType chestType();

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        int[] params = settings == null ? null : settings.getIntParams();
        if (params == null || params.length != 6
                || (params[0] != MODE_AMOUNT && params[0] != MODE_ALL)
                || params[1] < 1 || params[1] > Integer.MAX_VALUE
                || (params[2] != 0 && params[2] != 1)
                || !ChestWiredSupport.validTarget(params[3])
                || (params[4] != 0 && params[4] != 1)
                || !validTypeOption(params[5])
                || settings.getVariableIds().length != 1
                || (params[2] == 1 && settings.getVariableIds()[0].isBlank())
                || settings.getStringParam().length() > 200
                || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 2
                || settings.getUserSourceTypes().length != 2
                || settings.getDelay() < 0 || settings.getDelay() > 20) {
            throw new WiredSaveException("Invalid July chest reward action data");
        }
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null) {
            throw new WiredSaveException("Chest reward room is unavailable");
        }
        ChestManager manager = Emulator.getGameEnvironment().getChestManager();
        for (int databaseId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItemByDatabaseId(databaseId);
            if (item == null || ChestType.fromItem(item) != chestType()) {
                throw new WiredSaveException("A selected item is not the required chest type");
            }
        }
        return super.saveData(settings, client);
    }

    protected boolean validTypeOption(int value) {
        return true;
    }

    protected int mode() {
        return this.intParams.length > 0 ? this.intParams[0] : MODE_AMOUNT;
    }

    protected int requestedAmount(WiredContext context) {
        if (mode() == MODE_ALL) {
            return Integer.MAX_VALUE;
        }
        if (this.intParams.length < 4) {
            return 0;
        }
        if (this.intParams[2] == 0) {
            return Math.max(0, this.intParams[1]);
        }
        Collection<HabboItem> variableFurni = resolveFurniSource(
                context, this.furniSourceTypes, 1, List.of(), List.of());
        Collection<RoomUnit> variableUsers = resolveUserSource(
                context, this.userSourceTypes, 1);
        Integer value = ChestWiredSupport.resolveValue(context,
                this.variableIds.length == 0 ? "" : this.variableIds[0],
                this.intParams[3], variableFurni, variableUsers);
        return value == null ? 0 : Math.max(0, Math.min(MAX_AMOUNT, value));
    }

    protected List<HabboItem> sourceChests(WiredContext context) {
        return ChestWiredSupport.chests(Emulator.getGameEnvironment().getChestManager(),
                sourceItems(context, 0), chestType());
    }

    protected List<Habbo> receivers(WiredContext context) {
        return ChestWiredSupport.habbos(context.room(),
                resolveUserSource(context, this.userSourceTypes, 0));
    }

    protected boolean showPopup() {
        return this.intParams.length > 4 && this.intParams[4] != 0;
    }

    /**
     * Sends July's reward notification for a direct chest action. The packet is sent even when the
     * dialog is not opened automatically; {@code showPopup} controls only that client-side choice.
     */
    protected void sendFurnitureReward(Habbo receiver, List<HabboItem> rewarded) {
        if (receiver == null || receiver.getClient() == null || rewarded == null || rewarded.isEmpty()) {
            return;
        }
        Map<ChestContractPlan.ItemType, Integer> amounts = new LinkedHashMap<>();
        for (HabboItem item : rewarded) {
            if (item == null || item.getBaseItem() == null) {
                continue;
            }
            boolean wall = item.getBaseItem().getType() == FurnitureType.WALL;
            String poster = wall && "poster".equals(item.getBaseItem().getName())
                    ? item.getExtradata() : "";
            ChestContractPlan.ItemType type = new ChestContractPlan.ItemType(
                    wall, item.getBaseItem().getSpriteId(), poster);
            amounts.merge(type, 1, Integer::sum);
        }
        List<ChestContractPlan.Node> nodes = new ArrayList<>();
        for (Map.Entry<ChestContractPlan.ItemType, Integer> entry : amounts.entrySet()) {
            appendRewardNodes(nodes, 1, entry.getValue(), entry.getKey());
        }
        sendReward(receiver, nodes);
    }

    protected void sendCoinReward(Habbo receiver, int amount) {
        if (receiver == null || receiver.getClient() == null || amount <= 0) {
            return;
        }
        List<ChestContractPlan.Node> nodes = new ArrayList<>();
        appendRewardNodes(nodes, 0, amount, null);
        sendReward(receiver, nodes);
    }

    private static void appendRewardNodes(List<ChestContractPlan.Node> nodes, int type,
            int amount, ChestContractPlan.ItemType itemType) {
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, 100_000);
            nodes.add(new ChestContractPlan.Node(type, chunk, itemType));
            remaining -= chunk;
        }
    }

    private void sendReward(Habbo receiver, List<ChestContractPlan.Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < nodes.size(); offset += 256) {
            int end = Math.min(nodes.size(), offset + 256);
            receiver.getClient().sendResponse(new WiredTransactionSuccessComposer(
                    new ChestContractPlan.Rule(nodes.subList(offset, end)), this.stringParam,
                    offset == 0 && showPopup()));
        }
    }

    @Override protected int getFurniSourceSlotCount() { return 2; }
    @Override protected int getUserSourceSlotCount() { return 2; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected boolean supportsUserPicking() { return true; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL,
                FURNI_SOURCE_TRIGGERING_ITEM};
    }
    @Override protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }
    @Override protected int getDefaultFurniSourceForSlot(int slot) {
        return slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_TRIGGERING_ITEM;
    }
    @Override protected int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_TRIGGERING_USER;
    }
    @Override public boolean requiresTriggeringUser() { return false; }
}
