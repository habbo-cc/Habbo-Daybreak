package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Transactional JDBC persistence for the room-scoped variable manager. */
final class WiredVariableRepository implements WiredVariableManager.Repository {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableRepository.class);
    private static final WiredVariableRepository INSTANCE = new WiredVariableRepository();

    private WiredVariableRepository() {
    }

    static WiredVariableRepository instance() {
        return INSTANCE;
    }

    @Override
    public WiredVariableManager.LoadedRoom load(int roomId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // Every writer advances this row before touching definitions or
                // values. Holding the same lock prevents a room load from
                // combining a pre-mutation revision with post-mutation rows.
                ensureRevisionRow(connection, roomId);
                long revision = loadRevisionForUpdate(connection, roomId);
                Map<String, Integer> definitionHashes =
                        loadDefinitionHashes(connection, roomId);
                List<WiredVariableValue> values = loadValues(connection, roomId);
                connection.commit();
                return new WiredVariableManager.LoadedRoom(
                        true, revision, definitionHashes, values);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to load Wired variable manager for room {}", roomId, exception);
            return WiredVariableManager.LoadedRoom.failed();
        }
    }

    @Override
    public boolean commit(int roomId, long expectedRevision, long nextRevision,
                          WiredVariableManager.PersistenceMutation mutation) {
        if (roomId <= 0 || expectedRevision < 0L || nextRevision != expectedRevision + 1L
                || mutation == null) {
            return false;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureRevisionRow(connection, roomId);
                if (!advanceRevision(connection, roomId, expectedRevision, nextRevision)) {
                    connection.rollback();
                    return false;
                }
                apply(connection, roomId, mutation);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to persist Wired variable mutation for room {}", roomId, exception);
            return false;
        }
    }

    @Override
    public boolean deleteRoom(int roomId) {
        if (roomId <= 0) {
            return false;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteAliasDefinitionsForRoom(connection, roomId);
                deleteByRoom(connection, "room_wired_variable_values", roomId);
                deleteByRoom(connection, "room_wired_variable_definitions", roomId);
                deleteByRoom(connection, "room_wired_variable_revisions", roomId);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to delete Wired variable state for room {}", roomId, exception);
            return false;
        }
    }

    private static long loadRevisionForUpdate(Connection connection, int roomId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM room_wired_variable_revisions "
                        + "WHERE room_id = ? FOR UPDATE")) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Math.max(0L, set.getLong(1)) : 0L;
            }
        }
    }

    private static Map<String, Integer> loadDefinitionHashes(Connection connection, int roomId)
            throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT variable_id, definition_hash FROM room_wired_variable_definitions " +
                        "WHERE room_id = ? LIMIT " + (WiredVariableManager.MAX_DEFINITIONS + 1))) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (result.size() >= WiredVariableManager.MAX_DEFINITIONS) {
                        throw new SQLException("Wired variable definition limit exceeded");
                    }
                    result.put(set.getString(1), set.getInt(2));
                }
            }
        }
        return result;
    }

    private static List<WiredVariableValue> loadValues(Connection connection, int roomId)
            throws SQLException {
        List<WiredVariableValue> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT variable_id, scope_type, holder_id, value, created_at, updated_at, revision " +
                        "FROM room_wired_variable_values WHERE room_id = ? LIMIT " +
                        (WiredVariableManager.MAX_VALUES_PER_ROOM + 1))) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (result.size() >= WiredVariableManager.MAX_VALUES_PER_ROOM) {
                        throw new SQLException("Wired variable value limit exceeded");
                    }
                    WiredVariableHolder.Scope scope = WiredVariableHolder.Scope.fromCode(set.getInt("scope_type"));
                    if (scope == null) {
                        continue;
                    }
                    try {
                        result.add(new WiredVariableValue(
                                set.getString("variable_id"),
                                WiredVariableHolder.of(scope, set.getInt("holder_id")),
                                set.getInt("value"),
                                Math.max(0L, set.getLong("created_at")),
                                Math.max(0L, set.getLong("updated_at")),
                                Math.max(0L, set.getLong("revision"))));
                    } catch (IllegalArgumentException exception) {
                        LOGGER.warn("Ignoring invalid Wired variable holder row in room {}", roomId);
                    }
                }
            }
        }
        return result;
    }

    private static void ensureRevisionRow(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO room_wired_variable_revisions (room_id, revision) VALUES (?, 0)")) {
            statement.setInt(1, roomId);
            statement.executeUpdate();
        }
    }

    private static boolean advanceRevision(Connection connection, int roomId,
                                           long expectedRevision, long nextRevision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE room_wired_variable_revisions SET revision = ?, updated_at = ? " +
                        "WHERE room_id = ? AND revision = ?")) {
            statement.setLong(1, nextRevision);
            statement.setLong(2, System.currentTimeMillis());
            statement.setInt(3, roomId);
            statement.setLong(4, expectedRevision);
            return statement.executeUpdate() == 1;
        }
    }

    private static void apply(Connection connection, int roomId,
                              WiredVariableManager.PersistenceMutation mutation) throws SQLException {
        switch (mutation.kind) {
            case REVISION_ONLY -> { }
            case UPSERT_DEFINITION -> {
                upsertDefinition(connection, roomId, mutation.variableId, mutation.definitionHash);
                reconcileDefinitionValues(connection, roomId, mutation.variableId,
                        mutation.allowedScopeCode, mutation.retainValues);
                for (WiredVariableValue value : mutation.values) {
                    upsertValue(connection, roomId, value);
                }
            }
            case DELETE_DEFINITION -> deleteDefinition(connection, roomId, mutation.variableId);
            case UPSERT_VALUE -> upsertValue(connection, roomId, mutation.value);
            case DELETE_VALUE -> deleteValue(connection, roomId, mutation.variableId, mutation.holder);
            case DELETE_HOLDER -> deleteHolder(connection, roomId, mutation.holder);
            case RECONCILE -> reconcile(connection, roomId,
                    mutation.definitionHashes, mutation.values);
        }
    }

    private static void reconcile(Connection connection, int roomId,
                                  Map<String, Integer> definitionHashes,
                                  List<WiredVariableValue> values) throws SQLException {
        deleteByRoom(connection, "room_wired_variable_values", roomId);
        deleteByRoom(connection, "room_wired_variable_definitions", roomId);
        for (Map.Entry<String, Integer> definition : definitionHashes.entrySet()) {
            upsertDefinition(connection, roomId, definition.getKey(), definition.getValue());
        }
        for (WiredVariableValue value : values) {
            upsertValue(connection, roomId, value);
        }
    }

    private static void upsertDefinition(Connection connection, int roomId, String variableId,
                                         int definitionHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO room_wired_variable_definitions " +
                        "(room_id, variable_id, definition_hash) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE definition_hash = VALUES(definition_hash)")) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            statement.setInt(3, definitionHash);
            statement.executeUpdate();
        }
    }

    private static void deleteDefinition(Connection connection, int roomId, String variableId)
            throws SQLException {
        try (PreparedStatement values = connection.prepareStatement(
                "DELETE FROM room_wired_variable_values WHERE room_id = ? AND variable_id = ?");
             PreparedStatement definition = connection.prepareStatement(
                     "DELETE FROM room_wired_variable_definitions WHERE room_id = ? AND variable_id = ?")) {
            values.setInt(1, roomId);
            values.setString(2, variableId);
            values.executeUpdate();
            definition.setInt(1, roomId);
            definition.setString(2, variableId);
            definition.executeUpdate();
        }
    }

    private static void reconcileDefinitionValues(Connection connection, int roomId,
                                                  String variableId, Integer allowedScopeCode,
                                                  boolean retainValues) throws SQLException {
        String sql = retainValues && allowedScopeCode != null
                ? "DELETE FROM room_wired_variable_values WHERE room_id = ? AND variable_id = ? AND scope_type <> ?"
                : "DELETE FROM room_wired_variable_values WHERE room_id = ? AND variable_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            if (retainValues && allowedScopeCode != null) {
                statement.setInt(3, allowedScopeCode);
            }
            statement.executeUpdate();
        }
    }

    private static void upsertValue(Connection connection, int roomId, WiredVariableValue value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO room_wired_variable_values " +
                        "(room_id, variable_id, scope_type, holder_id, value, created_at, updated_at, revision) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "value = VALUES(value), created_at = VALUES(created_at), " +
                        "updated_at = VALUES(updated_at), revision = VALUES(revision)")) {
            statement.setInt(1, roomId);
            statement.setString(2, value.variableId());
            statement.setInt(3, value.holder().scope().code);
            statement.setInt(4, value.holder().stableId());
            statement.setInt(5, value.value());
            statement.setLong(6, value.createdAtMs());
            statement.setLong(7, value.updatedAtMs());
            statement.setLong(8, value.revision());
            statement.executeUpdate();
        }
    }

    private static void deleteValue(Connection connection, int roomId, String variableId,
                                    WiredVariableHolder holder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_variable_values WHERE room_id = ? AND variable_id = ? " +
                        "AND scope_type = ? AND holder_id = ?")) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            statement.setInt(3, holder.scope().code);
            statement.setInt(4, holder.stableId());
            statement.executeUpdate();
        }
    }

    private static void deleteHolder(Connection connection, int roomId, WiredVariableHolder holder)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_variable_values WHERE room_id = ? " +
                        "AND scope_type = ? AND holder_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, holder.scope().code);
            statement.setInt(3, holder.stableId());
            statement.executeUpdate();
        }
    }

    private static void deleteByRoom(Connection connection, String table, int roomId)
            throws SQLException {
        // Table is selected only from hard-coded call sites above.
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            statement.executeUpdate();
        }
    }

    private static void deleteAliasDefinitionsForRoom(Connection connection, int roomId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_variable_alias_definitions " +
                        "WHERE room_id = ? OR source_room_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, roomId);
            statement.executeUpdate();
        }
    }

}
