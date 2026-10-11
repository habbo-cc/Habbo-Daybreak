package com.eu.habbo.habbohotel.tickets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.games.tickets.TicketBalanceComposer;
import com.eu.habbo.messages.outgoing.games.tickets.TicketPurchaseResultComposer;
import com.eu.habbo.messages.outgoing.users.CreditBalanceMessageComposer;
import com.eu.habbo.messages.outgoing.users.HabboActivityPointNotificationMessageComposer;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared purchase, balance and wallet-notification service for the hotel ticket currency. */
public final class HotelTicketService {
    public static final int PURCHASE_OK = 0;
    public static final int PURCHASE_INSUFFICIENT_CREDITS = 1;
    public static final int PURCHASE_RECIPIENT_NOT_FOUND = 2;
    public static final int PURCHASE_INVALID_BUNDLE = 3;
    public static final int PURCHASE_DATABASE_ERROR = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelTicketService.class);
    private final HotelTicketRepository repository = new HotelTicketRepository();

    public int currencyType() {
        return Emulator.getConfig().getInt("hotel.ticket.currency.type", 106);
    }

    public int balance(Habbo habbo) {
        return habbo == null || habbo.getHabboInfo() == null
                ? 0 : Math.max(0, habbo.getHabboInfo().getCurrencyAmount(this.currencyType()));
    }

    public void applyCommittedBalance(Habbo habbo, int balance) {
        if (habbo != null && habbo.getHabboInfo() != null) {
            habbo.getHabboInfo().applyCommittedCurrencyBalance(this.currencyType(), Math.max(0, balance));
        }
    }

    public void sendCommittedBalance(Habbo habbo, int balance, int delta) {
        if (habbo == null || habbo.getHabboInfo() == null) return;
        int committed = Math.max(0, balance);
        this.sendSafely(habbo, new TicketBalanceComposer(committed), "ticket balance");
        this.sendSafely(habbo, new HabboActivityPointNotificationMessageComposer(
                committed, delta, this.currencyType()), "ticket activity points");
    }

    /** Original shared ticket shop: 2 tickets for 1 credit or 20 tickets for 6 credits. */
    public void buy(Habbo buyer, int bundleId, String recipientName) {
        if (buyer == null || buyer.getHabboInfo() == null || buyer.getClient() == null) return;

        int creditCost;
        int ticketsAdded;
        if (bundleId == 1) {
            creditCost = 1;
            ticketsAdded = 2;
        } else if (bundleId == 2) {
            creditCost = 6;
            ticketsAdded = 20;
        } else {
            this.sendSafely(buyer, new TicketPurchaseResultComposer(
                    PURCHASE_INVALID_BUNDLE, "", 0), "invalid ticket purchase");
            return;
        }

        Habbo onlineRecipient = recipientName == null || recipientName.isBlank()
                ? buyer : Emulator.getGameEnvironment().getHabboManager().getHabbo(recipientName.trim());
        List<Habbo> walletOwners = onlineRecipient == null || onlineRecipient == buyer
                ? List.of(buyer) : List.of(buyer, onlineRecipient);

        HotelTicketRepository.PurchaseResult result = this.withWalletLocks(walletOwners, () -> {
            HotelTicketRepository.PurchaseResult committed = this.repository.purchase(
                    buyer.getHabboInfo().getId(), recipientName, this.currencyType(), creditCost, ticketsAdded);
            if (committed.success()) {
                buyer.getHabboInfo().applyCommittedCreditBalance(committed.creditsRemaining());
                if (onlineRecipient != null
                        && onlineRecipient.getHabboInfo().getId() == committed.recipientUserId()) {
                    this.applyCommittedBalance(onlineRecipient, committed.ticketBalance());
                }
            }
            return committed;
        });

        if (!result.success()) {
            this.sendSafely(buyer, new TicketPurchaseResultComposer(result.status(), "", 0),
                    "ticket purchase failure");
            return;
        }

        this.sendSafely(buyer, new CreditBalanceMessageComposer(buyer), "credit balance");
        Habbo recipient = Emulator.getGameEnvironment().getHabboManager().getHabbo(result.recipientUserId());
        if (recipient != null) {
            if (recipient != onlineRecipient) {
                synchronized (recipient.getHabboInfo()) {
                    this.applyCommittedBalance(recipient, result.ticketBalance());
                }
            }
            this.sendCommittedBalance(recipient, result.ticketBalance(), result.ticketsAdded());
        }
        this.sendSafely(buyer, new TicketPurchaseResultComposer(
                PURCHASE_OK, result.recipientName(), result.ticketsAdded()), "ticket purchase result");
    }

    public <T> T withWalletLocks(List<Habbo> habbos, Supplier<T> action) {
        List<Habbo> ordered = habbos.stream()
                .filter(habbo -> habbo != null && habbo.getHabboInfo() != null)
                .distinct()
                .sorted(Comparator.comparingInt(habbo -> habbo.getHabboInfo().getId()))
                .toList();
        return this.withWalletLocks(ordered, 0, action);
    }

    private <T> T withWalletLocks(List<Habbo> habbos, int index, Supplier<T> action) {
        if (index >= habbos.size()) return action.get();
        synchronized (habbos.get(index).getHabboInfo()) {
            return this.withWalletLocks(habbos, index + 1, action);
        }
    }

    private void sendSafely(Habbo habbo, MessageComposer composer, String action) {
        try {
            if (habbo != null && habbo.getClient() != null) habbo.getClient().sendResponse(composer);
        } catch (RuntimeException exception) {
            int userId = habbo == null || habbo.getHabboInfo() == null ? 0 : habbo.getHabboInfo().getId();
            LOGGER.error("Hotel Tickets -> Failed to send {} to user {}.", action, userId, exception);
        }
    }
}
