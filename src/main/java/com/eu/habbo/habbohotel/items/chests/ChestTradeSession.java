package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One July Wired Trading session. Selected items are temporarily reserved
 * outside the user's inventory and are either restored on abort or committed
 * atomically by {@link ChestRepository}.
 */
public final class ChestTradeSession {
    public enum Phase {
        ADDING,
        COUNTDOWN,
        CLOSED
    }

    private final int userId;
    private final int roomId;
    private final int chestId;
    private final int chestVisibleId;
    private final ChestType chestType;
    private final List<Integer> chestIds;
    private final ChestContractPlan contractPlan;
    private final long startedAt;
    private final long expiresAt;
    private final Map<Integer, HabboItem> items = new LinkedHashMap<>();
    private Phase phase = Phase.ADDING;
    private long acceptedAt;

    public ChestTradeSession(int userId, int roomId, int chestId, int chestVisibleId,
            ChestType chestType, long now, int timeoutSeconds) {
        if (userId <= 0 || roomId <= 0 || chestId <= 0 || chestVisibleId == 0
                || chestType == null) {
            throw new IllegalArgumentException("Invalid Wired Trading identity");
        }
        this.userId = userId;
        this.roomId = roomId;
        this.chestId = chestId;
        this.chestVisibleId = chestVisibleId;
        this.chestType = chestType;
        this.chestIds = List.of(chestId);
        this.contractPlan = null;
        this.startedAt = now;
        this.expiresAt = timeoutSeconds <= 0
                ? Long.MAX_VALUE : now + timeoutSeconds * 1000L;
    }

    public static ChestTradeSession contract(
            int userId,
            int roomId,
            List<HabboItem> chests,
            ChestContractPlan contractPlan,
            long now,
            int timeoutSeconds) {
        if (chests == null || chests.isEmpty() || contractPlan == null) {
            throw new IllegalArgumentException("Invalid contract transaction");
        }
        List<Integer> ids =
                chests.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(HabboItem::getId)
                        .filter(id -> id > 0)
                        .distinct()
                        .sorted()
                        .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Invalid contract chests");
        }
        return new ChestTradeSession(
                userId, roomId, chests, ids, contractPlan, now, timeoutSeconds);
    }

    private ChestTradeSession(
            int userId,
            int roomId,
            List<HabboItem> chests,
            List<Integer> chestIds,
            ChestContractPlan contractPlan,
            long now,
            int timeoutSeconds) {
        HabboItem primary =
                chests.stream()
                        .filter(item -> item != null && item.getId() == chestIds.getFirst())
                        .findFirst()
                        .orElseThrow();
        if (userId <= 0
                || roomId <= 0
                || primary.getRoomVisibleId() == 0
                || contractPlan.contractId() <= 0) {
            throw new IllegalArgumentException("Invalid Wired contract identity");
        }
        this.userId = userId;
        this.roomId = roomId;
        this.chestId = primary.getId();
        this.chestVisibleId = primary.getRoomVisibleId();
        this.chestType = null;
        this.chestIds = List.copyOf(chestIds);
        this.contractPlan = contractPlan;
        this.startedAt = now;
        this.expiresAt =
                timeoutSeconds <= 0 ? Long.MAX_VALUE : now + timeoutSeconds * 1000L;
    }

    public synchronized boolean add(HabboItem item) {
        if (item == null || this.phase != Phase.ADDING || this.items.containsKey(item.getId())) {
            return false;
        }
        this.items.put(item.getId(), item);
        return true;
    }

    public synchronized HabboItem remove(int itemId) {
        if (this.phase != Phase.ADDING) {
            return null;
        }
        return this.items.remove(Math.abs(itemId));
    }

    public synchronized void resetAcceptance() {
        if (this.phase == Phase.CLOSED) {
            return;
        }
        this.phase = Phase.ADDING;
        this.acceptedAt = 0;
    }

    public synchronized boolean beginCountdown(long now) {
        if (this.phase != Phase.ADDING || this.items.isEmpty() || now >= this.expiresAt) {
            return false;
        }
        this.phase = Phase.COUNTDOWN;
        this.acceptedAt = now;
        return true;
    }

    public synchronized boolean canConfirm(long now, long minimumCountdownMillis) {
        return this.phase == Phase.COUNTDOWN && !this.items.isEmpty()
                && now >= this.acceptedAt + minimumCountdownMillis && now < this.expiresAt;
    }

    public synchronized List<HabboItem> items() {
        return List.copyOf(this.items.values());
    }

    public synchronized List<Integer> itemIds() {
        return List.copyOf(this.items.keySet());
    }

    public synchronized int itemCount() {
        return this.items.size();
    }

    public synchronized boolean isEmpty() {
        return this.items.isEmpty();
    }

    public synchronized void clear() {
        this.items.clear();
        this.phase = Phase.CLOSED;
        this.acceptedAt = 0;
    }

    public boolean expired(long now) {
        return now >= this.expiresAt;
    }

    public int userId() { return this.userId; }
    public int roomId() { return this.roomId; }
    public int chestId() { return this.chestId; }
    public int chestVisibleId() { return this.chestVisibleId; }
    public ChestType chestType() { return this.chestType; }
    public List<Integer> chestIds() { return this.chestIds; }
    public boolean isContract() { return this.contractPlan != null; }
    public ChestContractPlan contractPlan() { return this.contractPlan; }
    public long startedAt() { return this.startedAt; }
    public long expiresAt() { return this.expiresAt; }
    public synchronized Phase phase() { return this.phase; }
}
