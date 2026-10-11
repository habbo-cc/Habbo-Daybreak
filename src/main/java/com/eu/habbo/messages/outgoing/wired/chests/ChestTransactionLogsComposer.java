package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionLog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July S2C 2910 transaction-log list on NGH's collision-free local header. */
public final class ChestTransactionLogsComposer extends MessageComposer {
  private final ChestTransactionLog.Page page;

  public ChestTransactionLogsComposer(ChestTransactionLog.Page page) {
    if (page == null) {
      throw new IllegalArgumentException("page");
    }
    this.page = page;
  }

  @Override
  protected ServerMessage composeInternal() {
    this.response.init(Outgoing.ChestTransactionLogsComposer);
    this.response.appendInt(this.page.listType());
    this.response.appendLong(this.page.listId());
    this.response.appendInt(this.page.totalLogs());
    this.response.appendInt(this.page.currentPage());
    // July echoes the requested page size independently of the number of rows
    // returned; both the 10-row menu preview and 25-row window rely on it.
    this.response.appendInt(this.page.amount());
    this.response.appendInt(this.page.logs().size());
    for (ChestTransactionLog.Info info : this.page.logs()) {
      ChestTransactionWire.appendInfo(this.response, info);
    }
    return this.response;
  }
}
