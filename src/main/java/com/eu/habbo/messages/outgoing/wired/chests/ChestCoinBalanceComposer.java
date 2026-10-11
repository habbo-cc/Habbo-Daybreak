package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July S2C 1022 shape on local header 7123. */
public final class ChestCoinBalanceComposer extends MessageComposer {
    private final int chestVisibleId;
    private final int balance;
    private final boolean update;

    public ChestCoinBalanceComposer(int chestVisibleId, int balance, boolean update) {
        this.chestVisibleId = chestVisibleId;
        this.balance = balance;
        this.update = update;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestCoinBalanceComposer);
        this.response.appendInt(this.chestVisibleId);
        this.response.appendInt(this.balance);
        this.response.appendBoolean(this.update);
        return this.response;
    }
}
