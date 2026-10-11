package com.eu.habbo.habbohotel.rewardtrack;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rewardtrack.RewardTrackClaimResultMessageComposer;
import com.eu.habbo.messages.outgoing.rewardtrack.RewardTrackPremiumResultMessageComposer;
import com.eu.habbo.messages.outgoing.rewardtrack.RewardTrackProgressMessageComposer;
import com.eu.habbo.messages.outgoing.rewardtrack.RewardTracksMessageComposer;
import com.eu.habbo.messages.outgoing.users.ActivityPointsMessageComposer;
import com.eu.habbo.messages.outgoing.users.CreditBalanceMessageComposer;
import com.eu.habbo.messages.outgoing.users.HabboActivityPointNotificationMessageComposer;
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

public class RewardTrackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardTrackManager.class);
    private static final int CLAIM_SUCCESS = 0;
    private static final int CLAIM_NOT_ENOUGH_POINTS = 1;
    private static final int CLAIM_ALREADY_CLAIMED = 2;
    private static final int CLAIM_NOT_AVAILABLE = 3;
    private static final int CLAIM_PREMIUM_REQUIRED = 4;
    private static final int CLAIM_DISABLED = 5;
    private static final int CLAIM_UNKNOWN_REWARD = 6;
    private static final int CLAIM_GRANT_FAILED = 7;
    private static final int CLAIM_ERROR = 8;
    private static final int PREMIUM_SUCCESS = 0;
    private static final int PREMIUM_DISABLED = 1;
    private static final int PREMIUM_UNKNOWN_TRACK = 2;
    private static final int PREMIUM_NOT_ELIGIBLE = 3;
    private static final int PREMIUM_NOT_CONFIGURED = 4;
    private static final int PREMIUM_ALREADY_OWNED = 5;
    private static final int PREMIUM_INVALID_CONFIG = 6;
    private static final int PREMIUM_NOT_ENOUGH_CREDITS = 7;
    private static final int PREMIUM_NOT_ENOUGH_POINTS = 8;
    private static final int PREMIUM_ERROR = 9;
    private static final int DEFAULT_PREMIUM_POINTS_TYPE = 5;

    private final Map<String, RewardTrackDefinition> tracks;
    private boolean enabled;

    public RewardTrackManager() {
        this.tracks = new LinkedHashMap<>();
        this.reload();
    }

    public synchronized void reload() {
        long millis = System.currentTimeMillis();
        this.tracks.clear();
        this.enabled = Emulator.getConfig().getBoolean("rewardtrack.enabled", true);

        if (!this.enabled) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.loadTracks(connection);
            this.loadTasks(connection);
            this.loadTaskLevels(connection);
            this.loadRewards(connection);
            LOGGER.info("Reward Track Manager -> Loaded! ({} MS, {} tracks)",
                    System.currentTimeMillis() - millis, this.tracks.size());
        } catch (Exception e) {
            this.enabled = false;
            LOGGER.error("Reward Track Manager -> Failed to load definitions.", e);
        }
    }

    public synchronized void dispose() {
        this.tracks.clear();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public synchronized List<RewardTrackDefinition> getTracks() {
        return new ArrayList<>(this.tracks.values());
    }

    public void sendFullState(Habbo habbo, boolean reload) {
        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        habbo.getClient().sendResponse(new RewardTracksMessageComposer(!this.enabled, this.getTracks(), this.loadUserStates(habbo), reload));
    }

    public void claimReward(Habbo habbo, String trackId, String rewardId) {
        if (habbo == null || habbo.getClient() == null || trackId == null || rewardId == null) {
            return;
        }

        int resultCode = this.claimRewardInternal(habbo, trackId, rewardId);
        habbo.getClient().sendResponse(new RewardTrackClaimResultMessageComposer(trackId, rewardId, resultCode));
    }

    public void purchasePremium(Habbo habbo, String trackId) {
        if (habbo == null || habbo.getClient() == null || trackId == null) {
            return;
        }

        PremiumPurchaseResult result = this.purchasePremiumInternal(habbo, trackId);
        if (result.resultCode == PREMIUM_SUCCESS) {
            this.applyPremiumPurchase(habbo, result);
        }
        habbo.getClient().sendResponse(new RewardTrackPremiumResultMessageComposer(trackId, result.resultCode, result.points));
    }

    public void progress(Habbo habbo, String actionType) {
        this.progress(habbo, actionType, "", 1);
    }

    public void progress(Habbo habbo, String actionType, String parameter) {
        this.progress(habbo, actionType, parameter, 1);
    }

    public void progress(Habbo habbo, String actionType, String parameter, int amount) {
        if (!this.enabled || habbo == null || habbo.getClient() == null || actionType == null || actionType.isEmpty() || amount <= 0) {
            return;
        }

        String safeParameter = parameter != null ? parameter : "";
        int userId = habbo.getHabboInfo().getId();

        for (RewardTrackDefinition track : this.getTracks()) {
            for (RewardTrackTaskDefinition task : track.getTasks()) {
                if (!this.matchesTask(task, actionType, safeParameter)) {
                    continue;
                }

                try {
                    ProgressResult result = this.progressTask(userId, track, task, amount);
                    if (result.changed) {
                        habbo.getClient().sendResponse(new RewardTrackProgressMessageComposer(track.getId(), task.getId(), result.progressCount, result.points));
                    }
                } catch (Exception e) {
                    LOGGER.error("RewardTrackManager -> Failed to progress action {} for user {}.", actionType, userId, e);
                }
            }
        }
    }

    /** Applies July Wired action 58 directly to one configured track task. */
    public void progressWired(Habbo habbo, String trackId, String taskId, int value, boolean add) {
        if (!this.enabled || habbo == null || habbo.getClient() == null
                || value < 0 || trackId == null || taskId == null) {
            return;
        }
        RewardTrackDefinition track = this.tracks.get(trackId);
        RewardTrackTaskDefinition task = this.getTask(track, taskId);
        if (track == null || task == null || task.getLevels().isEmpty()) {
            return;
        }
        try {
            ProgressResult result = this.progressTaskWired(
                    habbo.getHabboInfo().getId(), track, task, value, add);
            if (result.changed) {
                habbo.getClient().sendResponse(new RewardTrackProgressMessageComposer(
                        trackId, taskId, result.progressCount, result.points));
            }
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Wired progress failed for track {}, task {}, user {}.",
                    trackId, taskId, habbo.getHabboInfo().getId(), e);
        }
    }

    /** Applies July Wired action 59 while preserving premium ownership. */
    public void resetWired(Habbo habbo, String trackId) {
        if (!this.enabled || habbo == null || habbo.getClient() == null
                || trackId == null || !this.tracks.containsKey(trackId)) {
            return;
        }
        int userId = habbo.getHabboInfo().getId();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM users_reward_track_tasks WHERE user_id = ? AND track_id = ?")) {
                    statement.setInt(1, userId);
                    statement.setString(2, trackId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM users_reward_track_claims WHERE user_id = ? AND track_id = ?")) {
                    statement.setInt(1, userId);
                    statement.setString(2, trackId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_reward_tracks SET points = 0, complete = 0, premium_complete = 0, "
                                + "updated_at = ? WHERE user_id = ? AND track_id = ?")) {
                    statement.setInt(1, Emulator.getIntUnixTimestamp());
                    statement.setInt(2, userId);
                    statement.setString(3, trackId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            this.sendFullState(habbo, false);
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Wired reset failed for track {}, user {}.",
                    trackId, userId, e);
        }
    }

    public boolean hasTrack(String trackId) {
        return trackId != null && this.tracks.containsKey(trackId);
    }

    public boolean hasTask(String trackId, String taskId) {
        return this.getTask(this.tracks.get(trackId), taskId) != null;
    }

    public Map<String, RewardTrackUserState> loadUserStates(Habbo habbo) {
        Map<String, RewardTrackUserState> states = new LinkedHashMap<>();
        if (habbo == null || !this.enabled) {
            return states;
        }

        for (RewardTrackDefinition track : this.getTracks()) {
            states.put(track.getId(), new RewardTrackUserState());
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT track_id, points, premium, complete, premium_complete FROM users_reward_tracks WHERE user_id = ?")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        RewardTrackUserState state = states.computeIfAbsent(set.getString("track_id"), key -> new RewardTrackUserState());
                        state.setPoints(set.getInt("points"));
                        state.setPremium(set.getBoolean("premium"));
                        state.setComplete(set.getBoolean("complete"));
                        state.setPremiumComplete(set.getBoolean("premium_complete"));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT track_id, task_id, progress_count FROM users_reward_track_tasks WHERE user_id = ?")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        RewardTrackUserState state = states.computeIfAbsent(set.getString("track_id"), key -> new RewardTrackUserState());
                        state.setTaskProgress(set.getString("task_id"), set.getInt("progress_count"));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT track_id, reward_id FROM users_reward_track_claims WHERE user_id = ?")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        RewardTrackUserState state = states.computeIfAbsent(set.getString("track_id"), key -> new RewardTrackUserState());
                        state.addClaimedReward(set.getString("reward_id"));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Failed to load user Reward Track state for user {}.", habbo.getHabboInfo().getId(), e);
        }

        return states;
    }

    private int claimRewardInternal(Habbo habbo, String trackId, String rewardId) {
        if (!this.enabled) {
            return CLAIM_DISABLED;
        }

        RewardTrackDefinition track = this.tracks.get(trackId);
        if (track == null) {
            return CLAIM_UNKNOWN_REWARD;
        }

        RewardTrackRewardDefinition reward = this.getReward(track, rewardId);
        if (reward == null) {
            return CLAIM_UNKNOWN_REWARD;
        }

        if (!this.isSupportedImmediateGrant(reward)) {
            return CLAIM_GRANT_FAILED;
        }

        int userId = habbo.getHabboInfo().getId();
        int resultCode;
        RewardGrantResult grantResult = RewardGrantResult.none();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                RewardTrackUserState state = this.loadUserState(connection, userId, trackId);
                if (state.isRewardClaimed(rewardId)) {
                    connection.rollback();
                    return CLAIM_ALREADY_CLAIMED;
                }

                if (reward.isPremium() && !state.isPremium()) {
                    connection.rollback();
                    return CLAIM_PREMIUM_REQUIRED;
                }

                if (state.getPoints() < reward.getRequiredPoints()) {
                    connection.rollback();
                    return CLAIM_NOT_ENOUGH_POINTS;
                }

                resultCode = this.insertClaim(connection, userId, trackId, rewardId) ? CLAIM_SUCCESS : CLAIM_ALREADY_CLAIMED;
                if (resultCode == CLAIM_SUCCESS) {
                    grantResult = this.grantReward(connection, habbo, reward);
                    if (!grantResult.success) {
                        connection.rollback();
                        return CLAIM_GRANT_FAILED;
                    }
                    this.updateCompletionFlags(connection, userId, track, state, reward);
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("RewardTrackManager -> Failed to claim reward {} on track {} for user {}.", rewardId, trackId, userId, e);
                resultCode = CLAIM_ERROR;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Failed to open claim transaction for reward {} on track {} for user {}.", rewardId, trackId, userId, e);
            return CLAIM_ERROR;
        }

        if (resultCode == CLAIM_SUCCESS) {
            this.applyRewardGrant(habbo, grantResult);
        }

        return resultCode;
    }

    private ProgressResult progressTask(int userId, RewardTrackDefinition track, RewardTrackTaskDefinition task, int amount) throws Exception {
        if (task.getLevels().isEmpty()) {
            return ProgressResult.unchanged();
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                RewardTrackUserState state = this.loadUserState(connection, userId, track.getId());
                if (state.isComplete() || (task.isPremium() && !state.isPremium())) {
                    connection.rollback();
                    return ProgressResult.unchanged();
                }

                int oldProgress = this.getTaskProgress(connection, userId, task.getId());
                int maxProgress = this.getMaxRequiredCount(task);
                if (oldProgress >= maxProgress) {
                    connection.rollback();
                    return ProgressResult.unchanged();
                }

                int newProgress = Math.min(maxProgress, oldProgress + amount);
                int oldCompletedLevel = this.getCompletedLevel(task, oldProgress, state.isPremium());
                int newCompletedLevel = this.getCompletedLevel(task, newProgress, state.isPremium());
                int pointsAwarded = this.getPointsAwarded(track, task, oldCompletedLevel, newCompletedLevel, state.isPremium());
                int newPoints = state.getPoints() + pointsAwarded;
                int now = Emulator.getIntUnixTimestamp();

                if (newProgress == oldProgress && pointsAwarded == 0) {
                    connection.rollback();
                    return ProgressResult.unchanged();
                }

                this.upsertUserTrack(connection, userId, track.getId(), newPoints, state.isPremium(), state.isComplete(), state.isPremiumComplete(), now);
                this.upsertTaskProgress(connection, userId, track.getId(), task.getId(), newProgress, newCompletedLevel, now);

                connection.commit();
                return new ProgressResult(true, newProgress, newPoints);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private ProgressResult progressTaskWired(int userId, RewardTrackDefinition track,
                                             RewardTrackTaskDefinition task, int value,
                                             boolean add) throws Exception {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                RewardTrackUserState state = this.loadUserState(connection, userId, track.getId());
                if (state.isComplete() || (task.isPremium() && !state.isPremium())) {
                    connection.rollback();
                    return ProgressResult.unchanged();
                }
                int oldProgress = this.getTaskProgress(connection, userId, task.getId());
                int maxProgress = this.getMaxRequiredCount(task);
                long requested = add ? (long) oldProgress + value : value;
                int newProgress = (int) Math.max(0, Math.min(maxProgress, requested));
                int oldLevel = this.getCompletedLevel(task, oldProgress, state.isPremium());
                int newLevel = this.getCompletedLevel(task, newProgress, state.isPremium());
                int oldTaskPoints = this.getPointsAwarded(track, task, 0, oldLevel, state.isPremium());
                int newTaskPoints = this.getPointsAwarded(track, task, 0, newLevel, state.isPremium());
                int newPoints = Math.max(0, state.getPoints() + newTaskPoints - oldTaskPoints);
                if (newProgress == oldProgress && newPoints == state.getPoints()) {
                    connection.rollback();
                    return ProgressResult.unchanged();
                }
                int now = Emulator.getIntUnixTimestamp();
                this.upsertUserTrack(connection, userId, track.getId(), newPoints, state.isPremium(),
                        state.isComplete(), state.isPremiumComplete(), now);
                this.setTaskProgress(connection, userId, track.getId(), task.getId(),
                        newProgress, newLevel, now);
                connection.commit();
                return new ProgressResult(true, newProgress, newPoints);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private RewardTrackUserState loadUserState(Connection connection, int userId, String trackId) throws SQLException {
        RewardTrackUserState state = new RewardTrackUserState();

        try (PreparedStatement statement = connection.prepareStatement("SELECT points, premium, complete, premium_complete FROM users_reward_tracks WHERE user_id = ? AND track_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setString(2, trackId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    state.setPoints(set.getInt("points"));
                    state.setPremium(set.getBoolean("premium"));
                    state.setComplete(set.getBoolean("complete"));
                    state.setPremiumComplete(set.getBoolean("premium_complete"));
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT reward_id FROM users_reward_track_claims WHERE user_id = ? AND track_id = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, trackId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    state.addClaimedReward(set.getString("reward_id"));
                }
            }
        }

        return state;
    }

    private int getTaskProgress(Connection connection, int userId, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT progress_count FROM users_reward_track_tasks WHERE user_id = ? AND task_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setString(2, taskId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("progress_count");
                }
            }
        }

        return 0;
    }

    private void upsertUserTrack(Connection connection, int userId, String trackId, int points, boolean premium, boolean complete, boolean premiumComplete, int now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_reward_tracks (user_id, track_id, points, premium, complete, premium_complete, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE points = VALUES(points), premium = VALUES(premium), complete = VALUES(complete), premium_complete = VALUES(premium_complete), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, userId);
            statement.setString(2, trackId);
            statement.setInt(3, points);
            statement.setBoolean(4, premium);
            statement.setBoolean(5, complete);
            statement.setBoolean(6, premiumComplete);
            statement.setInt(7, now);
            statement.setInt(8, now);
            statement.executeUpdate();
        }
    }

    private void upsertTaskProgress(Connection connection, int userId, String trackId, String taskId, int progress, int completedLevel, int now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_reward_track_tasks (user_id, track_id, task_id, progress_count, completed_level, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE progress_count = GREATEST(progress_count, VALUES(progress_count)), completed_level = GREATEST(completed_level, VALUES(completed_level)), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, userId);
            statement.setString(2, trackId);
            statement.setString(3, taskId);
            statement.setInt(4, progress);
            statement.setInt(5, completedLevel);
            statement.setInt(6, now);
            statement.setInt(7, now);
            statement.executeUpdate();
        }
    }

    private void setTaskProgress(Connection connection, int userId, String trackId, String taskId,
                                 int progress, int completedLevel, int now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users_reward_track_tasks "
                        + "(user_id, track_id, task_id, progress_count, completed_level, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                        + "progress_count = VALUES(progress_count), completed_level = VALUES(completed_level), "
                        + "updated_at = VALUES(updated_at)")) {
            statement.setInt(1, userId);
            statement.setString(2, trackId);
            statement.setString(3, taskId);
            statement.setInt(4, progress);
            statement.setInt(5, completedLevel);
            statement.setInt(6, now);
            statement.setInt(7, now);
            statement.executeUpdate();
        }
    }

    private PremiumPurchaseResult purchasePremiumInternal(Habbo habbo, String trackId) {
        if (!this.enabled) {
            return PremiumPurchaseResult.failed(PREMIUM_DISABLED);
        }

        RewardTrackDefinition track = this.tracks.get(trackId);
        if (track == null) {
            return PremiumPurchaseResult.failed(PREMIUM_UNKNOWN_TRACK);
        }

        if (!track.isPremiumEnabled()) {
            return PremiumPurchaseResult.failed(PREMIUM_NOT_CONFIGURED);
        }

        if (track.getPremiumCostCredits() < 0 || track.getPremiumCostPoints() < 0 || track.getPremiumCostPointsType() < 0 || track.getTaskPointsBoost() < 1 || track.getInstantPoints() < 0) {
            return PremiumPurchaseResult.failed(PREMIUM_INVALID_CONFIG);
        }

        int userId = habbo.getHabboInfo().getId();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                RewardTrackUserState state = this.loadUserState(connection, userId, trackId);
                if (state.isPremium()) {
                    connection.rollback();
                    return PremiumPurchaseResult.failed(PREMIUM_ALREADY_OWNED);
                }

                UserCreditsEvent creditsEvent = null;
                UserPointsEvent pointsEvent = null;
                if (track.getPremiumCostCredits() > 0) {
                    creditsEvent = new UserCreditsEvent(habbo, -track.getPremiumCostCredits());
                    if (Emulator.getPluginManager().fireEvent(creditsEvent).isCancelled()) {
                        connection.rollback();
                        return PremiumPurchaseResult.failed(PREMIUM_NOT_ELIGIBLE);
                    }
                }
                if (track.getPremiumCostPoints() > 0) {
                    pointsEvent = new UserPointsEvent(habbo, -track.getPremiumCostPoints(), track.getPremiumCostPointsType());
                    if (Emulator.getPluginManager().fireEvent(pointsEvent).isCancelled()) {
                        connection.rollback();
                        return PremiumPurchaseResult.failed(PREMIUM_NOT_ELIGIBLE);
                    }
                }

                int creditsDebit = creditsEvent != null ? -creditsEvent.credits : 0;
                int pointsDebit = pointsEvent != null ? -pointsEvent.points : 0;
                int pointsType = pointsEvent != null ? pointsEvent.type : track.getPremiumCostPointsType();
                if (creditsDebit < 0 || pointsDebit < 0 || pointsType < 0) {
                    connection.rollback();
                    return PremiumPurchaseResult.failed(PREMIUM_INVALID_CONFIG);
                }
                if (creditsDebit > 0 && !this.deductCredits(connection, userId, creditsDebit)) {
                    connection.rollback();
                    return PremiumPurchaseResult.failed(PREMIUM_NOT_ENOUGH_CREDITS);
                }
                if (pointsDebit > 0 && !this.deductCurrency(connection, userId, pointsType, pointsDebit)) {
                    connection.rollback();
                    return PremiumPurchaseResult.failed(PREMIUM_NOT_ENOUGH_POINTS);
                }

                int now = Emulator.getIntUnixTimestamp();
                int newPoints = state.getPoints() + track.getInstantPoints();
                boolean complete = this.isFreeTrackComplete(track, state, null);
                boolean premiumComplete = this.isPremiumTrackComplete(track, state, null);
                this.upsertUserTrack(connection, userId, trackId, newPoints, true, complete, premiumComplete, now);

                connection.commit();
                return PremiumPurchaseResult.success(newPoints, creditsDebit, pointsDebit, pointsType);
            } catch (Exception e) {
                connection.rollback();
                LOGGER.error("RewardTrackManager -> Failed to purchase premium for track {} and user {}.", trackId, userId, e);
                return PremiumPurchaseResult.failed(PREMIUM_ERROR);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Failed to open premium purchase transaction for track {} and user {}.", trackId, userId, e);
            return PremiumPurchaseResult.failed(PREMIUM_ERROR);
        }
    }

    private boolean insertClaim(Connection connection, int userId, String trackId, String rewardId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO users_reward_track_claims (user_id, track_id, reward_id, claimed_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, userId);
            statement.setString(2, trackId);
            statement.setString(3, rewardId);
            statement.setInt(4, Emulator.getIntUnixTimestamp());
            return statement.executeUpdate() > 0;
        }
    }

    private void updateCompletionFlags(Connection connection, int userId, RewardTrackDefinition track, RewardTrackUserState state, RewardTrackRewardDefinition newlyClaimedReward) throws SQLException {
        boolean complete = this.isFreeTrackComplete(track, state, newlyClaimedReward);
        boolean premiumComplete = this.isPremiumTrackComplete(track, state, newlyClaimedReward);

        this.upsertUserTrack(connection, userId, track.getId(), state.getPoints(), state.isPremium(), complete, premiumComplete, Emulator.getIntUnixTimestamp());
    }

    private boolean isFreeTrackComplete(RewardTrackDefinition track, RewardTrackUserState state, RewardTrackRewardDefinition newlyClaimedReward) {
        for (RewardTrackRewardDefinition reward : track.getRewards()) {
            if (!reward.isPremium() && !this.isRewardClaimed(state, reward, newlyClaimedReward)) {
                return false;
            }
        }

        return true;
    }

    private boolean isPremiumTrackComplete(RewardTrackDefinition track, RewardTrackUserState state, RewardTrackRewardDefinition newlyClaimedReward) {
        if (!track.isPremiumEnabled()) {
            return true;
        }

        for (RewardTrackRewardDefinition reward : track.getRewards()) {
            if (!this.isRewardClaimed(state, reward, newlyClaimedReward)) {
                return false;
            }
        }

        return true;
    }

    private boolean isRewardClaimed(RewardTrackUserState state, RewardTrackRewardDefinition reward, RewardTrackRewardDefinition newlyClaimedReward) {
        return state.isRewardClaimed(reward.getId()) || (newlyClaimedReward != null && reward.getId().equals(newlyClaimedReward.getId()));
    }

    private RewardTrackRewardDefinition getReward(RewardTrackDefinition track, String rewardId) {
        for (RewardTrackRewardDefinition reward : track.getRewards()) {
            if (reward.getId().equals(rewardId)) {
                return reward;
            }
        }

        return null;
    }

    private RewardTrackTaskDefinition getTask(RewardTrackDefinition track, String taskId) {
        if (track == null || taskId == null) {
            return null;
        }
        for (RewardTrackTaskDefinition task : track.getTasks()) {
            if (taskId.equals(task.getId())) {
                return task;
            }
        }
        return null;
    }

    private boolean matchesTask(RewardTrackTaskDefinition task, String actionType, String parameter) {
        if (!task.getActionType().equals(actionType)) {
            return false;
        }

        String taskParameter = task.getParameter() != null ? task.getParameter() : "";
        return taskParameter.isEmpty() || taskParameter.equals(parameter);
    }

    private int getMaxRequiredCount(RewardTrackTaskDefinition task) {
        int max = 0;
        for (RewardTrackTaskLevelDefinition level : task.getLevels()) {
            max = Math.max(max, level.getRequiredCount());
        }

        return max;
    }

    private int getCompletedLevel(RewardTrackTaskDefinition task, int progressCount, boolean premium) {
        int completed = 0;
        int index = 0;
        for (RewardTrackTaskLevelDefinition level : task.getLevels()) {
            index++;
            if (level.isPremium() && !premium) {
                continue;
            }
            if (progressCount >= level.getRequiredCount()) {
                completed = index;
            }
        }

        return completed;
    }

    private int getPointsAwarded(RewardTrackDefinition track, RewardTrackTaskDefinition task, int oldCompletedLevel, int newCompletedLevel, boolean premium) {
        if (newCompletedLevel <= oldCompletedLevel) {
            return 0;
        }

        int points = 0;
        int index = 0;
        for (RewardTrackTaskLevelDefinition level : task.getLevels()) {
            index++;
            if (index <= oldCompletedLevel || index > newCompletedLevel) {
                continue;
            }
            if (level.isPremium() && !premium) {
                continue;
            }
            points += level.getPointsReward();
        }

        if (premium && track.getTaskPointsBoost() > 1 && points > 0) {
            points = (int) Math.ceil(points * track.getTaskPointsBoost());
        }

        return points;
    }

    private boolean isSupportedImmediateGrant(RewardTrackRewardDefinition reward) {
        String type = reward.getRewardType();
        return "credits".equalsIgnoreCase(type)
                || "activity_points".equalsIgnoreCase(type)
                || "currency".equalsIgnoreCase(type)
                || "pixels".equalsIgnoreCase(type)
                || "badge".equalsIgnoreCase(type)
                || "catalog_item".equalsIgnoreCase(type)
                || "catalog.item".equalsIgnoreCase(type)
                || "habbicon".equalsIgnoreCase(type);
    }

    private RewardGrantResult grantReward(Connection connection, Habbo habbo, RewardTrackRewardDefinition reward) throws SQLException {
        String type = reward.getRewardType();
        int userId = habbo.getHabboInfo().getId();
        if ("credits".equalsIgnoreCase(type)) {
            UserCreditsEvent event = new UserCreditsEvent(habbo, reward.getRewardAmount());
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return RewardGrantResult.failed();
            }
            if (event.credits != 0 && !this.addCredits(connection, userId, event.credits)) {
                return RewardGrantResult.failed();
            }
            return RewardGrantResult.credits(event.credits);
        }

        if ("pixels".equalsIgnoreCase(type)) {
            UserPointsEvent event = new UserPointsEvent(habbo, reward.getRewardAmount(), 0);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return RewardGrantResult.failed();
            }
            if (event.points != 0 && !this.addCurrency(connection, userId, event.type, event.points)) {
                return RewardGrantResult.failed();
            }
            return RewardGrantResult.pixels(event.type, event.points);
        }

        if ("activity_points".equalsIgnoreCase(type) || "currency".equalsIgnoreCase(type)) {
            int currencyType = 0;
            try {
                if (reward.getExtraParams() != null && !reward.getExtraParams().isEmpty()) {
                    currencyType = Integer.parseInt(reward.getExtraParams());
                }
            } catch (NumberFormatException e) {
                LOGGER.error("RewardTrackManager -> Invalid currency type '{}' for reward {}.", reward.getExtraParams(), reward.getId(), e);
                return RewardGrantResult.failed();
            }

            UserPointsEvent event = new UserPointsEvent(habbo, reward.getRewardAmount(), currencyType);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return RewardGrantResult.failed();
            }
            if (event.points != 0 && !this.addCurrency(connection, userId, event.type, event.points)) {
                return RewardGrantResult.failed();
            }
            return RewardGrantResult.points(event.type, event.points);
        }

        if ("badge".equalsIgnoreCase(type)) {
            String badgeCode = this.getRewardValue(reward);
            if (badgeCode.isEmpty()) {
                LOGGER.error("RewardTrackManager -> Missing badge code for reward {}.", reward.getId());
                return RewardGrantResult.failed();
            }

            return RewardGrantResult.badge(badgeCode);
        }

        if ("catalog_item".equalsIgnoreCase(type) || "catalog.item".equalsIgnoreCase(type)) {
            CatalogGrant catalogGrant = this.parseCatalogGrant(reward);
            if (catalogGrant == null) {
                return RewardGrantResult.failed();
            }

            CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(catalogGrant.itemId);
            if (item == null) {
                LOGGER.error("RewardTrackManager -> Unknown catalog item id '{}' for reward {}.", catalogGrant.itemId, reward.getId());
                return RewardGrantResult.failed();
            }

            return RewardGrantResult.catalogItem(catalogGrant.itemId, catalogGrant.amount, catalogGrant.extraData);
        }

        if ("habbicon".equalsIgnoreCase(type)) {
            int habbiconId = this.parseIntegerRewardValue(reward, "Habbicon");
            if (habbiconId <= 0) {
                return RewardGrantResult.failed();
            }

            HabbiconManager manager = Emulator.getGameEnvironment().getHabbiconManager();
            if (manager == null || !manager.grantHabbicon(connection, userId, habbiconId)) {
                LOGGER.error("RewardTrackManager -> Failed to grant Habbicon '{}' for reward {}.", habbiconId, reward.getId());
                return RewardGrantResult.failed();
            }

            return RewardGrantResult.habbicon(habbiconId);
        }

        return RewardGrantResult.failed();
    }

    private String getRewardValue(RewardTrackRewardDefinition reward) {
        if (reward.getExtraParams() != null && !reward.getExtraParams().trim().isEmpty()) {
            return reward.getExtraParams().trim();
        }

        return reward.getId() == null ? "" : reward.getId().trim();
    }

    private int parseIntegerRewardValue(RewardTrackRewardDefinition reward, String label) {
        String value = this.getRewardValue(reward);
        if (value.isEmpty()) {
            LOGGER.error("RewardTrackManager -> Missing {} id for reward {}.", label, reward.getId());
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.error("RewardTrackManager -> Invalid {} id '{}' for reward {}.", label, value, reward.getId(), e);
            return 0;
        }
    }

    private CatalogGrant parseCatalogGrant(RewardTrackRewardDefinition reward) {
        String value = this.getRewardValue(reward);
        if (value.isEmpty()) {
            LOGGER.error("RewardTrackManager -> Missing catalog item id for reward {}.", reward.getId());
            return null;
        }

        String[] parts = value.split("\\|", 2);
        try {
            int itemId = Integer.parseInt(parts[0].trim());
            int amount = Math.max(1, reward.getRewardAmount());
            String extraData = parts.length > 1 ? parts[1] : "";
            return new CatalogGrant(itemId, amount, extraData);
        } catch (NumberFormatException e) {
            LOGGER.error("RewardTrackManager -> Invalid catalog item id '{}' for reward {}.", value, reward.getId(), e);
            return null;
        }
    }

    private boolean addCredits(Connection connection, int userId, int credits) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET credits = credits + ? WHERE id = ? LIMIT 1")) {
            statement.setInt(1, credits);
            statement.setInt(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean addCurrency(Connection connection, int userId, int type, int amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)")) {
            statement.setInt(1, userId);
            statement.setInt(2, type);
            statement.setInt(3, amount);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean deductCredits(Connection connection, int userId, int credits) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ? LIMIT 1")) {
            statement.setInt(1, credits);
            statement.setInt(2, userId);
            statement.setInt(3, credits);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean deductCurrency(Connection connection, int userId, int type, int amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE users_currency SET amount = amount - ? WHERE user_id = ? AND type = ? AND amount >= ? LIMIT 1")) {
            statement.setInt(1, amount);
            statement.setInt(2, userId);
            statement.setInt(3, type);
            statement.setInt(4, amount);
            return statement.executeUpdate() > 0;
        }
    }

    private void applyPremiumPurchase(Habbo habbo, PremiumPurchaseResult result) {
        if (habbo == null || result == null || result.resultCode != PREMIUM_SUCCESS) {
            return;
        }

        try {
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
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Failed to sync premium purchase balances for user {}.", habbo.getHabboInfo().getId(), e);
        }
    }

    private void applyRewardGrant(Habbo habbo, RewardGrantResult grantResult) {
        if (habbo == null || grantResult == null || !grantResult.success) {
            return;
        }

        try {
            if (grantResult.kind == RewardGrantResult.KIND_BADGE) {
                if (habbo.getClient() != null && grantResult.rewardValue != null && !grantResult.rewardValue.isEmpty()) {
                    habbo.addBadge(grantResult.rewardValue);
                }
                return;
            }

            if (grantResult.kind == RewardGrantResult.KIND_CATALOG_ITEM) {
                if (habbo.getClient() != null) {
                    CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(grantResult.catalogItemId);
                    if (item != null) {
                        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(item.getPageId());
                        Emulator.getGameEnvironment().getCatalogManager().purchaseItem(page, item, habbo, Math.max(1, grantResult.amount), grantResult.rewardValue == null ? "" : grantResult.rewardValue, true);
                    }
                }
                return;
            }

            if (grantResult.kind == RewardGrantResult.KIND_HABBICON) {
                HabbiconManager manager = Emulator.getGameEnvironment().getHabbiconManager();
                if (manager != null) {
                    manager.sendHabbiconState(habbo, grantResult.habbiconId);
                }
                return;
            }

            if (grantResult.amount == 0) {
                return;
            }

            if (grantResult.kind == RewardGrantResult.KIND_CREDITS) {
                habbo.getHabboInfo().addCredits(grantResult.amount);
                if (habbo.getClient() != null) {
                    habbo.getClient().sendResponse(new CreditBalanceMessageComposer(habbo));
                }
                return;
            }

            habbo.getHabboInfo().addCurrencyAmount(grantResult.currencyType, grantResult.amount);
            if (habbo.getClient() == null) {
                return;
            }
            if (grantResult.kind == RewardGrantResult.KIND_PIXELS) {
                habbo.getClient().sendResponse(new ActivityPointsMessageComposer(habbo));
            } else {
                habbo.getClient().sendResponse(new HabboActivityPointNotificationMessageComposer(habbo.getHabboInfo().getCurrencyAmount(grantResult.currencyType), grantResult.amount, grantResult.currencyType));
            }
        } catch (Exception e) {
            LOGGER.error("RewardTrackManager -> Failed to sync granted Reward Track balance for user {}.", habbo.getHabboInfo().getId(), e);
        }
    }

    private static class RewardGrantResult {
        private static final int KIND_NONE = 0;
        private static final int KIND_CREDITS = 1;
        private static final int KIND_PIXELS = 2;
        private static final int KIND_POINTS = 3;
        private static final int KIND_BADGE = 4;
        private static final int KIND_CATALOG_ITEM = 5;
        private static final int KIND_HABBICON = 6;

        private final boolean success;
        private final int kind;
        private final int currencyType;
        private final int amount;
        private final String rewardValue;
        private final int catalogItemId;
        private final int habbiconId;

        private RewardGrantResult(boolean success, int kind, int currencyType, int amount) {
            this(success, kind, currencyType, amount, null, 0, 0);
        }

        private RewardGrantResult(boolean success, int kind, int currencyType, int amount, String rewardValue, int catalogItemId) {
            this(success, kind, currencyType, amount, rewardValue, catalogItemId, 0);
        }

        private RewardGrantResult(boolean success, int kind, int currencyType, int amount, String rewardValue, int catalogItemId, int habbiconId) {
            this.success = success;
            this.kind = kind;
            this.currencyType = currencyType;
            this.amount = amount;
            this.rewardValue = rewardValue;
            this.catalogItemId = catalogItemId;
            this.habbiconId = habbiconId;
        }

        private static RewardGrantResult none() {
            return new RewardGrantResult(true, KIND_NONE, 0, 0);
        }

        private static RewardGrantResult failed() {
            return new RewardGrantResult(false, KIND_NONE, 0, 0);
        }

        private static RewardGrantResult credits(int amount) {
            return new RewardGrantResult(true, KIND_CREDITS, 0, amount);
        }

        private static RewardGrantResult pixels(int currencyType, int amount) {
            return new RewardGrantResult(true, KIND_PIXELS, currencyType, amount);
        }

        private static RewardGrantResult points(int currencyType, int amount) {
            return new RewardGrantResult(true, KIND_POINTS, currencyType, amount);
        }

        private static RewardGrantResult badge(String badgeCode) {
            return new RewardGrantResult(true, KIND_BADGE, 0, 0, badgeCode, 0);
        }

        private static RewardGrantResult catalogItem(int itemId, int amount, String extraData) {
            return new RewardGrantResult(true, KIND_CATALOG_ITEM, 0, amount, extraData, itemId);
        }

        private static RewardGrantResult habbicon(int habbiconId) {
            return new RewardGrantResult(true, KIND_HABBICON, 0, 0, null, 0, habbiconId);
        }
    }

    private static class CatalogGrant {
        private final int itemId;
        private final int amount;
        private final String extraData;

        private CatalogGrant(int itemId, int amount, String extraData) {
            this.itemId = itemId;
            this.amount = amount;
            this.extraData = extraData;
        }
    }

    private static class PremiumPurchaseResult {
        private final int resultCode;
        private final int points;
        private final int creditsDebited;
        private final int pointsDebited;
        private final int pointsType;

        private PremiumPurchaseResult(int resultCode, int points, int creditsDebited, int pointsDebited, int pointsType) {
            this.resultCode = resultCode;
            this.points = points;
            this.creditsDebited = creditsDebited;
            this.pointsDebited = pointsDebited;
            this.pointsType = pointsType;
        }

        private static PremiumPurchaseResult success(int points, int creditsDebited, int pointsDebited, int pointsType) {
            return new PremiumPurchaseResult(PREMIUM_SUCCESS, points, creditsDebited, pointsDebited, pointsType);
        }

        private static PremiumPurchaseResult failed(int resultCode) {
            return new PremiumPurchaseResult(resultCode, 0, 0, 0, DEFAULT_PREMIUM_POINTS_TYPE);
        }
    }

    private void loadTracks(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, theme, sort_order, premium_enabled, task_points_boost, instant_points, premium_cost_points, premium_cost_points_type, premium_cost_credits FROM reward_tracks WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RewardTrackDefinition track = new RewardTrackDefinition(
                            set.getString("id"),
                            set.getString("theme"),
                            set.getInt("sort_order"),
                            set.getBoolean("premium_enabled"),
                            set.getDouble("task_points_boost"),
                            set.getInt("instant_points"),
                            set.getInt("premium_cost_points"),
                            set.getInt("premium_cost_points_type"),
                            set.getInt("premium_cost_credits"));
                    this.tracks.put(track.getId(), track);
                }
            }
        }
    }

    private void loadTasks(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, track_id, action_type, parameter, premium, sort_order FROM reward_track_tasks ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RewardTrackDefinition track = this.tracks.get(set.getString("track_id"));
                    if (track == null) {
                        continue;
                    }

                    track.addTask(new RewardTrackTaskDefinition(
                            set.getString("id"),
                            set.getString("action_type"),
                            set.getString("parameter"),
                            set.getBoolean("premium"),
                            set.getInt("sort_order")));
                }
            }
        }
    }

    private void loadTaskLevels(Connection connection) throws Exception {
        Map<String, RewardTrackTaskDefinition> tasks = new LinkedHashMap<>();
        for (RewardTrackDefinition track : this.tracks.values()) {
            for (RewardTrackTaskDefinition task : track.getTasks()) {
                tasks.put(task.getId(), task);
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT task_id, required_count, points_reward, premium FROM reward_track_task_levels ORDER BY level ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RewardTrackTaskDefinition task = tasks.get(set.getString("task_id"));
                    if (task == null) {
                        continue;
                    }

                    task.addLevel(new RewardTrackTaskLevelDefinition(
                            set.getInt("required_count"),
                            set.getInt("points_reward"),
                            set.getBoolean("premium")));
                }
            }
        }
    }

    private void loadRewards(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, track_id, required_points, product_item_type_id, reward_type, extra_params, reward_amount, premium, sort_order FROM reward_track_rewards ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RewardTrackDefinition track = this.tracks.get(set.getString("track_id"));
                    if (track == null) {
                        continue;
                    }

                    track.addReward(new RewardTrackRewardDefinition(
                            set.getString("id"),
                            set.getInt("required_points"),
                            set.getInt("product_item_type_id"),
                            set.getString("reward_type"),
                            set.getString("extra_params"),
                            set.getInt("reward_amount"),
                            set.getBoolean("premium"),
                            set.getInt("sort_order")));
                }
            }
        }

        for (RewardTrackDefinition track : this.tracks.values()) {
            track.getRewards().stream().sorted(Comparator.comparingInt(RewardTrackRewardDefinition::getSortOrder));
        }
    }

    private static class ProgressResult {
        private final boolean changed;
        private final int progressCount;
        private final int points;

        private ProgressResult(boolean changed, int progressCount, int points) {
            this.changed = changed;
            this.progressCount = progressCount;
            this.points = points;
        }

        private static ProgressResult unchanged() {
            return new ProgressResult(false, 0, 0);
        }
    }
}
