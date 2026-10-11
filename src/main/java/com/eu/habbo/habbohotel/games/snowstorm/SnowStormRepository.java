package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.leaderboards.LeaderboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only startup verification and later SnowStorm persistence boundary. */
final class SnowStormRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnowStormRepository.class);

    enum ChargeStatus { SUCCESS, INSUFFICIENT_TICKETS, REJECTED, ERROR }

    record PlayerStats(int skillLevel, int rankedPoints) {}

    record PersistResult(boolean success, SnowStormFinishedGame finishedGame) {
        static PersistResult failed() { return new PersistResult(false, null); }
        static PersistResult succeeded(SnowStormFinishedGame finishedGame) {
            return new PersistResult(true, finishedGame);
        }
    }

    record ChargeResult(
            ChargeStatus status,
            int resultId,
            Map<Integer, Integer> balances,
            Map<Integer, Integer> costs) {
        ChargeResult {
            balances = Map.copyOf(balances);
            costs = Map.copyOf(costs);
        }

        boolean success() { return status == ChargeStatus.SUCCESS; }
        static ChargeResult failed(ChargeStatus status) {
            return new ChargeResult(status, -1, Map.of(), Map.of());
        }
    }

    SnowStormMapCatalog loadAndValidateMaps() throws SQLException {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            validate(connection, catalog);
        }
        return catalog;
    }

    int nextInstanceId() throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(instance_id), 0) + 1 FROM snowstorm_rounds");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SQLException("SnowStorm next instance ID query returned no row");
            }
            return Math.max(1, rows.getInt(1));
        }
    }

    List<SnowStormFinishedGame> loadRecentResults(int limit) throws SQLException {
        if (limit < 1) return List.of();
        List<SnowStormFinishedGame> results = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT result_id, instance_id, round_number, game_name, owner_user_id, owner_name, "
                             + "map_id, team_count, round_seconds, ended_at, end_reason, winning_team, `ranked`, "
                             + "team_0_score, team_1_score, team_2_score, team_3_score "
                             + "FROM snowstorm_rounds WHERE ended_at IS NOT NULL AND aborted = 0 "
                             + "AND game_name <> '' ORDER BY ended_at DESC, result_id DESC LIMIT ?")) {
            statement.setInt(1, Math.min(15, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) results.add(readFinishedGame(connection, rows));
            }
        }
        return List.copyOf(results);
    }

    PlayerStats playerStats(int userId) {
        if (userId <= 0) return new PlayerStats(1, 0);
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT skill_level, ranked_points FROM snowstorm_player_stats WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return new PlayerStats(SnowStormProgression.BEGINNER, 0);
                long rankedPoints = rows.getLong("ranked_points");
                int calculatedLevel = SnowStormProgression.skillLevel(rankedPoints);
                return new PlayerStats(calculatedLevel, SnowStormProgression.wirePoints(rankedPoints));
            }
        } catch (SQLException exception) {
            LOGGER.error("SnowStorm -> Failed to load ranked stats for user {}.", userId, exception);
            return new PlayerStats(SnowStormProgression.BEGINNER, 0);
        }
    }

    PersistResult persistResult(SnowStormRoundResult result) {
        if (result == null || result.resultId() <= 0 || result.instanceId() <= 0
                || result.roundNumber() <= 0 || result.players().isEmpty()) return PersistResult.failed();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int endedAt = Emulator.getIntUnixTimestamp();
                LeaderboardManager leaderboard = rankedLeaderboard(result);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE snowstorm_rounds SET ended_at = ?, end_reason = ?, winning_team = ?, "
                                + "team_0_score = ?, team_1_score = ?, team_2_score = ?, team_3_score = ?, "
                                + "aborted = ? WHERE result_id = ? AND instance_id = ? AND round_number = ? "
                                + "AND ended_at IS NULL")) {
                    update.setInt(1, endedAt);
                    update.setInt(2, result.endReason());
                    update.setInt(3, result.winningTeam());
                    for (int team = 0; team < 4; team++) {
                        SnowStormRoundResult.TeamResult score = result.teams().get(team);
                        update.setInt(4 + team, score == null ? 0 : score.score());
                    }
                    update.setBoolean(8, result.aborted());
                    update.setInt(9, result.resultId());
                    update.setInt(10, result.instanceId());
                    update.setInt(11, result.roundNumber());
                    if (update.executeUpdate() != 1) {
                        if (!matchesFinalizedRound(connection, result)) {
                            connection.rollback();
                            return PersistResult.failed();
                        }
                        connection.commit();
                        if (leaderboard != null) leaderboard.snowStormResultsCommitted();
                        return completedPersistence(result);
                    }
                }

                for (SnowStormRoundResult.PlayerResult player : result.players()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE snowstorm_round_players SET score = ?, placement = ?, winner = ?, "
                                    + "disconnected = ? WHERE instance_id = ? AND round_number = ? AND user_id = ?")) {
                        update.setInt(1, player.score());
                        update.setInt(2, player.placement());
                        update.setBoolean(3, player.winner());
                        update.setBoolean(4, player.disconnected());
                        update.setInt(5, result.instanceId());
                        update.setInt(6, result.roundNumber());
                        update.setInt(7, player.userId());
                        if (update.executeUpdate() != 1) {
                            throw new SQLException("SnowStorm charged roster row disappeared before result persistence");
                        }
                    }
                    if (updatesRankedStats(result) && player.userId() > 0) {
                        updateRankedStats(connection, player);
                    }
                }
                if (leaderboard != null) {
                    leaderboard.appendSnowStormResults(connection, result.players().stream()
                            .filter(player -> player.userId() > 0)
                            .map(player -> new LeaderboardManager.ScoreResult(
                                    player.userId(), Math.max(0, player.score()), player.winner() ? 1 : -1))
                            .toList(), endedAt);
                }
                connection.commit();
                if (leaderboard != null) leaderboard.snowStormResultsCommitted();
                return completedPersistence(result);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("SnowStorm -> Failed to persist instance {} round {} result {}.",
                        result.instanceId(), result.roundNumber(), result.resultId(), exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("SnowStorm -> Could not open result transaction for instance {}.",
                    result.instanceId(), exception);
        }
        return PersistResult.failed();
    }

    /** Closes the charged row when the protected in-memory start transition cannot be applied. */
    boolean abortChargedRound(int resultId, int instanceId, int roundNumber, int endReason) {
        if (resultId <= 0 || instanceId <= 0 || roundNumber <= 0) return false;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE snowstorm_rounds SET ended_at = ?, end_reason = ?, winning_team = ?, "
                             + "aborted = 1 WHERE result_id = ? AND instance_id = ? AND round_number = ? "
                             + "AND ended_at IS NULL")) {
            update.setInt(1, Emulator.getIntUnixTimestamp());
            update.setInt(2, endReason);
            update.setInt(3, SnowStormRoundResult.NO_TEAM_WINNER);
            update.setInt(4, resultId);
            update.setInt(5, instanceId);
            update.setInt(6, roundNumber);
            return update.executeUpdate() == 1;
        } catch (SQLException exception) {
            LOGGER.error("SnowStorm -> Failed to abort charged instance {} round {} result {}.",
                    instanceId, roundNumber, resultId, exception);
            return false;
        }
    }

    /**
     * Closes charged rounds left open by a previous emulator process. Their exact terminal state
     * cannot be reconstructed, so they are deliberately excluded from rankings as aborted rounds.
     */
    int abortUnfinishedRounds(int endReason) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE snowstorm_rounds SET ended_at = ?, end_reason = ?, winning_team = ?, "
                             + "aborted = 1 WHERE charged_at IS NOT NULL AND ended_at IS NULL")) {
            update.setInt(1, Emulator.getIntUnixTimestamp());
            update.setInt(2, endReason);
            update.setInt(3, SnowStormRoundResult.NO_TEAM_WINNER);
            return update.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("SnowStorm -> Failed to reconcile unfinished charged rounds.", exception);
            return -1;
        }
    }

    private PersistResult completedPersistence(SnowStormRoundResult result) {
        if (result.aborted()) return PersistResult.succeeded(null);
        SnowStormFinishedGame finished = findFinishedResult(result.resultId());
        return finished == null ? PersistResult.failed() : PersistResult.succeeded(finished);
    }

    private static boolean matchesFinalizedRound(
            Connection connection,
            SnowStormRoundResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT end_reason, winning_team, team_0_score, team_1_score, team_2_score, "
                        + "team_3_score, aborted FROM snowstorm_rounds WHERE result_id = ? "
                        + "AND instance_id = ? AND round_number = ? AND ended_at IS NOT NULL LIMIT 1")) {
            statement.setInt(1, result.resultId());
            statement.setInt(2, result.instanceId());
            statement.setInt(3, result.roundNumber());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt("end_reason") != result.endReason()
                        || rows.getInt("winning_team") != result.winningTeam()
                        || rows.getBoolean("aborted") != result.aborted()) {
                    return false;
                }
                for (int team = 0; team < 4; team++) {
                    SnowStormRoundResult.TeamResult expected = result.teams().get(team);
                    if (rows.getInt("team_" + team + "_score") != (expected == null ? 0 : expected.score())) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    private static boolean updatesRankedStats(SnowStormRoundResult result) {
        return result.ranked() && !result.aborted();
    }

    private static LeaderboardManager rankedLeaderboard(SnowStormRoundResult result)
            throws SQLException {
        if (!updatesRankedStats(result)) return null;
        if (Emulator.getGameEnvironment() == null
                || Emulator.getGameEnvironment().getLeaderboardManager() == null) {
            throw new SQLException("SnowStorm leaderboard backend is unavailable");
        }
        return Emulator.getGameEnvironment().getLeaderboardManager();
    }

    private static void updateRankedStats(
            Connection connection,
            SnowStormRoundResult.PlayerResult player) throws SQLException {
        int score = Math.max(0, player.score());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO snowstorm_player_stats "
                        + "(user_id, games_played, wins, score_all_time, score_month, ranked_points, skill_level, month_key) "
                        + "VALUES (?, 1, ?, ?, ?, ?, 1, DATE_FORMAT(UTC_TIMESTAMP(), '%Y-%m')) "
                        + "ON DUPLICATE KEY UPDATE games_played = games_played + 1, wins = wins + VALUES(wins), "
                        + "score_all_time = score_all_time + VALUES(score_all_time), "
                        + "score_month = IF(month_key = VALUES(month_key), score_month + VALUES(score_month), VALUES(score_month)), "
                        + "ranked_points = ranked_points + VALUES(ranked_points), month_key = VALUES(month_key)")) {
            statement.setInt(1, player.userId());
            statement.setInt(2, player.winner() ? 1 : 0);
            statement.setInt(3, score);
            statement.setInt(4, score);
            statement.setInt(5, score);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE snowstorm_player_stats SET skill_level = CASE "
                        + "WHEN ranked_points <= 10000 THEN 1 "
                        + "WHEN ranked_points <= 100000 THEN 2 "
                        + "WHEN ranked_points <= 500000 THEN 3 ELSE 4 END WHERE user_id = ?")) {
            statement.setInt(1, player.userId());
            statement.executeUpdate();
        }
    }

    SnowStormFinishedGame findFinishedResult(int resultId) {
        if (resultId <= 0) return null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT result_id, instance_id, round_number, game_name, owner_user_id, owner_name, "
                             + "map_id, team_count, round_seconds, ended_at, end_reason, winning_team, `ranked`, "
                             + "team_0_score, team_1_score, team_2_score, team_3_score "
                             + "FROM snowstorm_rounds WHERE result_id = ? AND ended_at IS NOT NULL "
                             + "AND aborted = 0 LIMIT 1")) {
            statement.setInt(1, resultId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readFinishedGame(connection, rows) : null;
            }
        } catch (SQLException exception) {
            LOGGER.error("SnowStorm -> Failed to load finished result {}.", resultId, exception);
            return null;
        }
    }

    private static SnowStormFinishedGame readFinishedGame(
            Connection connection,
            ResultSet row) throws SQLException {
        int instanceId = row.getInt("instance_id");
        int roundNumber = row.getInt("round_number");
        int teamCount = row.getInt("team_count");
        Map<Integer, Integer> teamScores = new LinkedHashMap<>();
        for (int team = 0; team < teamCount; team++) {
            teamScores.put(team, row.getInt("team_" + team + "_score"));
        }
        List<Integer> teamOrder = teamScores.keySet().stream()
                .sorted(Comparator.<Integer>comparingInt(team -> teamScores.getOrDefault(team, 0))
                        .reversed().thenComparingInt(Integer::intValue)).toList();
        Map<Integer, SnowStormRoundResult.TeamResult> teams = new LinkedHashMap<>();
        for (int index = 0; index < teamOrder.size(); index++) {
            int team = teamOrder.get(index);
            teams.put(team, new SnowStormRoundResult.TeamResult(team, teamScores.get(team), index + 1));
        }
        List<SnowStormRoundResult.PlayerResult> players = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id, player_name, team_id, score, placement, winner, disconnected "
                        + "FROM snowstorm_round_players WHERE instance_id = ? AND round_number = ? "
                        + "ORDER BY placement, team_id, user_id")) {
            statement.setInt(1, instanceId);
            statement.setInt(2, roundNumber);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    players.add(new SnowStormRoundResult.PlayerResult(rows.getInt("user_id"),
                            rows.getString("player_name"), rows.getInt("team_id"), rows.getInt("score"),
                            rows.getInt("placement"), rows.getBoolean("winner"),
                            rows.getBoolean("disconnected")));
                }
            }
        }
        return new SnowStormFinishedGame(row.getInt("result_id"), instanceId, roundNumber,
                row.getString("game_name"), row.getInt("owner_user_id"), row.getString("owner_name"),
                row.getInt("map_id"), teamCount, lengthChoiceForSeconds(row.getInt("round_seconds")),
                row.getInt("round_seconds"), row.getInt("ended_at"), row.getInt("end_reason"),
                row.getInt("winning_team"), row.getBoolean("ranked"), teams, players);
    }

    private static int lengthChoiceForSeconds(int seconds) {
        return switch (seconds) {
            case 120 -> 1;
            case 180 -> 2;
            case 300 -> 3;
            default -> throw new IllegalArgumentException("Unknown persisted SnowStorm duration " + seconds);
        };
    }

    /** Atomic, idempotent charge for one immutable roster; only humans own wallets. */
    ChargeResult chargeRound(
            SnowStormGame game,
            SnowStormStartingRoster roster,
            List<Habbo> participants,
            int currencyType,
            int configuredCost) {
        if (game == null || roster == null || roster.instanceId() != game.id()
                || configuredCost < 0 || currencyType < 0
                || participants.size() != roster.players().stream().filter(player -> !player.bot()).count()) {
            return ChargeResult.failed(ChargeStatus.REJECTED);
        }
        Map<Integer, Habbo> habbos = new LinkedHashMap<>();
        participants.stream().sorted(java.util.Comparator.comparingInt(
                        habbo -> habbo.getHabboInfo().getId()))
                .forEach(habbo -> habbos.put(habbo.getHabboInfo().getId(), habbo));
        if (!habbos.keySet().equals(roster.players().stream()
                .filter(player -> !player.bot())
                .map(SnowStormStartingRoster.Player::userId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            return ChargeResult.failed(ChargeStatus.REJECTED);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ChargeResult existing = existingCharge(connection, game, roster, currencyType);
                if (existing != null) {
                    connection.commit();
                    return existing;
                }

                Map<Integer, Integer> costs = effectiveCosts(habbos, currencyType, configuredCost);
                if (costs == null) {
                    connection.rollback();
                    return ChargeResult.failed(ChargeStatus.REJECTED);
                }

                Map<Integer, Integer> balances = new LinkedHashMap<>();
                try (PreparedStatement ensureCurrency = connection.prepareStatement(
                             "INSERT IGNORE INTO users_currency (user_id, type, amount) VALUES (?, ?, 0)");
                     PreparedStatement lockCurrency = connection.prepareStatement(
                             "SELECT amount FROM users_currency WHERE user_id = ? AND type = ? FOR UPDATE")) {
                    for (SnowStormStartingRoster.Player player : roster.players()) {
                        if (player.bot()) continue;
                        int userId = player.userId();
                        ensureCurrency.setInt(1, userId);
                        ensureCurrency.setInt(2, currencyType);
                        ensureCurrency.executeUpdate();

                        lockCurrency.setInt(1, userId);
                        lockCurrency.setInt(2, currencyType);
                        int balance;
                        try (ResultSet rows = lockCurrency.executeQuery()) {
                            if (!rows.next()) throw new SQLException("SnowStorm wallet row disappeared");
                            balance = rows.getInt(1);
                        }
                        int cost = costs.get(userId);
                        if (balance < cost) {
                            connection.rollback();
                            return ChargeResult.failed(ChargeStatus.INSUFFICIENT_TICKETS);
                        }
                        balances.put(userId, balance - cost);
                    }
                }

                int now = Emulator.getIntUnixTimestamp();
                int resultId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO snowstorm_rounds (instance_id, round_number, game_name, "
                                + "owner_user_id, owner_name, map_id, map_revision, map_checksum, "
                                + "team_count, round_seconds, started_at, charged_at, `ranked`) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, game.id());
                    insert.setInt(2, roster.roundNumber());
                    insert.setString(3, game.name());
                    insert.setInt(4, game.ownerUserId());
                    insert.setString(5, game.ownerName());
                    insert.setInt(6, game.map().mapId());
                    insert.setInt(7, game.map().revision());
                    insert.setInt(8, game.map().checksum());
                    insert.setInt(9, game.teamCount());
                    insert.setInt(10, game.roundSeconds());
                    insert.setInt(11, now);
                    insert.setInt(12, now);
                    insert.setBoolean(13, roster.ranked());
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("SnowStorm charge did not return a result ID");
                        }
                        resultId = keys.getInt(1);
                    }
                }

                try (PreparedStatement debit = connection.prepareStatement(
                             "UPDATE users_currency SET amount = amount - ? "
                                     + "WHERE user_id = ? AND type = ? AND amount >= ? LIMIT 1");
                     PreparedStatement insertPlayer = connection.prepareStatement(
                             "INSERT INTO snowstorm_round_players (instance_id, round_number, user_id, "
                                     + "object_id, player_name, team_id, human, ticket_cost) "
                                     + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (SnowStormStartingRoster.Player player : roster.players()) {
                        int cost = player.bot() ? 0 : costs.get(player.userId());
                        if (!player.bot()) {
                            debit.setInt(1, cost);
                            debit.setInt(2, player.userId());
                            debit.setInt(3, currencyType);
                            debit.setInt(4, cost);
                            if (debit.executeUpdate() != 1) {
                                throw new SQLException("SnowStorm locked wallet changed before debit");
                            }
                        }
                        insertPlayer.setInt(1, game.id());
                        insertPlayer.setInt(2, roster.roundNumber());
                        insertPlayer.setInt(3, player.userId());
                        insertPlayer.setInt(4, player.objectId());
                        insertPlayer.setString(5, player.name());
                        insertPlayer.setInt(6, player.teamId());
                        insertPlayer.setBoolean(7, !player.bot());
                        insertPlayer.setInt(8, cost);
                        insertPlayer.addBatch();
                    }
                    int[] inserted = insertPlayer.executeBatch();
                    if (inserted.length != roster.players().size()) {
                        throw new SQLException("SnowStorm charged roster batch was incomplete");
                    }
                    for (int count : inserted) {
                        if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                            throw new SQLException("SnowStorm charged roster row was not inserted");
                        }
                    }
                }
                connection.commit();
                return new ChargeResult(ChargeStatus.SUCCESS, resultId, balances, costs);
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                LOGGER.error("SnowStorm -> Failed to charge instance {} round {}.",
                        game.id(), roster.roundNumber(), exception);
                return ChargeResult.failed(ChargeStatus.ERROR);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("SnowStorm -> Could not open charge transaction for instance {}.",
                    game.id(), exception);
            return ChargeResult.failed(ChargeStatus.ERROR);
        }
    }

    private ChargeResult existingCharge(
            Connection connection,
            SnowStormGame game,
            SnowStormStartingRoster roster,
            int currencyType) throws SQLException {
        int resultId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT result_id, map_id, map_revision, map_checksum, team_count, round_seconds, `ranked` "
                        + "FROM snowstorm_rounds WHERE instance_id = ? AND round_number = ? FOR UPDATE")) {
            statement.setInt(1, game.id());
            statement.setInt(2, roster.roundNumber());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                if (rows.getInt("map_id") != game.map().mapId()
                        || rows.getInt("map_revision") != game.map().revision()
                        || rows.getInt("map_checksum") != game.map().checksum()
                        || rows.getInt("team_count") != game.teamCount()
                        || rows.getInt("round_seconds") != game.roundSeconds()
                        || rows.getBoolean("ranked") != roster.ranked()) {
                    return ChargeResult.failed(ChargeStatus.REJECTED);
                }
                resultId = rows.getInt("result_id");
            }
        }

        Map<Integer, Integer> costs = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id, object_id, player_name, team_id, human, ticket_cost "
                        + "FROM snowstorm_round_players WHERE instance_id = ? AND round_number = ? "
                        + "ORDER BY user_id FOR UPDATE")) {
            statement.setInt(1, game.id());
            statement.setInt(2, roster.roundNumber());
            try (ResultSet rows = statement.executeQuery()) {
                int index = 0;
                while (rows.next()) {
                    if (index >= roster.players().size()) {
                        return ChargeResult.failed(ChargeStatus.REJECTED);
                    }
                    SnowStormStartingRoster.Player expected = roster.players().get(index++);
                    if (rows.getInt("user_id") != expected.userId()
                            || rows.getInt("object_id") != expected.objectId()
                            || !rows.getString("player_name").equals(expected.name())
                            || rows.getInt("team_id") != expected.teamId()
                            || rows.getBoolean("human") != !expected.bot()
                            || (expected.bot() && rows.getInt("ticket_cost") != 0)) {
                        return ChargeResult.failed(ChargeStatus.REJECTED);
                    }
                    if (!expected.bot()) costs.put(expected.userId(), rows.getInt("ticket_cost"));
                }
                if (index != roster.players().size()) {
                    return ChargeResult.failed(ChargeStatus.REJECTED);
                }
            }
        }
        Map<Integer, Integer> balances = new LinkedHashMap<>();
        for (SnowStormStartingRoster.Player player : roster.players()) {
            if (player.bot()) continue;
            balances.put(player.userId(), readCurrency(connection, player.userId(), currencyType));
        }
        return new ChargeResult(ChargeStatus.SUCCESS, resultId, balances, costs);
    }

    private Map<Integer, Integer> effectiveCosts(
            Map<Integer, Habbo> participants,
            int currencyType,
            int configuredCost) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        try {
            for (Map.Entry<Integer, Habbo> entry : participants.entrySet()) {
                UserPointsEvent event = new UserPointsEvent(entry.getValue(), -configuredCost, currencyType);
                if (configuredCost > 0 && Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                    return null;
                }
                if (event.type != currencyType || event.type < 0) {
                    return null;
                }
                result.put(entry.getKey(), Math.abs(event.points));
            }
            return result;
        } catch (RuntimeException exception) {
            LOGGER.error("SnowStorm -> Ticket charge plugin callback failed.", exception);
            return null;
        }
    }

    private static int readCurrency(Connection connection, int userId, int type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM users_currency WHERE user_id = ? AND type = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, type);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    void validate(Connection connection, SnowStormMapCatalog catalog) throws SQLException {
        validateMaps(connection, catalog);
        validateItems(connection, catalog);
        validateMachines(connection, catalog);
        validateSpawns(connection, catalog);
    }

    private static void validateMaps(Connection connection, SnowStormMapCatalog catalog) throws SQLException {
        Set<Integer> seen = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT map_id, revision, width, height, world_units_per_tile, floor_map, collision_map, "
                        + "checksum, sha256 FROM snowstorm_maps WHERE enabled = 1 ORDER BY map_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int mapId = rows.getInt("map_id");
                SnowStormMapDefinition expected = expectedMap(catalog, mapId);
                require(seen.add(mapId), "duplicate enabled map " + mapId);
                equal(expected.revision(), rows.getInt("revision"), mapId, "revision");
                equal(expected.width(), rows.getInt("width"), mapId, "width");
                equal(expected.height(), rows.getInt("height"), mapId, "height");
                equal(expected.worldUnitsPerTile(), rows.getInt("world_units_per_tile"), mapId, "world units");
                equal(String.join("|", expected.floorRows()), rows.getString("floor_map"), mapId, "floor map");
                equal(String.join("|", expected.collisionRows()), rows.getString("collision_map"), mapId, "collision map");
                equal(expected.checksum(), rows.getInt("checksum"), mapId, "checksum");
                equal(expected.sha256(), rows.getString("sha256"), mapId, "SHA-256");
            }
        }
        require(seen.equals(Set.of(1, 2, 3, 4, 5, 6, 7)),
                "enabled map ids must be the complete range 1..7, found " + seen);
    }

    private static void validateItems(Connection connection, SnowStormMapCatalog catalog) throws SQLException {
        Map<Integer, Map<String, SnowStormMapItem>> expected = new HashMap<>();
        for (SnowStormMapDefinition map : catalog.maps()) {
            Map<String, SnowStormMapItem> byId = new HashMap<>();
            for (SnowStormMapItem item : map.items()) {
                byId.put(item.id(), item);
            }
            expected.put(map.mapId(), byId);
        }
        int seen = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT map_id, item_id, member_name, asset_name, resource_id, cast_id, tile_x, tile_y, "
                        + "world_x, world_y, world_z, direction, flip_x, source_loc_z, draw_order, walking_height, "
                        + "projectile_height, footprint_json, palette_id, palette_name, registration_x, registration_y "
                        + "FROM snowstorm_map_items WHERE map_id BETWEEN 1 AND 7 ORDER BY map_id, draw_order");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int mapId = rows.getInt("map_id");
                String itemId = rows.getString("item_id");
                SnowStormMapItem item = remove(expected.get(mapId), itemId, "item", mapId);
                seen++;
                equal(item.member(), rows.getString("member_name"), mapId, itemId + " member");
                equal(item.assetName(), rows.getString("asset_name"), mapId, itemId + " asset");
                equal(item.resourceId(), rows.getInt("resource_id"), mapId, itemId + " resource id");
                equal(item.castId(), rows.getInt("cast_id"), mapId, itemId + " cast id");
                equal(item.tileX(), rows.getInt("tile_x"), mapId, itemId + " tile x");
                equal(item.tileY(), rows.getInt("tile_y"), mapId, itemId + " tile y");
                equal(item.worldX(), rows.getInt("world_x"), mapId, itemId + " world x");
                equal(item.worldY(), rows.getInt("world_y"), mapId, itemId + " world y");
                equal(item.worldZ(), rows.getInt("world_z"), mapId, itemId + " world z");
                equal(item.direction(), rows.getInt("direction"), mapId, itemId + " direction");
                equal(item.flipX(), rows.getBoolean("flip_x"), mapId, itemId + " flip");
                equal(item.sourceLocZ(), rows.getInt("source_loc_z"), mapId, itemId + " source locZ");
                equal(item.drawOrder(), rows.getInt("draw_order"), mapId, itemId + " draw order");
                equal(item.walkingHeight(), rows.getInt("walking_height"), mapId, itemId + " walking height");
                equal(item.projectileCollisionHeight(), rows.getInt("projectile_height"), mapId,
                        itemId + " projectile height");
                equal(item.footprint(), parsePoints(rows.getString("footprint_json")), mapId, itemId + " footprint");
                equal(item.paletteId(), rows.getInt("palette_id"), mapId, itemId + " palette id");
                equal(item.paletteName(), rows.getString("palette_name"), mapId, itemId + " palette");
                equal(item.registrationX(), rows.getInt("registration_x"), mapId, itemId + " registration x");
                equal(item.registrationY(), rows.getInt("registration_y"), mapId, itemId + " registration y");
            }
        }
        require(expected.values().stream().allMatch(Map::isEmpty), "one or more generated map items are missing");
        require(seen == catalog.maps().stream().mapToInt(map -> map.items().size()).sum(),
                "map item row count differs from the generated catalog");
    }

    private static void validateMachines(Connection connection, SnowStormMapCatalog catalog) throws SQLException {
        Map<Integer, Map<Integer, SnowStormMachineDefinition>> expected = new HashMap<>();
        for (SnowStormMapDefinition map : catalog.maps()) {
            Map<Integer, SnowStormMachineDefinition> byId = new HashMap<>();
            for (SnowStormMachineDefinition machine : map.machines()) {
                byId.put(machine.id(), machine);
            }
            expected.put(map.mapId(), byId);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT map_id, machine_id, tile_x, tile_y, world_x, world_y, world_z, collection_tile_x, "
                        + "collection_tile_y, direction, collision_tiles_json, collision_radius, collision_height, "
                        + "initial_stock, maximum_stock, refill_interval_ms, transfer_interval_ms "
                        + "FROM snowstorm_map_machines WHERE map_id BETWEEN 1 AND 7 ORDER BY map_id, machine_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int mapId = rows.getInt("map_id");
                int machineId = rows.getInt("machine_id");
                SnowStormMachineDefinition machine = remove(expected.get(mapId), machineId, "machine", mapId);
                equal(machine.tileX(), rows.getInt("tile_x"), mapId, "machine " + machineId + " tile x");
                equal(machine.tileY(), rows.getInt("tile_y"), mapId, "machine " + machineId + " tile y");
                equal(machine.worldX(), rows.getInt("world_x"), mapId, "machine " + machineId + " world x");
                equal(machine.worldY(), rows.getInt("world_y"), mapId, "machine " + machineId + " world y");
                equal(machine.worldZ(), rows.getInt("world_z"), mapId, "machine " + machineId + " world z");
                equal(machine.collectionTileX(), rows.getInt("collection_tile_x"), mapId,
                        "machine " + machineId + " collection x");
                equal(machine.collectionTileY(), rows.getInt("collection_tile_y"), mapId,
                        "machine " + machineId + " collection y");
                equal(machine.direction(), rows.getInt("direction"), mapId, "machine " + machineId + " direction");
                equal(machine.collisionTiles(), parsePoints(rows.getString("collision_tiles_json")), mapId,
                        "machine " + machineId + " collision footprint");
                equal(machine.collisionRadius(), rows.getInt("collision_radius"), mapId,
                        "machine " + machineId + " collision radius");
                equal(machine.collisionHeight(), rows.getInt("collision_height"), mapId,
                        "machine " + machineId + " collision height");
                equal(machine.initialStock(), rows.getInt("initial_stock"), mapId,
                        "machine " + machineId + " initial stock");
                equal(machine.maximumStock(), rows.getInt("maximum_stock"), mapId,
                        "machine " + machineId + " maximum stock");
                equal(machine.refillIntervalMs(), rows.getInt("refill_interval_ms"), mapId,
                        "machine " + machineId + " refill interval");
                equal(machine.transferIntervalMs(), rows.getInt("transfer_interval_ms"), mapId,
                        "machine " + machineId + " transfer interval");
            }
        }
        require(expected.values().stream().allMatch(Map::isEmpty), "one or more generated machines are missing");
    }

    private static void validateSpawns(Connection connection, SnowStormMapCatalog catalog) throws SQLException {
        Map<Integer, Map<Integer, SnowStormSpawnCluster>> expected = new HashMap<>();
        for (SnowStormMapDefinition map : catalog.maps()) {
            Map<Integer, SnowStormSpawnCluster> byId = new HashMap<>();
            for (SnowStormSpawnCluster spawn : map.spawnClusters()) {
                byId.put(spawn.id(), spawn);
            }
            expected.put(map.mapId(), byId);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT map_id, spawn_id, center_x, center_y, radius, minimum_separation, team_count_mask, "
                        + "fallback_rule FROM snowstorm_map_spawns WHERE map_id BETWEEN 1 AND 7 "
                        + "ORDER BY map_id, spawn_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int mapId = rows.getInt("map_id");
                int spawnId = rows.getInt("spawn_id");
                SnowStormSpawnCluster spawn = remove(expected.get(mapId), spawnId, "spawn", mapId);
                equal(spawn.centerX(), rows.getInt("center_x"), mapId, "spawn " + spawnId + " center x");
                equal(spawn.centerY(), rows.getInt("center_y"), mapId, "spawn " + spawnId + " center y");
                equal(spawn.radius(), rows.getInt("radius"), mapId, "spawn " + spawnId + " radius");
                equal(spawn.minimumSeparation(), rows.getInt("minimum_separation"), mapId,
                        "spawn " + spawnId + " separation");
                equal(spawn.applicableTeamCountMask(), rows.getInt("team_count_mask"), mapId,
                        "spawn " + spawnId + " team mask");
                equal(spawn.fallbackRule(), rows.getString("fallback_rule"), mapId,
                        "spawn " + spawnId + " fallback");
            }
        }
        require(expected.values().stream().allMatch(Map::isEmpty), "one or more generated spawns are missing");
    }

    private static SnowStormMapDefinition expectedMap(SnowStormMapCatalog catalog, int mapId) throws SQLException {
        try {
            return catalog.get(mapId);
        } catch (IllegalArgumentException exception) {
            throw mismatch("unexpected enabled map " + mapId);
        }
    }

    private static <K, V> V remove(Map<K, V> values, K id, String kind, int mapId) throws SQLException {
        if (values == null) {
            throw mismatch("unexpected " + kind + " for map " + mapId);
        }
        V value = values.remove(id);
        if (value == null) {
            throw mismatch("unexpected or duplicate " + kind + " " + id + " for map " + mapId);
        }
        return value;
    }

    private static List<SnowStormTile> parsePoints(String json) throws SQLException {
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            java.util.ArrayList<SnowStormTile> result = new java.util.ArrayList<>();
            for (JsonElement element : array) {
                JsonArray point = element.getAsJsonArray();
                if (point.size() != 2) {
                    throw mismatch("invalid footprint point");
                }
                result.add(new SnowStormTile(point.get(0).getAsInt(), point.get(1).getAsInt()));
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw mismatch("invalid footprint JSON", exception);
        }
    }

    private static void equal(Object expected, Object actual, int mapId, String field) throws SQLException {
        if (!java.util.Objects.equals(expected, actual)) {
            throw mismatch("map " + mapId + " " + field + " differs from generated r26 data");
        }
    }

    private static void require(boolean condition, String message) throws SQLException {
        if (!condition) {
            throw mismatch(message);
        }
    }

    private static SQLException mismatch(String message) {
        return new SQLException("SnowStorm map SQL mismatch: " + message);
    }

    private static SQLException mismatch(String message, Throwable cause) {
        return new SQLException("SnowStorm map SQL mismatch: " + message, cause);
    }
}
