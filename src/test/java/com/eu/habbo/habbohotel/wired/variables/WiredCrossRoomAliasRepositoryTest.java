package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the real durable mutation path with only JDBC boundaries replaced. */
class WiredCrossRoomAliasRepositoryTest {
    @Test
    void unloadedValuelessCreationCommitsZeroToAuthoritativeSource() throws Exception {
        try (var fixture = new Transaction(false, false)) {
            var mutation = WiredCrossRoomAliasRepository.instance().setAliasValue(
                    99, "room:81", WiredVariableHolder.user(77), 999, true);
            assertNotNull(mutation);
            assertEquals("room:80", mutation.variableId());
            assertEquals(WiredVariableMutation.Kind.CREATED, mutation.kind());
            assertNull(mutation.beforeValue());
            assertEquals(0, mutation.afterValue());
            assertEquals(11, mutation.revision());
            assertEquals(WiredVariableMutation.CHANGE_ORIGIN_ANOTHER_ROOM, mutation.changeOrigin());
            verify(fixture.upsert).setInt(1, 100);
            verify(fixture.upsert).setString(2, "room:80");
            verify(fixture.upsert).setInt(5, 0);
            verify(fixture.upsert).executeUpdate();
            verify(fixture.connection).commit();
            verify(fixture.connection, never()).rollback();
        }
    }

    @Test
    void unloadedValuelessDeletionCommitsPresenceRemoval() throws Exception {
        try (var fixture = new Transaction(true, false)) {
            var mutation = WiredCrossRoomAliasRepository.instance().removeAliasValue(
                    99, "room:81", WiredVariableHolder.user(77));
            assertNotNull(mutation);
            assertEquals(WiredVariableMutation.Kind.DELETED, mutation.kind());
            assertEquals(0, mutation.beforeValue());
            assertNull(mutation.afterValue());
            verify(fixture.delete).setInt(1, 100);
            verify(fixture.delete).setString(2, "room:80");
            verify(fixture.delete).executeUpdate();
            verify(fixture.connection).commit();
        }
    }

    @Test
    void unloadedValuelessNumericTransformRollsBackWithoutExecutingOperand() throws Exception {
        try (var fixture = new Transaction(true, false)) {
            var transform = mock(WiredCrossRoomAliasRepository.ValueTransform.class);
            assertNull(WiredCrossRoomAliasRepository.instance().transformAliasValue(
                    99, "room:81", WiredVariableHolder.user(77), transform));
            verifyNoInteractions(transform);
            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
            verify(fixture.upsert, never()).executeUpdate();
            verify(fixture.delete, never()).executeUpdate();
        }
    }

    @Test
    void unloadedReadOnlyEdgeRejectsPresenceMutation() throws Exception {
        try (var fixture = new Transaction(false, true)) {
            assertNull(WiredCrossRoomAliasRepository.instance().setAliasValue(
                    99, "room:81", WiredVariableHolder.user(77), 999, true));
            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
            verify(fixture.upsert, never()).executeUpdate();
        }
    }

    private static final class Transaction implements AutoCloseable {
        final Connection connection = mock(Connection.class);
        final PreparedStatement upsert = mock(PreparedStatement.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        private final MockedStatic<Emulator> emulator;

        Transaction(boolean exists, boolean readOnly) throws Exception {
            Database database = mock(Database.class);
            HikariDataSource pool = mock(HikariDataSource.class);
            when(database.getDataSource()).thenReturn(pool);
            when(pool.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            PreparedStatement registry = mock(PreparedStatement.class);
            ResultSet aliasRow = entry(true, readOnly);
            ResultSet sourceRow = entry(false, false);
            when(registry.executeQuery()).thenReturn(aliasRow, sourceRow);
            PreparedStatement revision = mock(PreparedStatement.class);
            ResultSet revisionRow = mock(ResultSet.class);
            when(revisionRow.next()).thenReturn(true);
            when(revisionRow.getLong(1)).thenReturn(10L);
            when(revision.executeQuery()).thenReturn(revisionRow);
            PreparedStatement value = mock(PreparedStatement.class);
            ResultSet valueRow = mock(ResultSet.class);
            when(valueRow.next()).thenReturn(exists);
            when(valueRow.getInt(1)).thenReturn(0);
            when(valueRow.getLong(2)).thenReturn(1000L);
            when(value.executeQuery()).thenReturn(valueRow);
            PreparedStatement revisionUpdate = mock(PreparedStatement.class);
            when(revisionUpdate.executeUpdate()).thenReturn(1);
            when(delete.executeUpdate()).thenReturn(1);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                if (sql.startsWith("SELECT * FROM room_wired_variable_alias_definitions")) return registry;
                if (sql.startsWith("SELECT revision")) return revision;
                if (sql.startsWith("SELECT value")) return value;
                if (sql.startsWith("UPDATE room_wired_variable_revisions")) return revisionUpdate;
                if (sql.startsWith("INSERT INTO room_wired_variable_values")) return upsert;
                if (sql.startsWith("DELETE FROM room_wired_variable_values")) return delete;
                if (sql.startsWith("INSERT IGNORE INTO room_wired_variable_revisions")) return mock(PreparedStatement.class);
                throw new AssertionError("Unexpected SQL: " + sql);
            });
            emulator = mockStatic(Emulator.class);
            emulator.when(Emulator::getDatabase).thenReturn(database);
        }

        private static ResultSet entry(boolean alias, boolean readOnly) throws Exception {
            ResultSet row = mock(ResultSet.class);
            when(row.next()).thenReturn(true);
            when(row.getInt("definition_item_id")).thenReturn(alias ? 81 : 80);
            when(row.getInt("room_id")).thenReturn(alias ? 99 : 100);
            when(row.getString("variable_id")).thenReturn(alias ? "room:81" : "room:80");
            when(row.getInt("variable_type")).thenReturn((alias ? WiredVariableType.REFERENCE : WiredVariableType.USER).code);
            when(row.getInt("holder_scope")).thenReturn(WiredVariableHolder.Scope.USER.code);
            when(row.getInt("availability")).thenReturn(11);
            when(row.getInt("definition_version")).thenReturn(1);
            when(row.getObject("source_room_id")).thenReturn(alias ? Integer.valueOf(100) : null);
            when(row.getString("source_variable_id")).thenReturn(alias ? "room:80" : null);
            when(row.getBoolean("read_only")).thenReturn(readOnly);
            when(row.getBoolean("has_value")).thenReturn(false);
            return row;
        }

        @Override public void close() { emulator.close(); }
    }
}
