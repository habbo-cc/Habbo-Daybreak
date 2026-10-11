package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseLimits;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.users.cache.HabboOfferPurchase;
import com.eu.habbo.messages.incoming.MessageHandler;

public class PurchaseTargetedOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readRequiredInt();
        int amount = this.packet.readRequiredInt();

        if (offerId <= 0 || !CatalogPurchaseLimits.isValidAmount(amount)) return;


        if (Emulator.getIntUnixTimestamp() - this.client.getHabbo().getHabboStats().lastPurchaseTimestamp >= CatalogManager.PURCHASE_COOLDOWN) {
            this.client.getHabbo().getHabboStats().lastPurchaseTimestamp = Emulator.getIntUnixTimestamp();

            TargetOffer offer = Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(offerId);

            if (offer == null) return;

            HabboOfferPurchase purchase = HabboOfferPurchase.getOrCreate(this.client.getHabbo(), offerId);

            if (purchase != null) {
                amount = Math.min(offer.getPurchaseLimit() - purchase.getAmount(), amount);
                if (!CatalogPurchaseLimits.isValidAmount(amount)) return;
                int now = Emulator.getIntUnixTimestamp();
                if (offer.getExpirationTime() > now) {
                    purchase.update(amount, now);
                    CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(offer.getCatalogItem());
                    if (item == null) return;
                    if (item.isLimited()) {
                        amount = 1;
                    }
                    Emulator.getGameEnvironment().getCatalogManager().purchaseItem(null, item, this.client.getHabbo(), amount, "", false);

                }
            }
        }
    }
}
