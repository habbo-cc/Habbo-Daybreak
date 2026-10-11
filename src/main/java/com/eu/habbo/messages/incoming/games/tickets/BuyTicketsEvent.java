package com.eu.habbo.messages.incoming.games.tickets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.tickets.HotelTicketService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.games.tickets.TicketPurchaseResultComposer;

public final class BuyTicketsEvent extends MessageHandler {
    @Override
    public void handle() {
        int bundleId = this.packet.readRequiredInt();
        String recipient = this.packet.readBoundedString(32);
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected trailing ticket purchase payload");
        }
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;
        int now = Emulator.getIntUnixTimestamp();
        if (now - habbo.getHabboStats().lastPurchaseTimestamp < CatalogManager.PURCHASE_COOLDOWN) {
            this.client.sendResponse(new TicketPurchaseResultComposer(
                    HotelTicketService.PURCHASE_DATABASE_ERROR, "", 0));
            return;
        }
        habbo.getHabboStats().lastPurchaseTimestamp = now;
        Emulator.getGameEnvironment().getHotelTicketService().buy(habbo, bundleId, recipient);
    }
}
