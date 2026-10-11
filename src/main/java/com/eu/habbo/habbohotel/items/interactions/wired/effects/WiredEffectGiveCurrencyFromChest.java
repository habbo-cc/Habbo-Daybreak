package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** July AIR action 45 ({@code wf_act_give_currency}). */
public final class WiredEffectGiveCurrencyFromChest extends WiredEffectGiveFromChestBase {
    public WiredEffectGiveCurrencyFromChest(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }
    public WiredEffectGiveCurrencyFromChest(int id, int userId, Item item, String extraData,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }

    @Override public WiredEffectType getType() {
        return WiredEffectType.GIVE_CURRENCY_FROM_CHEST;
    }
    @Override protected ChestType chestType() { return ChestType.COINS; }
    @Override protected boolean validTypeOption(int value) { return value == 11 || value == 13; }

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
        int total = 0;
        for (Habbo receiver : receivers) {
            int remaining = requested;
            int rewarded = 0;
            for (HabboItem chest : chests) {
                int given = manager.giveCoinsFromWired(
                        context.room(), chest, receiver, remaining, getId());
                total += given;
                rewarded += given;
                if (mode() != MODE_ALL && (remaining -= given) <= 0) {
                    break;
                }
            }
            sendCoinReward(receiver, rewarded);
        }
        if (total > 0) {
            context.contextVariables().set(
                    "@event.transaction_complete.withdrawal.coins_count", total);
        }
    }
}
