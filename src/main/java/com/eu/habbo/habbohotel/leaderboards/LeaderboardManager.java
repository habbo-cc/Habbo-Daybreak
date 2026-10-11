package com.eu.habbo.habbohotel.leaderboards;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.gamehall.GamehallGameType;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class LeaderboardManager {
    public static final String ALL_GAMES = "ALL";
    public static final String BATTLEBALL = "BATTLEBALL";
    public static final String SNOWSTORM = "SNOWSTORM";
    public static final String POINTS = "POINTS";
    public static final String WINS = "WINS";
    public static final String LOSSES = "LOSSES";
    public static final String DRAWS = "DRAWS";
    public static final String GAMES_PLAYED = "GAMES_PLAYED";

    public record ScoreResult(int userId, int points, int outcome) {}
    public record BattleBallResult(int userId, int points, int outcome) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderboardManager.class);
    private static final int POINTS_PER_WIN = 1;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String RANK_ORDER =
            "s.value DESC, s.last_result_at DESC, LOWER(u.username) ASC, s.user_id ASC";

    private final Map<CacheKey, LeaderboardData> cache = new ConcurrentHashMap<>();

    public void dispose() {
        this.cache.clear();
    }

    public LeaderboardData getLeaderboard(int roomId, int requestedLeaderboardId, String requestedPeriod,
                                          int offset, int limit, Habbo habbo) {
        List<LeaderboardDefinition> available = this.loadDefinitions(roomId);
        if (available.isEmpty()) return null;

        LeaderboardDefinition selected = available.get(0);
        for (LeaderboardDefinition definition : available) {
            if (definition.id() == requestedLeaderboardId) {
                selected = definition;
                break;
            }
        }

        LeaderboardPeriod period = LeaderboardPeriod.fromString(requestedPeriod);
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        long periodStart = period.getCurrentPeriodStart();
        CacheKey key = new CacheKey(selected.gameType(), selected.metric(), period, periodStart, safeOffset, safeLimit);

        LeaderboardData cached = this.cache.get(key);
        LeaderboardData page = cached;
        if (page == null) {
            page = this.loadPage(selected, available, period, periodStart, safeOffset, safeLimit);
            this.cache.put(key, page);
        }

        LeaderboardRow ownRow = this.loadOwnRow(selected, period, periodStart, habbo);
        return new LeaderboardData(selected, available, period, safeOffset, safeLimit,
                page.totalRows(), page.rows(), ownRow);
    }

    public void recordWin(GamehallGameType gameType, int roomId, int stationId, Habbo winner, Habbo loser) {
        if (gameType == null || !isValidHabbo(winner) || !isValidHabbo(loser)) return;
        int winnerId = winner.getHabboInfo().getId();
        int loserId = loser.getHabboInfo().getId();
        if (winnerId == loserId) return;

        long now = System.currentTimeMillis() / 1000L;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                this.insertGamehallResult(connection, gameType.name(), roomId, stationId, winnerId, loserId, now);
                this.appendPlayerResult(connection, gameType.name(), winnerId, POINTS_PER_WIN, 1, now);
                this.appendPlayerResult(connection, gameType.name(), loserId, 0, -1, now);
                this.appendPlayerResult(connection, ALL_GAMES, winnerId, POINTS_PER_WIN, 1, now);
                this.appendPlayerResult(connection, ALL_GAMES, loserId, 0, -1, now);
                connection.commit();
                this.cache.clear();
            } catch (SQLException exception) {
                rollback(connection);
                LOGGER.error("Caught SQL exception while recording Games Hall result", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while recording Games Hall result", exception);
        }
    }

    public void recordBattleBallResults(List<BattleBallResult> results) {
        if (results == null || results.isEmpty()) return;
        List<ScoreResult> valid = results.stream()
                .filter(result -> result != null && result.userId() > 0 && result.points() >= 0)
                .map(result -> new ScoreResult(result.userId(), result.points(), result.outcome()))
                .toList();
        if (valid.isEmpty()) return;

        long now = System.currentTimeMillis() / 1000L;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                this.appendScoreResults(connection, BATTLEBALL, valid, true, now);
                connection.commit();
                this.cache.clear();
            } catch (SQLException exception) {
                rollback(connection);
                LOGGER.error("Caught SQL exception while recording BattleBall results", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while recording BattleBall results", exception);
        }
    }

    public void appendSnowStormResults(Connection connection, List<ScoreResult> results, long now) throws SQLException {
        this.appendScoreResults(connection, SNOWSTORM, results, false, now);
    }

    public void snowStormResultsCommitted() {
        this.cache.clear();
    }

    private void appendScoreResults(Connection connection, String gameType, List<ScoreResult> results,
                                    boolean includeAllGames, long now) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(results, "results");
        for (ScoreResult result : results) {
            if (result == null || result.userId() <= 0 || result.points() < 0
                    || result.outcome() < -1 || result.outcome() > 1) {
                throw new SQLException("Invalid " + gameType + " leaderboard result");
            }
            this.appendPlayerResult(connection, gameType, result.userId(), result.points(), result.outcome(), now);
            if (includeAllGames) {
                this.appendPlayerResult(connection, ALL_GAMES, result.userId(),
                        result.outcome() > 0 ? POINTS_PER_WIN : 0, result.outcome(), now);
            }
        }
    }

    private void appendPlayerResult(Connection connection, String gameType, int userId,
                                    int points, int outcome, long now) throws SQLException {
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            long periodStart = period.getCurrentPeriodStart();
            this.incrementMetric(connection, gameType, POINTS, period, periodStart, userId, points, now);
            this.incrementMetric(connection, gameType, GAMES_PLAYED, period, periodStart, userId, 1, now);
            if (outcome > 0) {
                this.incrementMetric(connection, gameType, WINS, period, periodStart, userId, 1, now);
            } else if (outcome < 0) {
                this.incrementMetric(connection, gameType, LOSSES, period, periodStart, userId, 1, now);
            } else {
                this.incrementMetric(connection, gameType, DRAWS, period, periodStart, userId, 1, now);
            }
        }
    }

    private void incrementMetric(Connection connection, String gameType, String metric,
                                 LeaderboardPeriod period, long periodStart, int userId,
                                 int amount, long now) throws SQLException {
        String sql = "INSERT INTO leaderboard_stats "
                + "(game_type, metric, period_type, period_start, user_id, value, last_result_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "value = value + VALUES(value), "
                + "last_result_at = GREATEST(last_result_at, VALUES(last_result_at))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, gameType);
            statement.setString(2, metric);
            statement.setString(3, period.name());
            statement.setLong(4, periodStart);
            statement.setInt(5, userId);
            statement.setInt(6, amount);
            statement.setLong(7, now);
            statement.executeUpdate();
        }
    }

    private List<LeaderboardDefinition> loadDefinitions(int roomId) {
        List<LeaderboardDefinition> definitions = new ArrayList<>();
        String sql = "SELECT id, room_id, game_type, metric, title_localization_code, "
                + "description_localization_code, menu_localization_code, sort_order "
                + "FROM leaderboards WHERE room_id = ? AND enabled = 1 ORDER BY sort_order ASC, id ASC";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    definitions.add(new LeaderboardDefinition(
                            set.getInt("id"), set.getInt("room_id"), set.getString("game_type"),
                            set.getString("metric"), set.getString("title_localization_code"),
                            set.getString("description_localization_code"), set.getString("menu_localization_code"),
                            set.getInt("sort_order")));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while loading leaderboard definitions for room {}", roomId, exception);
        }
        return definitions;
    }

    private LeaderboardData loadPage(LeaderboardDefinition selected, List<LeaderboardDefinition> available,
                                     LeaderboardPeriod period, long periodStart, int offset, int limit) {
        List<LeaderboardRow> rows = new ArrayList<>();
        int totalRows = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            totalRows = this.countRows(connection, selected, period, periodStart);
            String sql = "SELECT s.user_id, u.username, u.look, u.gender, s.value, s.last_result_at "
                    + "FROM leaderboard_stats s INNER JOIN users u ON u.id = s.user_id "
                    + "WHERE s.game_type = ? AND s.metric = ? AND s.period_type = ? "
                    + "AND s.period_start = ? AND s.value > 0 ORDER BY " + RANK_ORDER + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindBoard(statement, selected, period, periodStart);
                statement.setInt(5, limit);
                statement.setInt(6, offset);
                try (ResultSet set = statement.executeQuery()) {
                    int rank = offset + 1;
                    while (set.next()) rows.add(rowFromSet(set, rank++, false));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while loading leaderboard {}", selected.id(), exception);
        }
        return new LeaderboardData(selected, available, period, offset, limit, totalRows, rows, null);
    }

    private LeaderboardRow loadOwnRow(LeaderboardDefinition selected, LeaderboardPeriod period,
                                      long periodStart, Habbo habbo) {
        if (!isValidHabbo(habbo)) return null;
        int userId = habbo.getHabboInfo().getId();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            String sql = "SELECT s.user_id, u.username, u.look, u.gender, s.value, s.last_result_at "
                    + "FROM leaderboard_stats s INNER JOIN users u ON u.id = s.user_id "
                    + "WHERE s.game_type = ? AND s.metric = ? AND s.period_type = ? "
                    + "AND s.period_start = ? AND s.user_id = ? AND s.value > 0 LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindBoard(statement, selected, period, periodStart);
                statement.setInt(5, userId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) return rowFromSet(set,
                            this.getRank(connection, selected, period, periodStart, set.getInt("value"),
                                    set.getLong("last_result_at"), set.getString("username"), userId), true);
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while loading own leaderboard row", exception);
        }
        return null;
    }

    private int countRows(Connection connection, LeaderboardDefinition selected,
                          LeaderboardPeriod period, long periodStart) throws SQLException {
        String sql = "SELECT COUNT(*) FROM leaderboard_stats WHERE game_type = ? AND metric = ? "
                + "AND period_type = ? AND period_start = ? AND value > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindBoard(statement, selected, period, periodStart);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    private int getRank(Connection connection, LeaderboardDefinition selected, LeaderboardPeriod period,
                        long periodStart, int value, long lastResultAt, String username, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) + 1 FROM leaderboard_stats s INNER JOIN users u ON u.id = s.user_id "
                + "WHERE s.game_type = ? AND s.metric = ? AND s.period_type = ? AND s.period_start = ? AND ("
                + "s.value > ? OR (s.value = ? AND s.last_result_at > ?) OR "
                + "(s.value = ? AND s.last_result_at = ? AND LOWER(u.username) < LOWER(?)) OR "
                + "(s.value = ? AND s.last_result_at = ? AND LOWER(u.username) = LOWER(?) AND s.user_id < ?))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindBoard(statement, selected, period, periodStart);
            int index = 5;
            statement.setInt(index++, value);
            statement.setInt(index++, value);
            statement.setLong(index++, lastResultAt);
            statement.setInt(index++, value);
            statement.setLong(index++, lastResultAt);
            statement.setString(index++, username);
            statement.setInt(index++, value);
            statement.setLong(index++, lastResultAt);
            statement.setString(index++, username);
            statement.setInt(index, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    private static void bindBoard(PreparedStatement statement, LeaderboardDefinition selected,
                                  LeaderboardPeriod period, long periodStart) throws SQLException {
        statement.setString(1, selected.gameType());
        statement.setString(2, selected.metric());
        statement.setString(3, period.name());
        statement.setLong(4, periodStart);
    }

    private static LeaderboardRow rowFromSet(ResultSet set, int rank, boolean own) throws SQLException {
        return new LeaderboardRow(set.getInt("user_id"), rank, set.getString("username"),
                set.getString("look"), set.getString("gender"), set.getInt("value"), own);
    }

    private void insertGamehallResult(Connection connection, String gameType, int roomId, int stationId,
                                      int winnerId, int loserId, long now) throws SQLException {
        String sql = "INSERT INTO gamehall_match_results "
                + "(game_type, room_id, station_id, player_one_user_id, player_two_user_id, winner_user_id, "
                + "loser_user_id, result_type, ended_at, duration_seconds, winner_score, loser_score) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'WIN', ?, 0, ?, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
            statement.setString(1, gameType);
            statement.setInt(2, roomId);
            statement.setInt(3, stationId);
            statement.setInt(4, winnerId);
            statement.setInt(5, loserId);
            statement.setInt(6, winnerId);
            statement.setInt(7, loserId);
            statement.setLong(8, now);
            statement.setInt(9, POINTS_PER_WIN);
            statement.executeUpdate();
        }
    }

    private static boolean isValidHabbo(Habbo habbo) {
        return habbo != null && habbo.getHabboInfo() != null;
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private record CacheKey(String gameType, String metric, LeaderboardPeriod period,
                            long periodStart, int offset, int limit) {
    }
}
