package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Durable MariaDB 10.2 registry for shared definitions and Reference/Echo
 * edges. This lets read-only aliases resolve a persistent value while the
 * source room is unloaded.
 */
public final class WiredCrossRoomAliasRepository {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(WiredCrossRoomAliasRepository.class);
    private static final WiredCrossRoomAliasRepository INSTANCE =
            new WiredCrossRoomAliasRepository();

    private WiredCrossRoomAliasRepository() {
    }

    public static WiredCrossRoomAliasRepository instance() {
        return INSTANCE;
    }

    public boolean upsert(Entry entry) {
        if (!valid(entry) || Emulator.getDatabase() == null) {
            return false;
        }
        String sql = "INSERT INTO room_wired_variable_alias_definitions "
                + "(definition_item_id,room_id,variable_id,variable_type,holder_scope,"
                + "variable_name,availability,has_value,definition_version,definition_hash,"
                + "source_room_id,source_variable_id,read_only,updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                + "room_id=VALUES(room_id),variable_id=VALUES(variable_id),"
                + "variable_type=VALUES(variable_type),holder_scope=VALUES(holder_scope),"
                + "variable_name=VALUES(variable_name),availability=VALUES(availability),"
                + "has_value=VALUES(has_value),definition_version=VALUES(definition_version),"
                + "definition_hash=VALUES(definition_hash),source_room_id=VALUES(source_room_id),"
                + "source_variable_id=VALUES(source_variable_id),read_only=VALUES(read_only),"
                + "updated_at=VALUES(updated_at)";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, entry);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                LOGGER.error("Failed to persist Wired variable registry item {}",
                        entry.itemId(), exception);
                return false;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to open Wired variable registry transaction", exception);
            return false;
        }
    }

    public boolean syncSourceDefinition(WiredVariableDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.availabilityCode()
                != WiredVariableAvailability.SHARED_PERMANENT.code) {
            return executeDelete(definition.definitionItemId());
        }
        WiredVariableHolder.Scope scope = definition.holderScope();
        if (scope == null) {
            return false;
        }
        return upsert(new Entry(
                definition.definitionItemId(),
                definition.roomId(),
                definition.variableId(),
                definition.type(),
                scope.code,
                definition.name(),
                definition.availabilityCode(),
                definition.hasValue(),
                1,
                0,
                null,
                null,
                false,
                System.currentTimeMillis()));
    }

    public boolean delete(int itemId) {
        if (itemId <= 0) {
            return false;
        }
        return update("DELETE FROM room_wired_variable_alias_definitions "
                + "WHERE definition_item_id=?", itemId) > 0;
    }

    public Entry loadByAlias(int roomId, String variableId) {
        return one("SELECT * FROM room_wired_variable_alias_definitions "
                + "WHERE room_id=? AND variable_id=?", roomId, variableId);
    }

    public Entry loadByItem(int itemId) {
        return one("SELECT * FROM room_wired_variable_alias_definitions "
                + "WHERE definition_item_id=?", itemId);
    }

    public List<Entry> loadBySource(int roomId, String variableId) {
        if (roomId <= 0 || blank(variableId)) {
            return List.of();
        }
        return many("SELECT * FROM room_wired_variable_alias_definitions "
                + "WHERE source_room_id=? AND source_variable_id=?", roomId, variableId);
    }

    public PersistedValue loadPersistedValue(
            int roomId, String variableId, WiredVariableHolder holder) {
        if (roomId <= 0 || blank(variableId) || holder == null
                || Emulator.getDatabase() == null) {
            return null;
        }
        String sql = "SELECT d.definition_item_id,d.variable_type,d.holder_scope,"
                + "d.variable_name,d.availability,d.has_value,d.definition_version,"
                + "d.definition_hash,v.value,v.created_at,v.updated_at,v.revision "
                + "FROM room_wired_variable_alias_definitions d "
                + "JOIN room_wired_variable_values v "
                + "ON v.room_id=d.room_id AND v.variable_id=d.variable_id "
                + "WHERE d.room_id=? AND d.variable_id=? "
                + "AND v.scope_type=? AND v.holder_id=? LIMIT 1";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            statement.setInt(3, holder.scope().code);
            statement.setInt(4, holder.stableId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PersistedValue(
                        result.getInt(1),
                        WiredVariableType.fromCode(result.getInt(2)),
                        result.getInt(3),
                        result.getString(4),
                        result.getInt(5),
                        result.getBoolean(6),
                        result.getInt(7),
                        result.getInt(8),
                        holder,
                        result.getInt(9),
                        result.getLong(10),
                        result.getLong(11),
                        result.getLong(12));
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to read unloaded Wired variable {} from room {}",
                    variableId, roomId, exception);
            return null;
        }
    }

    /**
     * Atomically applies a Change Variable operation through an alias whose
     * source room is unloaded. The durable alias chain, write permission,
     * holder scope and room revision are checked inside one transaction.
     */
    public WiredVariableMutation transformAliasValue(
            int originRoomId,
            String aliasVariableId,
            WiredVariableHolder holder,
            ValueTransform transform) {
        return mutateAliasValue(originRoomId, aliasVariableId, holder,
                transform, false, false, true);
    }

    public WiredVariableMutation setAliasValue(
            int originRoomId,
            String aliasVariableId,
            WiredVariableHolder holder,
            int value,
            boolean onlyIfAbsent) {
        return mutateAliasValue(originRoomId, aliasVariableId, holder,
                current -> OptionalInt.of(value), onlyIfAbsent, false, false);
    }

    public WiredVariableMutation removeAliasValue(
            int originRoomId,
            String aliasVariableId,
            WiredVariableHolder holder) {
        return mutateAliasValue(originRoomId, aliasVariableId, holder,
                current -> OptionalInt.empty(), false, true, false);
    }

    private WiredVariableMutation mutateAliasValue(
            int originRoomId,
            String aliasVariableId,
            WiredVariableHolder holder,
            ValueTransform transform,
            boolean onlyIfAbsent,
            boolean remove, boolean numericTransform) {
        if (originRoomId <= 0 || blank(aliasVariableId)
                || holder == null || transform == null
                || Emulator.getDatabase() == null) {
            return null;
        }

        try (Connection connection =
                     Emulator.getDatabase().getDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                DurableTarget target = resolveWritableTarget(
                        connection, originRoomId, aliasVariableId, holder);
                if (target == null || numericTransform && !target.hasValue()) {
                    connection.rollback();
                    return null;
                }
                var environment = Emulator.getGameEnvironment();
                if (environment != null
                        && environment.getRoomManager().getRoom(target.roomId()) != null) {
                    connection.rollback();
                    return null;
                }

                long currentRevision =
                        lockRevision(connection, target.roomId());
                StoredValue current = lockValue(
                        connection, target.roomId(), target.variableId(), holder);
                if (remove && current == null) {
                    connection.rollback();
                    return null;
                }
                if (onlyIfAbsent && current != null) {
                    connection.rollback();
                    return null;
                }

                int before = current == null ? 0 : current.value();
                OptionalInt transformed = transform.apply(before);
                if (!remove && transformed.isEmpty()) {
                    connection.rollback();
                    return null;
                }
                int after = remove || !target.hasValue() ? 0 : transformed.getAsInt();
                if (!remove && current != null && before == after) {
                    connection.rollback();
                    return null;
                }

                long revision = advanceLockedRevision(
                        connection, target.roomId(), currentRevision);
                long now = System.currentTimeMillis();
                if (remove) {
                    deletePersistedValue(connection, target.roomId(),
                            target.variableId(), holder);
                } else {
                    upsertPersistedValue(connection, target.roomId(),
                            new WiredVariableValue(
                                    target.variableId(),
                                    holder,
                                    after,
                                    current == null ? now : current.createdAt(),
                                    now,
                                    revision));
                }
                connection.commit();

                return new WiredVariableMutation(
                        target.variableId(),
                        holder,
                        current == null ? null : before,
                        remove ? null : after,
                        remove
                                ? WiredVariableMutation.Kind.DELETED
                                : current == null
                                ? WiredVariableMutation.Kind.CREATED
                                : WiredVariableMutation.Kind.VALUE_CHANGED,
                        revision,
                        target.definitionItemId(),
                        WiredVariableMutation.CHANGE_ORIGIN_ANOTHER_ROOM);
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly(connection);
                LOGGER.error("Failed to mutate unloaded Wired alias {} in room {}",
                        aliasVariableId, originRoomId, exception);
                return null;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to open unloaded Wired alias transaction", exception);
            return null;
        }
    }

    private static DurableTarget resolveWritableTarget(
            Connection connection,
            int originRoomId,
            String aliasVariableId,
            WiredVariableHolder holder) throws SQLException {
        int roomId = originRoomId;
        String variableId = aliasVariableId;
        boolean readOnly = false;
        boolean followedAlias = false;
        Set<String> visited = new HashSet<>();

        for (int depth = 0; depth < WiredVariableAliasResolver.MAX_DEPTH; depth++) {
            if (!visited.add(roomId + ":" + variableId)) {
                return null;
            }
            Entry entry = selectEntry(connection, roomId, variableId);
            if (entry == null) {
                return null;
            }
            if (entry.sourceRoomId() != null) {
                followedAlias = true;
                readOnly |= entry.readOnly();
                roomId = entry.sourceRoomId();
                variableId = entry.sourceVariableId();
                continue;
            }

            if (!followedAlias
                    || readOnly
                    || entry.availability()
                    != WiredVariableAvailability.SHARED_PERMANENT.code
                    || entry.scope() != holder.scope().code
                    || (holder.scope() == WiredVariableHolder.Scope.FURNI
                    && roomId != originRoomId)) {
                return null;
            }
            return new DurableTarget(roomId, variableId, entry.itemId(), entry.hasValue());
        }
        return null;
    }

    private static Entry selectEntry(
            Connection connection, int roomId, String variableId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM room_wired_variable_alias_definitions "
                        + "WHERE room_id=? AND variable_id=? LIMIT 1 FOR UPDATE")) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? row(result) : null;
            }
        }
    }

    private static StoredValue lockValue(
            Connection connection,
            int roomId,
            String variableId,
            WiredVariableHolder holder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value,created_at FROM room_wired_variable_values "
                        + "WHERE room_id=? AND variable_id=? "
                        + "AND scope_type=? AND holder_id=? FOR UPDATE")) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            statement.setInt(3, holder.scope().code);
            statement.setInt(4, holder.stableId());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new StoredValue(result.getInt(1), result.getLong(2))
                        : null;
            }
        }
    }

    private static long lockRevision(
            Connection connection, int roomId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO room_wired_variable_revisions "
                        + "(room_id,revision,updated_at) VALUES (?,0,0)")) {
            insert.setInt(1, roomId);
            insert.executeUpdate();
        }

        try (PreparedStatement select = connection.prepareStatement(
                "SELECT revision FROM room_wired_variable_revisions "
                        + "WHERE room_id=? FOR UPDATE")) {
            select.setInt(1, roomId);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Missing Wired variable revision");
                }
                return result.getLong(1);
            }
        }
    }

    private static long advanceLockedRevision(
            Connection connection, int roomId, long current) throws SQLException {
        final long next;
        try {
            next = Math.addExact(current, 1L);
        } catch (ArithmeticException exception) {
            throw new SQLException("Wired variable revision overflow", exception);
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE room_wired_variable_revisions "
                        + "SET revision=?,updated_at=? WHERE room_id=?")) {
            update.setLong(1, next);
            update.setLong(2, System.currentTimeMillis());
            update.setInt(3, roomId);
            if (update.executeUpdate() != 1) {
                throw new SQLException("Failed to advance Wired variable revision");
            }
        }
        return next;
    }

    private static void upsertPersistedValue(
            Connection connection,
            int roomId,
            WiredVariableValue value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO room_wired_variable_values "
                        + "(room_id,variable_id,scope_type,holder_id,value,"
                        + "created_at,updated_at,revision) VALUES (?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE value=VALUES(value),"
                        + "created_at=VALUES(created_at),updated_at=VALUES(updated_at),"
                        + "revision=VALUES(revision)")) {
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

    private static void deletePersistedValue(
            Connection connection,
            int roomId,
            String variableId,
            WiredVariableHolder holder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_variable_values "
                        + "WHERE room_id=? AND variable_id=? "
                        + "AND scope_type=? AND holder_id=?")) {
            statement.setInt(1, roomId);
            statement.setString(2, variableId);
            statement.setInt(3, holder.scope().code);
            statement.setInt(4, holder.stableId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Failed to delete Wired variable value");
            }
        }
    }

    private static int update(String sql, int itemId) {
        if (Emulator.getDatabase() == null) {
            return -1;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("Failed to update Wired variable registry item {}", itemId, exception);
            return -1;
        }
    }

    /** Returns true when the statement succeeded, including when no stale row existed. */
    private static boolean executeDelete(int itemId) {
        return update("DELETE FROM room_wired_variable_alias_definitions "
                + "WHERE definition_item_id=?", itemId) >= 0;
    }

    private static Entry one(String sql, Object... parameters) {
        List<Entry> entries = many(sql, parameters);
        return entries.isEmpty() ? null : entries.getFirst();
    }

    private static List<Entry> many(String sql, Object... parameters) {
        if (Emulator.getDatabase() == null) {
            return List.of();
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Entry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(row(result));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to query Wired variable registry", exception);
            return List.of();
        }
    }

    private static Entry row(ResultSet result) throws SQLException {
        return new Entry(
                result.getInt("definition_item_id"),
                result.getInt("room_id"),
                result.getString("variable_id"),
                WiredVariableType.fromCode(result.getInt("variable_type")),
                result.getInt("holder_scope"),
                result.getString("variable_name"),
                result.getInt("availability"),
                result.getBoolean("has_value"),
                result.getInt("definition_version"),
                result.getInt("definition_hash"),
                (Integer) result.getObject("source_room_id"),
                result.getString("source_variable_id"),
                result.getBoolean("read_only"),
                result.getLong("updated_at"));
    }

    private static void bind(PreparedStatement statement, Entry entry) throws SQLException {
        statement.setInt(1, entry.itemId());
        statement.setInt(2, entry.roomId());
        statement.setString(3, entry.variableId());
        statement.setInt(4, entry.type().code);
        statement.setInt(5, entry.scope());
        statement.setString(6, entry.name());
        statement.setInt(7, entry.availability());
        statement.setBoolean(8, entry.hasValue());
        statement.setInt(9, entry.version());
        statement.setInt(10, entry.hash());
        if (entry.sourceRoomId() == null) {
            statement.setNull(11, Types.INTEGER);
        } else {
            statement.setInt(11, entry.sourceRoomId());
        }
        statement.setString(12, entry.sourceVariableId());
        statement.setBoolean(13, entry.readOnly());
        statement.setLong(14, entry.updatedAt());
    }

    private static boolean valid(Entry entry) {
        if (entry == null || entry.itemId() <= 0 || entry.roomId() <= 0
                || blank(entry.variableId()) || entry.type() == null
                || entry.type() == WiredVariableType.UNKNOWN
                || entry.scope() < 0 || entry.scope() > 2
                || entry.version() <= 0 || entry.updatedAt() < 0) {
            return false;
        }
        boolean alias = entry.type() == WiredVariableType.REFERENCE
                || entry.type() == WiredVariableType.ECHO;
        return alias
                ? entry.sourceRoomId() != null && !blank(entry.sourceVariableId())
                : entry.sourceRoomId() == null && entry.sourceVariableId() == null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > 64;
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            LOGGER.error("Failed to roll back Wired variable registry transaction",
                    rollbackFailure);
        }
    }

    public record Entry(
            int itemId,
            int roomId,
            String variableId,
            WiredVariableType type,
            int scope,
            String name,
            int availability,
            boolean hasValue,
            int version,
            int hash,
            Integer sourceRoomId,
            String sourceVariableId,
            boolean readOnly,
            long updatedAt) {
    }

    public record PersistedValue(
            int definitionItemId,
            WiredVariableType type,
            int scope,
            String name,
            int availability,
            boolean hasValue,
            int version,
            int hash,
            WiredVariableHolder holder,
            int value,
            long createdAt,
            long updatedAt,
            long revision) {
    }

    @FunctionalInterface
    public interface ValueTransform {
        OptionalInt apply(int current);
    }

    private record DurableTarget(int roomId, String variableId, int definitionItemId, boolean hasValue) {
    }

    private record StoredValue(int value, long createdAt) {
    }
}
