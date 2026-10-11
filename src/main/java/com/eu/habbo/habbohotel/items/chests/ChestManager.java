package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTransactionOutcome;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.NotificationDialogMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListRemoveMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectDataUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.users.ActivityPointsMessageComposer;
import com.eu.habbo.messages.outgoing.users.CreditBalanceMessageComposer;
import com.eu.habbo.messages.outgoing.wired.chests.ChestCoinBalanceComposer;
import com.eu.habbo.messages.outgoing.wired.chests.ChestFurniContentsComposer;
import com.eu.habbo.messages.outgoing.wired.chests.ChestFurniContentsUpdateComposer;
import com.eu.habbo.messages.outgoing.wired.chests.ChestOpenInstructionComposer;
import com.eu.habbo.messages.outgoing.wired.chests.WiredTradeCancelledComposer;
import com.eu.habbo.messages.outgoing.wired.chests.WiredTradeCompletedComposer;
import com.eu.habbo.messages.outgoing.wired.chests.WiredTradeInitiateComposer;
import com.eu.habbo.messages.outgoing.wired.chests.WiredTradeItemUpdateComposer;
import com.eu.habbo.messages.outgoing.wired.chests.WiredTransactionSuccessComposer;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Room-facing chest domain service; packet handlers do not access SQL directly. */
public final class ChestManager {
  private static final int CONTENTS_FRAGMENT_SIZE = 100;
  private static final int TRADE_TIMEOUT_SECONDS = 60;
  private static final long TRADE_CONFIRM_COUNTDOWN_MILLIS = 2800L;
  private static final int MAX_TRADE_ITEMS = 1500;
  private static final int CHEST_LOCK_STRIPES = 256;
  private static final int CONTRACT_CAPABILITIES =
      WiredCapabilityService.CAPABILITY_VARIABLES
          | WiredCapabilityService.CAPABILITY_CHESTS
          | WiredCapabilityService.CAPABILITY_CHEST_WIRED
          | WiredCapabilityService.CAPABILITY_CONTRACTS;
  private static final String NOTIFICATION_FULL = "wired_chests.chest_full";
  private static final String NOTIFICATION_DONATION = "wired_chests.donation";
  private static final String NOTIFICATION_WITHDRAW = "wired_chests.someone_withdraws";
  private static final String NOTIFICATION_EMPTY = "wired_chests.chest_empty";
  private static final String NOTIFICATION_WIRED = "wired_chests.chest_wired_transaction";
  private final ChestRepository repository;
  private final Map<Integer, CachedChest> cache = new ConcurrentHashMap<>();
  private final ReentrantLock[] chestLocks = createChestLocks();

  public ChestManager() {
    this(new ChestRepository());
  }

  ChestManager(ChestRepository repository) {
    this.repository = repository;
  }

  public boolean isChest(HabboItem item) {
    return ChestType.fromItem(item) != null;
  }

  public ChestSettings settings(HabboItem chest) {
    CachedChest cached = snapshot(chest);
    return cached == null ? null : cached.settings();
  }

  public int contentsCount(HabboItem chest) {
    CachedChest cached = snapshot(chest);
    return cached == null ? 0 : cached.contentsCount();
  }

