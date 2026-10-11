package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionLog;
import com.eu.habbo.messages.ServerMessage;

/** Shared field-order encoder for July's transaction list and details packets. */
final class ChestTransactionWire {
  private ChestTransactionWire() {}

  static void appendInfo(ServerMessage response, ChestTransactionLog.Info info) {
    response.appendLong(info.transactionId());
    response.appendInt(info.roomId());
    response.appendInt(info.transactionType());
    response.appendString(info.definitionInfo());
    response.appendInt(info.userId());
    response.appendString(info.username());
    response.appendLong(info.timestamp());
    response.appendString(info.readableTimestamp());
    response.appendInt(info.chestCount());
    response.appendInt(info.withdrawFurniCount());
    response.appendInt(info.depositFurniCount());
    response.appendInt(info.withdrawCoinsCount());
    response.appendInt(info.depositCoinsCount());
  }

  static void appendItemTypes(
      ServerMessage response, java.util.List<ChestTransactionLog.ItemType> itemTypes) {
    response.appendInt(itemTypes.size());
    for (ChestTransactionLog.ItemType itemType : itemTypes) {
      response.appendBoolean(itemType.wall());
      response.appendInt(itemType.typeId());
      response.appendString(itemType.legacyPosterId());
      response.appendInt(itemType.amount());
    }
  }
}
