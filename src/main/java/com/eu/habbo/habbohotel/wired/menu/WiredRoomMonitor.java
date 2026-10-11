package com.eu.habbo.habbohotel.wired.menu;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, room-scoped diagnostics and user-authored entries for the Wired Menu.
 *
 * The persistent layout and bounded newest-first cache are adapted from Seth's
 * {@code WiredCreatorToolsLogManager}; this service is deliberately independent
 * of Creator Tools. July's Write to Logs Wired actions use the same room log
 * stream with the official WIRED source value.
 */
public final class WiredRoomMonitor {
    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_WARN = 1;
    public static final int LEVEL_ERROR = 2;
    public static final int LEVEL_DEBUG = 3;
    public static final int SOURCE_SYSTEM = 0;
    public static final int SOURCE_WIRED = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredRoomMonitor.class);
    private static final int MAX_LOGS = 200;
    private static final int MAX_ERRORS = 100;
    private static final int MAX_DIAGNOSTIC_MESSAGE = 255;
    private static final int MAX_LOG_MESSAGE = 400;
    private static final List<OfficialError> OFFICIAL_ERRORS = List.of(
            new OfficialError(0, "EXECUTION_CAP", "ERROR"),
            new OfficialError(1, "DELAYED_EVENTS_CAP", "ERROR"),
            new OfficialError(2, "EXECUTOR_OVERLOAD", "ERROR"),
            new OfficialError(3, "MARKED_AS_HEAVY", "WARNING"),
            new OfficialError(4, "KILLED", "ERROR"),
            new OfficialError(5, "RECURSION_TIMEOUT", "ERROR"),
            new OfficialError(6, "TOO_MANY_VARIABLES", "ERROR"));
    private static final Map<Integer, RoomState> ROOMS = new ConcurrentHashMap<>();

    private WiredRoomMonitor() {
    }

    public static void runtimeError(Room room, String source, Throwable throwable) {
        if (room == null || throwable == null) {
            return;
        }
        recordError(room, -1, LEVEL_ERROR, SOURCE_WIRED,
                bounded(throwable.getClass().getSimpleName(), 64),
                normalizeSource(source),
                bounded(throwable.getMessage(), MAX_DIAGNOSTIC_MESSAGE));
    }

    public static void executionCap(Room room) {
        official(room, 0);
    }

    public static void delayedEventsCap(Room room) {
        official(room, 1);
    }

    public static void executorOverload(Room room) {
        official(room, 2);
    }

    public static void markedAsHeavy(Room room) {
        official(room, 3);
    }

    public static void killed(Room room) {
        official(room, 4);
    }

    public static void recursionTimeout(Room room) {
        official(room, 5);
    }

    public static void tooManyVariables(Room room) {
        official(room, 6);
    }

    private static void official(Room room, int id) {
        if (id < 0 || id >= OFFICIAL_ERRORS.size()) {
            return;
        }
        OfficialError error = OFFICIAL_ERRORS.get(id);
        int level = "WARNING".equals(error.category()) ? LEVEL_WARN : LEVEL_ERROR;
        recordError(room, error.id(), level, SOURCE_SYSTEM,
                error.name(), error.category(), "");
    }

    /** Records one July Write to Logs action entry in the room's bounded stream. */
    public static void wiredLog(Room room, int level, String message) {
        if (room == null) {
            return;
        }
        int normalizedLevel = level >= LEVEL_INFO && level <= LEVEL_DEBUG
                ? level : LEVEL_INFO;
        long now = System.currentTimeMillis();
        RoomState state = ROOMS.computeIfAbsent(room.getId(), WiredRoomMonitor::load);
        LogEntry log;
        synchronized (state) {
            log = new LogEntry(
                    state.nextLogId++,
                    normalizedLevel,
                    SOURCE_WIRED,
                    bounded(message, MAX_LOG_MESSAGE),
                    now);
            state.logs.addFirst(log);
            while (state.logs.size() > MAX_LOGS) {
                state.logs.removeLast();
            }
        }
        persistLog(room.getId(), log);
    }

    private static void recordError(Room room, int officialId, int level,
                                    int logSource, String name,
                                    String source, String message) {
        if (room == null) {
            return;
        }
        name = bounded(name, 64);
        message = bounded(message, MAX_DIAGNOSTIC_MESSAGE);
        String normalizedSource = normalizeSource(source);
        long now = System.currentTimeMillis();
        RoomState state = ROOMS.computeIfAbsent(room.getId(), WiredRoomMonitor::load);
        ErrorEntry error;
        LogEntry log;
        synchronized (state) {
            String key = normalizedSource + '\0' + name + '\0' + message;
            MutableError aggregate = state.errors.get(key);
            if (aggregate != null && officialId >= 0
                    && now - aggregate.lastAt < 1_000L) {
                aggregate.count++;
                aggregate.lastAt = now;
                return;
            }
            if (aggregate == null) {
                int id = officialId >= 0 ? officialId : state.nextErrorId++;
                aggregate = new MutableError(id, name, normalizedSource,
                        message, 0, now, now);
                state.errors.put(key, aggregate);
                trimErrors(state);
            }
            aggregate.count++;
            aggregate.lastAt = now;
            error = aggregate.snapshot();
            log = new LogEntry(state.nextLogId++, level, logSource,
                    bounded(normalizedSource + ": " + name
                            + (message.isEmpty() ? "" : " - " + message),
                            MAX_DIAGNOSTIC_MESSAGE),
                    now);
            state.logs.addFirst(log);
            while (state.logs.size() > MAX_LOGS) {
                state.logs.removeLast();
            }
        }
        persist(room.getId(), log, error);
    }

    public static List<ErrorEntry> errors(Room room) {
        if (room == null) {
            return List.of();
        }
        RoomState state = ROOMS.computeIfAbsent(room.getId(), WiredRoomMonitor::load);
        synchronized (state) {
            Map<Integer, ErrorEntry> recordedById = new LinkedHashMap<>();
            state.errors.values().stream()
                    .map(MutableError::snapshot)
                    .sorted(Comparator.comparingLong(ErrorEntry::lastAt).reversed())
                    .forEach(error -> recordedById.putIfAbsent(error.id(), error));
            List<ErrorEntry> result = new ArrayList<>();
            for (OfficialError definition : OFFICIAL_ERRORS) {
                ErrorEntry recorded = recordedById.remove(definition.id());
                result.add(new ErrorEntry(
                        definition.id(),
                        definition.name(),
                        definition.category(),
                        "",
                        recorded == null ? 0 : recorded.count(),
                        recorded == null ? -1L : recorded.firstAt(),
                        recorded == null ? -1L : recorded.lastAt()));
            }
            recordedById.values().stream()
                    .limit(Math.max(0, MAX_ERRORS - result.size()))
                    .forEach(result::add);
            return List.copyOf(result);
        }
    }

    public static LogPage logs(Room room, int page, int amount, int level,
                               int source, String query) {
        if (room == null) {
            return new LogPage(0, List.of());
        }
        String needle = bounded(query, 64).toLowerCase(Locale.ROOT);
        RoomState state = ROOMS.computeIfAbsent(room.getId(), WiredRoomMonitor::load);
        synchronized (state) {
            List<LogEntry> matching = state.logs.stream()
                    .filter(entry -> level < 0 || entry.level() == level)
                    .filter(entry -> source < 0 || entry.source() == source)
                    .filter(entry -> needle.isEmpty()
                            || entry.message().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
            long offsetLong = (long) (page - 1) * amount;
            int from = (int) Math.min(matching.size(), offsetLong);
            return new LogPage(matching.size(), List.copyOf(matching.subList(
                    from, Math.min(matching.size(), from + amount))));
        }
    }

    public static void clearErrors(Room room) {
        if (room == null) {
            return;
        }
        RoomState state = ROOMS.computeIfAbsent(room.getId(), WiredRoomMonitor::load);
        synchronized (state) {
            state.errors.clear();
            state.nextErrorId = 1000;
        }
        try (Connection connection = connection();
             PreparedStatement errors = connection.prepareStatement(
                     "DELETE FROM room_wired_monitor_errors WHERE room_id = ?")) {
            errors.setInt(1, room.getId());
            errors.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to clear Wired monitor errors for room {}: {}",
                    room.getId(), exception.getMessage());
        }
    }

    private static RoomState load(int roomId) {
        RoomState state = new RoomState();
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, level, source, message, created_at "
                            + "FROM room_wired_monitor_logs WHERE room_id = ? "
                            + "ORDER BY id DESC LIMIT ?")) {
                statement.setInt(1, roomId);
                statement.setInt(2, MAX_LOGS);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long id = result.getLong(1);
                        state.logs.addLast(new LogEntry(id, result.getInt(2),
                                result.getInt(3), result.getString(4), result.getLong(5)));
                        state.nextLogId = Math.max(state.nextLogId, id + 1);
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT monitor_id, error_name, category, message, occurrence_count, "
                            + "first_at, last_at FROM room_wired_monitor_errors "
                            + "WHERE room_id = ? ORDER BY last_at DESC LIMIT ?")) {
                statement.setInt(1, roomId);
                statement.setInt(2, MAX_ERRORS);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        MutableError error = new MutableError(result.getInt(1),
                                result.getString(2), result.getString(3),
                                result.getString(4), result.getInt(5),
                                result.getLong(6), result.getLong(7));
                        state.errors.put(error.key(), error);
                        if (error.id >= 1000) {
                            state.nextErrorId = Math.max(
                                    state.nextErrorId, error.id + 1);
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load Wired monitor for room {}: {}",
                    roomId, exception.getMessage());
        }
        return state;
    }

    private static void persist(int roomId, LogEntry log, ErrorEntry error) {
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO room_wired_monitor_logs "
                            + "(room_id, level, source, message, created_at) VALUES (?, ?, ?, ?, ?)")) {
                statement.setInt(1, roomId);
                statement.setInt(2, log.level());
                statement.setInt(3, log.source());
                statement.setString(4, log.message());
                statement.setLong(5, log.timestamp());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO room_wired_monitor_errors "
                            + "(room_id, monitor_id, error_name, category, message, "
                            + "occurrence_count, first_at, last_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE occurrence_count = VALUES(occurrence_count), "
                            + "last_at = VALUES(last_at)")) {
                statement.setInt(1, roomId);
                statement.setInt(2, error.id());
                statement.setString(3, error.name());
                statement.setString(4, error.category());
                statement.setString(5, error.message());
                statement.setInt(6, error.count());
                statement.setLong(7, error.firstAt());
                statement.setLong(8, error.lastAt());
                statement.executeUpdate();
            }
            trim(connection, roomId);
        } catch (SQLException exception) {
            LOGGER.warn("Failed to persist Wired monitor for room {}: {}",
                    roomId, exception.getMessage());
        }
    }

    private static void persistLog(int roomId, LogEntry log) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO room_wired_monitor_logs "
                             + "(room_id, level, source, message, created_at) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, roomId);
            statement.setInt(2, log.level());
            statement.setInt(3, log.source());
            statement.setString(4, log.message());
            statement.setLong(5, log.timestamp());
            statement.executeUpdate();
            trimPersistedLogs(connection, roomId);
        } catch (SQLException exception) {
            LOGGER.warn("Failed to persist Wired log for room {}: {}",
                    roomId, exception.getMessage());
        }
    }

    private static void trim(Connection connection, int roomId) throws SQLException {
        trimPersistedLogs(connection, roomId);
        trimPersistedErrors(connection, roomId);
    }

    private static void trimPersistedLogs(Connection connection, int roomId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_monitor_logs WHERE room_id = ? AND id NOT IN "
                        + "(SELECT id FROM (SELECT id FROM room_wired_monitor_logs "
                        + "WHERE room_id = ? ORDER BY id DESC LIMIT ?) newest)")) {
            statement.setInt(1, roomId);
            statement.setInt(2, roomId);
            statement.setInt(3, MAX_LOGS);
            statement.executeUpdate();
        }
    }

    private static void trimPersistedErrors(Connection connection, int roomId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_monitor_errors WHERE room_id = ? AND monitor_id NOT IN "
                        + "(SELECT monitor_id FROM (SELECT monitor_id FROM room_wired_monitor_errors "
                        + "WHERE room_id = ? ORDER BY last_at DESC LIMIT ?) newest)")) {
            statement.setInt(1, roomId);
            statement.setInt(2, roomId);
            statement.setInt(3, MAX_ERRORS);
            statement.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            throw new SQLException("database unavailable");
        }
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static void trimErrors(RoomState state) {
        while (state.errors.size() > MAX_ERRORS) {
            String oldest = state.errors.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().lastAt))
                    .map(Map.Entry::getKey).orElse(null);
            if (oldest == null) {
                return;
            }
            state.errors.remove(oldest);
        }
    }

    private static String normalizeSource(String source) {
        String value = bounded(source, 32).trim();
        return value.isEmpty() ? "ENGINE" : value.toUpperCase(Locale.ROOT);
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class RoomState {
        private final Deque<LogEntry> logs = new ArrayDeque<>();
        private final Map<String, MutableError> errors = new LinkedHashMap<>();
        private long nextLogId = 1;
        private int nextErrorId = 1000;
    }

    private static final class MutableError {
        private final int id;
        private final String name;
        private final String category;
        private final String message;
        private int count;
        private final long firstAt;
        private long lastAt;

        private MutableError(int id, String name, String category, String message,
                             int count, long firstAt, long lastAt) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.message = message;
            this.count = count;
            this.firstAt = firstAt;
            this.lastAt = lastAt;
        }

        private String key() {
            return category + '\0' + name + '\0' + message;
        }

        private ErrorEntry snapshot() {
            return new ErrorEntry(id, name, category, message, count, firstAt, lastAt);
        }
    }

    public record LogEntry(long id, int level, int source, String message, long timestamp) {
        public String timestampText() {
            return Instant.ofEpochMilli(timestamp).toString();
        }
    }

    public record ErrorEntry(int id, String name, String category, String message,
                             int count, long firstAt, long lastAt) {
        /** July serializes milliseconds since the most recent occurrence. */
        public long msSinceLastOccurrence() {
            return count <= 0 || lastAt < 0
                    ? -1L
                    : Math.max(0L, System.currentTimeMillis() - lastAt);
        }
    }

    private record OfficialError(int id, String name, String category) {
    }

    public record LogPage(int total, List<LogEntry> entries) {
    }
}
