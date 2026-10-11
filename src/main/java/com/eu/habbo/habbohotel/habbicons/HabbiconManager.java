package com.eu.habbo.habbohotel.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconInfoMessageComposer;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconShopDataMessageComposer;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconStatusChangedMessageComposer;
import com.eu.habbo.messages.outgoing.habbicons.OwnedHabbiconsMessageComposer;
import com.eu.habbo.messages.outgoing.habbicons.RoomUserHabbiconMessageComposer;
import com.eu.habbo.messages.outgoing.users.ActivityPointsMessageComposer;
import com.eu.habbo.messages.outgoing.users.CreditBalanceMessageComposer;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HabbiconManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabbiconManager.class);

    public static final int STATE_PURCHASABLE = 0;
    public static final int STATE_CLAIMABLE = 1;
    public static final int STATE_OWNED = 2;
    public static final int STATE_FAVORITE_OWNED = 3;
    public static final int STATE_LOCKED = 4;
    public static final int STATE_REWARD_MARKER = 5;

    public static final int ACTION_BUY_COLLECTION = 1;
    public static final int ACTION_BUY_SINGLE = 2;
    public static final int ACTION_CLAIM_REWARD = 3;
    public static final int ACTION_FAVORITE = 4;
    public static final int ACTION_UNFAVORITE = 5;
    public static final int ACTION_USE_IN_ROOM = 6;

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_DISABLED = 1;
    public static final int RESULT_NOT_FOUND = 2;
    public static final int RESULT_ALREADY_OWNED = 3;
    public static final int RESULT_NOT_OWNED = 4;
    public static final int RESULT_NOT_ENOUGH_CREDITS = 5;
    public static final int RESULT_NOT_ENOUGH_POINTS = 6;
    public static final int RESULT_NOT_CLAIMABLE = 7;
    public static final int RESULT_COOLDOWN = 8;
    public static final int RESULT_NOT_IN_ROOM = 9;
    public static final int RESULT_ERROR = 10;

    private final Map<Integer, HabbiconCollectionDefinition> collections;
    private final Map<Integer, HabbiconDefinition> habbicons;
    private final Map<Integer, Integer> lastRoomUse;

    private boolean enabled;
    private String assetRoot;
    private String assetHash;
    private int recentsMax;
    private int useCooldownSeconds;

    public HabbiconManager() {
        this.collections = new LinkedHashMap<>();
        this.habbicons = new LinkedHashMap<>();
        this.lastRoomUse = new ConcurrentHashMap<>();
        this.reload();
    }

    public synchronized void reload() {
        long millis = System.currentTimeMillis();
        this.collections.clear();
        this.habbicons.clear();
        this.enabled = Emulator.getConfig().getBoolean("habbicons.enabled", false);
        this.assetRoot = Emulator.getConfig().getValue("habbicons.asset.root", "http://localhost/ngh/habbicons");
        this.assetHash = Emulator.getConfig().getValue("habbicons.asset.hash", "dev");
        this.recentsMax = Math.max(0, Emulator.getConfig().getInt("habbicons.recents.max", 20));
        this.useCooldownSeconds = Math.max(0, Emulator.getConfig().getInt("habbicons.use.cooldown.seconds", 5));

        if (!this.enabled) {
            LOGGER.info("Habbicon Manager -> Disabled by configuration.");
            LOGGER.debug("Habbicon Manager -> Asset root: {}", this.assetRoot);
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.loadCollections(connection);
            this.loadHabbicons(connection);
            LOGGER.info("Habbicon Manager -> Loaded! ({} MS, {} collections, {} habbicons)",
                    System.currentTimeMillis() - millis, this.collections.size(), this.habbicons.size());
            LOGGER.debug("Habbicon Manager -> Asset root: {}", this.assetRoot);
        } catch (Exception e) {
            this.enabled = false;
            LOGGER.error("Habbicon Manager -> Failed to load; feature disabled until reload.", e);
        }
    }

    public synchronized void dispose() {
        this.collections.clear();
        this.habbicons.clear();
        this.lastRoomUse.clear();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getAssetRoot() {
        return this.assetRoot;
    }

    public String getAssetHash() {
        return this.assetHash;
    }

    public int getRecentsMax() {
        return this.recentsMax;
    }

    public synchronized List<HabbiconCollectionDefinition> getCollections() {
        return new ArrayList<>(this.collections.values());
    }

    public synchronized HabbiconDefinition getHabbicon(int id) {
        return this.habbicons.get(id);
    }

    public synchronized HabbiconCollectionDefinition getCollection(int id) {
        return this.collections.get(id);
    }

    public void sendShopData(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        HabbiconUserState state = this.loadUserState(habbo);
        LOGGER.info("[HABBICONS] sendShopData user={} enabled={} collections={} owned={} recents={}",
                habbo.getHabboInfo().getUsername(),
                this.enabled,
                this.collections.size(),
                state.getOwned().size(),
                state.getRecents().size());
        habbo.getClient().sendResponse(new HabbiconShopDataMessageComposer(!this.enabled, this.assetRoot, this.assetHash, this.getCollections(), state, this));
        habbo.getClient().sendResponse(new OwnedHabbiconsMessageComposer(state, this));
    }

    public void sendInfo(Habbo habbo, int habbiconId) {
        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        HabbiconUserState state = this.loadUserState(habbo);
        habbo.getClient().sendResponse(new HabbiconInfoMessageComposer(this.enabled, habbiconId, habbicon, state, this));
    }

    public void buyCollection(Habbo habbo, int collectionId) {
        OperationResult result = this.buyCollectionInternal(habbo, collectionId);
        this.finishOperation(habbo, ACTION_BUY_COLLECTION, 0, collectionId, result);
    }

    public void buyHabbicon(Habbo habbo, int habbiconId) {
        OperationResult result = this.buyHabbiconInternal(habbo, habbiconId);
        this.finishOperation(habbo, ACTION_BUY_SINGLE, habbiconId, result.collectionId, result);
    }

    public void claimReward(Habbo habbo, int habbiconId) {
        OperationResult result = this.claimRewardInternal(habbo, habbiconId);
        this.finishOperation(habbo, ACTION_CLAIM_REWARD, habbiconId, result.collectionId, result);
    }

    public void favorite(Habbo habbo, int habbiconId) {
        OperationResult result = this.setFavorite(habbo, habbiconId, true);
        this.finishOperation(habbo, ACTION_FAVORITE, habbiconId, result.collectionId, result);
    }

    public void unfavorite(Habbo habbo, int habbiconId) {
        OperationResult result = this.setFavorite(habbo, habbiconId, false);
        this.finishOperation(habbo, ACTION_UNFAVORITE, habbiconId, result.collectionId, result);
    }

    public void useInRoom(Habbo habbo, int habbiconId) {
        OperationResult result = this.useInRoomInternal(habbo, habbiconId);
        this.finishOperation(habbo, ACTION_USE_IN_ROOM, habbiconId, result.collectionId, result);
    }

    public boolean useInMessenger(Habbo habbo, int habbiconId) {
        if (!this.enabled) {
            return false;
        }

        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        if (habbo == null || habbicon == null || !habbicon.isEnabled()) {
            return false;
        }

        int userId = habbo.getHabboInfo().getId();
        int now = Emulator.getIntUnixTimestamp();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                if (!this.isOwned(connection, userId, habbiconId)) {
                    connection.rollback();
                    return false;
                }

                this.upsertRecent(connection, userId, habbiconId, now);
                this.trimRecents(connection, userId);
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("HabbiconManager -> Failed to use Habbicon {} in messenger for user {}.", habbiconId, userId, e);
                return false;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to open messenger-use transaction for user {}.", userId, e);
            return false;
        }
    }

    public HabbiconUserState loadUserState(Habbo habbo) {
        HabbiconUserState state = new HabbiconUserState();
        if (habbo == null) {
            return state;
        }

        int userId = habbo.getHabboInfo().getId();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT habbicon_id, favorite FROM users_habbicons WHERE user_id = ? ORDER BY habbicon_id ASC")) {
                statement.setInt(1, userId);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        state.addOwned(set.getInt("habbicon_id"), set.getBoolean("favorite"));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT habbicon_id FROM users_habbicon_recents WHERE user_id = ? ORDER BY used_at DESC LIMIT ?")) {
                statement.setInt(1, userId);
                statement.setInt(2, this.recentsMax);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        state.addRecent(set.getInt("habbicon_id"));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to load Habbicon state for user {}.", userId, e);
        }

        return state;
    }

    public int getState(HabbiconDefinition habbicon, HabbiconUserState userState) {
        if (habbicon == null) {
            return STATE_PURCHASABLE;
        }

        if (userState != null && userState.isOwned(habbicon.getId())) {
            return userState.isFavorite(habbicon.getId()) ? STATE_FAVORITE_OWNED : STATE_OWNED;
        }

        if (habbicon.isReward()) {
            return this.isRewardClaimable(habbicon, userState) ? STATE_CLAIMABLE : STATE_REWARD_MARKER;
        }

        return STATE_PURCHASABLE;
    }

    public boolean grantHabbicon(Connection connection, int userId, int habbiconId) throws SQLException {
        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        if (!this.enabled || connection == null || userId <= 0 || habbicon == null || !habbicon.isEnabled()) {
            return false;
        }

        this.insertOwnership(connection, userId, habbiconId, Emulator.getIntUnixTimestamp());
        return true;
    }

    public void sendHabbiconState(Habbo habbo, int habbiconId) {
        if (habbo == null || habbo.getClient() == null || habbiconId <= 0) {
            return;
        }

        HabbiconUserState state = this.loadUserState(habbo);
        HabbiconDefinition definition = this.getHabbicon(habbiconId);
        int stateValue = definition == null ? STATE_LOCKED : this.getState(definition, state);
        habbo.getClient().sendResponse(new HabbiconStatusChangedMessageComposer(habbiconId, stateValue));
        habbo.getClient().sendResponse(new OwnedHabbiconsMessageComposer(state, this));
    }

    public boolean isCollectionComplete(int collectionId, HabbiconUserState userState) {
        HabbiconCollectionDefinition collection = this.getCollection(collectionId);
        if (collection == null) {
            return false;
        }

        boolean hasPurchasable = false;
        for (HabbiconDefinition habbicon : collection.getHabbicons()) {
            if (!habbicon.isEnabled() || habbicon.isReward()) {
                continue;
            }

            hasPurchasable = true;
            if (userState == null || !userState.isOwned(habbicon.getId())) {
                return false;
            }
        }

        return hasPurchasable;
    }

    public boolean isRewardClaimable(HabbiconDefinition habbicon, HabbiconUserState userState) {
        return habbicon != null && habbicon.isReward() && userState != null && !userState.isOwned(habbicon.getId()) && this.isCollectionComplete(habbicon.getCollectionId(), userState);
    }

    private OperationResult buyCollectionInternal(Habbo habbo, int collectionId) {
        if (!this.enabled) {
            return OperationResult.failed(RESULT_DISABLED, collectionId);
        }

        HabbiconCollectionDefinition collection = this.getCollection(collectionId);
        if (habbo == null || collection == null || !collection.isEnabled()) {
            return OperationResult.failed(RESULT_NOT_FOUND, collectionId);
        }

        List<HabbiconDefinition> toGrant = new ArrayList<>();
        int userId = habbo.getHabboInfo().getId();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                HabbiconUserState state = this.loadUserState(connection, userId);
                for (HabbiconDefinition habbicon : collection.getHabbicons()) {
                    if (!habbicon.isEnabled() || habbicon.isReward() || state.isOwned(habbicon.getId())) {
                        continue;
                    }
                    toGrant.add(habbicon);
                }

                if (toGrant.isEmpty()) {
                    connection.rollback();
                    return OperationResult.failed(RESULT_ALREADY_OWNED, collectionId);
                }

                Deduction deduction = this.deduct(connection, habbo, collection.getPriceCredits(), collection.getPriceActivityPoints(), collection.getActivityPointType());
                if (deduction.resultCode != RESULT_SUCCESS) {
                    connection.rollback();
                    return OperationResult.failed(deduction.resultCode, collectionId);
                }

                int now = Emulator.getIntUnixTimestamp();
                for (HabbiconDefinition habbicon : toGrant) {
                    this.insertOwnership(connection, userId, habbicon.getId(), now);
                }

                connection.commit();
                return OperationResult.success(collectionId, STATE_OWNED, deduction.credits, deduction.points, deduction.pointsType);
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("HabbiconManager -> Failed to buy collection {} for user {}.", collectionId, userId, e);
                return OperationResult.failed(RESULT_ERROR, collectionId);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to open buy collection transaction for user {}.", userId, e);
            return OperationResult.failed(RESULT_ERROR, collectionId);
        }
    }

    private OperationResult buyHabbiconInternal(Habbo habbo, int habbiconId) {
        if (!this.enabled) {
            return OperationResult.failed(RESULT_DISABLED, 0);
        }

        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        if (habbo == null || habbicon == null || !habbicon.isEnabled() || habbicon.isReward()) {
            return OperationResult.failed(RESULT_NOT_FOUND, habbicon == null ? 0 : habbicon.getCollectionId());
        }

        int userId = habbo.getHabboInfo().getId();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                HabbiconUserState state = this.loadUserState(connection, userId);
                if (state.isOwned(habbiconId)) {
                    connection.rollback();
                    return OperationResult.failed(RESULT_ALREADY_OWNED, habbicon.getCollectionId());
                }

                Deduction deduction = this.deduct(connection, habbo, habbicon.getPriceCredits(), habbicon.getPriceActivityPoints(), habbicon.getActivityPointType());
                if (deduction.resultCode != RESULT_SUCCESS) {
                    connection.rollback();
                    return OperationResult.failed(deduction.resultCode, habbicon.getCollectionId());
                }

                this.insertOwnership(connection, userId, habbiconId, Emulator.getIntUnixTimestamp());
                connection.commit();
                return OperationResult.success(habbicon.getCollectionId(), STATE_OWNED, deduction.credits, deduction.points, deduction.pointsType);
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("HabbiconManager -> Failed to buy Habbicon {} for user {}.", habbiconId, userId, e);
                return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to open buy Habbicon transaction for user {}.", userId, e);
            return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
        }
    }

    private OperationResult claimRewardInternal(Habbo habbo, int habbiconId) {
        if (!this.enabled) {
            return OperationResult.failed(RESULT_DISABLED, 0);
        }

        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        if (habbo == null || habbicon == null || !habbicon.isEnabled() || !habbicon.isReward()) {
            return OperationResult.failed(RESULT_NOT_FOUND, habbicon == null ? 0 : habbicon.getCollectionId());
        }

        int userId = habbo.getHabboInfo().getId();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                HabbiconUserState state = this.loadUserState(connection, userId);
                if (state.isOwned(habbiconId)) {
                    connection.rollback();
                    return OperationResult.failed(RESULT_ALREADY_OWNED, habbicon.getCollectionId());
                }

                if (!this.isRewardClaimable(habbicon, state)) {
                    connection.rollback();
                    return OperationResult.failed(RESULT_NOT_CLAIMABLE, habbicon.getCollectionId());
                }

                this.insertOwnership(connection, userId, habbiconId, Emulator.getIntUnixTimestamp());
                connection.commit();
                return OperationResult.success(habbicon.getCollectionId(), STATE_OWNED, 0, 0, 0);
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("HabbiconManager -> Failed to claim reward Habbicon {} for user {}.", habbiconId, userId, e);
                return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to open claim reward transaction for user {}.", userId, e);
            return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
        }
    }

    private OperationResult setFavorite(Habbo habbo, int habbiconId, boolean favorite) {
        if (!this.enabled) {
            return OperationResult.failed(RESULT_DISABLED, 0);
        }

        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        if (habbo == null || habbicon == null || !habbicon.isEnabled()) {
            return OperationResult.failed(RESULT_NOT_FOUND, habbicon == null ? 0 : habbicon.getCollectionId());
        }

        int userId = habbo.getHabboInfo().getId();
        int now = Emulator.getIntUnixTimestamp();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users_habbicons SET favorite = ?, updated_at = ? WHERE user_id = ? AND habbicon_id = ?")) {
            statement.setBoolean(1, favorite);
            statement.setInt(2, now);
            statement.setInt(3, userId);
            statement.setInt(4, habbiconId);
            if (statement.executeUpdate() == 0) {
                return OperationResult.failed(RESULT_NOT_OWNED, habbicon.getCollectionId());
            }
            return OperationResult.success(habbicon.getCollectionId(), favorite ? STATE_FAVORITE_OWNED : STATE_OWNED, 0, 0, 0);
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to update favorite={} for Habbicon {} user {}.", favorite, habbiconId, userId, e);
            return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
        }
    }

    private OperationResult useInRoomInternal(Habbo habbo, int habbiconId) {
        if (!this.enabled) {
            return OperationResult.failed(RESULT_DISABLED, 0);
        }

        HabbiconDefinition habbicon = this.getHabbicon(habbiconId);
        if (habbo == null || habbicon == null || !habbicon.isEnabled()) {
            return OperationResult.failed(RESULT_NOT_FOUND, habbicon == null ? 0 : habbicon.getCollectionId());
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getRoomUnit() == null || !habbo.getRoomUnit().isInRoom()) {
            return OperationResult.failed(RESULT_NOT_IN_ROOM, habbicon.getCollectionId());
        }

        int userId = habbo.getHabboInfo().getId();
        int now = Emulator.getIntUnixTimestamp();
        int lastUse = this.lastRoomUse.getOrDefault(userId, 0);
        if (this.useCooldownSeconds > 0 && now - lastUse < this.useCooldownSeconds) {
            return OperationResult.failed(RESULT_COOLDOWN, habbicon.getCollectionId());
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                if (!this.isOwned(connection, userId, habbiconId)) {
                    connection.rollback();
                    return OperationResult.failed(RESULT_NOT_OWNED, habbicon.getCollectionId());
                }

                this.upsertRecent(connection, userId, habbiconId, now);
                this.trimRecents(connection, userId);
                connection.commit();
                this.lastRoomUse.put(userId, now);
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("HabbiconManager -> Failed to use Habbicon {} in room for user {}.", habbiconId, userId, e);
                return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("HabbiconManager -> Failed to open use-in-room transaction for user {}.", userId, e);
            return OperationResult.failed(RESULT_ERROR, habbicon.getCollectionId());
        }

        ServerMessage message = new RoomUserHabbiconMessageComposer(habbo.getRoomUnit().getId(), habbiconId).compose();
        for (Habbo roomHabbo : room.getHabbos()) {
            if (roomHabbo == null || roomHabbo.getClient() == null) {
                continue;
            }
            roomHabbo.getClient().sendResponse(message);
        }

        return OperationResult.success(habbicon.getCollectionId(), this.getState(habbicon, this.loadUserState(habbo)), 0, 0, 0);
    }

    private void finishOperation(Habbo habbo, int action, int habbiconId, int collectionId, OperationResult result) {
        if (habbo == null || habbo.getClient() == null || result == null) {
            return;
        }

        if (result.resultCode == RESULT_SUCCESS) {
            this.applyBalance(habbo, result);
            if (action == ACTION_USE_IN_ROOM) {
                Emulator.getGameEnvironment().getRewardTrackManager().progress(habbo, "use_habbicon");
            }
        }

        HabbiconUserState state = this.loadUserState(habbo);
        if (action == ACTION_BUY_COLLECTION && collectionId > 0) {
            HabbiconCollectionDefinition collection = this.getCollection(collectionId);
            if (collection != null) {
                for (HabbiconDefinition definition : collection.getHabbicons()) {
                    habbo.getClient().sendResponse(new HabbiconStatusChangedMessageComposer(definition.getId(), this.getState(definition, state)));
                }
            }
        } else if (habbiconId > 0) {
            HabbiconDefinition definition = this.getHabbicon(habbiconId);
            int stateValue = definition == null ? STATE_LOCKED : this.getState(definition, state);
            habbo.getClient().sendResponse(new HabbiconStatusChangedMessageComposer(habbiconId, stateValue));
            this.sendCollectionRewardState(habbo, collectionId, state);
        }

        if (result.resultCode == RESULT_SUCCESS) {
            habbo.getClient().sendResponse(new OwnedHabbiconsMessageComposer(state, this));
        }
    }

    private void sendCollectionRewardState(Habbo habbo, int collectionId, HabbiconUserState state) {
        if (habbo == null || habbo.getClient() == null || collectionId <= 0) {
            return;
        }

        HabbiconCollectionDefinition collection = this.getCollection(collectionId);
        if (collection == null) {
            return;
        }

        for (HabbiconDefinition definition : collection.getHabbicons()) {
            if (definition.isReward()) {
                habbo.getClient().sendResponse(new HabbiconStatusChangedMessageComposer(definition.getId(), this.getState(definition, state)));
                return;
            }
        }
    }

    private void loadCollections(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, name, enabled, price_credits, price_activity_points, activity_point_type, sort_order FROM habbicon_collections WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    HabbiconCollectionDefinition collection = new HabbiconCollectionDefinition(
                            set.getInt("id"),
                            set.getString("name"),
                            set.getBoolean("enabled"),
                            set.getInt("price_credits"),
                            set.getInt("price_activity_points"),
                            set.getInt("activity_point_type"),
                            set.getInt("sort_order"));
                    this.collections.put(collection.getId(), collection);
                }
            }
        }
    }

    private void loadHabbicons(Connection connection) throws SQLException {
        List<HabbiconDefinition> loaded = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, collection_id, name, enabled, is_reward, price_credits, price_activity_points, activity_point_type, sort_order FROM habbicons WHERE enabled = 1 ORDER BY collection_id ASC, sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    HabbiconDefinition habbicon = new HabbiconDefinition(
                            set.getInt("id"),
                            set.getInt("collection_id"),
                            set.getString("name"),
                            set.getBoolean("enabled"),
                            set.getBoolean("is_reward"),
                            set.getInt("price_credits"),
                            set.getInt("price_activity_points"),
                            set.getInt("activity_point_type"),
                            set.getInt("sort_order"));
                    loaded.add(habbicon);
                }
            }
        }

        loaded.sort(Comparator.comparingInt(HabbiconDefinition::getSortOrder).thenComparingInt(HabbiconDefinition::getId));
        for (HabbiconDefinition habbicon : loaded) {
            HabbiconCollectionDefinition collection = this.collections.get(habbicon.getCollectionId());
            if (collection == null) {
                continue;
            }

            this.habbicons.put(habbicon.getId(), habbicon);
            collection.addHabbicon(habbicon);
        }
    }

    private HabbiconUserState loadUserState(Connection connection, int userId) throws SQLException {
        HabbiconUserState state = new HabbiconUserState();

        try (PreparedStatement statement = connection.prepareStatement("SELECT habbicon_id, favorite FROM users_habbicons WHERE user_id = ? ORDER BY habbicon_id ASC")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    state.addOwned(set.getInt("habbicon_id"), set.getBoolean("favorite"));
                }
            }
        }

        return state;
    }

    private Deduction deduct(Connection connection, Habbo habbo, int credits, int points, int pointsType) throws SQLException {
        if (credits < 0 || points < 0 || pointsType < 0) {
            return Deduction.failed(RESULT_ERROR);
        }

        int userId = habbo.getHabboInfo().getId();
        int creditsToDebit = credits;
        int pointsToDebit = points;

        if (creditsToDebit > 0) {
            UserCreditsEvent event = new UserCreditsEvent(habbo, -creditsToDebit);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return Deduction.failed(RESULT_ERROR);
            }
            creditsToDebit = Math.abs(event.credits);
            if (!this.deductCredits(connection, userId, creditsToDebit)) {
                return Deduction.failed(RESULT_NOT_ENOUGH_CREDITS);
            }
        }

        if (pointsToDebit > 0) {
            UserPointsEvent event = new UserPointsEvent(habbo, -pointsToDebit, pointsType);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return Deduction.failed(RESULT_ERROR);
            }
            pointsToDebit = Math.abs(event.points);
            if (!this.deductCurrency(connection, userId, event.type, pointsToDebit)) {
                return Deduction.failed(RESULT_NOT_ENOUGH_POINTS);
            }
            pointsType = event.type;
        }

        return Deduction.success(creditsToDebit, pointsToDebit, pointsType);
    }

    private boolean deductCredits(Connection connection, int userId, int credits) throws SQLException {
        if (credits <= 0) {
            return true;
        }

        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ? LIMIT 1")) {
            statement.setInt(1, credits);
            statement.setInt(2, userId);
            statement.setInt(3, credits);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean deductCurrency(Connection connection, int userId, int type, int amount) throws SQLException {
        if (amount <= 0) {
            return true;
        }

        try (PreparedStatement statement = connection.prepareStatement("UPDATE users_currency SET amount = amount - ? WHERE user_id = ? AND type = ? AND amount >= ? LIMIT 1")) {
            statement.setInt(1, amount);
            statement.setInt(2, userId);
            statement.setInt(3, type);
            statement.setInt(4, amount);
            return statement.executeUpdate() > 0;
        }
    }

    private void insertOwnership(Connection connection, int userId, int habbiconId, int now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_habbicons (user_id, habbicon_id, state, favorite, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?) ON DUPLICATE KEY UPDATE updated_at = updated_at")) {
            statement.setInt(1, userId);
            statement.setInt(2, habbiconId);
            statement.setInt(3, STATE_OWNED);
            statement.setInt(4, now);
            statement.setInt(5, now);
            statement.executeUpdate();
        }
    }

    private boolean isOwned(Connection connection, int userId, int habbiconId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users_habbicons WHERE user_id = ? AND habbicon_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, habbiconId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private void upsertRecent(Connection connection, int userId, int habbiconId, int now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_habbicon_recents (user_id, habbicon_id, used_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE used_at = VALUES(used_at)")) {
            statement.setInt(1, userId);
            statement.setInt(2, habbiconId);
            statement.setInt(3, now);
            statement.executeUpdate();
        }
    }

    private void trimRecents(Connection connection, int userId) throws SQLException {
        if (this.recentsMax <= 0) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users_habbicon_recents WHERE user_id = ?")) {
                statement.setInt(1, userId);
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users_habbicon_recents WHERE user_id = ? AND habbicon_id NOT IN (SELECT habbicon_id FROM (SELECT habbicon_id FROM users_habbicon_recents WHERE user_id = ? ORDER BY used_at DESC LIMIT ?) keepers)")) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            statement.setInt(3, this.recentsMax);
            statement.executeUpdate();
        }
    }

    private void applyBalance(Habbo habbo, OperationResult result) {
        if (habbo == null || result == null) {
            return;
        }

        if (result.creditsDebited > 0) {
            habbo.getHabboInfo().addCredits(-result.creditsDebited);
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new CreditBalanceMessageComposer(habbo));
            }
        }

        if (result.pointsDebited > 0) {
            habbo.getHabboInfo().addCurrencyAmount(result.pointsType, -result.pointsDebited);
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new ActivityPointsMessageComposer(habbo));
            }
        }
    }

    private static class OperationResult {
        private final int resultCode;
        private final int collectionId;
        private final int state;
        private final int creditsDebited;
        private final int pointsDebited;
        private final int pointsType;

        private OperationResult(int resultCode, int collectionId, int state, int creditsDebited, int pointsDebited, int pointsType) {
            this.resultCode = resultCode;
            this.collectionId = collectionId;
            this.state = state;
            this.creditsDebited = creditsDebited;
            this.pointsDebited = pointsDebited;
            this.pointsType = pointsType;
        }

        private static OperationResult success(int collectionId, int state, int creditsDebited, int pointsDebited, int pointsType) {
            return new OperationResult(RESULT_SUCCESS, collectionId, state, creditsDebited, pointsDebited, pointsType);
        }

        private static OperationResult failed(int resultCode, int collectionId) {
            return new OperationResult(resultCode, collectionId, STATE_PURCHASABLE, 0, 0, 0);
        }
    }

    private static class Deduction {
        private final int resultCode;
        private final int credits;
        private final int points;
        private final int pointsType;

        private Deduction(int resultCode, int credits, int points, int pointsType) {
            this.resultCode = resultCode;
            this.credits = credits;
            this.points = points;
            this.pointsType = pointsType;
        }

        private static Deduction success(int credits, int points, int pointsType) {
            return new Deduction(RESULT_SUCCESS, credits, points, pointsType);
        }

        private static Deduction failed(int resultCode) {
            return new Deduction(resultCode, 0, 0, 0);
        }
    }
}
