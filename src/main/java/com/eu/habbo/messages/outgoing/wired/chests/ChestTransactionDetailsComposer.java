package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionLog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July S2C 1306 transaction-details payload on NGH's collision-free local header. */
public final class ChestTransactionDetailsComposer extends MessageComposer {
  private final ChestTransactionLog.Details details;

  public ChestTransactionDetailsComposer(ChestTransactionLog.Details details) {
    if (details == null || details.info() == null) {
      throw new IllegalArgumentException("details");
    }
    this.details = details;
  }

  @Override
  protected ServerMessage composeInternal() {
    this.response.init(Outgoing.ChestTransactionDetailsComposer);
    ChestTransactionWire.appendInfo(this.response, this.details.info());
    this.response.appendInt(this.details.chestIds().size());
    for (int chestId : this.details.chestIds()) {
      this.response.appendInt(chestId);
    }
    ChestTransactionWire.appendItemTypes(this.response, this.details.depositedFurnis());
    ChestTransactionWire.appendItemTypes(this.response, this.details.withdrawnFurnis());
    this.response.appendBoolean(this.details.incompleteData());
    return this.response;
  }
}
