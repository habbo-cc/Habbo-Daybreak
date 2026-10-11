package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transaction boundary for chest state. Every mutating operation locks the chest item and settings
 * row first, so capacity and ownership checks cannot race across room threads or emulator nodes.
 */
public final class ChestRepository {
  private static final Logger LOGGER = LoggerFactory.getLogger(ChestRepository.class);

  public enum Result {
    OK,
    NOT_FOUND,
    STALE_ROOM,
    NOT_OWNER,
    WRONG_TYPE,
    LOCKED,
    FULL,
    EMPTY,
    INVALID_AMOUNT,
    INSUFFICIENT_CREDITS,
    INSUFFICIENT_DIAMONDS,
    ITEM_NOT_OWNED,
    ITEM_RESERVED,
    CANCELLED,
    CONFLICT,
    STORAGE_ERROR
  }

  public record StoredItem(int itemId, int lockState, long transactionId) {}

  public record StoredHabboItem(HabboItem item, int lockState, long transactionId) {}

  public record ItemTransfer(Result result, int itemId) {
    public static ItemTransfer failure(Result result) {
      return new ItemTransfer(result, 0);
    }
  }

  public record ItemBatchTransfer(Result result, List<Integer> itemIds) {
    public ItemBatchTransfer {
      itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }

    public static ItemBatchTransfer failure(Result result) {
      return new ItemBatchTransfer(result, List.of());
    }
  }

  public record CoinTransfer(Result result, int chestBalance, int userBalance) {
    public static CoinTransfer failure(Result result) {
      return new CoinTransfer(result, 0, 0);
    }
  }

  public record UpgradeTransfer(
      Result result, int creditBalance, int currencyBalance, int currencyType) {
    public static UpgradeTransfer failure(Result result) {
      return new UpgradeTransfer(result, 0, 0, 0);
    }
  }

  public record CreditItemBatchTransfer(
      Result result, List<Integer> itemIds, int chestBalance, int depositedCredits) {
    public CreditItemBatchTransfer {
      itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }

    public static CreditItemBatchTransfer failure(Result result) {
      return new CreditItemBatchTransfer(result, List.of(), 0, 0);
    }
  }

  public record ContractTransfer(
      Result result,
      ChestTransactionFailure failure,
      long transactionId,
      int multiplier,
      List<Integer> depositedItemIds,
      List<Integer> withdrawnItemIds,
      int depositedCoins,
      int withdrawnCoins,
      int userBalance,
      Map<Integer, Integer> coinBalances,
      Map<Integer, List<Integer>> depositedFurnitureByChest,
      Map<Integer, List<Integer>> withdrawnFurnitureByChest) {
    public ContractTransfer {
      depositedItemIds =
          depositedItemIds == null ? List.of() : List.copyOf(depositedItemIds);
      withdrawnItemIds =
          withdrawnItemIds == null ? List.of() : List.copyOf(withdrawnItemIds);
      coinBalances = coinBalances == null ? Map.of() : Map.copyOf(coinBalances);
      depositedFurnitureByChest = immutableLists(depositedFurnitureByChest);
      withdrawnFurnitureByChest = immutableLists(withdrawnFurnitureByChest);
    }

    public static ContractTransfer failure(Result result, ChestTransactionFailure failure) {
      return new ContractTransfer(
          result,
          failure,
          0,
          0,
          List.of(),
          List.of(),
          0,
          0,
          0,
          Map.of(),
          Map.of(),
          Map.of());
    }

    private static Map<Integer, List<Integer>> immutableLists(
        Map<Integer, List<Integer>> source) {
      if (source == null || source.isEmpty()) {
        return Map.of();
      }
      Map<Integer, List<Integer>> copy = new LinkedHashMap<>();
      source.forEach((key, value) -> copy.put(key, value == null ? List.of() : List.copyOf(value)));
      return Map.copyOf(copy);
    }
  }

  public record PendingNotification(
      long id,
      int ownerId,
      String type,
      int chestId,
      int roomId,
      String actorName,
      String chestName,
      long createdAt) {
    public PendingNotification {
      type = type == null ? "" : type;
      actorName = actorName == null ? "" : actorName;
      chestName = chestName == null ? "" : chestName;
    }
  }

  public record NotificationBatch(Result result, List<PendingNotification> notifications) {
    public NotificationBatch {
      notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }

    public static NotificationBatch failure(Result result) {
      return new NotificationBatch(result, List.of());
    }
  }

  private final DataSource dataSource;

  public ChestRepository() {
    this(Emulator.getDatabase().getDataSource());
  }

