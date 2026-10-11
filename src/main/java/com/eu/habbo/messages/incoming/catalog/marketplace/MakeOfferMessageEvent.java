package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.PurchaseErrorMessageComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceMakeOfferResultMessageComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MakeOfferMessageEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MakeOfferMessageEvent.class);

    @Override
    public void handle() throws Exception {
        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(new MarketplaceMakeOfferResultMessageComposer(MarketplaceMakeOfferResultMessageComposer.MARKETPLACE_DISABLED));
            return;
        }

        int credits = this.packet.readInt();

        this.packet.readInt(); // unknown - not used
        int itemId = this.packet.readInt();

        if (!MarketPlace.isValidListingPrice(credits)) {
            this.client.sendResponse(new PurchaseErrorMessageComposer(PurchaseErrorMessageComposer.SERVER_ERROR));
            return;
        }

        HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
        if (item != null) {
            if (!item.getBaseItem().allowMarketplace()) {
                String message = Emulator.getTexts().getValue("scripter.warning.marketplace.forbidden").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%itemname%", item.getBaseItem().getName()).replace("%credits%", credits + "");
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
                this.client.sendResponse(new PurchaseErrorMessageComposer(PurchaseErrorMessageComposer.SERVER_ERROR));
                return;
            }

            if (MarketPlace.sellItem(this.client, item, credits)) {
                this.client.sendResponse(new MarketplaceMakeOfferResultMessageComposer(MarketplaceMakeOfferResultMessageComposer.POST_SUCCESS));
            } else {
                this.client.sendResponse(new MarketplaceMakeOfferResultMessageComposer(MarketplaceMakeOfferResultMessageComposer.FAILED_TECHNICAL_ERROR));
            }
        }
    }
}