  public Map<String, String> furnitureData(HabboItem chest) {
    if (!isChest(chest)) {
      return Map.of("state", "0");
    }
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          LinkedHashMap<String, String> data = new LinkedHashMap<>();
          if (settings == null) {
            data.put("state", "0");
            if (ChestType.fromItem(chest) == ChestType.FURNI) {
              data.put("visuals", "");
            }
            return data;
          }
          int contents = contentsCount(chest);
          boolean open = isVisuallyOpen(chest, settings);
          data.put("state", Integer.toString(visualState(chest, settings, contents, open)));
          if (ChestType.fromItem(chest) == ChestType.FURNI) {
            data.put("visuals", open ? furnitureVisuals(chest) : "");
          }
          data.put("locked", flag(settings.locked()));
          data.put("auto_lock", flag(settings.autoLock()));
          data.put("capacity", Integer.toString(settings.capacity()));
          data.put("contents_count", Integer.toString(contents));
          data.put("capacity_level", Integer.toString(settings.capacityLevel()));
          data.put("chest_name", settings.name());
          data.put("chest_desc", settings.description());
          data.put("everyone_can_open", flag(settings.everyoneCanOpen()));
          data.put("everyone_can_donate", flag(settings.everyoneCanDonate()));
          data.put("state_control_mode", Integer.toString(settings.stateControlMode()));
          data.put("is_wired_enabled", flag(settings.wiredEnabled()));
          data.put("notify_mode", Integer.toString(settings.notifyMode()));
          data.put("preview_mode", Integer.toString(settings.previewMode()));
          data.put("preview_amount", Integer.toString(settings.previewAmount()));
          data.put("notification_chest_full", flag(settings.notifyFull()));
          data.put("notification_donation", flag(settings.notifyDonation()));
          data.put("notification_someone_withdraws", flag(settings.notifyWithdraw()));
          data.put("notification_chest_empty", flag(settings.notifyEmpty()));
          data.put("notification_wired_transaction", flag(settings.notifyWiredTransaction()));
          return data;
        });
  }

  public boolean canViewLogs(GameClient client, Room room, HabboItem chest) {
    return chest != null
        && allowed(ChestAuthorization.Operation.VIEW_LOGS, client, room, chest, settings(chest));
  }

  public ChestTransactionLog.Page roomTransactionLogs(int roomId, int amount, int page) {
    return this.repository.loadRoomTransactionLogs(roomId, amount, page);
  }

  public ChestTransactionLog.Page chestTransactionLogs(
      int roomId, HabboItem chest, int amount, int page) {
    return this.repository.loadChestTransactionLogs(
        roomId, chest.getId(), chest.getRoomVisibleId(), amount, page);
  }

  public ChestTransactionLog.Details transactionDetails(long transactionId) {
    return this.repository.loadTransactionDetails(transactionId);
  }

  public void open(GameClient client, Room room, HabboItem chest) {
    if (client == null
        || room == null
        || !client
            .getWiredCapabilityState()
            .supportsRoom(WiredCapabilityService.CAPABILITY_CHESTS, room.getId())) {
      return;
    }
    ChestSettings settings = settings(chest);
    if (!allowed(ChestAuthorization.Operation.OPEN, client, room, chest, settings)) {
      return;
    }
    client.sendResponse(new ChestOpenInstructionComposer(chest.getRoomVisibleId()));
  }

  public void sendContents(GameClient client, Room room, HabboItem chest) {
    if (client == null
        || room == null
        || !client
            .getWiredCapabilityState()
            .supportsRoom(WiredCapabilityService.CAPABILITY_CHESTS, room.getId())) {
      return;
    }
    ChestSettings settings = settings(chest);
    if (!allowed(ChestAuthorization.Operation.OPEN, client, room, chest, settings)) {
      return;
    }
    long targetKey = chestKey(room.getId(), chest.getId());
    long previousKey = client.setActiveChest(room.getId(), chest.getId());
    if (previousKey != 0L && previousKey != targetKey) {
      refreshViewerKey(previousKey);
    }
    withChestLock(
        chest.getId(),
        () -> {
          ChestSettings current = settings(chest);
          if (!client.isActiveChest(room.getId(), chest.getId())
              || !allowed(ChestAuthorization.Operation.OPEN, client, room, chest, current)) {
            if (client.clearActiveChest(targetKey)) {
              refreshAppearance(room, chest);
            }
            return null;
          }
          if (ChestType.fromItem(chest) == ChestType.COINS) {
            client.sendResponse(
                new ChestCoinBalanceComposer(
                    chest.getRoomVisibleId(), this.repository.loadCoinBalance(chest.getId()), false));
          } else {
            List<ChestRepository.StoredHabboItem> items =
                this.repository.loadStoredHabboItems(chest.getId());
            int fragments =
                Math.max(1, (items.size() + CONTENTS_FRAGMENT_SIZE - 1) / CONTENTS_FRAGMENT_SIZE);
            for (int fragment = 0; fragment < fragments; fragment++) {
              int from = fragment * CONTENTS_FRAGMENT_SIZE;
              int to = Math.min(items.size(), from + CONTENTS_FRAGMENT_SIZE);
              client.sendResponse(
                  new ChestFurniContentsComposer(
                      chest.getRoomVisibleId(), fragments, fragment, items.subList(from, to)));
            }
          }
          refreshAppearance(room, chest);
          return null;
        });
  }

  public void close(GameClient client, Room room, HabboItem chest) {
    if (client != null
        && room != null
        && chest != null
        && client.isActiveChest(room.getId(), chest.getId())) {
      ChestTradeSession session = client.getChestTradeSession();
      if (session != null && !session.isContract() && session.chestId() == chest.getId()) {
        abortTrade(client, true, 3);
      }
      withChestLock(
          chest.getId(),
          () -> {
            long expectedKey = chestKey(room.getId(), chest.getId());
            if (client.clearActiveChest(expectedKey)) {
              refreshAppearance(room, chest);
            }
            return null;
          });
    }
  }

  /** Clears a viewer on room exit, capability reset or disconnect and refreshes mode 0 state. */
  public void closeActiveViewer(GameClient client) {
    if (client == null) {
      return;
    }
    long activeChestKey = client.getActiveChestKey();
    int roomId = (int) (activeChestKey >>> 32);
    int chestId = (int) activeChestKey;
    if (roomId <= 0 || chestId <= 0) {
      client.clearActiveChest(activeChestKey);
      return;
    }
    Room room =
        com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
    HabboItem chest = room == null ? null : room.getHabboItemByDatabaseId(chestId);
    withChestLock(
        chestId,
        () -> {
          boolean cleared = client.clearActiveChest(activeChestKey);
          if (cleared && room != null && chest != null && isChest(chest)) {
            refreshAppearance(room, chest);
          }
          return null;
        });
  }

  private void refreshViewerKey(long viewerKey) {
    int roomId = (int) (viewerKey >>> 32);
    int chestId = (int) viewerKey;
    if (roomId <= 0 || chestId <= 0) {
      return;
    }
    Room room =
        com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
    HabboItem chest = room == null ? null : room.getHabboItemByDatabaseId(chestId);
    if (chest == null || !isChest(chest)) {
      return;
    }
    withChestLock(
        chestId,
        () -> {
          refreshAppearance(room, chest);
          return null;
        });
  }

  private static long chestKey(int roomId, int chestId) {
    return ((long) roomId << 32) | (chestId & 0xffffffffL);
  }

  /** Existing toggle-state Wired uses this producer for appearance mode 3. */
  public void toggleStateFromWired(Room room, HabboItem chest) {
    if (room == null
        || !isChest(chest)
        || chest.getRoomId() != room.getId()
        || room.getHabboItemByDatabaseId(chest.getId()) != chest
        || chest.getBaseItem().getStateCount() <= 0) {
      return;
    }
    withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          if (settings == null || settings.stateControlMode() != 3) {
            return null;
          }
          int current = 0;
          try {
            current = Integer.parseInt(chest.getExtradata());
          } catch (RuntimeException ignored) {
            // Existing item-toggle semantics recover malformed state at zero.
          }
          int next = Math.floorMod(current + 1, chest.getBaseItem().getStateCount());
          chest.setExtradata(Integer.toString(next));
          chest.needsUpdate(true);
          invalidate(chest.getId());
          refreshAppearance(room, chest);
          return null;
        });
  }

  /** Match-furni state changes share the same mode-3 gate and serialization as toggle-state. */
  public void setStateFromWired(Room room, HabboItem chest, String requestedState) {
    if (room == null
        || !isChest(chest)
        || chest.getRoomId() != room.getId()
        || room.getHabboItemByDatabaseId(chest.getId()) != chest
        || chest.getBaseItem().getStateCount() <= 0
        || requestedState == null) {
      return;
    }
    final int requested;
    try {
      requested = Integer.parseInt(requestedState.trim());
    } catch (RuntimeException ignored) {
      return;
    }
    withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          if (settings == null || settings.stateControlMode() != 3) {
            return null;
          }
          String next = Integer.toString(Math.floorMod(requested, chest.getBaseItem().getStateCount()));
          if (next.equals(chest.getExtradata())) {
            return null;
          }
          chest.setExtradata(next);
          chest.needsUpdate(true);
          invalidate(chest.getId());
          refreshAppearance(room, chest);
          return null;
        });
  }

  /** Starts July's inventory Wired Trading flow for a manual chest deposit. */
  public void startTrade(GameClient client, Room room, HabboItem chest) {
    if (client == null || room == null || chest == null || client.getHabbo() == null) {
      return;
    }
    ChestType type = ChestType.fromItem(chest);
    ChestSettings settings = settings(chest);
    if (type == null
        || !client.isActiveChest(room.getId(), chest.getId())
        || !allowed(ChestAuthorization.Operation.DONATE, client, room, chest, settings)) {
      client.sendResponse(
          new WiredTradeCancelledComposer(settings != null && settings.locked() ? 15 : 1));
      return;
    }
    if (room.getActiveTradeForHabbo(client.getHabbo()) != null) {
      client.sendResponse(new WiredTradeCancelledComposer(4));
      return;
    }
    if (client.getChestTradeSession() != null) {
      abortTrade(client, true, 3);
    }
    int userId = client.getHabbo().getHabboInfo().getId();
    ChestTradeSession session =
        new ChestTradeSession(
            userId,
            room.getId(),
            chest.getId(),
            chest.getRoomVisibleId(),
            type,
            System.currentTimeMillis(),
            TRADE_TIMEOUT_SECONDS);
    if (!client.beginChestTradeSession(session)) {
      client.sendResponse(new WiredTradeCancelledComposer(4));
      return;
    }
    scheduleTradeTimeout(client, session);
    client.sendResponse(new WiredTradeInitiateComposer(type, true, TRADE_TIMEOUT_SECONDS));
    client.sendResponse(new WiredTradeItemUpdateComposer(session, false, 0));
  }

  /**
   * Starts July action 47's contract flow. A null result means the session was accepted; otherwise
   * the returned official failure is safe to expose to action 26.
   */
  public ChestTransactionFailure startContractTransaction(
      Room room,
      Habbo user,
      List<HabboItem> requestedChests,
      ChestContractPlan contract,
      int timeoutSeconds) {
    GameClient client = user == null ? null : user.getClient();
    if (room == null || user == null || client == null || contract == null) {
      return ChestTransactionFailure.WIRED_MISCONFIGURATION;
    }
    if (!WiredCapabilityService.isRoomCapabilityReady(CONTRACT_CAPABILITIES)
        || !client.getWiredCapabilityState().supportsRoom(CONTRACT_CAPABILITIES, room.getId())) {
      return ChestTransactionFailure.FEATURE_DISABLED;
    }
    if (user.getHabboInfo().getCurrentRoom() != room
        || user.getRoomUnit() == null
        || !user.getRoomUnit().isInRoom()
        || room.getHabbo(user.getRoomUnit()) != user) {
      return ChestTransactionFailure.USER_CANNOT_TRADE;
    }
    HabboItem contractItem = room.getHabboItemByDatabaseId(contract.contractId());
    if (contractItem == null
        || contractItem.getRoomVisibleId() != contract.contractVisibleId()) {
      return ChestTransactionFailure.CHEST_NOT_IN_ROOM;
    }
    if (room.getActiveTradeForHabbo(user) != null || client.getChestTradeSession() != null) {
      return ChestTransactionFailure.ALREADY_TRADING;
    }

    LinkedHashMap<Integer, HabboItem> usable = new LinkedHashMap<>();
    if (requestedChests != null) {
      requestedChests.stream()
          .filter(
              chest ->
                  chest != null
                      && room.getHabboItemByDatabaseId(chest.getId()) == chest
                      && (isWiredUsable(chest, ChestType.COINS)
                          || isWiredUsable(chest, ChestType.FURNI)))
          .sorted(Comparator.comparingInt(HabboItem::getId))
          .forEach(chest -> usable.put(chest.getId(), chest));
    }
    if (usable.isEmpty()) {
      return ChestTransactionFailure.NO_AVAILABLE_CHESTS;
    }
    if (usable.size() > WiredManager.MAXIMUM_FURNI_SELECTION) {
      return ChestTransactionFailure.TOO_MANY_CHESTS;
    }

    int effectiveTimeout =
        timeoutSeconds == 0 ? 0 : Math.max(30, Math.min(3600, timeoutSeconds));
    if (contract.contractType()
        == com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.TYPE_REWARD) {
      return executeContractTransaction(
          room, user, List.copyOf(usable.values()), contract, List.of(), false);
    }
    ChestTradeSession session =
        ChestTradeSession.contract(
            user.getHabboInfo().getId(),
            room.getId(),
            List.copyOf(usable.values()),
            contract,
            System.currentTimeMillis(),
            effectiveTimeout);
    if (!client.beginChestTradeSession(session)) {
      return ChestTransactionFailure.ALREADY_TRADING;
    }
    scheduleTradeTimeout(client, session);
    client.sendResponse(
        new WiredTradeInitiateComposer(contract, true, false, effectiveTimeout));
    client.sendResponse(new WiredTradeItemUpdateComposer(session, false, 0));
    return null;
  }

  public boolean cancelContractTransaction(
      Habbo user, Room room, Set<Integer> contractIds, boolean anyContract) {
    GameClient client = user == null ? null : user.getClient();
    ChestTradeSession session = client == null ? null : client.getChestTradeSession();
    if (room == null
        || user.getHabboInfo().getCurrentRoom() != room
        || session == null
        || !session.isContract()
        || session.roomId() != room.getId()
        || (!anyContract
            && (contractIds == null
                || !contractIds.contains(session.contractPlan().contractId())))) {
      return false;
    }
    abortTrade(client, true, ChestTransactionFailure.TRADE_CANCELLED.code());
    return true;
  }

  public void updateTradeItems(GameClient client, boolean remove, List<Integer> itemIds) {
    ChestTradeSession session = validTradeSession(client, true);
    if (session == null
        || itemIds == null
        || itemIds.isEmpty()
        || session.phase() != ChestTradeSession.Phase.ADDING) {
      return;
    }
    boolean changed = false;
    Set<Integer> seen = new HashSet<>();
    if (remove) {
      for (Integer requestedId : itemIds) {
        if (requestedId == null || !seen.add(Math.abs(requestedId))) {
          continue;
        }
        HabboItem restored = session.remove(requestedId);
        if (restored != null) {
          restoreReservedItem(client, restored);
          changed = true;
        }
      }
    } else {
      for (Integer requestedId : itemIds) {
        if (requestedId == null || session.itemCount() >= MAX_TRADE_ITEMS) {
          break;
        }
        int itemId = Math.abs(requestedId);
        if (itemId <= 0 || !seen.add(itemId)) {
          continue;
        }
        HabboItem item = client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
        if (!isValidTradeItem(session, item) || !wouldFit(session, item)) {
          continue;
        }
        client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
        // A plugin may veto or replace an inventory removal.
        if (client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId) != null) {
          continue;
        }
        if (!session.add(item)) {
          restoreReservedItem(client, item);
          continue;
        }
        changed = true;
      }
    }
    if (changed) {
      session.resetAcceptance();
    }
    sendTradeUpdate(client, session);
  }

  public void acceptTrade(GameClient client, boolean confirm) {
    ChestTradeSession session = validTradeSession(client, true);
    if (session == null) {
      return;
    }
    long now = System.currentTimeMillis();
    ChestRepository.Result valid = validateTradeOffer(client, session);
    if (valid != ChestRepository.Result.OK) {
      if (valid == ChestRepository.Result.FULL) {
        client.sendResponse(new WiredTradeItemUpdateComposer(session, false, 0));
      } else {
        abortTrade(client, true, failureCode(valid));
      }
      return;
    }
    if (!confirm) {
      if (session.beginCountdown(now)) {
        client.sendResponse(new WiredTradeItemUpdateComposer(session, false, 0));
      }
      return;
    }
    synchronized (session) {
      if (!session.canConfirm(now, TRADE_CONFIRM_COUNTDOWN_MILLIS)) {
        abortTrade(client, true, session.expired(now) ? 2 : 18);
        return;
      }
      commitTrade(client, session);
    }
  }

  /**
   * Releases all in-memory item reservations. notifyClient is false for disconnect/room-leave
   * teardown where no UI packet should be emitted.
   */
  public void abortTrade(GameClient client, boolean notifyClient, int reason) {
    if (client == null) {
      return;
    }
    ChestTradeSession session = client.clearChestTradeSession();
    abortClearedTrade(client, session, notifyClient, reason);
  }

  private void scheduleTradeTimeout(GameClient client, ChestTradeSession session) {
    if (client == null || session == null || session.expiresAt() == Long.MAX_VALUE) {
      return;
    }
    long delay = Math.max(1L, session.expiresAt() - System.currentTimeMillis());
    com.eu.habbo.Emulator.getThreading().run(() -> expireTrade(client, session), delay);
  }

  private void expireTrade(GameClient client, ChestTradeSession expected) {
    synchronized (expected) {
      if (!expected.expired(System.currentTimeMillis())) {
        scheduleTradeTimeout(client, expected);
        return;
      }
      ChestTradeSession session = client.clearChestTradeSession(expected);
      abortClearedTrade(client, session, true, 2);
    }
  }

  private void abortClearedTrade(
      GameClient client, ChestTradeSession session, boolean notifyClient, int reason) {
    if (session == null) {
      return;
    }
    for (HabboItem item : session.items()) {
      restoreReservedItem(client, item);
    }
    Room room =
        client.getHabbo() == null ? null : client.getHabbo().getHabboInfo().getCurrentRoom();
    HabboItem contract =
        !session.isContract() || room == null || room.getId() != session.roomId()
            ? null
            : room.getHabboItemByDatabaseId(session.contractPlan().contractId());
    session.clear();
    if (notifyClient) {
      client.sendResponse(new WiredTradeCancelledComposer(reason));
    }
    if (contract != null && client.getHabbo().getRoomUnit() != null) {
      WiredManager.triggerTransactionFailed(
          room,
          client.getHabbo().getRoomUnit(),
          contract,
          ChestTransactionFailure.fromCode(reason));
    }
  }

  public boolean hasActiveTrade(GameClient client) {
    return client != null && client.getChestTradeSession() != null;
  }

  public void setRoomChestLocks(GameClient client, Room room, boolean locked, boolean allChests) {
    if (client == null
        || room == null
        || client.getHabbo() == null
        || (!locked && allChests)
        || (allChests && !room.isOwner(client.getHabbo()))) {
      return;
    }
    int actorId = client.getHabbo().getHabboInfo().getId();
    for (HabboItem chest : new ArrayList<>(room.getFloorItems())) {
      if (!isChest(chest) || (!allChests && chest.getUserId() != actorId)) {
        continue;
      }
      ChestSettings settings = settings(chest);
      if (settings != null) {
        saveSafety(client, room, chest, locked, settings.autoLock(), settings.capacity());
      }
    }
  }

  public ChestRepository.Result saveGeneral(
      GameClient client,
      Room room,
      HabboItem chest,
      String name,
      String description,
      boolean everyoneCanOpen,
      boolean everyoneCanDonate,
      int stateControlMode,
      int previewMode,
      int previewAmount,
      boolean enableWired) {
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          if (!allowed(ChestAuthorization.Operation.EDIT, client, room, chest, settings)) {
            return ChestRepository.Result.NOT_OWNER;
          }
          int actorId = client.getHabbo().getHabboInfo().getId();
          ChestRepository.Result result =
              this.repository.saveGeneral(
                  chest,
                  room.getId(),
                  actorId,
                  name,
                  description,
                  everyoneCanOpen,
                  everyoneCanDonate,
                  stateControlMode,
                  previewMode,
                  previewAmount,
                  enableWired);
          return afterMutation(room, chest, result);
        });
  }

  public ChestRepository.Result saveNotifications(
      GameClient client,
      Room room,
      HabboItem chest,
      int notifyMode,
      boolean full,
      boolean donation,
      boolean withdraw,
      boolean empty,
      boolean wiredTransaction) {
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          if (!allowed(ChestAuthorization.Operation.EDIT, client, room, chest, settings)) {
            return ChestRepository.Result.NOT_OWNER;
          }
          int actorId = client.getHabbo().getHabboInfo().getId();
          ChestRepository.Result result =
              this.repository.saveNotifications(
                  chest,
                  room.getId(),
                  actorId,
                  notifyMode,
                  full,
                  donation,
                  withdraw,
                  empty,
                  wiredTransaction);
          return afterMutation(room, chest, result);
        });
  }

  public ChestRepository.Result saveSafety(
      GameClient client,
      Room room,
      HabboItem chest,
      boolean locked,
      boolean autoLock,
      int capacity) {
    ChestRepository.Result applied =
        withChestLock(
            chest.getId(),
            () -> {
              ChestSettings settings = settings(chest);
              ChestAuthorization.Operation operation =
                  locked ? ChestAuthorization.Operation.LOCK : ChestAuthorization.Operation.UNLOCK;
              if (!allowed(operation, client, room, chest, settings)) {
                return ChestRepository.Result.NOT_OWNER;
              }
              int ownerId = chest.getUserId();
              ChestRepository.Result result =
                  this.repository.saveSafety(
                      chest, room.getId(), ownerId, locked, autoLock, capacity);
              return afterMutation(room, chest, result);
            });
    if (applied == ChestRepository.Result.OK && locked) {
      abortTradesUsingChest(room, chest, 15);
    }
    return applied;
  }

  /**
   * Applies July's owner-leave safety policy to every chest owned by the departing user. Repository
   * locking makes repeated leave/disconnect paths safe across room threads.
   */
  public void autoLockChestsForOwner(Room room, Habbo owner) {
    if (room == null || owner == null) {
      return;
    }
    int ownerId = owner.getHabboInfo().getId();
    List<HabboItem> roomItems = new ArrayList<>();
    roomItems.addAll(room.getFloorItems());
    roomItems.addAll(room.getWallItems());
    for (HabboItem chest : roomItems) {
      if (!isChest(chest) || chest.getUserId() != ownerId) {
        continue;
      }
      ChestSettings current = settings(chest);
      if (current == null || current.locked() || !current.autoLock()) {
        continue;
      }
      ChestRepository.Result result =
          withChestLock(
              chest.getId(),
              () -> {
                ChestSettings lockedCurrent = settings(chest);
                if (lockedCurrent == null
                    || lockedCurrent.locked()
                    || !lockedCurrent.autoLock()) {
                  return ChestRepository.Result.CANCELLED;
                }
                ChestRepository.Result lockedResult =
                    this.repository.autoLock(chest, room.getId(), ownerId);
                return afterMutation(room, chest, lockedResult);
              });
      if (result == ChestRepository.Result.OK) {
        abortTradesUsingChest(room, chest, 15);
      }
    }
  }

  /**
   * Atomically deletes an empty chest. Non-empty chests are retained to prevent their stored
   * furniture or currency from being orphaned.
   */
  public ChestRepository.Result deleteChest(HabboItem chest) {
    if (!isChest(chest)) {
      return ChestRepository.Result.WRONG_TYPE;
    }
    Room room =
        chest.getRoomId() > 0
            ? com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(chest.getRoomId())
            : null;
    return withChestLock(
        chest.getId(),
        () -> {
          if (room != null) {
            abortTradesUsingChest(room, chest, 13);
          }
          ChestRepository.Result result = this.repository.deleteChest(chest);
          if (result == ChestRepository.Result.OK) {
            invalidate(chest.getId());
          }
          return result;
        });
  }

  public ChestRepository.Result upgrade(
      GameClient client, Room room, HabboItem chest, int levels, int creditCost, int diamondCost) {
    ChestSettings settings = settings(chest);
    if (!allowed(ChestAuthorization.Operation.EDIT, client, room, chest, settings)) {
      return ChestRepository.Result.NOT_OWNER;
    }
    if (levels <= 0) {
      return ChestRepository.Result.INVALID_AMOUNT;
    }
    long requestedCredits = (long) Math.max(0, creditCost) * levels;
    long requestedDiamonds = (long) Math.max(0, diamondCost) * levels;
    if (requestedCredits > Integer.MAX_VALUE || requestedDiamonds > Integer.MAX_VALUE) {
      return ChestRepository.Result.INVALID_AMOUNT;
    }
    Habbo actor = client.getHabbo();
    Integer approvedCredits =
        requestedCredits == 0
            ? 0
            : approveCreditTransfer(actor, -(int) requestedCredits, false);
    ApprovedPoints approvedDiamonds =
        requestedDiamonds == 0
            ? new ApprovedPoints(0, 5)
            : approvePointsDebit(actor, (int) requestedDiamonds, 5);
    if (approvedCredits == null || approvedDiamonds == null) {
      return ChestRepository.Result.CANCELLED;
    }
    ChestRepository.UpgradeTransfer transfer =
        withChestLock(
            chest.getId(),
            () -> {
              ChestSettings current = settings(chest);
              if (!allowed(ChestAuthorization.Operation.EDIT, client, room, chest, current)) {
                return ChestRepository.UpgradeTransfer.failure(ChestRepository.Result.NOT_OWNER);
              }
              ChestRepository.UpgradeTransfer lockedTransfer;
              synchronized (actor.getHabboInfo()) {
                lockedTransfer =
                    this.repository.upgradeCapacity(
                        chest,
                        room.getId(),
                        actor.getHabboInfo().getId(),
                        levels,
                        approvedCredits,
                        approvedDiamonds.amount(),
                        approvedDiamonds.type());
                if (lockedTransfer.result() == ChestRepository.Result.OK) {
                  if (approvedCredits > 0) {
                    actor
                        .getHabboInfo()
                        .applyCommittedCreditBalance(lockedTransfer.creditBalance());
                  }
                  if (approvedDiamonds.amount() > 0) {
                    actor
                        .getHabboInfo()
                        .applyCommittedCurrencyBalance(
                            lockedTransfer.currencyType(), lockedTransfer.currencyBalance());
                  }
                }
              }
              if (lockedTransfer.result() == ChestRepository.Result.OK) {
                afterMutation(room, chest, ChestRepository.Result.OK);
              }
              return lockedTransfer;
            });
    if (transfer.result() == ChestRepository.Result.OK) {
      if (approvedCredits > 0) {
        client.sendResponse(new CreditBalanceMessageComposer(actor));
      }
      if (approvedDiamonds.amount() > 0) {
        client.sendResponse(new ActivityPointsMessageComposer(actor));
      }
    }
    return transfer.result();
  }

  public ChestRepository.ItemTransfer depositItem(
      GameClient client, Room room, HabboItem chest, HabboItem inventoryItem) {
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          int before = contentsCount(chest);
          if (inventoryItem == null
              || !inventoryItem.getBaseItem().allowTrade()
              || !allowed(ChestAuthorization.Operation.DONATE, client, room, chest, settings)) {
            return ChestRepository.ItemTransfer.failure(ChestRepository.Result.ITEM_NOT_OWNED);
          }
          int actorId = client.getHabbo().getHabboInfo().getId();
          ChestRepository.ItemTransfer result =
              this.repository.depositItem(
                  chest,
                  room.getId(),
                  actorId,
                  ChestTransactionLog.Audit.manual(
                      client.getHabbo().getHabboInfo().getUsername()),
                  inventoryItem.getId());
          if (result.result() == ChestRepository.Result.OK) {
            inventoryItem.setUserId(0);
            inventoryItem.setRoomId(0);
            client.getHabbo().removeFurniture(inventoryItem);
            afterMutation(room, chest, ChestRepository.Result.OK);
            broadcastToViewers(
                room,
                chest,
                new ChestFurniContentsUpdateComposer(
                    chest.getRoomVisibleId(),
                    new int[0],
                    List.of(new ChestRepository.StoredHabboItem(inventoryItem, 0, 0))));
            notifyMutation(
                settings,
                room,
                chest,
                client.getHabbo(),
                before,
                contentsCount(chest),
                true,
                false,
                false);
          }
          return result;
        });
  }

  public ChestRepository.ItemTransfer withdrawItem(
      GameClient client, Room room, HabboItem chest, int itemId) {
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          int before = contentsCount(chest);
          if (!allowed(ChestAuthorization.Operation.WITHDRAW, client, room, chest, settings)) {
            return ChestRepository.ItemTransfer.failure(ChestRepository.Result.NOT_OWNER);
          }
          int actorId = client.getHabbo().getHabboInfo().getId();
          ChestRepository.ItemTransfer result =
              this.repository.withdrawItem(
                  chest,
                  room.getId(),
                  actorId,
                  ChestTransactionLog.Audit.manual(
                      client.getHabbo().getHabboInfo().getUsername()),
                  itemId);
          if (result.result() == ChestRepository.Result.OK) {
            HabboItem item =
                com.eu.habbo.Emulator.getGameEnvironment().getItemManager().loadHabboItem(itemId);
            if (item != null) {
              client.getHabbo().addFurniture(item);
            }
            afterMutation(room, chest, ChestRepository.Result.OK);
            notifyMutation(
                settings,
                room,
                chest,
                client.getHabbo(),
                before,
                contentsCount(chest),
                false,
                true,
                false);
          }
          return result;
        });
  }

  public ChestRepository.ItemBatchTransfer withdrawByType(
      GameClient client,
      Room room,
      HabboItem chest,
      boolean wall,
      int typeId,
      String legacyPosterId,
      int amount) {
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          if (amount <= 0
              || amount > 1000
              || !allowed(
                  ChestAuthorization.Operation.WITHDRAW, client, room, chest, settings)) {
            return ChestRepository.ItemBatchTransfer.failure(
                ChestRepository.Result.INVALID_AMOUNT);
          }
          String poster = legacyPosterId == null ? "" : legacyPosterId;
          List<ChestRepository.StoredHabboItem> stored =
              this.repository.loadStoredHabboItems(chest.getId());
          List<ChestRepository.StoredHabboItem> selected =
              stored.stream()
                  .filter(
                      entry -> {
                        HabboItem item = entry.item();
                        boolean itemWall =
                            item.getBaseItem().getType()
                                == com.eu.habbo.habbohotel.items.FurnitureType.WALL;
                        if (itemWall != wall || item.getBaseItem().getSpriteId() != typeId) {
                          return false;
                        }
                        return poster.isEmpty() || poster.equals(item.getExtradata());
                      })
                  .sorted(Comparator.comparingInt(entry -> entry.item().getId()))
                  .limit(amount)
                  .toList();
          if (selected.isEmpty()) {
            return ChestRepository.ItemBatchTransfer.failure(ChestRepository.Result.EMPTY);
          }
          return completeBatchWithdrawal(client, room, chest, selected);
        });
  }

  public ChestRepository.ItemBatchTransfer withdrawAll(
      GameClient client, Room room, HabboItem chest) {
    return withChestLock(
        chest.getId(),
        () -> {
          ChestSettings settings = settings(chest);
          if (!allowed(ChestAuthorization.Operation.WITHDRAW, client, room, chest, settings)) {
            return ChestRepository.ItemBatchTransfer.failure(ChestRepository.Result.NOT_OWNER);
          }
          List<ChestRepository.StoredHabboItem> selected =
              this.repository.loadStoredHabboItems(chest.getId()).stream()
                  .filter(entry -> entry.lockState() == 0 && entry.transactionId() == 0)
                  .sorted(Comparator.comparingInt(entry -> entry.item().getId()))
                  .toList();
          if (selected.isEmpty()) {
            return ChestRepository.ItemBatchTransfer.failure(ChestRepository.Result.EMPTY);
          }
          return completeBatchWithdrawal(client, room, chest, selected);
        });
  }

  public ChestRepository.CoinTransfer depositCoins(
      GameClient client, Room room, HabboItem chest, int amount) {
    ChestSettings settings = settings(chest);
    if (!allowed(ChestAuthorization.Operation.DONATE, client, room, chest, settings)) {
      return ChestRepository.CoinTransfer.failure(ChestRepository.Result.NOT_OWNER);
    }
    Habbo actor = client.getHabbo();
    Integer approvedAmount = approveCreditTransfer(actor, -amount, false);
    if (approvedAmount == null) {
      return ChestRepository.CoinTransfer.failure(ChestRepository.Result.CANCELLED);
    }
    ChestRepository.CoinTransfer result =
        withChestLock(
            chest.getId(),
            () -> {
              ChestSettings current = settings(chest);
              if (!allowed(
                  ChestAuthorization.Operation.DONATE, client, room, chest, current)) {
                return ChestRepository.CoinTransfer.failure(ChestRepository.Result.NOT_OWNER);
              }
              ChestRepository.CoinTransfer lockedResult;
              synchronized (actor.getHabboInfo()) {
                lockedResult =
                    this.repository.depositCoins(
                        chest,
                        room.getId(),
                        actor.getHabboInfo().getId(),
                        ChestTransactionLog.Audit.manual(
                            actor.getHabboInfo().getUsername()),
                        approvedAmount);
                if (lockedResult.result() == ChestRepository.Result.OK) {
                  actor
                      .getHabboInfo()
                      .applyCommittedCreditBalance(lockedResult.userBalance());
                }
              }
              if (lockedResult.result() == ChestRepository.Result.OK) {
                afterMutation(room, chest, ChestRepository.Result.OK);
                broadcastToViewers(
                    room,
                    chest,
                    new ChestCoinBalanceComposer(
                        chest.getRoomVisibleId(), lockedResult.chestBalance(), true));
              }
              return lockedResult;
            });
    if (result.result() == ChestRepository.Result.OK) {
      client.sendResponse(new CreditBalanceMessageComposer(actor));
      notifyMutation(
          settings,
          room,
          chest,
          actor,
          result.chestBalance() - approvedAmount,
          result.chestBalance(),
          true,
          false,
          false);
    }
    return result;
  }

  public ChestRepository.CoinTransfer withdrawCoins(
      GameClient client, Room room, HabboItem chest, int amount) {
    ChestSettings settings = settings(chest);
    if (!allowed(ChestAuthorization.Operation.WITHDRAW, client, room, chest, settings)) {
      return ChestRepository.CoinTransfer.failure(ChestRepository.Result.NOT_OWNER);
    }
    Habbo actor = client.getHabbo();
    Integer approvedAmount = approveCreditTransfer(actor, amount, true);
    if (approvedAmount == null) {
      return ChestRepository.CoinTransfer.failure(ChestRepository.Result.CANCELLED);
    }
    ChestRepository.CoinTransfer result =
        withChestLock(
            chest.getId(),
            () -> {
              ChestSettings current = settings(chest);
              if (!allowed(
                  ChestAuthorization.Operation.WITHDRAW, client, room, chest, current)) {
                return ChestRepository.CoinTransfer.failure(ChestRepository.Result.NOT_OWNER);
              }
              ChestRepository.CoinTransfer lockedResult;
              synchronized (actor.getHabboInfo()) {
                lockedResult =
                    this.repository.withdrawCoins(
                        chest,
                        room.getId(),
                        actor.getHabboInfo().getId(),
                        ChestTransactionLog.Audit.manual(
                            actor.getHabboInfo().getUsername()),
                        approvedAmount);
                if (lockedResult.result() == ChestRepository.Result.OK) {
                  actor
                      .getHabboInfo()
                      .applyCommittedCreditBalance(lockedResult.userBalance());
                }
              }
              if (lockedResult.result() == ChestRepository.Result.OK) {
                afterMutation(room, chest, ChestRepository.Result.OK);
                broadcastToViewers(
                    room,
                    chest,
                    new ChestCoinBalanceComposer(
                        chest.getRoomVisibleId(), lockedResult.chestBalance(), true));
              }
              return lockedResult;
            });
    if (result.result() == ChestRepository.Result.OK) {
      client.sendResponse(new CreditBalanceMessageComposer(actor));
      notifyMutation(
          settings,
          room,
          chest,
          actor,
          result.chestBalance() + approvedAmount,
          result.chestBalance(),
          false,
          true,
          false);
    }
    return result;
  }

  /** True only while July's chest-Wired safety switch is enabled and the chest is unlocked. */
  public boolean isWiredUsable(HabboItem chest, ChestType expectedType) {
    if (chest == null || ChestType.fromItem(chest) != expectedType) {
      return false;
    }
    ChestSettings settings = settings(chest);
    return settings != null && settings.wiredEnabled() && !settings.locked();
  }

  /** Snapshot of unreserved furniture, in deterministic FIFO deposit order. */
  public List<HabboItem> wiredStoredFurniture(HabboItem chest) {
    if (!isWiredUsable(chest, ChestType.FURNI)) {
      return List.of();
    }
    return this.repository.loadStoredHabboItems(chest.getId()).stream()
        .filter(entry -> entry.lockState() == 0 && entry.transactionId() == 0)
        .map(ChestRepository.StoredHabboItem::item)
        .toList();
  }

  /**
   * Seth's runtime behavior confirms July failure 16 is a preflight for multi-user rewards. The
   * actual transfer remains authoritative and repeats these checks under database row locks.
   */
  public boolean canFulfillContractRewards(
      ChestContractPlan contract, List<HabboItem> chests, int recipientCount) {
    if (contract == null || chests == null || recipientCount < 1) {
      return false;
    }
    List<HabboItem> furniture = new ArrayList<>();
    long coins = 0;
    for (HabboItem chest : chests) {
      ChestType type = ChestType.fromItem(chest);
      if (type == ChestType.FURNI && isWiredUsable(chest, type)) {
        furniture.addAll(wiredStoredFurniture(chest));
      } else if (type == ChestType.COINS && isWiredUsable(chest, type)) {
        coins += Math.max(0, this.repository.loadCoinBalance(chest.getId()));
      }
    }
    return ChestContractEvaluator.canFulfillRewards(
        contract, furniture, coins, recipientCount);
  }

  public int wiredFurnitureCount(HabboItem chest, List<HabboItem> typeItems) {
    return (int)
        wiredStoredFurniture(chest).stream()
            .filter(item -> matchesAnyType(item, typeItems))
            .count();
  }

  /** July chest appearance preview, also consumed by scanner mode 1. */
  public List<HabboItem> wiredPreviewFurniture(HabboItem chest) {
    if (!isWiredUsable(chest, ChestType.FURNI)) {
      return List.of();
    }
    return withChestLock(
        chest.getId(), () -> previewFurniture(chest, wiredStoredFurniture(chest)));
  }

  private List<HabboItem> appearancePreviewFurniture(HabboItem chest) {
    if (chest == null || ChestType.fromItem(chest) != ChestType.FURNI) {
      return List.of();
    }
    List<HabboItem> stored =
        this.repository.loadStoredHabboItems(chest.getId()).stream()
            .map(ChestRepository.StoredHabboItem::item)
            .toList();
    return previewFurniture(chest, stored);
  }

  private List<HabboItem> previewFurniture(HabboItem chest, List<HabboItem> candidates) {
    List<HabboItem> stored = new ArrayList<>(candidates);
    ChestSettings settings = settings(chest);
    if (settings == null || settings.previewMode() == 0 || stored.isEmpty()) {
      return List.of();
    }
    int mode = settings.previewMode();
    if (mode == 1 || mode == 2 || mode == 7) {
      Collections.shuffle(stored, new Random((((long) chest.getId()) << 32) ^ settings.revision()));
    } else if (mode == 3 || mode == 4) {
      Collections.reverse(stored);
    }
    int amount = Math.min(settings.previewAmount(), stored.size());
    if (mode == 2 || mode == 4 || mode == 6 || mode == 7) {
      return pickDifferentPreviewTypes(stored, amount);
    }
    return List.copyOf(stored.subList(0, amount));
  }

  private static List<HabboItem> pickDifferentPreviewTypes(
      List<HabboItem> stored, int amount) {
    LinkedHashMap<String, List<HabboItem>> byType = new LinkedHashMap<>();
    for (HabboItem item : stored) {
      byType.computeIfAbsent(appearanceTypeKey(item), ignored -> new ArrayList<>()).add(item);
    }
    List<HabboItem> selected = new ArrayList<>();
    int round = 0;
    while (selected.size() < amount) {
      boolean added = false;
      for (List<HabboItem> typeItems : byType.values()) {
        if (round < typeItems.size()) {
          selected.add(typeItems.get(round));
          added = true;
          if (selected.size() == amount) {
            return List.copyOf(selected);
          }
        }
      }
      if (!added) {
        break;
      }
      round++;
    }
    return List.copyOf(selected);
  }

  /**
   * Atomically moves stored furniture into a room user's inventory. Iteration codes are July's 0
   * random, 1 FIFO and 2 LIFO.
   */
  public List<HabboItem> giveFurnitureFromWired(
      Room room,
      HabboItem chest,
      Habbo receiver,
      int requestedAmount,
      List<HabboItem> typeItems,
      int iterationMode,
      int effectId) {
    if (room == null
        || receiver == null
        || requestedAmount <= 0
        || !isWiredUsable(chest, ChestType.FURNI)) {
      return List.of();
    }
    return withChestLock(
        chest.getId(),
        () -> {
          if (!isWiredUsable(chest, ChestType.FURNI)) {
            return List.of();
          }
          ChestSettings settings = settings(chest);
          List<HabboItem> candidates =
              new ArrayList<>(
                  wiredStoredFurniture(chest).stream()
                      .filter(item -> matchesAnyType(item, typeItems))
                      .toList());
          if (iterationMode == 0) {
            Collections.shuffle(candidates);
          } else if (iterationMode == 2) {
            Collections.reverse(candidates);
          }
          if (candidates.isEmpty()) {
            return List.of();
          }
          List<HabboItem> selected =
              new ArrayList<>(
                  candidates.subList(0, Math.min(requestedAmount, candidates.size())));
          int before = contentsCount(chest);
          List<Integer> ids = selected.stream().map(HabboItem::getId).toList();
          int receiverId = receiver.getHabboInfo().getId();
          ChestRepository.ItemBatchTransfer result =
              this.repository.withdrawItems(
                  chest,
                  room.getId(),
                  receiverId,
                  ChestTransactionLog.Audit.wired(
                      receiver.getHabboInfo().getUsername(), effectId, requestedAmount),
                  ids);
          if (result.result() != ChestRepository.Result.OK) {
            return List.of();
          }
          Map<Integer, HabboItem> byId = new LinkedHashMap<>();
          for (HabboItem item : selected) {
            byId.put(item.getId(), item);
          }
          List<HabboItem> delivered = new ArrayList<>();
          for (int id : result.itemIds()) {
            HabboItem item = byId.get(id);
            if (item != null) {
              item.setUserId(receiverId);
              item.setRoomId(0);
              receiver.addFurniture(item);
              delivered.add(item);
            }
          }
          afterMutation(room, chest, ChestRepository.Result.OK);
          int[] removed = result.itemIds().stream().mapToInt(Integer::intValue).toArray();
          broadcastToViewers(
              room,
              chest,
              new ChestFurniContentsUpdateComposer(
                  chest.getRoomVisibleId(), removed, List.of()));
          notifyMutation(
              settings,
              room,
              chest,
              receiver,
              before,
              contentsCount(chest),
              false,
              true,
              true);
          return List.copyOf(delivered);
        });
  }

  /** Atomically transfers credits from a Wired-enabled coin chest to a room user. */
  public int giveCoinsFromWired(
      Room room, HabboItem chest, Habbo receiver, int requestedAmount, int effectId) {
    if (room == null
        || receiver == null
        || requestedAmount <= 0
        || !isWiredUsable(chest, ChestType.COINS)) {
      return 0;
    }
    int before = contentsCount(chest);
    int amount = Math.min(before, requestedAmount);
    if (amount <= 0) {
      return 0;
    }
    Integer approvedAmount = approveCreditTransfer(receiver, amount, true);
    if (approvedAmount == null) {
      return 0;
    }
    ChestSettings settings = settings(chest);
    ChestRepository.CoinTransfer result =
        withChestLock(
            chest.getId(),
            () -> {
              if (!isWiredUsable(chest, ChestType.COINS)) {
                return ChestRepository.CoinTransfer.failure(ChestRepository.Result.LOCKED);
              }
              ChestRepository.CoinTransfer lockedResult;
              synchronized (receiver.getHabboInfo()) {
                lockedResult =
                    this.repository.withdrawCoins(
                        chest,
                        room.getId(),
                        receiver.getHabboInfo().getId(),
                        ChestTransactionLog.Audit.wired(
                            receiver.getHabboInfo().getUsername(), effectId, requestedAmount),
                        approvedAmount);
                if (lockedResult.result() == ChestRepository.Result.OK) {
                  receiver
                      .getHabboInfo()
                      .applyCommittedCreditBalance(lockedResult.userBalance());
                }
              }
              if (lockedResult.result() == ChestRepository.Result.OK) {
                afterMutation(room, chest, ChestRepository.Result.OK);
                broadcastToViewers(
                    room,
                    chest,
                    new ChestCoinBalanceComposer(
                        chest.getRoomVisibleId(), lockedResult.chestBalance(), true));
              }
              return lockedResult;
            });
    if (result.result() != ChestRepository.Result.OK) {
      return 0;
    }
    if (receiver.getClient() != null) {
      receiver.getClient().sendResponse(new CreditBalanceMessageComposer(receiver));
    }
    notifyMutation(
        settings, room, chest, receiver, before, result.chestBalance(), false, true, true);
    return approvedAmount;
  }

  /**
   * Runs the canonical plugin hook before a chest transaction mutates money. The returned value is
   * the positive magnitude approved by plugins.
   */
  private static Integer approveCreditTransfer(Habbo habbo, int signedDelta, boolean credit) {
    if (habbo == null || signedDelta == 0) {
      return null;
    }
    UserCreditsEvent event = new UserCreditsEvent(habbo, signedDelta);
    if (com.eu.habbo.Emulator.getPluginManager().fireEvent(event).isCancelled()) {
      return null;
    }
    if ((credit && event.credits <= 0)
        || (!credit && event.credits >= 0)
        || event.credits == Integer.MIN_VALUE) {
      return null;
    }
    return credit ? event.credits : -event.credits;
  }

  private static ApprovedPoints approvePointsDebit(Habbo habbo, int amount, int type) {
    if (habbo == null || amount <= 0 || type < 0) {
      return null;
    }
    UserPointsEvent event = new UserPointsEvent(habbo, -amount, type);
    if (com.eu.habbo.Emulator.getPluginManager().fireEvent(event).isCancelled()
        || event.points >= 0
        || event.points == Integer.MIN_VALUE
        || event.type < 0) {
      return null;
    }
    return new ApprovedPoints(-event.points, event.type);
  }

  private record ApprovedPoints(int amount, int type) {}

  private static boolean matchesAnyType(HabboItem item, List<HabboItem> typeItems) {
    if (item == null) {
      return false;
    }
    if (typeItems == null || typeItems.isEmpty()) {
      return true;
    }
    for (HabboItem type : typeItems) {
      if (type == null || type.getBaseItem() == null || item.getBaseItem() == null) {
        continue;
      }
      if (type.getBaseItem().getType() == item.getBaseItem().getType()
          && type.getBaseItem().getSpriteId() == item.getBaseItem().getSpriteId()
          && (type.getExtradata() == null
              || type.getExtradata().isEmpty()
              || type.getExtradata().equals(item.getExtradata()))) {
        return true;
      }
    }
    return false;
  }

  private ChestTradeSession validTradeSession(GameClient client, boolean notifyOnAbort) {
    if (client == null || client.getHabbo() == null) {
      return null;
    }
    ChestTradeSession session = client.getChestTradeSession();
    if (session == null) {
      return null;
    }
    long now = System.currentTimeMillis();
    if (session.expired(now)) {
      abortTrade(client, notifyOnAbort, 2);
      return null;
    }
    Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
    if (session.isContract()) {
      HabboItem contract =
          room == null
              ? null
              : room.getHabboItemByDatabaseId(session.contractPlan().contractId());
      boolean validChests =
          room != null
              && session.chestIds().stream()
                  .map(room::getHabboItemByDatabaseId)
                  .allMatch(
                      chest ->
                          chest != null
                              && (isWiredUsable(chest, ChestType.COINS)
                                  || isWiredUsable(chest, ChestType.FURNI)));
      if (room == null
          || room.getId() != session.roomId()
          || contract == null
          || contract.getRoomVisibleId() != session.contractPlan().contractVisibleId()
          || !validChests
          || !client
              .getWiredCapabilityState()
              .supportsRoom(CONTRACT_CAPABILITIES, room.getId())) {
        abortTrade(client, notifyOnAbort, ChestTransactionFailure.CHEST_NOT_IN_ROOM.code());
        return null;
      }
      return session;
    }
    HabboItem chest = room == null ? null : room.getHabboItemByDatabaseId(session.chestId());
    if (room == null
        || room.getId() != session.roomId()
        || chest == null
        || ChestType.fromItem(chest) != session.chestType()
        || !client.isActiveChest(room.getId(), chest.getId())
        || !client
            .getWiredCapabilityState()
            .supportsRoom(WiredCapabilityService.CAPABILITY_CHESTS, room.getId())) {
      abortTrade(client, notifyOnAbort, 13);
      return null;
    }
    return session;
  }

  private void sendTradeUpdate(GameClient client, ChestTradeSession session) {
    ChestRepository.Result valid = validateTradeOffer(client, session);
    client.sendResponse(
        new WiredTradeItemUpdateComposer(session, valid == ChestRepository.Result.OK, 0));
  }

  private ChestRepository.Result validateTradeOffer(GameClient client, ChestTradeSession session) {
    if (client == null || client.getHabbo() == null || session == null || session.isEmpty()) {
      return ChestRepository.Result.EMPTY;
    }
    if (session.isContract()) {
      return ChestContractEvaluator.evaluate(session.contractPlan(), session.items()).valid()
          ? ChestRepository.Result.OK
          : ChestRepository.Result.WRONG_TYPE;
    }
    Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
    HabboItem chest = room == null ? null : room.getHabboItemByDatabaseId(session.chestId());
    if (room == null
        || room.getId() != session.roomId()
        || chest == null
        || ChestType.fromItem(chest) != session.chestType()) {
      return ChestRepository.Result.STALE_ROOM;
    }
    ChestSettings current = settings(chest);
    if (!allowed(ChestAuthorization.Operation.DONATE, client, room, chest, current)) {
      return current != null && current.locked()
          ? ChestRepository.Result.LOCKED
          : ChestRepository.Result.NOT_OWNER;
    }
    long amount = 0;
    for (HabboItem item : session.items()) {
      if (!isValidTradeItem(session, item)) {
        return ChestRepository.Result.WRONG_TYPE;
      }
      amount += session.chestType() == ChestType.COINS ? creditValue(item) : 1;
      if (amount > Integer.MAX_VALUE) {
        return ChestRepository.Result.INVALID_AMOUNT;
      }
    }
    long stored =
        session.chestType() == ChestType.COINS
            ? this.repository.loadCoinBalance(chest.getId())
            : this.repository.loadStoredItems(chest.getId()).size();
    return stored + amount <= current.capacity()
        ? ChestRepository.Result.OK
        : ChestRepository.Result.FULL;
  }

  private boolean wouldFit(ChestTradeSession session, HabboItem candidate) {
    if (session == null || candidate == null) {
      return false;
    }
    if (session.isContract()) {
      return session.itemCount() < MAX_TRADE_ITEMS
          && ChestContractEvaluator.itemPermitted(session.contractPlan(), candidate);
    }
    Room room =
        candidate.getUserId() == session.userId()
            ? com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(session.roomId())
            : null;
    HabboItem chest = room == null ? null : room.getHabboItemByDatabaseId(session.chestId());
    ChestSettings current = chest == null ? null : settings(chest);
    if (current == null) {
      return false;
    }
    long selected = 0;
    for (HabboItem item : session.items()) {
      selected += session.chestType() == ChestType.COINS ? creditValue(item) : 1;
    }
    selected += session.chestType() == ChestType.COINS ? creditValue(candidate) : 1;
    long stored =
        session.chestType() == ChestType.COINS
            ? this.repository.loadCoinBalance(chest.getId())
            : this.repository.loadStoredItems(chest.getId()).size();
    return stored + selected <= current.capacity();
  }

  private static boolean isValidTradeItem(ChestTradeSession session, HabboItem item) {
    if (session == null
        || item == null
        || item.getBaseItem() == null
        || item.getId() == session.chestId()
        || item.getUserId() != session.userId()
        || item.getRoomId() != 0
        || !item.getBaseItem().allowTrade()) {
      return false;
    }
    if (session.isContract()) {
      return ChestContractEvaluator.itemPermitted(session.contractPlan(), item);
    }
    int credits = creditValue(item);
    return session.chestType() == ChestType.COINS ? credits > 0 : credits == 0;
  }

  public static int creditValue(HabboItem item) {
    if (item == null || item.getBaseItem() == null) {
      return 0;
    }
    String name = item.getBaseItem().getName();
    String normalized = name == null ? "" : name.toUpperCase(Locale.ROOT);
    if (normalized.contains("_DIAMOND_")
        || (!normalized.startsWith("CF_") && !normalized.startsWith("CFC_"))) {
      return 0;
    }
    try {
      int value = Integer.parseInt(normalized.split("_", 3)[1]);
      return Math.max(0, value);
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private void commitTrade(GameClient client, ChestTradeSession session) {
    if (session.isContract()) {
      commitContractTrade(client, session);
      return;
    }
    Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
    HabboItem chest = room == null ? null : room.getHabboItemByDatabaseId(session.chestId());
    if (room == null || chest == null) {
      abortTrade(client, true, 13);
      return;
    }
    ReentrantLock chestLock = chestLock(chest.getId());
    chestLock.lock();
    try {
    ChestSettings chestSettings = settings(chest);
    int before = contentsCount(chest);
    ChestType chestType = session.chestType();
    List<HabboItem> items = session.items();
    ChestRepository.Result result;
    int coinBalance = 0;
    if (chestType == ChestType.COINS) {
      long total = items.stream().mapToLong(ChestManager::creditValue).sum();
      if (total <= 0 || total > Integer.MAX_VALUE) {
        abortTrade(client, true, 1);
        return;
      }
      ChestRepository.CreditItemBatchTransfer transfer =
          this.repository.depositCreditItems(
              chest,
              room.getId(),
              session.userId(),
              ChestTransactionLog.Audit.manual(
                  client.getHabbo().getHabboInfo().getUsername()),
              session.itemIds());
      result = transfer.result();
      coinBalance = transfer.chestBalance();
    } else {
      result =
          this.repository
              .depositItems(
                  chest,
                  room.getId(),
                  session.userId(),
                  ChestTransactionLog.Audit.manual(
                      client.getHabbo().getHabboInfo().getUsername()),
                  session.itemIds())
              .result();
    }
    if (result != ChestRepository.Result.OK) {
      abortTrade(client, true, failureCode(result));
      return;
    }
    client.clearChestTradeSession();
    for (HabboItem item : items) {
      item.setUserId(0);
      item.setRoomId(0);
      client.sendResponse(new FurniListRemoveMessageComposer(item.getId()));
    }
    session.clear();
    afterMutation(room, chest, ChestRepository.Result.OK);
    if (chestType == ChestType.COINS) {
      broadcastToViewers(
          room, chest, new ChestCoinBalanceComposer(chest.getRoomVisibleId(), coinBalance, true));
    } else {
      List<ChestRepository.StoredHabboItem> added =
          items.stream().map(item -> new ChestRepository.StoredHabboItem(item, 0, 0)).toList();
      broadcastToViewers(
          room,
          chest,
          new ChestFurniContentsUpdateComposer(chest.getRoomVisibleId(), new int[0], added));
    }
    client.sendResponse(new WiredTradeCompletedComposer());
    notifyMutation(
        chestSettings,
        room,
        chest,
        client.getHabbo(),
        before,
        contentsCount(chest),
        true,
        false,
        false);
    } finally {
      chestLock.unlock();
    }
  }

  private void commitContractTrade(GameClient client, ChestTradeSession session) {
    Room room =
        client == null || client.getHabbo() == null
            ? null
            : client.getHabbo().getHabboInfo().getCurrentRoom();
    if (room == null || room.getId() != session.roomId()) {
      abortTrade(client, true, ChestTransactionFailure.CHEST_NOT_IN_ROOM.code());
      return;
    }
    List<HabboItem> chests =
        session.chestIds().stream()
            .map(room::getHabboItemByDatabaseId)
            .filter(java.util.Objects::nonNull)
            .toList();
    ChestTransactionFailure failure =
        executeContractTransaction(
            room,
            client.getHabbo(),
            chests,
            session.contractPlan(),
            session.items(),
            true);
    if (failure != null) {
      abortTrade(client, true, failure.code());
    }
  }

  private ChestTransactionFailure executeContractTransaction(
      Room room,
      Habbo user,
      List<HabboItem> chests,
      ChestContractPlan contract,
      List<HabboItem> offeredItems,
      boolean completeTradeUi) {
    GameClient client = user == null ? null : user.getClient();
    ChestContractEvaluator.Evaluation evaluation =
        ChestContractEvaluator.evaluate(contract, offeredItems);
    if (room == null
        || user == null
        || client == null
        || contract == null
        || !evaluation.valid()) {
      return ChestTransactionFailure.INVALID_TRADE;
    }
    if (evaluation.withdrawnCoins() > 0) {
      Integer approved = approveCreditTransfer(user, evaluation.withdrawnCoins(), true);
      if (approved == null) {
        return ChestTransactionFailure.USER_CANCELLED;
      }
      if (approved != evaluation.withdrawnCoins()) {
        return ChestTransactionFailure.INVALID_TRADE;
      }
    }

    ChestRepository.ContractTransfer transfer;
    List<ReentrantLock> contractLocks = lockChestStripes(chests);
    try {
    Map<Integer, HabboItem> offeredById = new LinkedHashMap<>();
    for (HabboItem item : offeredItems) {
      offeredById.put(item.getId(), item);
    }
    Map<Integer, HabboItem> storedById = new LinkedHashMap<>();
    Map<Integer, ChestSettings> settingsBefore = new LinkedHashMap<>();
    Map<Integer, Integer> countsBefore = new LinkedHashMap<>();
    for (HabboItem chest : chests) {
      settingsBefore.put(chest.getId(), settings(chest));
      countsBefore.put(chest.getId(), contentsCount(chest));
      if (ChestType.fromItem(chest) == ChestType.FURNI) {
        for (HabboItem item : wiredStoredFurniture(chest)) {
          storedById.put(item.getId(), item);
        }
      }
    }

    synchronized (user.getHabboInfo()) {
      transfer =
          this.repository.executeContract(
              room.getId(),
              user.getHabboInfo().getId(),
              user.getHabboInfo().getUsername(),
              chests,
              contract,
              offeredItems);
      if (transfer.result() == ChestRepository.Result.OK
          && transfer.withdrawnCoins() > 0) {
        user.getHabboInfo().applyCommittedCreditBalance(transfer.userBalance());
      }
    }
    if (transfer.result() != ChestRepository.Result.OK) {
      return transfer.failure() == null
          ? ChestTransactionFailure.DATABASE_ERROR
          : transfer.failure();
    }

    for (int itemId : transfer.depositedItemIds()) {
      HabboItem item = offeredById.get(itemId);
      if (item != null) {
        item.setUserId(0);
        item.setRoomId(0);
      }
      client.sendResponse(new FurniListRemoveMessageComposer(itemId));
    }
    for (int itemId : transfer.withdrawnItemIds()) {
      HabboItem item = storedById.get(itemId);
      if (item == null) {
        continue;
      }
      item.setUserId(user.getHabboInfo().getId());
      item.setRoomId(0);
      user.addFurniture(item);
    }
    if (transfer.withdrawnCoins() > 0) {
      client.sendResponse(new CreditBalanceMessageComposer(user));
    }

    for (HabboItem chest : chests) {
      int chestId = chest.getId();
      afterMutation(room, chest, ChestRepository.Result.OK);
      if (ChestType.fromItem(chest) == ChestType.COINS) {
        Integer balance = transfer.coinBalances().get(chestId);
        if (balance != null) {
          broadcastToViewers(
              room,
              chest,
              new ChestCoinBalanceComposer(chest.getRoomVisibleId(), balance, true));
        }
      } else {
        int[] removed =
            transfer.withdrawnFurnitureByChest().getOrDefault(chestId, List.of()).stream()
                .mapToInt(Integer::intValue)
                .toArray();
        List<ChestRepository.StoredHabboItem> added =
            transfer.depositedFurnitureByChest().getOrDefault(chestId, List.of()).stream()
                .map(offeredById::get)
                .filter(java.util.Objects::nonNull)
                .map(item -> new ChestRepository.StoredHabboItem(item, 0, 0))
                .toList();
        if (removed.length > 0 || !added.isEmpty()) {
          broadcastToViewers(
              room,
              chest,
              new ChestFurniContentsUpdateComposer(
                  chest.getRoomVisibleId(), removed, added));
        }
      }
      notifyMutation(
          settingsBefore.get(chestId),
          room,
          chest,
          user,
          countsBefore.getOrDefault(chestId, 0),
          contentsCount(chest),
          contentsCount(chest) > countsBefore.getOrDefault(chestId, 0)
              || !transfer
                  .depositedFurnitureByChest()
                  .getOrDefault(chestId, List.of())
                  .isEmpty(),
          contentsCount(chest) < countsBefore.getOrDefault(chestId, 0)
              || !transfer
                  .withdrawnFurnitureByChest()
                  .getOrDefault(chestId, List.of())
                  .isEmpty(),
          true);
    }
    } finally {
      unlockChestStripes(contractLocks);
    }

    if (completeTradeUi) {
      ChestTradeSession active = client.clearChestTradeSession();
      if (active != null) {
        active.clear();
      }
      client.sendResponse(new WiredTradeCompletedComposer());
    }
    if (contract.getRule() != null) {
      client.sendResponse(
          new WiredTransactionSuccessComposer(contract, transfer.multiplier()));
    }
    HabboItem contractItem = room.getHabboItemByDatabaseId(contract.contractId());
    if (contractItem != null) {
      WiredManager.triggerTransactionCompleted(
          room,
          user.getRoomUnit(),
          contractItem,
          WiredTransactionOutcome.completed(
              transfer.multiplier(),
              transfer.depositedFurnitureByChest().values().stream()
                  .mapToInt(List::size)
                  .sum(),
              transfer.depositedCoins(),
              transfer.withdrawnItemIds().size(),
              transfer.withdrawnCoins()));
    }
    return null;
  }

  private static int failureCode(ChestRepository.Result result) {
    return switch (result) {
      case EMPTY -> 10;
      case FULL -> 19;
      case LOCKED -> 15;
      case STALE_ROOM, NOT_FOUND -> 13;
      case STORAGE_ERROR -> 1001;
      default -> 1;
    };
  }

  private static void restoreReservedItem(GameClient client, HabboItem item) {
    if (client == null || client.getHabbo() == null || item == null) {
      return;
    }
    var items = client.getHabbo().getInventory().getItemsComponent().getItems();
    synchronized (items) {
      if (!items.containsKey(item.getId())) {
        items.put(item.getId(), item);
      }
    }
  }

  private static ReentrantLock[] createChestLocks() {
    ReentrantLock[] locks = new ReentrantLock[CHEST_LOCK_STRIPES];
    for (int index = 0; index < locks.length; index++) {
      locks[index] = new ReentrantLock();
    }
    return locks;
  }

  private ReentrantLock chestLock(int chestId) {
    return this.chestLocks[Math.floorMod(chestId, this.chestLocks.length)];
  }

  private <T> T withChestLock(int chestId, Supplier<T> action) {
    ReentrantLock lock = chestLock(chestId);
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }

  /** Multi-chest order is unique ascending stripe, then HabboInfo inside those locks. */
  private List<ReentrantLock> lockChestStripes(List<HabboItem> chests) {
    List<ReentrantLock> locks =
        chests == null
            ? List.of()
            : chests.stream()
                .filter(java.util.Objects::nonNull)
                .map(HabboItem::getId)
                .map(id -> Math.floorMod(id, this.chestLocks.length))
                .distinct()
                .sorted()
                .map(index -> this.chestLocks[index])
                .toList();
    for (ReentrantLock lock : locks) {
      lock.lock();
    }
    return locks;
  }

  private static void unlockChestStripes(List<ReentrantLock> locks) {
    for (int index = locks.size() - 1; index >= 0; index--) {
      locks.get(index).unlock();
    }
  }

  private boolean isVisuallyOpen(HabboItem chest, ChestSettings settings) {
    return switch (settings.stateControlMode()) {
      case 0 -> hasActiveViewer(chest);
      case 1 -> true;
      case 2 -> false;
      case 3 -> wiredControlledOpen(chest);
      default -> false;
    };
  }

  private boolean hasActiveViewer(HabboItem chest) {
    if (chest == null || chest.getRoomId() <= 0) {
      return false;
    }
    Room room =
        com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(chest.getRoomId());
    if (room == null) {
      return false;
    }
    boolean found = false;
    for (Habbo habbo : new ArrayList<>(room.getHabbos())) {
      GameClient viewer = habbo == null ? null : habbo.getClient();
      if (viewer == null || !viewer.isActiveChest(room.getId(), chest.getId())) {
        continue;
      }
      if (habbo.getHabboInfo().getCurrentRoom() == room
          && habbo.getRoomUnit() != null
          && habbo.getRoomUnit().isInRoom()
          && viewer
              .getWiredCapabilityState()
              .supportsRoom(WiredCapabilityService.CAPABILITY_CHESTS, room.getId())) {
        found = true;
        continue;
      }
      long expectedKey = chestKey(room.getId(), chest.getId());
      viewer.clearActiveChest(expectedKey);
    }
    return found;
  }

  private static boolean wiredControlledOpen(HabboItem chest) {
    String state = chest == null ? null : chest.getExtradata();
    if (state == null || state.isBlank()) {
      return false;
    }
    try {
      return Integer.parseInt(state.trim()) > 0;
    } catch (NumberFormatException ignored) {
      return "true".equalsIgnoreCase(state) || "open".equalsIgnoreCase(state);
    }
  }

  private static int visualState(
      HabboItem chest, ChestSettings settings, int contents, boolean open) {
    if (!open) {
      return 0;
    }
    if (ChestType.fromItem(chest) != ChestType.COINS) {
      return 1;
    }
    long balance = Math.max(0L, contents);
    if (balance == 0) {
      return 1;
    }
    long capacity = Math.max(0L, settings.capacity());
    if (capacity == 0) {
      return 4;
    }
    if (balance * 100L <= capacity * 33L) {
      return 2;
    }
    if (balance * 100L <= capacity * 66L) {
      return 3;
    }
    return 4;
  }

  private String furnitureVisuals(HabboItem chest) {
    StringBuilder visuals = new StringBuilder();
    for (HabboItem item : appearancePreviewFurniture(chest)) {
      if (item == null || item.getBaseItem() == null) {
        continue;
      }
      if (visuals.length() > 0) {
        visuals.append(';');
      }
      boolean wall =
          item.getBaseItem().getType()
              == com.eu.habbo.habbohotel.items.FurnitureType.WALL;
      visuals.append(wall).append(',').append(item.getBaseItem().getSpriteId());
      String poster = legacyPosterExtra(item);
      if (!poster.isEmpty()) {
        visuals.append(',').append(poster);
      }
    }
    return visuals.toString();
  }

  private static String appearanceTypeKey(HabboItem item) {
    if (item == null || item.getBaseItem() == null) {
      return "";
    }
    boolean wall =
        item.getBaseItem().getType()
            == com.eu.habbo.habbohotel.items.FurnitureType.WALL;
    return wall + ":" + item.getBaseItem().getSpriteId() + ":" + legacyPosterExtra(item);
  }

  private static String legacyPosterExtra(HabboItem item) {
    if (item == null
        || item.getBaseItem() == null
        || item.getBaseItem().getType()
            != com.eu.habbo.habbohotel.items.FurnitureType.WALL
        || !"poster".equals(item.getBaseItem().getName())) {
      return "";
    }
    return item.getExtradata() == null ? "" : item.getExtradata();
  }

  private void refreshAppearance(Room room, HabboItem chest) {
    if (room != null && chest != null && isChest(chest)) {
      room.sendComposer(new ObjectDataUpdateMessageComposer(chest).compose());
    }
  }

  public void invalidate(int chestId) {
    withChestLock(
        chestId,
        () -> {
          this.cache.remove(chestId);
          return null;
        });
  }

  public void clear() {
    this.cache.clear();
  }

  private CachedChest snapshot(HabboItem chest) {
    if (!isChest(chest)) {
      return null;
    }
    return withChestLock(
        chest.getId(),
        () ->
            this.cache.computeIfAbsent(
                chest.getId(),
                ignored -> {
                  ChestSettings settings = this.repository.loadSettings(chest);
                  if (settings == null) {
                    return null;
                  }
                  ChestType type = ChestType.fromItem(chest);
                  int count =
                      type == ChestType.COINS
                          ? this.repository.loadCoinBalance(chest.getId())
                          : this.repository.loadStoredItems(chest.getId()).size();
                  return new CachedChest(settings, count);
                }));
  }

  private ChestRepository.Result afterMutation(
      Room room, HabboItem chest, ChestRepository.Result result) {
    if (result == ChestRepository.Result.OK) {
      invalidate(chest.getId());
      refreshAppearance(room, chest);
    }
    return result;
  }

  private ChestRepository.ItemBatchTransfer completeBatchWithdrawal(
      GameClient client,
      Room room,
      HabboItem chest,
      List<ChestRepository.StoredHabboItem> selected) {
    return withChestLock(
        chest.getId(), () -> completeBatchWithdrawalLocked(client, room, chest, selected));
  }

  private ChestRepository.ItemBatchTransfer completeBatchWithdrawalLocked(
      GameClient client,
      Room room,
      HabboItem chest,
      List<ChestRepository.StoredHabboItem> selected) {
    ChestSettings settings = settings(chest);
    int before = contentsCount(chest);
    List<Integer> ids = selected.stream().map(entry -> entry.item().getId()).toList();
    int actorId = client.getHabbo().getHabboInfo().getId();
    ChestRepository.ItemBatchTransfer result =
        this.repository.withdrawItems(
            chest,
            room.getId(),
            actorId,
            ChestTransactionLog.Audit.manual(
                client.getHabbo().getHabboInfo().getUsername()),
            ids);
    if (result.result() != ChestRepository.Result.OK) {
      return result;
    }
    Map<Integer, HabboItem> byId = new LinkedHashMap<>();
    for (ChestRepository.StoredHabboItem entry : selected) {
      byId.put(entry.item().getId(), entry.item());
    }
    THashSet<HabboItem> withdrawn = new THashSet<>();
    for (int id : result.itemIds()) {
      HabboItem item = byId.get(id);
      if (item != null) {
        item.setUserId(actorId);
        item.setRoomId(0);
        withdrawn.add(item);
      }
    }
    if (!withdrawn.isEmpty()) {
      client.getHabbo().addFurniture(withdrawn);
    }
    afterMutation(room, chest, ChestRepository.Result.OK);
    int[] removed = result.itemIds().stream().mapToInt(Integer::intValue).toArray();
    broadcastToViewers(
        room,
        chest,
        new ChestFurniContentsUpdateComposer(chest.getRoomVisibleId(), removed, List.of()));
    notifyMutation(
        settings, room, chest, client.getHabbo(), before, contentsCount(chest), false, true, false);
    return result;
  }

  private static void broadcastToViewers(Room room, HabboItem chest, MessageComposer composer) {
    if (room == null || chest == null || composer == null) {
      return;
    }
    for (Habbo habbo : new ArrayList<>(room.getHabbos())) {
      GameClient viewer = habbo.getClient();
      if (viewer != null && viewer.isActiveChest(room.getId(), chest.getId())) {
        viewer.sendResponse(composer);
      }
    }
  }

  private void abortTradesUsingChest(Room room, HabboItem chest, int reason) {
    if (room == null || chest == null) {
      return;
    }
    boolean viewerCleared = false;
    for (Habbo habbo : new ArrayList<>(room.getHabbos())) {
      GameClient client = habbo.getClient();
      ChestTradeSession session = client == null ? null : client.getChestTradeSession();
      if (session != null && session.chestIds().contains(chest.getId())) {
        abortTrade(client, true, reason);
      }
      if (client != null && client.isActiveChest(room.getId(), chest.getId())) {
        long expectedKey = chestKey(room.getId(), chest.getId());
        viewerCleared |= client.clearActiveChest(expectedKey);
      }
    }
    if (viewerCleared && room.getHabboItemByDatabaseId(chest.getId()) == chest) {
      withChestLock(
          chest.getId(),
          () -> {
            refreshAppearance(room, chest);
            return null;
          });
    }
  }

  /**
   * Sends July's existing generic notification packet with its official wired_chests.* localization
   * keys and bundled icon names. No Nitro-only chest notification packet is introduced.
   */
  private void notifyMutation(
      ChestSettings settings,
      Room room,
      HabboItem chest,
      Habbo actor,
      int before,
      int after,
      boolean donation,
      boolean withdrawal,
      boolean wiredTransaction) {
    if (settings == null || room == null || chest == null) {
      return;
    }
    int previous = Math.max(0, before);
    int current = Math.max(0, after);
    if (donation && settings.notifyDonation()) {
      notifyChest(settings, room, chest, actor, NOTIFICATION_DONATION);
    }
    if (settings.notifyFull() && previous < settings.capacity() && current >= settings.capacity()) {
      notifyChest(settings, room, chest, null, NOTIFICATION_FULL);
    }
    if (settings.wiredEnabled() && withdrawal && settings.notifyWithdraw()) {
      notifyChest(settings, room, chest, actor, NOTIFICATION_WITHDRAW);
    }
    if (settings.wiredEnabled() && settings.notifyEmpty() && previous > 0 && current == 0) {
      notifyChest(settings, room, chest, null, NOTIFICATION_EMPTY);
    }
    if (settings.wiredEnabled() && wiredTransaction && settings.notifyWiredTransaction()) {
      notifyChest(settings, room, chest, actor, NOTIFICATION_WIRED);
    }
  }

  private void notifyChest(
      ChestSettings settings, Room room, HabboItem chest, Habbo actor, String type) {
    int ownerId = chest.getUserId();
    if ((NOTIFICATION_DONATION.equals(type) || NOTIFICATION_WITHDRAW.equals(type))
        && actor != null
        && actor.getHabboInfo().getId() == ownerId) {
      return;
    }
    Habbo owner = com.eu.habbo.Emulator.getGameEnvironment().getHabboManager().getHabbo(ownerId);
    if (settings.notifyMode() == 1
        && owner != null
        && owner.getHabboInfo().getCurrentRoom() == room) {
      return;
    }
    ChestRepository.PendingNotification notification =
        new ChestRepository.PendingNotification(
            0,
            ownerId,
            type,
            chest.getId(),
            room.getId(),
            actor == null ? "" : actor.getHabboInfo().getUsername(),
            notificationChestName(settings, chest),
            System.currentTimeMillis());
    if (owner != null && owner.getClient() != null) {
      sendNotification(owner.getClient(), notification);
    } else {
      this.repository.queueNotification(notification);
    }
  }

  /** Delivers July's maximum of five persisted notifications after login. */
  public void deliverPendingNotifications(Habbo owner) {
    if (owner == null || owner.getClient() == null) {
      return;
    }
    ChestRepository.NotificationBatch batch =
        this.repository.drainNotifications(owner.getHabboInfo().getId());
    if (batch.result() != ChestRepository.Result.OK) {
      return;
    }
    for (ChestRepository.PendingNotification notification : batch.notifications()) {
      sendNotification(owner.getClient(), notification);
    }
  }

  private static void sendNotification(
      GameClient client, ChestRepository.PendingNotification notification) {
    if (client == null || notification == null) {
      return;
    }
    THashMap<String, String> parameters = new THashMap<>();
    parameters.put("display", "BUBBLE");
    parameters.put("image", notificationIcon(notification.type()));
    parameters.put("linkUrl", "event:navigator/goto/" + notification.roomId());
    parameters.put("chest_name", notification.chestName());
    parameters.put("user_name", notification.actorName());
    client.sendResponse(new NotificationDialogMessageComposer(notification.type(), parameters));
  }

  private static String notificationIcon(String type) {
    return switch (type) {
      case NOTIFICATION_FULL -> "chests_icon_chest_full";
      case NOTIFICATION_DONATION -> "chests_icon_chest_donation";
      case NOTIFICATION_WITHDRAW -> "chests_icon_chest_withdraw";
      case NOTIFICATION_EMPTY -> "chests_icon_chest_empty";
      case NOTIFICATION_WIRED -> "chests_icon_chest_wired_transaction";
      default -> "chests_icon_chest_capacity_exceeds";
    };
  }

  private static String notificationChestName(ChestSettings settings, HabboItem chest) {
    if (settings != null && !settings.name().isBlank()) {
      return settings.name();
    }
    String typeName = ChestType.fromItem(chest) == ChestType.COINS ? "Credit Chest" : "Furni Chest";
    return typeName + " #" + chest.getRoomVisibleId();
  }

  private static boolean allowed(
      ChestAuthorization.Operation operation,
      GameClient client,
      Room room,
      HabboItem chest,
      ChestSettings settings) {
    if (client == null || client.getHabbo() == null || settings == null) {
      return false;
    }
    Habbo habbo = client.getHabbo();
    WiredMenuSettings menu = WiredMenuSettings.load(room.getId());
    int actorId = habbo.getHabboInfo().getId();
    return ChestAuthorization.allowed(
        operation,
        new ChestAuthorization.Facts(
            true,
            habbo.getHabboInfo().getCurrentRoom() == room
                && habbo.getRoomUnit() != null
                && habbo.getRoomUnit().isInRoom(),
            chest.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(chest.getId()) == chest,
            chest.getUserId() == actorId,
            room.isOwner(habbo),
            menu.canRead(room, habbo),
            menu.canModify(room, habbo),
            settings.everyoneCanOpen(),
            settings.everyoneCanDonate(),
            settings.locked(),
            settings.wiredEnabled()));
  }

  private static String flag(boolean value) {
    return value ? "1" : "0";
  }

  private record CachedChest(ChestSettings settings, int contentsCount) {}
}
