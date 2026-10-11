package com.eu.habbo.habbohotel.leaderboards;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardManagerSnowStormTest {
    @Test
    void appendsSnowStormMetricsToEveryPeriodInsideCallerTransaction() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        new LeaderboardManager().appendSnowStormResults(connection,
                List.of(new LeaderboardManager.ScoreResult(42, 17, 1)), 1_725_000_000L);

        int periods = LeaderboardPeriod.values().length;
        verify(connection, times(periods * 3)).prepareStatement(anyString());
        verify(statement, times(periods * 3)).setString(1, LeaderboardManager.SNOWSTORM);
        verify(statement, never()).setString(1, LeaderboardManager.ALL_GAMES);
        verify(statement, times(periods * 3)).setInt(5, 42);
        verify(statement, times(periods)).setString(2, LeaderboardManager.POINTS);
        verify(statement, times(periods)).setString(2, LeaderboardManager.GAMES_PLAYED);
        verify(statement, times(periods)).setString(2, LeaderboardManager.WINS);
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void rejectsInvalidSnowStormOutcomeBeforeWriting() throws Exception {
        Connection connection = mock(Connection.class);

        assertThrows(SQLException.class, () -> new LeaderboardManager()
                .appendSnowStormResults(connection,
                        List.of(new LeaderboardManager.ScoreResult(42, 17, 2)), 1L));

        verify(connection, never()).prepareStatement(anyString());
    }
}
