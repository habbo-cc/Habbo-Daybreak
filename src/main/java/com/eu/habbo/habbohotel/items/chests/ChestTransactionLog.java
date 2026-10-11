package com.eu.habbo.habbohotel.items.chests;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Immutable July transaction-log wire model backed by the chest audit tables. */
public final class ChestTransactionLog {
  private static final DateTimeFormatter READABLE_TIMESTAMP =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
  public static final int LIST_CHEST = 0;
  public static final int LIST_ROOM = 1;

  public static final int TYPE_MANUAL = 0;
  public static final int TYPE_WIRED = 1;
  public static final int TYPE_CONTRACT_PAYMENT = 2;
  public static final int TYPE_CONTRACT_REWARD = 3;
  public static final int TYPE_CONTRACT_TRADE = 4;
  public static final int TYPE_AUTO_WITHDRAW = 5;

  public static final int DIRECTION_WITHDRAW = 0;
  public static final int DIRECTION_DEPOSIT = 1;

  private ChestTransactionLog() {}

  /** Immutable metadata written in the same database transaction as a chest mutation. */
  public record Audit(int transactionType, String definitionInfo, String actorName) {
    public Audit {
      if (transactionType != TYPE_MANUAL && transactionType != TYPE_WIRED
          && transactionType != TYPE_AUTO_WITHDRAW) {
        throw new IllegalArgumentException("Unsupported direct chest transaction type");
      }
      definitionInfo = definitionInfo == null ? "" : definitionInfo;
      actorName = actorName == null ? "" : actorName;
    }

    public static Audit manual(String actorName) {
      return new Audit(TYPE_MANUAL, "", actorName);
    }

    public static Audit wired(String actorName, int effectId, int requestedAmount) {
      return new Audit(
          TYPE_WIRED,
          "wired:" + Math.max(0, effectId) + ":amount:" + Math.max(0, requestedAmount),
          actorName);
    }
  }

  public record Info(
      long transactionId,
      int roomId,
      int transactionType,
      String definitionInfo,
      int userId,
      String username,
      long timestamp,
      int chestCount,
      int withdrawFurniCount,
      int depositFurniCount,
      int withdrawCoinsCount,
      int depositCoinsCount) {
    public Info {
      definitionInfo = definitionInfo == null ? "" : definitionInfo;
      username = username == null ? "" : username;
    }

    public String readableTimestamp() {
      return timestamp <= 0 ? "" : READABLE_TIMESTAMP.format(Instant.ofEpochMilli(timestamp));
    }
  }

  public record ItemType(boolean wall, int typeId, String legacyPosterId, int amount) {
    public ItemType {
      legacyPosterId = legacyPosterId == null ? "" : legacyPosterId;
      amount = Math.max(0, amount);
    }
  }

  public record Page(
      int listType,
      long listId,
      int totalLogs,
      int currentPage,
      int amount,
      List<Info> logs) {
    public Page {
      logs = logs == null ? List.of() : List.copyOf(logs);
    }
  }

  public record Details(
      Info info,
      List<Integer> chestIds,
      List<ItemType> depositedFurnis,
      List<ItemType> withdrawnFurnis,
      boolean incompleteData) {
    public Details {
      chestIds = chestIds == null ? List.of() : List.copyOf(chestIds);
      depositedFurnis =
          depositedFurnis == null ? List.of() : List.copyOf(depositedFurnis);
      withdrawnFurnis =
          withdrawnFurnis == null ? List.of() : List.copyOf(withdrawnFurnis);
    }
  }

  public static int contractType(int contractType) {
    return switch (contractType) {
      case 0 -> TYPE_CONTRACT_PAYMENT;
      case 1 -> TYPE_CONTRACT_TRADE;
      case 2 -> TYPE_CONTRACT_REWARD;
      default -> throw new IllegalArgumentException("Unknown chest contract type " + contractType);
    };
  }
}
