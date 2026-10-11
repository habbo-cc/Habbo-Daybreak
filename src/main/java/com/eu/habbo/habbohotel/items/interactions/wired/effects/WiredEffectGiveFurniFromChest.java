package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonChestItemTypeScanner;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** July AIR action 46 ({@code wf_act_give_furni}). */
public final class WiredEffectGiveFurniFromChest extends WiredEffectGiveFromChestBase {
    public WiredEffectGiveFurniFromChest(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }
    public WiredEffectGiveFurniFromChest(int id, int userId, Item item, String extraData,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }

    @Override public WiredEffectType getType() {
        return WiredEffectType.GIVE_FURNI_FROM_CHEST;
    }
    @Override protected ChestType chestType() { return ChestType.FURNI; }
    @Override protected boolean validTypeOption(int value) { return value >= 0 && value <= 2; }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) {
            return;
        }
        ChestManager manager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> chests = sourceChests(context);
        List<Habbo> receivers = receivers(context);
        int requested = requestedAmount(context);
        if (chests.isEmpty() || receivers.isEmpty() || requested <= 0) {
            return;
        }
        WiredAddonChestItemTypeScanner scanner = null;
        if (context.stack() != null) {
            InteractionWiredAddon addon = context.stack().addon(
                    WiredAddonType.CHEST_ITEM_TYPE_SCANNER);
            if (addon instanceof WiredAddonChestItemTypeScanner configured) {
                scanner = configured;
            }
        }
        List<HabboItem> types = scanner == null ? List.of() : scanner.resolveItemTypes(context);
        int total = 0;
        for (Habbo receiver : receivers) {
            int remaining = requested;
            List<HabboItem> rewarded = new ArrayList<>();
            for (HabboItem chest : chests) {
                List<HabboItem> given = manager.giveFurnitureFromWired(
                        context.room(), chest, receiver, remaining, types, this.intParams[5],
                        getId());
                total += given.size();
                rewarded.addAll(given);
                if (mode() != MODE_ALL && (remaining -= given.size()) <= 0) {
                    break;
                }
            }
            sendFurnitureReward(receiver, rewarded);
        }
        if (total > 0) {
            context.contextVariables().set(
                    "@event.transaction_complete.withdrawal.furni_count", total);
        }
    }
}