  public ChestRepository(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource");
    }
    this.dataSource = dataSource;
  }

  public ChestSettings loadSettings(HabboItem chest) {
    ChestType type = ChestType.fromItem(chest);
    if (type == null) {
      return null;
    }
    int defaultCapacity = ChestCapacityPolicy.capacity(chest, type, 0);
    String sql = "SELECT * FROM items_chest_settings WHERE chest_id = ? LIMIT 1";
    try (Connection connection = this.dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, chest.getId());
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? readSettings(result) : ChestSettings.defaults(defaultCapacity);
      }
    } catch (SQLException exception) {
      LOGGER.error("Failed to load chest settings for {}", chest.getId(), exception);
      return null;
    }
  }

  public List<StoredItem> loadStoredItems(int chestId) {
    List<StoredItem> result = new ArrayList<>();
    String sql =
        "SELECT item_id, lock_state, transaction_id "
            + "FROM items_chest_storage WHERE chest_id = ? "
            + "ORDER BY deposited_at, item_id";
    try (Connection connection = this.dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, chestId);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          result.add(new StoredItem(rows.getInt(1), rows.getInt(2), rows.getLong(3)));
        }
      }
    } catch (SQLException exception) {
      LOGGER.error("Failed to load chest contents for {}", chestId, exception);
    }
    return result;
  }

  public List<StoredHabboItem> loadStoredHabboItems(int chestId) {
    List<StoredHabboItem> result = new ArrayList<>();
    String sql =
        "SELECT i.*, s.lock_state AS chest_lock_state, "
            + "s.transaction_id AS chest_transaction_id "
            + "FROM items_chest_storage s INNER JOIN items i ON i.id = s.item_id "
            + "WHERE s.chest_id = ? ORDER BY s.deposited_at, s.item_id";
    try (Connection connection = this.dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, chestId);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          HabboItem item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(rows);
          if (item != null) {
            result.add(
                new StoredHabboItem(
                    item, rows.getInt("chest_lock_state"), rows.getLong("chest_transaction_id")));
          }
        }
      }
    } catch (SQLException exception) {
      LOGGER.error("Failed to load chest furniture for {}", chestId, exception);
    }
    return result;
  }

  public int loadCoinBalance(int chestId) {
    String sql = "SELECT coins FROM items_chest_coins WHERE chest_id = ? LIMIT 1";
    try (Connection connection = this.dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, chestId);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Math.max(0, result.getInt(1)) : 0;
      }
    } catch (SQLException exception) {
      LOGGER.error("Failed to load coin chest balance for {}", chestId, exception);
      return 0;
    }
  }

  /** Loads one 1-based July room transaction-log page in newest-first order. */
  public ChestTransactionLog.Page loadRoomTransactionLogs(int roomId, int amount, int page) {
    return loadTransactionLogs(
        ChestTransactionLog.LIST_ROOM, roomId, roomId, 0, amount, page);
  }

  /**
   * Loads one 1-based July chest transaction-log page. {@code visibleChestId} is echoed on the
   * wire, while {@code databaseChestId} remains the durable query key.
   */
  public ChestTransactionLog.Page loadChestTransactionLogs(
      int roomId, int databaseChestId, int visibleChestId, int amount, int page) {
    return loadTransactionLogs(
        ChestTransactionLog.LIST_CHEST,
        visibleChestId,
        roomId,
        databaseChestId,
        amount,
        page);
  }

  /** Loads the complete retained snapshot required by July's transaction-details packet. */
  public ChestTransactionLog.Details loadTransactionDetails(long transactionId) {
    if (transactionId <= 0) {
      return null;
    }
    try (Connection connection = this.dataSource.getConnection()) {
      ChestTransactionLog.Info info;
      try (PreparedStatement select =
          connection.prepareStatement(
              "SELECT transaction_id, room_id, transaction_type, definition_info, user_id, "
                  + "username, created_at, chest_count, withdraw_furni_count, "
                  + "deposit_furni_count, withdraw_coins_count, deposit_coins_count "
                  + "FROM wired_chest_logs WHERE transaction_id = ?")) {
        select.setLong(1, transactionId);
        try (ResultSet row = select.executeQuery()) {
          if (!row.next()) {
            return null;
          }
          info = readTransactionInfo(row);
        }
      }
      List<Integer> chestIds = new ArrayList<>();
      try (PreparedStatement select =
          connection.prepareStatement(
              "SELECT chest_id FROM wired_chest_log_chests "
                  + "WHERE transaction_id = ? ORDER BY chest_id")) {
        select.setLong(1, transactionId);
        try (ResultSet row = select.executeQuery()) {
          while (row.next()) {
            chestIds.add(row.getInt(1));
          }
        }
      }
      List<ChestTransactionLog.ItemType> withdrawn =
          loadLogItemTypes(connection, transactionId, ChestTransactionLog.DIRECTION_WITHDRAW);
      List<ChestTransactionLog.ItemType> deposited =
          loadLogItemTypes(connection, transactionId, ChestTransactionLog.DIRECTION_DEPOSIT);
      int retainedWithdrawn = withdrawn.stream().mapToInt(ChestTransactionLog.ItemType::amount).sum();
      int retainedDeposited = deposited.stream().mapToInt(ChestTransactionLog.ItemType::amount).sum();
      boolean incomplete =
          retainedWithdrawn != info.withdrawFurniCount()
              || retainedDeposited != info.depositFurniCount();
      return new ChestTransactionLog.Details(
          info, chestIds, deposited, withdrawn, incomplete);
    } catch (SQLException exception) {
      LOGGER.warn(
          "Failed to load Wired chest transaction details {}",
          transactionId,
          exception);
      return null;
    }
  }

  private ChestTransactionLog.Page loadTransactionLogs(
      int listType, long listId, int roomId, int chestId, int amount, int page) {
    int safeAmount = Math.max(1, Math.min(100, amount));
    int safePage = Math.max(1, Math.min(100_000, page));
    int offset;
    try {
      offset = Math.multiplyExact(safePage - 1, safeAmount);
    } catch (ArithmeticException ignored) {
      offset = Integer.MAX_VALUE;
    }
    boolean chestList = listType == ChestTransactionLog.LIST_CHEST;
    String countSql =
        chestList
            ? "SELECT COUNT(*) FROM wired_chest_logs l "
                + "INNER JOIN wired_chest_log_chests c ON c.transaction_id = l.transaction_id "
                + "WHERE l.room_id = ? AND c.chest_id = ?"
            : "SELECT COUNT(*) FROM wired_chest_logs WHERE room_id = ?";
    String pageSql =
        "SELECT l.transaction_id, l.room_id, l.transaction_type, l.definition_info, "
            + "l.user_id, l.username, l.created_at, l.chest_count, "
            + "l.withdraw_furni_count, l.deposit_furni_count, "
            + "l.withdraw_coins_count, l.deposit_coins_count "
            + "FROM wired_chest_logs l "
            + (chestList
                ? "INNER JOIN wired_chest_log_chests c "
                    + "ON c.transaction_id = l.transaction_id "
                : "")
            + "WHERE l.room_id = ? "
            + (chestList ? "AND c.chest_id = ? " : "")
            + "ORDER BY l.transaction_id DESC LIMIT ? OFFSET ?";
    try (Connection connection = this.dataSource.getConnection()) {
      int total = 0;
      try (PreparedStatement count = connection.prepareStatement(countSql)) {
        count.setInt(1, roomId);
        if (chestList) {
          count.setInt(2, chestId);
        }
        try (ResultSet row = count.executeQuery()) {
          if (row.next()) {
            total = Math.max(0, row.getInt(1));
          }
        }
      }
      List<ChestTransactionLog.Info> logs = new ArrayList<>();
      try (PreparedStatement select = connection.prepareStatement(pageSql)) {
        int parameter = 1;
        select.setInt(parameter++, roomId);
        if (chestList) {
          select.setInt(parameter++, chestId);
        }
        select.setInt(parameter++, safeAmount);
        select.setInt(parameter, offset);
        try (ResultSet row = select.executeQuery()) {
          while (row.next()) {
            logs.add(readTransactionInfo(row));
          }
        }
      }
      return new ChestTransactionLog.Page(
          listType, listId, total, safePage, safeAmount, logs);
    } catch (SQLException exception) {
      LOGGER.warn(
          "Failed to load Wired chest transaction page for room {} and chest {}",
          roomId,
          chestId,
          exception);
      return new ChestTransactionLog.Page(
          listType, listId, 0, safePage, safeAmount, List.of());
    }
  }

  private static ChestTransactionLog.Info readTransactionInfo(ResultSet row)
      throws SQLException {
    return new ChestTransactionLog.Info(
        row.getLong("transaction_id"),
        row.getInt("room_id"),
        row.getInt("transaction_type"),
        row.getString("definition_info"),
        row.getInt("user_id"),
        row.getString("username"),
        row.getLong("created_at"),
        row.getInt("chest_count"),
        row.getInt("withdraw_furni_count"),
        row.getInt("deposit_furni_count"),
        row.getInt("withdraw_coins_count"),
        row.getInt("deposit_coins_count"));
  }

  private static List<ChestTransactionLog.ItemType> loadLogItemTypes(
      Connection connection, long transactionId, int direction) throws SQLException {
    List<ChestTransactionLog.ItemType> result = new ArrayList<>();
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT is_wall, type_id, legacy_poster_id, amount "
                + "FROM wired_chest_log_items "
                + "WHERE transaction_id = ? AND direction = ? "
                + "ORDER BY is_wall, type_id, legacy_poster_id")) {
      select.setLong(1, transactionId);
      select.setInt(2, direction);
      try (ResultSet row = select.executeQuery()) {
        while (row.next()) {
          result.add(
              new ChestTransactionLog.ItemType(
                  row.getBoolean("is_wall"),
                  row.getInt("type_id"),
                  row.getString("legacy_poster_id"),
                  row.getInt("amount")));
        }
      }
    }
    return List.copyOf(result);
  }

  public Result saveGeneral(
      HabboItem chest,
      int expectedRoomId,
      int expectedOwnerId,
      String name,
      String description,
      boolean everyoneCanOpen,
      boolean everyoneCanDonate,
      int stateControlMode,
      int previewMode,
      int previewAmount,
      boolean enableWired) {
    return mutateSettings(
        chest,
        expectedRoomId,
        expectedOwnerId,
        (connection, current) -> {
          if (enableWired && !current.wiredEnabled() && ChestCapacityPolicy.isStarter(chest)) {
            return Result.INVALID_AMOUNT;
          }
          ChestSettings next =
              current.withGeneral(
                  name,
                  description,
                  everyoneCanOpen,
                  everyoneCanDonate,
                  stateControlMode,
                  previewMode,
                  previewAmount,
                  enableWired);
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE items_chest_settings SET allow_open = ?, allow_donate = ?, "
                      + "display_name = ?, description = ?, appearance_state = ?, "
                      + "preview_mode = ?, preview_amount = ?, wired_enabled = ?, "
                      + "revision = revision + 1, updated_at = ? WHERE chest_id = ?")) {
            update.setBoolean(1, next.everyoneCanOpen());
            update.setBoolean(2, next.everyoneCanDonate());
            update.setString(3, next.name());
            update.setString(4, next.description());
            update.setInt(5, next.stateControlMode());
            update.setInt(6, next.previewMode());
            update.setInt(7, next.previewAmount());
            update.setBoolean(8, next.wiredEnabled());
            update.setLong(9, now());
            update.setInt(10, chest.getId());
            return update.executeUpdate() == 1 ? Result.OK : Result.CONFLICT;
          }
        });
  }

  public Result saveSafety(
      HabboItem chest,
      int expectedRoomId,
      int expectedOwnerId,
      boolean locked,
      boolean autoLock,
      int capacity) {
    ChestType type = ChestType.fromItem(chest);
    if (type == null) {
      return Result.WRONG_TYPE;
    }
    return mutateSettings(
        chest,
        expectedRoomId,
        expectedOwnerId,
        (connection, current) -> {
          int maximum = ChestCapacityPolicy.capacity(chest, type, current.capacityLevel());
          if (capacity < 0 || capacity > maximum) {
            return Result.INVALID_AMOUNT;
          }
          int contents =
              type == ChestType.COINS
                  ? lockCoinBalance(connection, chest.getId())
                  : countStoredItems(connection, chest.getId());
          if (capacity < contents) {
            return Result.FULL;
          }
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE items_chest_settings SET locked = ?, auto_lock = ?, capacity = ?, "
                      + "revision = revision + 1, updated_at = ? WHERE chest_id = ?")) {
            update.setBoolean(1, locked);
            update.setBoolean(2, autoLock);
            update.setInt(3, capacity);
            update.setLong(4, now());
            update.setInt(5, chest.getId());
            return update.executeUpdate() == 1 ? Result.OK : Result.CONFLICT;
          }
        });
  }

  /**
   * Locks an auto-locking chest while holding both its item and settings rows. The operation is
   * idempotent so duplicate room-leave paths are harmless.
   */
  public Result autoLock(HabboItem chest, int expectedRoomId, int expectedOwnerId) {
    if (ChestType.fromItem(chest) == null) {
      return Result.WRONG_TYPE;
    }
    return mutateSettings(
        chest,
        expectedRoomId,
        expectedOwnerId,
        (connection, current) -> {
          if (current.locked() || !current.autoLock()) {
            return Result.OK;
          }
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE items_chest_settings SET locked = 1, revision = revision + 1, "
                      + "updated_at = ? WHERE chest_id = ? AND locked = 0 "
                      + "AND auto_lock = 1")) {
            update.setLong(1, now());
            update.setInt(2, chest.getId());
            return update.executeUpdate() == 1 ? Result.OK : Result.CONFLICT;
          }
        });
  }

  /**
   * Persists at most July's five offline chest notifications per owner. Locking the users row
   * serializes an empty queue as well as a populated one.
   */
  public Result queueNotification(PendingNotification notification) {
    if (notification == null
        || notification.ownerId() <= 0
        || notification.chestId() <= 0
        || notification.roomId() <= 0
        || notification.type().isBlank()
        || notification.type().length() > 40
        || notification.actorName().length() > 64
        || notification.chestName().length() > 64) {
      return Result.INVALID_AMOUNT;
    }
    return inTransaction(
        connection -> {
          if (!lockUser(connection, notification.ownerId())) {
            return Result.NOT_FOUND;
          }
          List<Long> existing = new ArrayList<>();
          try (PreparedStatement select =
              connection.prepareStatement(
                  "SELECT id FROM users_chest_notifications WHERE user_id = ? "
                      + "ORDER BY id ASC FOR UPDATE")) {
            select.setInt(1, notification.ownerId());
            try (ResultSet rows = select.executeQuery()) {
              while (rows.next()) {
                existing.add(rows.getLong(1));
              }
            }
          }
          int removeCount = Math.max(0, existing.size() - 4);
          if (removeCount > 0) {
            try (PreparedStatement delete =
                connection.prepareStatement(
                    "DELETE FROM users_chest_notifications WHERE id = ? " + "AND user_id = ?")) {
              for (int i = 0; i < removeCount; i++) {
                delete.setLong(1, existing.get(i));
                delete.setInt(2, notification.ownerId());
                delete.addBatch();
              }
              delete.executeBatch();
            }
          }
          try (PreparedStatement insert =
              connection.prepareStatement(
                  "INSERT INTO users_chest_notifications "
                      + "(user_id, notification_type, chest_id, room_id, "
                      + "actor_name, chest_name, created_at) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            insert.setInt(1, notification.ownerId());
            insert.setString(2, notification.type());
            insert.setInt(3, notification.chestId());
            insert.setInt(4, notification.roomId());
            insert.setString(5, notification.actorName());
            insert.setString(6, notification.chestName());
            insert.setLong(7, notification.createdAt());
            return insert.executeUpdate() == 1 ? Result.OK : Result.CONFLICT;
          }
        },
        Result.STORAGE_ERROR);
  }

  /** Claims and removes up to five queued notifications during login. */
  public NotificationBatch drainNotifications(int ownerId) {
    if (ownerId <= 0) {
      return NotificationBatch.failure(Result.INVALID_AMOUNT);
    }
    return inTransaction(
        connection -> {
          if (!lockUser(connection, ownerId)) {
            return NotificationBatch.failure(Result.NOT_FOUND);
          }
          List<PendingNotification> notifications = new ArrayList<>();
          try (PreparedStatement select =
              connection.prepareStatement(
                  "SELECT id, notification_type, chest_id, room_id, "
                      + "actor_name, chest_name, created_at "
                      + "FROM users_chest_notifications WHERE user_id = ? "
                      + "ORDER BY id ASC LIMIT 5 FOR UPDATE")) {
            select.setInt(1, ownerId);
            try (ResultSet rows = select.executeQuery()) {
              while (rows.next()) {
                notifications.add(
                    new PendingNotification(
                        rows.getLong("id"),
                        ownerId,
                        rows.getString("notification_type"),
                        rows.getInt("chest_id"),
                        rows.getInt("room_id"),
                        rows.getString("actor_name"),
                        rows.getString("chest_name"),
                        rows.getLong("created_at")));
              }
            }
          }
          if (!notifications.isEmpty()) {
            try (PreparedStatement delete =
                connection.prepareStatement(
                    "DELETE FROM users_chest_notifications WHERE id = ? " + "AND user_id = ?")) {
              for (PendingNotification notification : notifications) {
                delete.setLong(1, notification.id());
                delete.setInt(2, ownerId);
                delete.addBatch();
              }
              delete.executeBatch();
            }
          }
          return new NotificationBatch(Result.OK, notifications);
        },
        NotificationBatch.failure(Result.STORAGE_ERROR));
  }

  public Result saveNotifications(
      HabboItem chest,
      int expectedRoomId,
      int expectedOwnerId,
      int notifyMode,
      boolean full,
      boolean donation,
      boolean withdraw,
      boolean empty,
      boolean wiredTransaction) {
    if (notifyMode < 0 || notifyMode > 1) {
      return Result.INVALID_AMOUNT;
    }
    return mutateSettings(
        chest,
        expectedRoomId,
        expectedOwnerId,
        (connection, current) -> {
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE items_chest_settings SET notify_mode = ?, notify_full = ?, "
                      + "notify_donation = ?, notify_withdraw = ?, notify_empty = ?, "
                      + "notify_wired_transaction = ?, revision = revision + 1, "
                      + "updated_at = ? WHERE chest_id = ?")) {
            update.setInt(1, notifyMode);
            update.setBoolean(2, full);
            update.setBoolean(3, donation);
            update.setBoolean(4, withdraw);
            update.setBoolean(5, empty);
            update.setBoolean(6, wiredTransaction);
            update.setLong(7, now());
            update.setInt(8, chest.getId());
            return update.executeUpdate() == 1 ? Result.OK : Result.CONFLICT;
          }
        });
  }

  public UpgradeTransfer upgradeCapacity(
      HabboItem chest,
      int expectedRoomId,
      int expectedOwnerId,
      int levels,
      int creditCost,
      int currencyCost,
      int currencyType) {
    ChestType type = ChestType.fromItem(chest);
    if (type == null || creditCost < 0 || currencyCost < 0 || currencyType < 0) {
      return UpgradeTransfer.failure(
          type == null ? Result.WRONG_TYPE : Result.INVALID_AMOUNT);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, expectedOwnerId);
          if (identity != Result.OK) {
            return UpgradeTransfer.failure(identity);
          }
          ChestSettings current = ensureAndLockSettings(connection, chest, type);
          if (current.locked()) {
            return UpgradeTransfer.failure(Result.LOCKED);
          }
          if (!ChestCapacityPolicy.validUpgrade(chest, type, current.capacityLevel(), levels)) {
            return UpgradeTransfer.failure(Result.INVALID_AMOUNT);
          }
          if (!debitCredits(connection, expectedOwnerId, creditCost)) {
            return UpgradeTransfer.failure(Result.INSUFFICIENT_CREDITS);
          }
          if (!debitCurrency(connection, expectedOwnerId, currencyType, currencyCost)) {
            return UpgradeTransfer.failure(Result.INSUFFICIENT_DIAMONDS);
          }
          int nextLevel = current.capacityLevel() + levels;
          int nextCapacity = ChestCapacityPolicy.capacity(chest, type, nextLevel);
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE items_chest_settings SET capacity_level = ?, capacity = ?, "
                      + "revision = revision + 1, updated_at = ? WHERE chest_id = ?")) {
            update.setInt(1, nextLevel);
            update.setInt(2, nextCapacity);
            update.setLong(3, now());
            update.setInt(4, chest.getId());
            if (update.executeUpdate() != 1) {
              return UpgradeTransfer.failure(Result.CONFLICT);
            }
          }
          int creditBalance =
              creditCost == 0 ? 0 : requireUserCredits(connection, expectedOwnerId);
          int currencyBalance =
              currencyCost == 0
                  ? 0
                  : requireUserCurrency(connection, expectedOwnerId, currencyType);
          return new UpgradeTransfer(
              Result.OK, creditBalance, currencyBalance, currencyType);
        },
        UpgradeTransfer.failure(Result.STORAGE_ERROR));
  }

  public ItemTransfer depositItem(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      int inventoryItemId) {
    if (ChestType.fromItem(chest) != ChestType.FURNI || chest.getId() == inventoryItemId) {
      return ItemTransfer.failure(Result.WRONG_TYPE);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, chest.getUserId());
          if (identity != Result.OK) {
            return ItemTransfer.failure(identity);
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, ChestType.FURNI);
          if (settings.locked() && actorId != chestRow.userId()) {
            return ItemTransfer.failure(Result.LOCKED);
          }
          if (countStoredItems(connection, chest.getId()) >= settings.capacity()) {
            return ItemTransfer.failure(Result.FULL);
          }
          ItemRow item = lockItem(connection, inventoryItemId);
          if (item == null || item.userId() != actorId || item.roomId() != 0) {
            return ItemTransfer.failure(Result.ITEM_NOT_OWNED);
          }
          try (PreparedStatement insert =
              connection.prepareStatement(
                  "INSERT INTO items_chest_storage (item_id, chest_id, deposited_by, deposited_at,"
                      + " lock_state, transaction_id) VALUES (?, ?, ?, ?, 0, 0)")) {
            insert.setInt(1, inventoryItemId);
            insert.setInt(2, chest.getId());
            insert.setInt(3, actorId);
            insert.setLong(4, now());
            insert.executeUpdate();
          } catch (SQLException duplicate) {
            return ItemTransfer.failure(Result.ITEM_RESERVED);
          }
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE items SET user_id = 0, room_id = 0, wall_pos = '', "
                      + "x = 0, y = 0, z = 0, rot = 0 "
                      + "WHERE id = ? AND user_id = ? AND room_id = 0")) {
            update.setInt(1, inventoryItemId);
            update.setInt(2, actorId);
            if (update.executeUpdate() != 1) {
              return ItemTransfer.failure(Result.CONFLICT);
            }
          }
          insertDirectLog(
              connection,
              expectedRoomId,
              actorId,
              audit,
              chest.getId(),
              List.of(item),
              List.of(),
              0,
              0);
          return new ItemTransfer(Result.OK, inventoryItemId);
        },
        ItemTransfer.failure(Result.STORAGE_ERROR));
  }

  /**
   * Atomically deposits a complete confirmed Wired Trade offer. Item locks are taken in id order to
   * avoid cross-session deadlocks.
   */
  public ItemBatchTransfer depositItems(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      List<Integer> requestedItemIds) {
    if (ChestType.fromItem(chest) != ChestType.FURNI) {
      return ItemBatchTransfer.failure(Result.WRONG_TYPE);
    }
    List<Integer> ordered = normalizeItemIds(requestedItemIds);
    if (ordered.isEmpty()) {
      return ItemBatchTransfer.failure(Result.EMPTY);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, chest.getUserId());
          if (identity != Result.OK) {
            return ItemBatchTransfer.failure(identity);
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, ChestType.FURNI);
          if (settings.locked() && actorId != chestRow.userId()) {
            return ItemBatchTransfer.failure(Result.LOCKED);
          }
          if ((long) countStoredItems(connection, chest.getId()) + ordered.size()
              > settings.capacity()) {
            return ItemBatchTransfer.failure(Result.FULL);
          }
          List<ItemRow> depositedRows = new ArrayList<>();
          for (int itemId : ordered) {
            if (itemId == chest.getId()) {
              return ItemBatchTransfer.failure(Result.WRONG_TYPE);
            }
            ItemRow item = lockItem(connection, itemId);
            if (item == null || item.userId() != actorId || item.roomId() != 0) {
              return ItemBatchTransfer.failure(Result.ITEM_NOT_OWNED);
            }
            if (!item.allowTrade() || creditValue(item.itemName()) > 0) {
              return ItemBatchTransfer.failure(Result.WRONG_TYPE);
            }
            depositedRows.add(item);
          }
          try (PreparedStatement insert =
                  connection.prepareStatement(
                      "INSERT INTO items_chest_storage "
                          + "(item_id, chest_id, deposited_by, deposited_at, lock_state, "
                          + "transaction_id) VALUES (?, ?, ?, ?, 0, 0)");
              PreparedStatement update =
                  connection.prepareStatement(
                      "UPDATE items SET user_id = 0, room_id = 0, wall_pos = '', "
                          + "x = 0, y = 0, z = 0, rot = 0 "
                          + "WHERE id = ? AND user_id = ? AND room_id = 0")) {
            long depositedAt = now();
            for (int itemId : ordered) {
              insert.setInt(1, itemId);
              insert.setInt(2, chest.getId());
              insert.setInt(3, actorId);
              insert.setLong(4, depositedAt);
              try {
                if (insert.executeUpdate() != 1) {
                  return ItemBatchTransfer.failure(Result.CONFLICT);
                }
              } catch (SQLException duplicate) {
                return ItemBatchTransfer.failure(Result.ITEM_RESERVED);
              }
              update.setInt(1, itemId);
              update.setInt(2, actorId);
              if (update.executeUpdate() != 1) {
                return ItemBatchTransfer.failure(Result.CONFLICT);
              }
            }
          }
          insertDirectLog(
              connection,
              expectedRoomId,
              actorId,
              audit,
              chest.getId(),
              depositedRows,
              List.of(),
              0,
              0);
          return new ItemBatchTransfer(Result.OK, ordered);
        },
        ItemBatchTransfer.failure(Result.STORAGE_ERROR));
  }

  /**
   * Redeems selected credit furniture directly into a coin chest in the same transaction that
   * deletes the item rows.
   */
  public CreditItemBatchTransfer depositCreditItems(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      List<Integer> requestedItemIds) {
    if (ChestType.fromItem(chest) != ChestType.COINS) {
      return CreditItemBatchTransfer.failure(Result.WRONG_TYPE);
    }
    List<Integer> ordered = normalizeItemIds(requestedItemIds);
    if (ordered.isEmpty()) {
      return CreditItemBatchTransfer.failure(Result.EMPTY);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, chest.getUserId());
          if (identity != Result.OK) {
            return CreditItemBatchTransfer.failure(identity);
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, ChestType.COINS);
          if (settings.locked() && actorId != chestRow.userId()) {
            return CreditItemBatchTransfer.failure(Result.LOCKED);
          }
          long depositedCredits = 0;
          for (int itemId : ordered) {
            if (itemId == chest.getId()) {
              return CreditItemBatchTransfer.failure(Result.WRONG_TYPE);
            }
            ItemRow item = lockItem(connection, itemId);
            if (item == null || item.userId() != actorId || item.roomId() != 0) {
              return CreditItemBatchTransfer.failure(Result.ITEM_NOT_OWNED);
            }
            int value = item.allowTrade() ? creditValue(item.itemName()) : 0;
            if (value <= 0) {
              return CreditItemBatchTransfer.failure(Result.WRONG_TYPE);
            }
            depositedCredits += value;
            if (depositedCredits > Integer.MAX_VALUE) {
              return CreditItemBatchTransfer.failure(Result.INVALID_AMOUNT);
            }
          }
          int balance = lockCoinBalance(connection, chest.getId());
          long nextBalance = (long) balance + depositedCredits;
          if (nextBalance > settings.capacity() || nextBalance > Integer.MAX_VALUE) {
            return CreditItemBatchTransfer.failure(Result.FULL);
          }
          try (PreparedStatement delete =
                  connection.prepareStatement(
                      "DELETE FROM items WHERE id = ? AND user_id = ? AND room_id = 0");
              PreparedStatement update =
                  connection.prepareStatement(
                      "UPDATE items_chest_coins SET coins = ?, revision = revision + 1 "
                          + "WHERE chest_id = ? AND coins = ?")) {
            for (int itemId : ordered) {
              delete.setInt(1, itemId);
              delete.setInt(2, actorId);
              if (delete.executeUpdate() != 1) {
                return CreditItemBatchTransfer.failure(Result.CONFLICT);
              }
            }
            update.setInt(1, (int) nextBalance);
            update.setInt(2, chest.getId());
            update.setInt(3, balance);
            if (update.executeUpdate() != 1) {
              return CreditItemBatchTransfer.failure(Result.CONFLICT);
            }
          }
          insertDirectLog(
              connection,
              expectedRoomId,
              actorId,
              audit,
              chest.getId(),
              List.of(),
              List.of(),
              (int) depositedCredits,
              0);
          return new CreditItemBatchTransfer(
              Result.OK, ordered, (int) nextBalance, (int) depositedCredits);
        },
        CreditItemBatchTransfer.failure(Result.STORAGE_ERROR));
  }

  public ItemTransfer withdrawItem(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      int storedItemId) {
    ItemBatchTransfer batch =
        withdrawItems(chest, expectedRoomId, actorId, audit, List.of(storedItemId));
    return batch.result() == Result.OK && batch.itemIds().size() == 1
        ? new ItemTransfer(Result.OK, batch.itemIds().getFirst())
        : ItemTransfer.failure(batch.result());
  }

  public ItemBatchTransfer withdrawItems(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      List<Integer> requestedItemIds) {
    if (ChestType.fromItem(chest) != ChestType.FURNI) {
      return ItemBatchTransfer.failure(Result.WRONG_TYPE);
    }
    LinkedHashSet<Integer> unique = new LinkedHashSet<>();
    if (requestedItemIds != null) {
      requestedItemIds.stream().filter(id -> id != null && id > 0).forEach(unique::add);
    }
    if (unique.isEmpty()) {
      return ItemBatchTransfer.failure(Result.EMPTY);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, chest.getUserId());
          if (identity != Result.OK) {
            return ItemBatchTransfer.failure(identity);
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, ChestType.FURNI);
          if (settings.locked() && actorId != chestRow.userId()) {
            return ItemBatchTransfer.failure(Result.LOCKED);
          }
          List<Integer> ordered = unique.stream().sorted(Comparator.naturalOrder()).toList();
          List<ItemRow> withdrawnRows = new ArrayList<>();
          for (int itemId : ordered) {
            try (PreparedStatement lock =
                connection.prepareStatement(
                    "SELECT lock_state, transaction_id FROM items_chest_storage "
                        + "WHERE chest_id = ? AND item_id = ? FOR UPDATE")) {
              lock.setInt(1, chest.getId());
              lock.setInt(2, itemId);
              try (ResultSet row = lock.executeQuery()) {
                if (!row.next()) {
                  return ItemBatchTransfer.failure(Result.NOT_FOUND);
                }
                if (row.getInt(1) != 0 || row.getLong(2) != 0) {
                  return ItemBatchTransfer.failure(Result.ITEM_RESERVED);
                }
              }
            }
            ItemRow item = lockItem(connection, itemId);
            if (item == null || item.userId() != 0 || item.roomId() != 0) {
              return ItemBatchTransfer.failure(Result.CONFLICT);
            }
            withdrawnRows.add(item);
          }
          try (PreparedStatement delete =
                  connection.prepareStatement(
                      "DELETE FROM items_chest_storage WHERE chest_id = ? AND item_id = ? "
                          + "AND lock_state = 0 AND transaction_id = 0");
              PreparedStatement update =
                  connection.prepareStatement(
                      "UPDATE items SET user_id = ?, room_id = 0 "
                          + "WHERE id = ? AND user_id = 0 AND room_id = 0")) {
            for (int itemId : ordered) {
              delete.setInt(1, chest.getId());
              delete.setInt(2, itemId);
              if (delete.executeUpdate() != 1) {
                return ItemBatchTransfer.failure(Result.CONFLICT);
              }
              update.setInt(1, actorId);
              update.setInt(2, itemId);
              if (update.executeUpdate() != 1) {
                return ItemBatchTransfer.failure(Result.CONFLICT);
              }
            }
          }
          insertDirectLog(
              connection,
              expectedRoomId,
              actorId,
              audit,
              chest.getId(),
              List.of(),
              withdrawnRows,
              0,
              0);
          return new ItemBatchTransfer(Result.OK, ordered);
        },
        ItemBatchTransfer.failure(Result.STORAGE_ERROR));
  }

  public CoinTransfer depositCoins(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      int amount) {
    if (ChestType.fromItem(chest) != ChestType.COINS || amount <= 0) {
      return CoinTransfer.failure(Result.INVALID_AMOUNT);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, chest.getUserId());
          if (identity != Result.OK) {
            return CoinTransfer.failure(identity);
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, ChestType.COINS);
          if (settings.locked() && actorId != chestRow.userId()) {
            return CoinTransfer.failure(Result.LOCKED);
          }
          int balance = lockCoinBalance(connection, chest.getId());
          if ((long) balance + amount > settings.capacity()) {
            return CoinTransfer.failure(Result.FULL);
          }
          Integer userBalance = lockUserCredits(connection, actorId);
          if (userBalance == null || userBalance < amount) {
            return CoinTransfer.failure(Result.INVALID_AMOUNT);
          }
          try (PreparedStatement user =
                  connection.prepareStatement(
                      "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ?");
              PreparedStatement coins =
                  connection.prepareStatement(
                      "UPDATE items_chest_coins SET coins = coins + ?, revision = revision + 1 "
                          + "WHERE chest_id = ? AND coins + ? <= ?")) {
            user.setInt(1, amount);
            user.setInt(2, actorId);
            user.setInt(3, amount);
            if (user.executeUpdate() != 1) {
              return CoinTransfer.failure(Result.CONFLICT);
            }
            coins.setInt(1, amount);
            coins.setInt(2, chest.getId());
            coins.setInt(3, amount);
            coins.setInt(4, settings.capacity());
            if (coins.executeUpdate() != 1) {
              return CoinTransfer.failure(Result.CONFLICT);
            }
          }
          insertDirectLog(
              connection,
              expectedRoomId,
              actorId,
              audit,
              chest.getId(),
              List.of(),
              List.of(),
              amount,
              0);
          return new CoinTransfer(Result.OK, balance + amount, userBalance - amount);
        },
        CoinTransfer.failure(Result.STORAGE_ERROR));
  }

  public CoinTransfer withdrawCoins(
      HabboItem chest,
      int expectedRoomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      int amount) {
    if (ChestType.fromItem(chest) != ChestType.COINS || amount <= 0) {
      return CoinTransfer.failure(Result.INVALID_AMOUNT);
    }
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chest.getId());
          Result identity = validateChestRow(chestRow, expectedRoomId, chest.getUserId());
          if (identity != Result.OK) {
            return CoinTransfer.failure(identity);
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, ChestType.COINS);
          if (settings.locked() && actorId != chestRow.userId()) {
            return CoinTransfer.failure(Result.LOCKED);
          }
          int balance = lockCoinBalance(connection, chest.getId());
          if (balance < amount) {
            return CoinTransfer.failure(Result.EMPTY);
          }
          Integer userBalance = lockUserCredits(connection, actorId);
          if (userBalance == null || (long) userBalance + amount > Integer.MAX_VALUE) {
            return CoinTransfer.failure(Result.INVALID_AMOUNT);
          }
          try (PreparedStatement coins =
                  connection.prepareStatement(
                      "UPDATE items_chest_coins SET coins = coins - ?, revision = revision + 1 "
                          + "WHERE chest_id = ? AND coins >= ?");
              PreparedStatement user =
                  connection.prepareStatement(
                      "UPDATE users SET credits = credits + ? WHERE id = ?")) {
            coins.setInt(1, amount);
            coins.setInt(2, chest.getId());
            coins.setInt(3, amount);
            if (coins.executeUpdate() != 1) {
              return CoinTransfer.failure(Result.CONFLICT);
            }
            user.setInt(1, amount);
            user.setInt(2, actorId);
            if (user.executeUpdate() != 1) {
              return CoinTransfer.failure(Result.CONFLICT);
            }
          }
          insertDirectLog(
              connection,
              expectedRoomId,
              actorId,
              audit,
              chest.getId(),
              List.of(),
              List.of(),
              0,
              amount);
          return new CoinTransfer(Result.OK, balance - amount, userBalance + amount);
        },
        CoinTransfer.failure(Result.STORAGE_ERROR));
  }

  /**
   * Commits a July contract payment and reward as one database transaction. No item, chest balance
   * or user balance changes unless every rule, capacity and reward check succeeds under row locks.
   */
  public ContractTransfer executeContract(
      int expectedRoomId,
      int actorId,
      String actorName,
      List<HabboItem> requestedChests,
      ChestContractPlan contract,
      List<HabboItem> offeredItems) {
    ChestContractEvaluator.Evaluation evaluation =
        ChestContractEvaluator.evaluate(contract, offeredItems);
    if (expectedRoomId <= 0 || actorId <= 0 || contract == null || !evaluation.valid()) {
      return ContractTransfer.failure(Result.INVALID_AMOUNT, ChestTransactionFailure.INVALID_TRADE);
    }
    Map<Integer, HabboItem> uniqueChests = new LinkedHashMap<>();
    if (requestedChests != null) {
      requestedChests.stream()
          .filter(chest -> chest != null && ChestType.fromItem(chest) != null)
          .sorted(Comparator.comparingInt(HabboItem::getId))
          .forEach(chest -> uniqueChests.put(chest.getId(), chest));
    }
    if (uniqueChests.isEmpty()) {
      return ContractTransfer.failure(
          Result.NOT_FOUND, ChestTransactionFailure.NO_AVAILABLE_CHESTS);
    }
    Map<Integer, HabboItem> uniqueOffers = new LinkedHashMap<>();
    if (offeredItems != null) {
      offeredItems.stream()
          .filter(item -> item != null && item.getId() > 0)
          .sorted(Comparator.comparingInt(HabboItem::getId))
          .forEach(item -> uniqueOffers.put(item.getId(), item));
    }
    if (uniqueOffers.size() != (offeredItems == null ? 0 : offeredItems.size())) {
      return ContractTransfer.failure(Result.CONFLICT, ChestTransactionFailure.INVALID_TRADE);
    }

    return inTransaction(
        connection -> {
          Map<Integer, LockedChest> lockedChests = new LinkedHashMap<>();
          for (HabboItem chest : uniqueChests.values()) {
            ChestType type = ChestType.fromItem(chest);
            ItemRow row = lockItem(connection, chest.getId());
            Result identity = validateChestRow(row, expectedRoomId, chest.getUserId());
            if (identity != Result.OK
                || row.baseItemId() != chest.getBaseItem().getId()) {
              return ContractTransfer.failure(
                  identity, ChestTransactionFailure.CHEST_NOT_IN_ROOM);
            }
            ChestSettings settings = ensureAndLockSettings(connection, chest, type);
            if (!settings.wiredEnabled() || settings.locked()) {
              return ContractTransfer.failure(
                  Result.LOCKED, ChestTransactionFailure.NO_AVAILABLE_CHESTS);
            }
            int coinBalance =
                type == ChestType.COINS ? lockCoinBalance(connection, chest.getId()) : 0;
            int storedCount =
                type == ChestType.FURNI ? countStoredItems(connection, chest.getId()) : 0;
            lockedChests.put(
                chest.getId(),
                new LockedChest(
                    chest.getId(), type, settings.capacity(), coinBalance, storedCount));
          }

          Integer userBalance = lockUserCredits(connection, actorId);
          if (userBalance == null) {
            return ContractTransfer.failure(
                Result.NOT_FOUND, ChestTransactionFailure.USER_CANNOT_TRADE);
          }

          List<LockedOffer> lockedOffers = new ArrayList<>();
          for (Map.Entry<Integer, HabboItem> entry : uniqueOffers.entrySet()) {
            ItemRow row = lockItem(connection, entry.getKey());
            HabboItem expected = entry.getValue();
            if (row == null
                || row.userId() != actorId
                || row.roomId() != 0
                || !row.allowTrade()
                || expected.getBaseItem() == null
                || row.baseItemId() != expected.getBaseItem().getId()
                || row.spriteId() != expected.getBaseItem().getSpriteId()
                || !normalized(row.extraData()).equals(normalized(expected.getExtradata()))) {
              return ContractTransfer.failure(
                  Result.ITEM_NOT_OWNED, ChestTransactionFailure.INVALID_TRADE);
            }
            lockedOffers.add(new LockedOffer(entry.getKey(), row));
          }

          List<StoredRow> storedRows = new ArrayList<>();
          for (LockedChest chest : lockedChests.values()) {
            if (chest.type() == ChestType.FURNI) {
              storedRows.addAll(loadAndLockStoredRows(connection, chest.id()));
            }
          }
          storedRows.sort(
              Comparator.comparingLong(StoredRow::depositedAt)
                  .thenComparingInt(StoredRow::itemId));

          RewardSelection rewards =
              selectRewards(contract.getRule(), evaluation.multiplier(), storedRows);
          if (rewards == null) {
            return ContractTransfer.failure(
                Result.EMPTY, ChestTransactionFailure.INSUFFICIENT_FUNDS);
          }
          long availableCoins =
              lockedChests.values().stream()
                  .filter(chest -> chest.type() == ChestType.COINS)
                  .mapToLong(LockedChest::coinBalance)
                  .sum();
          if (availableCoins < evaluation.withdrawnCoins()) {
            return ContractTransfer.failure(
                Result.EMPTY, ChestTransactionFailure.INSUFFICIENT_FUNDS);
          }

          Map<Integer, Integer> nextCoinBalances = new LinkedHashMap<>();
          for (LockedChest chest : lockedChests.values()) {
            if (chest.type() == ChestType.COINS) {
              nextCoinBalances.put(chest.id(), chest.coinBalance());
            }
          }
          int remainingRewardCoins = evaluation.withdrawnCoins();
          for (LockedChest chest : lockedChests.values()) {
            if (chest.type() != ChestType.COINS || remainingRewardCoins == 0) {
              continue;
            }
            int withdrawn = Math.min(nextCoinBalances.get(chest.id()), remainingRewardCoins);
            nextCoinBalances.put(chest.id(), nextCoinBalances.get(chest.id()) - withdrawn);
            remainingRewardCoins -= withdrawn;
          }

          int remainingDepositCoins = evaluation.depositedCoins();
          for (LockedChest chest : lockedChests.values()) {
            if (chest.type() != ChestType.COINS || remainingDepositCoins == 0) {
              continue;
            }
            int current = nextCoinBalances.get(chest.id());
            int accepted = Math.min(Math.max(0, chest.capacity() - current), remainingDepositCoins);
            nextCoinBalances.put(chest.id(), current + accepted);
            remainingDepositCoins -= accepted;
          }
          if (remainingDepositCoins != 0) {
            return ContractTransfer.failure(
                Result.FULL, ChestTransactionFailure.CAPACITY_EXCEEDED);
          }

          Map<Integer, Integer> furnitureCounts = new LinkedHashMap<>();
          for (LockedChest chest : lockedChests.values()) {
            if (chest.type() == ChestType.FURNI) {
              int withdrawn =
                  rewards.byChest().getOrDefault(chest.id(), List.of()).size();
              furnitureCounts.put(chest.id(), chest.storedCount() - withdrawn);
            }
          }
          Map<Integer, List<Integer>> depositsByChest = new LinkedHashMap<>();
          for (LockedOffer offer : lockedOffers) {
            if (creditValue(offer.row().itemName()) > 0) {
              continue;
            }
            LockedChest destination = null;
            for (LockedChest chest : lockedChests.values()) {
              if (chest.type() == ChestType.FURNI
                  && furnitureCounts.get(chest.id()) < chest.capacity()) {
                destination = chest;
                break;
              }
            }
            if (destination == null) {
              return ContractTransfer.failure(
                  Result.FULL, ChestTransactionFailure.CAPACITY_EXCEEDED);
            }
            depositsByChest
                .computeIfAbsent(destination.id(), ignored -> new ArrayList<>())
                .add(offer.itemId());
            furnitureCounts.put(destination.id(), furnitureCounts.get(destination.id()) + 1);
          }

          long nextUserBalance = (long) userBalance + evaluation.withdrawnCoins();
          if (nextUserBalance > Integer.MAX_VALUE) {
            return ContractTransfer.failure(
                Result.INVALID_AMOUNT, ChestTransactionFailure.FUNDS_UNAVAILABLE);
          }

          long timestamp = now();
          int transactionType = ChestTransactionLog.contractType(contract.contractType());
          String definitionInfo = "contract:" + contract.contractId();
          long transactionId =
              insertCompletedTransaction(
                  connection,
                  expectedRoomId,
                  actorId,
                  actorName,
                  transactionType,
                  definitionInfo,
                  timestamp);
          if (transactionId <= 0) {
            return ContractTransfer.failure(
                Result.STORAGE_ERROR, ChestTransactionFailure.DATABASE_ERROR);
          }
          insertTransactionChests(connection, transactionId, lockedChests.keySet());

          try (PreparedStatement removeStorage =
                  connection.prepareStatement(
                      "DELETE FROM items_chest_storage WHERE chest_id = ? AND item_id = ? "
                          + "AND lock_state = 0 AND transaction_id = 0");
              PreparedStatement giveItem =
                  connection.prepareStatement(
                      "UPDATE items SET user_id = ?, room_id = 0 WHERE id = ? "
                          + "AND user_id = 0 AND room_id = 0");
              PreparedStatement addStorage =
                  connection.prepareStatement(
                      "INSERT INTO items_chest_storage "
                          + "(item_id, chest_id, deposited_by, deposited_at, lock_state, "
                          + "transaction_id) VALUES (?, ?, ?, ?, 0, 0)");
              PreparedStatement storeItem =
                  connection.prepareStatement(
                      "UPDATE items SET user_id = 0, room_id = 0, wall_pos = '', "
                          + "x = 0, y = 0, z = 0, rot = 0 "
                          + "WHERE id = ? AND user_id = ? AND room_id = 0");
              PreparedStatement redeemCredit =
                  connection.prepareStatement(
                      "DELETE FROM items WHERE id = ? AND user_id = ? AND room_id = 0")) {
            for (StoredRow reward : rewards.items()) {
              removeStorage.setInt(1, reward.chestId());
              removeStorage.setInt(2, reward.itemId());
              if (removeStorage.executeUpdate() != 1) {
                return ContractTransfer.failure(
                    Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
              }
              giveItem.setInt(1, actorId);
              giveItem.setInt(2, reward.itemId());
              if (giveItem.executeUpdate() != 1) {
                return ContractTransfer.failure(
                    Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
              }
            }
            for (Map.Entry<Integer, List<Integer>> deposit : depositsByChest.entrySet()) {
              for (int itemId : deposit.getValue()) {
                addStorage.setInt(1, itemId);
                addStorage.setInt(2, deposit.getKey());
                addStorage.setInt(3, actorId);
                addStorage.setLong(4, timestamp);
                if (addStorage.executeUpdate() != 1) {
                  return ContractTransfer.failure(
                      Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
                }
                storeItem.setInt(1, itemId);
                storeItem.setInt(2, actorId);
                if (storeItem.executeUpdate() != 1) {
                  return ContractTransfer.failure(
                      Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
                }
              }
            }
            for (LockedOffer offer : lockedOffers) {
              if (creditValue(offer.row().itemName()) <= 0) {
                continue;
              }
              redeemCredit.setInt(1, offer.itemId());
              redeemCredit.setInt(2, actorId);
              if (redeemCredit.executeUpdate() != 1) {
                return ContractTransfer.failure(
                    Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
              }
            }
          }

          try (PreparedStatement updateCoins =
                  connection.prepareStatement(
                      "UPDATE items_chest_coins SET coins = ?, revision = revision + 1 "
                          + "WHERE chest_id = ? AND coins = ?");
              PreparedStatement updateUser =
                  connection.prepareStatement(
                      "UPDATE users SET credits = credits + ? WHERE id = ? AND credits = ?")) {
            for (LockedChest chest : lockedChests.values()) {
              if (chest.type() != ChestType.COINS) {
                continue;
              }
              int next = nextCoinBalances.get(chest.id());
              if (next == chest.coinBalance()) {
                continue;
              }
              updateCoins.setInt(1, next);
              updateCoins.setInt(2, chest.id());
              updateCoins.setInt(3, chest.coinBalance());
              if (updateCoins.executeUpdate() != 1) {
                return ContractTransfer.failure(
                    Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
              }
            }
            if (evaluation.withdrawnCoins() > 0) {
              updateUser.setInt(1, evaluation.withdrawnCoins());
              updateUser.setInt(2, actorId);
              updateUser.setInt(3, userBalance);
              if (updateUser.executeUpdate() != 1) {
                return ContractTransfer.failure(
                    Result.CONFLICT, ChestTransactionFailure.DATABASE_ERROR);
              }
            }
          }

          insertContractLog(
              connection,
              transactionId,
              expectedRoomId,
              actorId,
              actorName,
              transactionType,
              definitionInfo,
              lockedChests.keySet(),
              evaluation,
              lockedOffers.stream()
                  .map(LockedOffer::row)
                  .filter(item -> creditValue(item.itemName()) <= 0)
                  .toList(),
              rewards.items().stream().map(StoredRow::item).toList(),
              timestamp);
          return new ContractTransfer(
              Result.OK,
              null,
              transactionId,
              evaluation.multiplier(),
              List.copyOf(uniqueOffers.keySet()),
              rewards.items().stream().map(StoredRow::itemId).toList(),
              evaluation.depositedCoins(),
              evaluation.withdrawnCoins(),
              (int) nextUserBalance,
              nextCoinBalances,
              depositsByChest,
              rewards.byChest());
        },
        ContractTransfer.failure(Result.STORAGE_ERROR, ChestTransactionFailure.DATABASE_ERROR));
  }

  /**
   * Deletes an empty chest and its auxiliary rows atomically. The item row is locked until commit,
   * preventing a concurrent deposit from becoming orphaned between the contents check and deletion.
   */
  public Result deleteChest(HabboItem chest) {
    if (ChestType.fromItem(chest) == null) {
      return Result.WRONG_TYPE;
    }
    int chestId = chest.getId();
    return inTransaction(
        connection -> {
          ItemRow chestRow = lockItem(connection, chestId);
          if (chestRow == null) {
            return Result.NOT_FOUND;
          }
          if (countStoredItems(connection, chestId) != 0
              || lockCoinBalance(connection, chestId) != 0) {
            return Result.CONFLICT;
          }
          try (PreparedStatement storage =
                  connection.prepareStatement(
                      "DELETE FROM items_chest_storage WHERE chest_id = ?");
              PreparedStatement coins =
                  connection.prepareStatement("DELETE FROM items_chest_coins WHERE chest_id = ?");
              PreparedStatement settings =
                  connection.prepareStatement(
                      "DELETE FROM items_chest_settings WHERE chest_id = ?");
              PreparedStatement notifications =
                  connection.prepareStatement(
                      "DELETE FROM users_chest_notifications WHERE chest_id = ?");
              PreparedStatement item =
                  connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
            storage.setInt(1, chestId);
            coins.setInt(1, chestId);
            settings.setInt(1, chestId);
            notifications.setInt(1, chestId);
            item.setInt(1, chestId);
            storage.executeUpdate();
            coins.executeUpdate();
            settings.executeUpdate();
            notifications.executeUpdate();
            if (item.executeUpdate() != 1) {
              return Result.CONFLICT;
            }
          }
          return Result.OK;
        },
        Result.STORAGE_ERROR);
  }

  private Result mutateSettings(
      HabboItem chest, int roomId, int ownerId, SettingsMutation mutation) {
    ChestType type = ChestType.fromItem(chest);
    if (type == null) {
      return Result.WRONG_TYPE;
    }
    return inTransaction(
        connection -> {
          Result identity = validateChestRow(lockItem(connection, chest.getId()), roomId, ownerId);
          if (identity != Result.OK) {
            return identity;
          }
          ChestSettings settings = ensureAndLockSettings(connection, chest, type);
          return mutation.apply(connection, settings);
        },
        Result.STORAGE_ERROR);
  }

  private static List<StoredRow> loadAndLockStoredRows(Connection connection, int chestId)
      throws SQLException {
    List<StoredRow> rows = new ArrayList<>();
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT s.item_id, s.deposited_at, i.user_id, i.room_id, i.item_id, "
                + "b.item_name, b.allow_trade, b.sprite_id, b.type, i.extra_data "
                + "FROM items_chest_storage s "
                + "INNER JOIN items i ON i.id = s.item_id "
                + "INNER JOIN items_base b ON b.id = i.item_id "
                + "WHERE s.chest_id = ? AND s.lock_state = 0 AND s.transaction_id = 0 "
                + "ORDER BY s.item_id FOR UPDATE")) {
      select.setInt(1, chestId);
      try (ResultSet row = select.executeQuery()) {
        while (row.next()) {
          ItemRow item =
              new ItemRow(
                  row.getInt(3),
                  row.getInt(4),
                  row.getInt(5),
                  row.getString(6),
                  row.getBoolean(7),
                  row.getInt(8),
                  row.getString(9),
                  row.getString(10));
          if (item.userId() != 0 || item.roomId() != 0) {
            throw new SQLException("Stored chest item has invalid ownership");
          }
          rows.add(new StoredRow(row.getInt(1), chestId, row.getLong(2), item));
        }
      }
    }
    return rows;
  }

  private static RewardSelection selectRewards(
      ChestContractPlan.Rule rule, int multiplier, List<StoredRow> storedRows) {
    if (rule == null) {
      return new RewardSelection(List.of(), Map.of());
    }
    Set<Integer> used = new HashSet<>();
    List<StoredRow> selected = new ArrayList<>();
    Map<Integer, List<Integer>> byChest = new LinkedHashMap<>();
    for (ChestContractPlan.Node node : rule.nodes()) {
      if (node.type() == 0) {
        continue;
      }
      long requiredLong = (long) node.amount() * multiplier;
      if (requiredLong <= 0 || requiredLong > Integer.MAX_VALUE) {
        return null;
      }
      int required = (int) requiredLong;
      int found = 0;
      for (StoredRow row : storedRows) {
        if (used.contains(row.itemId()) || !matches(node, row.item())) {
          continue;
        }
        used.add(row.itemId());
        selected.add(row);
        byChest.computeIfAbsent(row.chestId(), ignored -> new ArrayList<>()).add(row.itemId());
        if (++found == required) {
          break;
        }
      }
      if (found != required) {
        return null;
      }
    }
    return new RewardSelection(selected, byChest);
  }

  private static boolean matches(ChestContractPlan.Node node, ItemRow item) {
    if (node == null || item == null) {
      return false;
    }
    if (node.type() == 0) {
      return creditValue(item.itemName()) > 0;
    }
    ChestContractPlan.ItemType type = node.itemType();
    boolean wall = "I".equalsIgnoreCase(item.furnitureType());
    return type != null
        && type.wall() == wall
        && type.typeId() == item.spriteId()
        && (normalized(type.poster()).isEmpty()
            || normalized(type.poster()).equals(normalized(item.extraData())));
  }

  private static long insertCompletedTransaction(
      Connection connection,
      int roomId,
      int actorId,
      String actorName,
      int transactionType,
      String definitionInfo,
      long timestamp)
      throws SQLException {
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO wired_chest_transactions "
                + "(room_id, actor_id, actor_name, transaction_type, definition_info, status, "
                + "created_at, expires_at, completed_at) VALUES (?, ?, ?, ?, ?, 1, ?, 0, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
      insert.setInt(1, roomId);
      insert.setInt(2, actorId);
      insert.setString(3, safe(actorName, 64));
      insert.setInt(4, transactionType);
      insert.setString(5, safe(definitionInfo, 255));
      insert.setLong(6, timestamp);
      insert.setLong(7, timestamp);
      if (insert.executeUpdate() != 1) {
        return 0;
      }
      try (ResultSet keys = insert.getGeneratedKeys()) {
        return keys.next() ? keys.getLong(1) : 0;
      }
    }
  }

  private static void insertTransactionChests(
      Connection connection, long transactionId, Set<Integer> chestIds) throws SQLException {
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO wired_chest_transaction_chests (transaction_id, chest_id) "
                + "VALUES (?, ?)")) {
      for (int chestId : chestIds) {
        insert.setLong(1, transactionId);
        insert.setInt(2, chestId);
        if (insert.executeUpdate() != 1) {
          throw new SQLException("Could not attach chest to transaction");
        }
      }
    }
  }

  private static void insertContractLog(
      Connection connection,
      long transactionId,
      int roomId,
      int actorId,
      String actorName,
      int transactionType,
      String definitionInfo,
      Set<Integer> chestIds,
      ChestContractEvaluator.Evaluation evaluation,
      List<ItemRow> depositedItems,
      List<ItemRow> withdrawnItems,
      long timestamp)
      throws SQLException {
    insertLog(
        connection,
        transactionId,
        roomId,
        actorId,
        actorName,
        transactionType,
        definitionInfo,
        chestIds,
        depositedItems,
        withdrawnItems,
        evaluation.depositedCoins(),
        evaluation.withdrawnCoins(),
        timestamp);
  }

  private static void insertDirectLog(
      Connection connection,
      int roomId,
      int actorId,
      ChestTransactionLog.Audit audit,
      int chestId,
      List<ItemRow> depositedItems,
      List<ItemRow> withdrawnItems,
      int depositedCoins,
      int withdrawnCoins)
      throws SQLException {
    if (audit == null) {
      throw new SQLException("Chest audit metadata is required");
    }
    long timestamp = now();
    long transactionId =
        insertCompletedTransaction(
            connection,
            roomId,
            actorId,
            audit.actorName(),
            audit.transactionType(),
            audit.definitionInfo(),
            timestamp);
    if (transactionId <= 0) {
      throw new SQLException("Could not create completed chest transaction");
    }
    Set<Integer> chestIds = Set.of(chestId);
    insertTransactionChests(connection, transactionId, chestIds);
    insertLog(
        connection,
        transactionId,
        roomId,
        actorId,
        audit.actorName(),
        audit.transactionType(),
        audit.definitionInfo(),
        chestIds,
        depositedItems,
        withdrawnItems,
        depositedCoins,
        withdrawnCoins,
        timestamp);
  }

  private static void insertLog(
      Connection connection,
      long transactionId,
      int roomId,
      int actorId,
      String actorName,
      int transactionType,
      String definitionInfo,
      Set<Integer> chestIds,
      List<ItemRow> depositedItems,
      List<ItemRow> withdrawnItems,
      int depositedCoins,
      int withdrawnCoins,
      long timestamp)
      throws SQLException {
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO wired_chest_logs "
                + "(transaction_id, room_id, transaction_type, definition_info, user_id, "
                + "username, created_at, chest_count, withdraw_furni_count, "
                + "deposit_furni_count, withdraw_coins_count, deposit_coins_count) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      insert.setLong(1, transactionId);
      insert.setInt(2, roomId);
      insert.setInt(3, transactionType);
      insert.setString(4, safe(definitionInfo, 255));
      insert.setInt(5, actorId);
      insert.setString(6, safe(actorName, 64));
      insert.setLong(7, timestamp);
      insert.setInt(8, chestIds.size());
      insert.setInt(9, withdrawnItems == null ? 0 : withdrawnItems.size());
      insert.setInt(10, depositedItems == null ? 0 : depositedItems.size());
      insert.setInt(11, Math.max(0, withdrawnCoins));
      insert.setInt(12, Math.max(0, depositedCoins));
      if (insert.executeUpdate() != 1) {
        throw new SQLException("Could not write chest log");
      }
    }
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO wired_chest_log_chests (transaction_id, chest_id) VALUES (?, ?)")) {
      for (int chestId : chestIds) {
        insert.setLong(1, transactionId);
        insert.setInt(2, chestId);
        if (insert.executeUpdate() != 1) {
          throw new SQLException("Could not attach chest to log");
        }
      }
    }
    insertLogItems(
        connection,
        transactionId,
        ChestTransactionLog.DIRECTION_DEPOSIT,
        depositedItems);
    insertLogItems(
        connection,
        transactionId,
        ChestTransactionLog.DIRECTION_WITHDRAW,
        withdrawnItems);
  }

  private static void insertLogItems(
      Connection connection, long transactionId, int direction, List<ItemRow> items)
      throws SQLException {
    if (items == null || items.isEmpty()) {
      return;
    }
    Map<LogItemKey, Integer> grouped = new LinkedHashMap<>();
    for (ItemRow item : items) {
      if (item == null) {
        continue;
      }
      boolean wall = "I".equalsIgnoreCase(item.furnitureType());
      String poster =
          wall && item.itemName() != null && item.itemName().startsWith("poster")
              ? safe(item.extraData(), 255)
              : "";
      LogItemKey key = new LogItemKey(wall, item.spriteId(), poster);
      grouped.merge(key, 1, Integer::sum);
    }
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO wired_chest_log_items "
                + "(transaction_id, direction, is_wall, type_id, legacy_poster_id, amount) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
      for (Map.Entry<LogItemKey, Integer> entry : grouped.entrySet()) {
        insert.setLong(1, transactionId);
        insert.setInt(2, direction);
        insert.setBoolean(3, entry.getKey().wall());
        insert.setInt(4, entry.getKey().typeId());
        insert.setString(5, entry.getKey().poster());
        insert.setInt(6, entry.getValue());
        if (insert.executeUpdate() != 1) {
          throw new SQLException("Could not write chest transaction item snapshot");
        }
      }
    }
  }

  private static String normalized(String value) {
    return value == null ? "" : value.trim();
  }

  private static String safe(String value, int maximum) {
    String clean = value == null ? "" : value.replace('\0', ' ').trim();
    return clean.length() <= maximum ? clean : clean.substring(0, maximum);
  }

  private ChestSettings ensureAndLockSettings(
      Connection connection, HabboItem chest, ChestType type) throws SQLException {
    int capacity = ChestCapacityPolicy.capacity(chest, type, 0);
    long now = now();
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO items_chest_settings "
                + "(chest_id, capacity, created_at, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE chest_id = VALUES(chest_id)")) {
      insert.setInt(1, chest.getId());
      insert.setInt(2, capacity);
      insert.setLong(3, now);
      insert.setLong(4, now);
      insert.executeUpdate();
    }
    if (type == ChestType.COINS) {
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO items_chest_coins (chest_id, coins, revision) VALUES (?, 0, 0) "
                  + "ON DUPLICATE KEY UPDATE chest_id = VALUES(chest_id)")) {
        insert.setInt(1, chest.getId());
        insert.executeUpdate();
      }
    }
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT * FROM items_chest_settings WHERE chest_id = ? FOR UPDATE")) {
      select.setInt(1, chest.getId());
      try (ResultSet row = select.executeQuery()) {
        if (!row.next()) {
          throw new SQLException("Chest settings insert vanished");
        }
        return readSettings(row);
      }
    }
  }

  private static ChestSettings readSettings(ResultSet row) throws SQLException {
    return new ChestSettings(
        row.getBoolean("allow_open"),
        row.getBoolean("allow_donate"),
        row.getString("display_name"),
        row.getString("description"),
        row.getInt("appearance_state"),
        row.getInt("preview_mode"),
        row.getInt("preview_amount"),
        row.getInt("capacity"),
        row.getInt("capacity_level"),
        row.getBoolean("wired_enabled"),
        row.getBoolean("locked"),
        row.getBoolean("auto_lock"),
        row.getInt("notify_mode"),
        row.getBoolean("notify_full"),
        row.getBoolean("notify_donation"),
        row.getBoolean("notify_withdraw"),
        row.getBoolean("notify_empty"),
        row.getBoolean("notify_wired_transaction"),
        row.getLong("revision"));
  }

  private static ItemRow lockItem(Connection connection, int itemId) throws SQLException {
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT i.user_id, i.room_id, i.item_id, b.item_name, b.allow_trade, "
                + "b.sprite_id, b.type, i.extra_data "
                + "FROM items i INNER JOIN items_base b ON b.id = i.item_id "
                + "WHERE i.id = ? FOR UPDATE")) {
      select.setInt(1, itemId);
      try (ResultSet row = select.executeQuery()) {
        return row.next()
            ? new ItemRow(
                row.getInt(1),
                row.getInt(2),
                row.getInt(3),
                row.getString(4),
                row.getBoolean(5),
                row.getInt(6),
                row.getString(7),
                row.getString(8))
            : null;
      }
    }
  }

  private static int creditValue(String itemName) {
    if (itemName == null
        || itemName.contains("_diamond_")
        || (!itemName.startsWith("CF_") && !itemName.startsWith("CFC_"))) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(itemName.split("_", 3)[1]));
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private static Result validateChestRow(ItemRow row, int expectedRoomId, int expectedOwnerId) {
    if (row == null) {
      return Result.NOT_FOUND;
    }
    if (row.roomId() != expectedRoomId || expectedRoomId <= 0) {
      return Result.STALE_ROOM;
    }
    return row.userId() == expectedOwnerId ? Result.OK : Result.NOT_OWNER;
  }

  private static int countStoredItems(Connection connection, int chestId) throws SQLException {
    try (PreparedStatement count =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM items_chest_storage WHERE chest_id = ?")) {
      count.setInt(1, chestId);
      try (ResultSet row = count.executeQuery()) {
        return row.next() ? row.getInt(1) : 0;
      }
    }
  }

  private static List<Integer> normalizeItemIds(List<Integer> requestedItemIds) {
    LinkedHashSet<Integer> unique = new LinkedHashSet<>();
    if (requestedItemIds != null) {
      requestedItemIds.stream().filter(id -> id != null && id > 0).limit(1500).forEach(unique::add);
    }
    return unique.stream().sorted(Comparator.naturalOrder()).toList();
  }

  private static int lockCoinBalance(Connection connection, int chestId) throws SQLException {
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO items_chest_coins (chest_id, coins, revision) VALUES (?, 0, 0) "
                + "ON DUPLICATE KEY UPDATE chest_id = VALUES(chest_id)")) {
      insert.setInt(1, chestId);
      insert.executeUpdate();
    }
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT coins FROM items_chest_coins WHERE chest_id = ? FOR UPDATE")) {
      select.setInt(1, chestId);
      try (ResultSet row = select.executeQuery()) {
        return row.next() ? row.getInt(1) : 0;
      }
    }
  }

  private static Integer lockUserCredits(Connection connection, int userId) throws SQLException {
    try (PreparedStatement select =
        connection.prepareStatement("SELECT credits FROM users WHERE id = ? FOR UPDATE")) {
      select.setInt(1, userId);
      try (ResultSet row = select.executeQuery()) {
        return row.next() ? row.getInt(1) : null;
      }
    }
  }

  private static boolean lockUser(Connection connection, int userId) throws SQLException {
    try (PreparedStatement select =
        connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
      select.setInt(1, userId);
      try (ResultSet row = select.executeQuery()) {
        return row.next();
      }
    }
  }

  private static boolean debitCredits(Connection connection, int userId, int amount)
      throws SQLException {
    if (amount == 0) {
      return true;
    }
    try (PreparedStatement update =
        connection.prepareStatement(
            "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ?")) {
      update.setInt(1, amount);
      update.setInt(2, userId);
      update.setInt(3, amount);
      return update.executeUpdate() == 1;
    }
  }

  private static boolean debitCurrency(
      Connection connection, int userId, int currencyType, int amount) throws SQLException {
    if (amount == 0) {
      return true;
    }
    try (PreparedStatement update =
        connection.prepareStatement(
            "UPDATE users_currency SET amount = amount - ? "
                + "WHERE user_id = ? AND type = ? AND amount >= ?")) {
      update.setInt(1, amount);
      update.setInt(2, userId);
      update.setInt(3, currencyType);
      update.setInt(4, amount);
      return update.executeUpdate() == 1;
    }
  }

  private static int requireUserCredits(Connection connection, int userId) throws SQLException {
    Integer balance = lockUserCredits(connection, userId);
    if (balance == null) {
      throw new SQLException("User disappeared during chest upgrade");
    }
    return Math.max(0, balance);
  }

  private static int requireUserCurrency(
      Connection connection, int userId, int currencyType) throws SQLException {
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT amount FROM users_currency WHERE user_id = ? AND type = ? FOR UPDATE")) {
      select.setInt(1, userId);
      select.setInt(2, currencyType);
      try (ResultSet row = select.executeQuery()) {
        if (!row.next()) {
          throw new SQLException("Currency disappeared during chest upgrade");
        }
        return Math.max(0, row.getInt(1));
      }
    }
  }

  private <T> T inTransaction(Transaction<T> transaction, T failure) {
    try (Connection connection = this.dataSource.getConnection()) {
      boolean previousAutoCommit = connection.getAutoCommit();
      int previousIsolation = connection.getTransactionIsolation();
      try {
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        T result = transaction.apply(connection);
        if (isSuccess(result)) {
          connection.commit();
        } else {
          connection.rollback();
        }
        return result;
      } catch (Exception exception) {
        rollbackQuietly(connection);
        LOGGER.error("Chest transaction failed", exception);
        return failure;
      } finally {
        try {
          connection.setTransactionIsolation(previousIsolation);
          connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
          // Connection is closing.
        }
      }
    } catch (SQLException exception) {
      LOGGER.error("Could not open chest transaction", exception);
      return failure;
    }
  }

  private static boolean isSuccess(Object value) {
    if (value instanceof Result result) {
      return result == Result.OK;
    }
    if (value instanceof ItemTransfer transfer) {
      return transfer.result() == Result.OK;
    }
    if (value instanceof ItemBatchTransfer transfer) {
      return transfer.result() == Result.OK;
    }
    if (value instanceof CreditItemBatchTransfer transfer) {
      return transfer.result() == Result.OK;
    }
    if (value instanceof NotificationBatch batch) {
      return batch.result() == Result.OK;
    }
    if (value instanceof ContractTransfer transfer) {
      return transfer.result() == Result.OK;
    }
    if (value instanceof UpgradeTransfer transfer) {
      return transfer.result() == Result.OK;
    }
    return value instanceof CoinTransfer transfer && transfer.result() == Result.OK;
  }

  private static void rollbackQuietly(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException ignored) {
      // Original exception is more useful.
    }
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  private record ItemRow(
      int userId,
      int roomId,
      int baseItemId,
      String itemName,
      boolean allowTrade,
      int spriteId,
      String furnitureType,
      String extraData) {}

  private record LockedChest(
      int id, ChestType type, int capacity, int coinBalance, int storedCount) {}

  private record LockedOffer(int itemId, ItemRow row) {}

  private record StoredRow(int itemId, int chestId, long depositedAt, ItemRow item) {}

  private record LogItemKey(boolean wall, int typeId, String poster) {}

  private record RewardSelection(
      List<StoredRow> items, Map<Integer, List<Integer>> byChest) {}

  @FunctionalInterface
  private interface Transaction<T> {
    T apply(Connection connection) throws Exception;
  }

  @FunctionalInterface
  private interface SettingsMutation {
    Result apply(Connection connection, ChestSettings settings) throws Exception;
  }
}
