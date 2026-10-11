package com.eu.habbo.habbohotel.games.battleball;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.leaderboards.LeaderboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** All BattleBall persistence, including atomic per-round ticket charging. */
final class BattleBallRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(BattleBallRepository.class);
    record LoadedData(Map<Integer, BattleBallMap> maps, Map<Integer, Map<Integer, List<BattleBallPosition>>> spawns) {}
    enum ChargeStatus { SUCCESS, INSUFFICIENT_TICKETS, REJECTED, ERROR }
    record ChargeResult(ChargeStatus status, Map<Integer, Integer> balances, Map<Integer, Integer> costs) {
        boolean success() { return this.status == ChargeStatus.SUCCESS; }
        static ChargeResult insufficientTickets() { return failed(ChargeStatus.INSUFFICIENT_TICKETS); }
        static ChargeResult rejected() { return failed(ChargeStatus.REJECTED); }
        static ChargeResult error() { return failed(ChargeStatus.ERROR); }
        private static ChargeResult failed(ChargeStatus status) { return new ChargeResult(status, Map.of(), Map.of()); }
    }
    private record PersistResult(boolean persisted, BattleBallFinishedGame finished) {}

    LoadedData load() throws SQLException {
        Map<Integer, BattleBallMap> maps = new LinkedHashMap<>();
        Map<Integer, Map<Integer, List<BattleBallPosition>>> spawns = new HashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT map_id AS id, tile_map, heightmap FROM battleball_maps WHERE enabled = 1 ORDER BY map_id");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) maps.put(rows.getInt("id"),
                        new BattleBallMap(rows.getInt("id"), rows.getString("tile_map"), rows.getString("heightmap")));
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT map_id, team_id, x, y, z, rotation FROM battleball_spawns ORDER BY map_id, team_id, id");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    spawns.computeIfAbsent(rows.getInt("map_id"), ignored -> new HashMap<>())
                            .computeIfAbsent(rows.getInt("team_id"), ignored -> new ArrayList<>())
                            .add(new BattleBallPosition(rows.getInt("x"), rows.getInt("y"), rows.getDouble("z"), rows.getInt("rotation")));
                }
            }
        }
        if (maps.size() != 5) throw new SQLException("Expected all five original BattleBall maps, loaded " + maps.size());
        return new LoadedData(Map.copyOf(maps), immutableSpawns(spawns));
    }

    List<BattleBallFinishedGame> loadRecentResults(int limit) throws SQLException {
        if (limit < 1) return List.of();
        List<BattleBallFinishedGame> results = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT result_id, instance_id, round_number, game_name, owner_user_id, owner_name, map_id, "
                             + "private_game, team_count, capacity, round_seconds, ended_at, winning_team, "
                             + "team_0_score, team_1_score, team_2_score, team_3_score "
                             + "FROM battleball_rounds WHERE ended_at IS NOT NULL AND aborted = 0 AND game_name <> '' "
                             + "ORDER BY ended_at DESC, result_id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) results.add(this.readFinishedGame(connection, rows));
            }
        }
        return List.copyOf(results);
    }

    ChargeResult chargeRound(BattleBallGame game, List<Habbo> players, int currencyType,
                             int configuredCost, boolean rankedRound) {
        if (configuredCost < 0 || currencyType < 0) return ChargeResult.rejected();
        Map<Integer, Integer> effectiveCosts = new LinkedHashMap<>();
        try {
            for (Habbo habbo : players) {
                UserPointsEvent event = new UserPointsEvent(habbo, -configuredCost, currencyType);
                if (configuredCost > 0 && Emulator.getPluginManager().fireEvent(event).isCancelled()) return ChargeResult.rejected();
                if (event.type < 0) return ChargeResult.rejected();
                effectiveCosts.put(habbo.getHabboInfo().getId(), Math.abs(event.points));
                if (event.type != currencyType) return ChargeResult.rejected();
            }
        } catch (RuntimeException exception) {
            LOGGER.error("BattleBall -> Ticket charge plugin callback failed for instance {}.", game.id(), exception);
            return ChargeResult.error();
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int round = game.roundNumber() + 1;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO battleball_rounds (instance_id, round_number, game_name, owner_user_id, owner_name, "
                                + "map_id, private_game, team_count, capacity, round_seconds, started_at, `ranked`) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setInt(1, game.id()); insert.setInt(2, round);
                    insert.setString(3, game.name()); insert.setInt(4, game.ownerUserId());
                    insert.setString(5, game.ownerName()); insert.setInt(6, game.map().id());
                    insert.setBoolean(7, game.privateGame()); insert.setInt(8, game.teams().size());
                    insert.setInt(9, game.capacity()); insert.setInt(10, game.roundSeconds());
                    insert.setInt(11, Emulator.getIntUnixTimestamp()); insert.setBoolean(12, rankedRound);
                    insert.executeUpdate();
                }
                Map<Integer, Integer> balances = new LinkedHashMap<>();
                for (Habbo habbo : players) {
                    int userId = habbo.getHabboInfo().getId();
                    int cost = effectiveCosts.get(userId);
                    if (cost > 0) {
                        try (PreparedStatement debit = connection.prepareStatement(
                                "UPDATE users_currency SET amount = amount - ? WHERE user_id = ? AND type = ? AND amount >= ? LIMIT 1")) {
                            debit.setInt(1, cost); debit.setInt(2, userId); debit.setInt(3, currencyType); debit.setInt(4, cost);
                            if (debit.executeUpdate() != 1) { connection.rollback(); return ChargeResult.insufficientTickets(); }
                        }
                    }
                    try (PreparedStatement participant = connection.prepareStatement(
                            "INSERT INTO battleball_round_players (instance_id, round_number, user_id, player_name, team_id, ticket_cost) VALUES (?, ?, ?, ?, ?, ?)")) {
                        participant.setInt(1, game.id()); participant.setInt(2, round); participant.setInt(3, userId);
                        BattleBallPlayer player = game.players().stream().filter(value -> value.userId() == userId).findFirst().orElseThrow();
                        participant.setString(4, player.name()); participant.setInt(5, player.teamId());
                        participant.setInt(6, cost); participant.executeUpdate();
                    }
                    balances.put(userId, readCurrency(connection, userId, currencyType));
                }
                connection.commit();
                return new ChargeResult(ChargeStatus.SUCCESS, Map.copyOf(balances), Map.copyOf(effectiveCosts));
            } catch (Exception exception) {
                connection.rollback();
                LOGGER.error("BattleBall -> Failed to charge instance {} round {}.", game.id(), game.roundNumber() + 1, exception);
                return ChargeResult.error();
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("BattleBall -> Could not open the ticket transaction for instance {}.", game.id(), exception);
            return ChargeResult.error();
        }
    }

    BattleBallFinishedGame saveResults(BattleBallRoundResult result) {
        return this.persistRound(result).finished();
    }

    /**
     * Closes a round that was charged but never reached its natural end -- the owner left, disconnected
     * or idled out.
     *
     * Previously such a round was simply dropped: tickets had already been debited by chargeRound,
     * every participant lost their entry fee, no score or stat was written, and battleball_rounds
     * kept ended_at NULL forever, so the row could never be reconciled.
     *
     * The policy is forfeit rather than refund. Refunding would let an owner cancel any round they
     * were losing and hand everyone their tickets back. Players who already left forfeit their personal
     * result; remaining players are persisted, and the round has no winner. Aborted rounds are deliberately
     * excluded from the leaderboard.
     */
    boolean abortRound(BattleBallRoundResult result) {
        if (result.roundNumber() < 1) return false;
        return this.persistRound(result).persisted();
    }

    private PersistResult persistRound(BattleBallRoundResult result) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE battleball_rounds SET ended_at = ?, winning_team = ?, aborted = ?, "
                                + "team_0_score = ?, team_1_score = ?, team_2_score = ?, team_3_score = ? "
                                + "WHERE instance_id = ? AND round_number = ? AND ended_at IS NULL")) {
                    update.setInt(1, Emulator.getIntUnixTimestamp()); update.setInt(2, result.winningTeam());
                    update.setBoolean(3, result.aborted());
                    for (int team = 0; team < 4; team++) update.setInt(4 + team, result.teamScores().getOrDefault(team, 0));
                    update.setInt(8, result.instanceId()); update.setInt(9, result.roundNumber());
                    if (update.executeUpdate() != 1) {
                        // No open round row: nothing was charged, or it is already closed.
                        connection.rollback();
                        return new PersistResult(false, null);
                    }
                }
                for (BattleBallRoundResult.PlayerResult player : result.players()) {
                    try (PreparedStatement updatePlayer = connection.prepareStatement(
                            "INSERT INTO battleball_round_players (instance_id, round_number, user_id, player_name, team_id, ticket_cost, score, winner) "
                                    + "VALUES (?, ?, ?, ?, ?, 0, ?, ?) ON DUPLICATE KEY UPDATE "
                                    + "player_name = VALUES(player_name), team_id = VALUES(team_id), score = VALUES(score), winner = VALUES(winner)")) {
                        updatePlayer.setInt(1, result.instanceId()); updatePlayer.setInt(2, result.roundNumber());
                        updatePlayer.setInt(3, player.userId()); updatePlayer.setString(4, player.name());
                        updatePlayer.setInt(5, player.teamId()); updatePlayer.setInt(6, player.score());
                        updatePlayer.setBoolean(7, player.winner()); updatePlayer.executeUpdate();
                    }
                    if (player.userId() > 0 && updatesRankedStats(result)) {
                        try (PreparedStatement stats = connection.prepareStatement(
                                "INSERT INTO battleball_player_stats (user_id, games_played, wins, total_score, skill_level) VALUES (?, 1, ?, ?, ?) "
                                        + "ON DUPLICATE KEY UPDATE games_played = games_played + 1, wins = wins + VALUES(wins), total_score = total_score + VALUES(total_score), "
                                        + "skill_level = GREATEST(1, LEAST(10, FLOOR(total_score / 5000) + 1))")) {
                            stats.setInt(1, player.userId()); stats.setInt(2, player.winner() ? 1 : 0);
                            stats.setInt(3, player.score());
                            stats.setInt(4, Math.max(1, Math.min(10, player.score() / 5000 + 1)));
                            stats.executeUpdate();
                        }
                    }
                }
                connection.commit();
                if (result.ranked() && !result.aborted()) this.publishLeaderboard(result);
                BattleBallFinishedGame finished = result.aborted() ? null
                        : this.findFinishedResult(result.instanceId(), result.roundNumber());
                return new PersistResult(result.aborted() || finished != null, finished);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("BattleBall -> Failed to persist instance {} round {} results (aborted={}).",
                        result.instanceId(), result.roundNumber(), result.aborted(), exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("BattleBall -> Could not open the result transaction for instance {}.",
                    result.instanceId(), exception);
        }
        return new PersistResult(false, null);
    }

    private BattleBallFinishedGame findFinishedResult(int instanceId, int roundNumber) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT result_id, instance_id, round_number, game_name, owner_user_id, owner_name, map_id, "
                             + "private_game, team_count, capacity, round_seconds, ended_at, winning_team, "
                             + "team_0_score, team_1_score, team_2_score, team_3_score "
                             + "FROM battleball_rounds WHERE instance_id = ? AND round_number = ? AND ended_at IS NOT NULL AND aborted = 0 LIMIT 1")) {
            statement.setInt(1, instanceId); statement.setInt(2, roundNumber);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? this.readFinishedGame(connection, rows) : null;
            }
        } catch (SQLException exception) {
            LOGGER.error("BattleBall -> Failed to reload instance {} round {} for the finished-games list.",
                    instanceId, roundNumber, exception);
            return null;
        }
    }

    private BattleBallFinishedGame readFinishedGame(Connection connection, ResultSet row) throws SQLException {
        int instanceId = row.getInt("instance_id");
        int roundNumber = row.getInt("round_number");
        List<BattleBallFinishedGame.PlayerResult> players = new ArrayList<>();
        Map<Integer, Integer> teamScores = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id, player_name, team_id, score, winner FROM battleball_round_players "
                        + "WHERE instance_id = ? AND round_number = ? ORDER BY team_id, score DESC, user_id")) {
            statement.setInt(1, instanceId); statement.setInt(2, roundNumber);
            try (ResultSet playerRows = statement.executeQuery()) {
                while (playerRows.next()) {
                    int teamId = playerRows.getInt("team_id");
                    int score = playerRows.getInt("score");
                    players.add(new BattleBallFinishedGame.PlayerResult(playerRows.getInt("user_id"),
                            playerRows.getString("player_name"), teamId, score, playerRows.getBoolean("winner")));
                }
            }
        }
        int teamCount = row.getInt("team_count");
        for (int team = 0; team < teamCount; team++) teamScores.put(team, row.getInt("team_" + team + "_score"));
        return new BattleBallFinishedGame(row.getInt("result_id"), instanceId, roundNumber,
                row.getString("game_name"), row.getInt("owner_user_id"), row.getString("owner_name"),
                row.getInt("map_id"), row.getBoolean("private_game"), teamCount, players.size(),
                row.getInt("capacity"), row.getInt("round_seconds"), row.getInt("ended_at"),
                row.getInt("winning_team"), teamScores, players);
    }

    /**
     * Feeds the round into the shared Games Hall leaderboard, so BattleBall ranks beside the seated
     * games instead of behind the dead habbo.com high-score page the original linked to.
     *
     * Scored per player rather than per team: points are the tiles that player personally claimed, so a
     * strong player on a losing team still ranks. The outcome is their team's placing -- a win for the
     * top team, a draw when tied for top, a loss otherwise -- which keeps second of four meaningfully
     * different from last, unlike a plain winner/loser split.
     *
     * Runs after the commit: a leaderboard write failing must not roll back the round itself.
     */
    private void publishLeaderboard(BattleBallRoundResult round) {
        LeaderboardManager leaderboard = Emulator.getGameEnvironment() == null
                ? null : Emulator.getGameEnvironment().getLeaderboardManager();
        if (leaderboard == null) return;
        List<LeaderboardManager.BattleBallResult> results = new ArrayList<>();
        int best = round.teamScores().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (BattleBallRoundResult.PlayerResult player : round.players()) {
            if (player.userId() < 1) continue;
            int teamScore = round.teamScores().getOrDefault(player.teamId(), 0);
            int outcome;
            if (round.winningTeam() >= 0) {
                outcome = player.winner() ? 1 : -1;
            } else {
                // No single winner: everyone level with the best score drew, the rest lost.
                outcome = teamScore == best ? 0 : -1;
            }
            results.add(new LeaderboardManager.BattleBallResult(
                    player.userId(), player.score(), outcome));
        }
        leaderboard.recordBattleBallResults(results);
    }

    int skillLevel(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT skill_level FROM battleball_player_stats WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getInt(1) : 1; }
        } catch (SQLException exception) {
            LOGGER.error("BattleBall -> Failed to load skill level for user {}.", userId, exception);
            return 1;
        }
    }

    int nextInstanceId() throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(instance_id), 0) + 1 FROM battleball_rounds")) {
            if (!rows.next()) throw new SQLException("BattleBall instance-id query returned no row");
            return Math.max(1, rows.getInt(1));
        }
    }

    static boolean updatesRankedStats(BattleBallRoundResult result) {
        return result.ranked() && !result.aborted();
    }

    private static int readCurrency(Connection connection, int userId, int type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT amount FROM users_currency WHERE user_id = ? AND type = ? LIMIT 1")) {
            statement.setInt(1, userId); statement.setInt(2, type);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getInt(1) : 0; }
        }
    }

    private static Map<Integer, Map<Integer, List<BattleBallPosition>>> immutableSpawns(
            Map<Integer, Map<Integer, List<BattleBallPosition>>> input) {
        Map<Integer, Map<Integer, List<BattleBallPosition>>> result = new HashMap<>();
        input.forEach((mapId, teams) -> {
            Map<Integer, List<BattleBallPosition>> teamCopy = new HashMap<>();
            teams.forEach((teamId, positions) -> teamCopy.put(teamId, List.copyOf(positions)));
            result.put(mapId, Map.copyOf(teamCopy));
        });
        return Map.copyOf(result);
    }
}
