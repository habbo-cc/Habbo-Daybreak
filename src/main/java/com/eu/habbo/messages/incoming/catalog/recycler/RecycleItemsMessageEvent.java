package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.PurchaseErrorMessageComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerFinishedMessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.UnseenItemsMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListInvalidateMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListRemoveMessageComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import gnu.trove.set.hash.THashSet;

public class RecycleItemsMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (ShutdownEmulator.timestamp > 0) {
            this.client.sendResponse(new HotelWillCloseInMinutesMessageComposer((ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
            return;
        }

        if (Emulator.getGameEnvironment().getCatalogManager().ecotronItem != null && ItemManager.RECYCLER_ENABLED) {
            THashSet<HabboItem> items = new THashSet<>();

            int count = this.packet.readBoundedCount(1000, Integer.BYTES);
            if (count < Emulator.getConfig().getInt("recycler.value", 8)) return;

            for (int i = 0; i < count; i++) {
                HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(this.packet.readRequiredInt());

                if (item == null)
                    return;

                if (item.getBaseItem().allowRecyle()) {
                    items.add(item);
                }
            }

            if (items.size() == count) {
                for (HabboItem item : items) {
                    this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
                    this.client.sendResponse(new FurniListRemoveMessageComposer(item.getGiftAdjustedId()));
                    Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));
                }
            } else {
                this.client.sendResponse(new PurchaseErrorMessageComposer(PurchaseErrorMessageComposer.SERVER_ERROR));
                return;
            }

            HabboItem reward = Emulator.getGameEnvironment().getItemManager().handleRecycle(this.client.getHabbo(), Emulator.getGameEnvironment().getCatalogManager().getRandomRecyclerPrize().getId() + "");
            if (reward == null) {
                this.client.sendResponse(new PurchaseErrorMessageComposer(PurchaseErrorMessageComposer.SERVER_ERROR));
                return;
            }

            this.client.sendResponse(new UnseenItemsMessageComposer(reward));
            this.client.getHabbo().getInventory().getItemsComponent().addItem(reward);
            this.client.sendResponse(new RecyclerFinishedMessageComposer(RecyclerFinishedMessageComposer.RECYCLING_COMPLETE));
            this.client.sendResponse(new FurniListInvalidateMessageComposer());

            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("FurnimaticQuest"));
        } else {
            this.client.sendResponse(new RecyclerFinishedMessageComposer(RecyclerFinishedMessageComposer.RECYCLING_CLOSED));
        }
    }
}
