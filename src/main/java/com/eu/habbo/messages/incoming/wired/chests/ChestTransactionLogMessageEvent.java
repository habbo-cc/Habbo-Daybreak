package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.chests.ChestTransactionDetailsComposer;
import com.eu.habbo.messages.outgoing.wired.chests.ChestTransactionLogsComposer;

/**
 * Authenticated room boundary for July's transaction history packets. Database chest identifiers
 * never cross the client boundary; chest requests and responses retain room-visible identifiers.
 */
public final class ChestTransactionLogMessageEvent extends MessageHandler {
  private static final int MAX_PAGE_SIZE = 100;

  @Override
  public int getRatelimit() {
    return 250;
  }

  @Override
  public void handle() {
    Context context = resolve();
    if (context == null) {
      return;
    }
    switch (this.packet.getMessageId()) {
      case Incoming.ChestRoomTransactionLogsMessageEvent -> roomLogs(context);
      case Incoming.ChestTransactionLogsMessageEvent -> chestLogs(context);
      case Incoming.ChestTransactionDetailsMessageEvent -> details(context);
      default -> throw new MalformedPacketException("unknown chest transaction-log request");
    }
    if (this.packet.bytesAvailable() != 0) {
      throw new MalformedPacketException("unexpected trailing chest transaction-log payload");
    }
  }

  private void roomLogs(Context context) {
    int amount = amount(this.packet.readRequiredInt());
    int page = page(this.packet.readRequiredInt());
    if (!context.menuSettings().canRead(context.room(), context.habbo())) {
      return;
    }
    this.client.sendResponse(
        new ChestTransactionLogsComposer(
            context.manager().roomTransactionLogs(context.room().getId(), amount, page)));
  }

  private void chestLogs(Context context) {
    int visibleId = this.packet.readRequiredInt();
    int amount = amount(this.packet.readRequiredInt());
    int page = page(this.packet.readRequiredInt());
    HabboItem chest = context.room().getHabboItem(visibleId);
    if (chest == null
        || !context.manager().isChest(chest)
        || !context.manager().canViewLogs(this.client, context.room(), chest)) {
      return;
    }
    this.client.sendResponse(
        new ChestTransactionLogsComposer(
            context.manager().chestTransactionLogs(
                context.room().getId(), chest, amount, page)));
  }

  private void details(Context context) {
    long transactionId = this.packet.readRequiredLong();
    ChestTransactionLog.Details details =
        context.manager().transactionDetails(transactionId);
    if (details == null
        || details.info().roomId() != context.room().getId()
        || !canViewDetails(context, details)) {
      return;
    }
    this.client.sendResponse(new ChestTransactionDetailsComposer(details));
  }

  private boolean canViewDetails(Context context, ChestTransactionLog.Details details) {
    if (context.menuSettings().canRead(context.room(), context.habbo())) {
      return true;
    }
    for (int chestId : details.chestIds()) {
      HabboItem chest = context.room().getHabboItemByDatabaseId(chestId);
      if (chest != null
          && context.manager().canViewLogs(this.client, context.room(), chest)) {
        return true;
      }
    }
    return false;
  }

  private Context resolve() {
    if (this.client == null
        || this.client.getHabbo() == null
        || this.client.getHabbo().getHabboInfo() == null) {
      return null;
    }
    Habbo habbo = this.client.getHabbo();
    Room room = habbo.getHabboInfo().getCurrentRoom();
    int required =
        WiredCapabilityService.CAPABILITY_CHESTS
            | WiredCapabilityService.CAPABILITY_MENU_CHESTS;
    if (room == null
        || habbo.getRoomUnit() == null
        || !habbo.getRoomUnit().isInRoom()
        || !this.client.getWiredCapabilityState().supportsRoom(required, room.getId())) {
      return null;
    }
    return new Context(
        room,
        habbo,
        Emulator.getGameEnvironment().getChestManager(),
        WiredMenuSettings.load(room.getId()));
  }

  private static int amount(int value) {
    if (value < 1 || value > MAX_PAGE_SIZE) {
      throw new MalformedPacketException("invalid chest transaction page size");
    }
    return value;
  }

  private static int page(int value) {
    if (value < 1 || value > 100_000) {
      throw new MalformedPacketException("invalid chest transaction page");
    }
    return value;
  }

  private record Context(
      Room room, Habbo habbo, ChestManager manager, WiredMenuSettings menuSettings) {}
}
