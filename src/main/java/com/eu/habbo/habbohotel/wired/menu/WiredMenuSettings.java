package com.eu.habbo.habbohotel.wired.menu;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent room policy used by July's Wired Menu settings tab. */
public record WiredMenuSettings(int modifyMask, int readMask, String timezone) {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredMenuSettings.class);
    private static final int VALID_MASK = 0x0F;
    private static final int EVERYONE = 1;
    private static final int RIGHTS = 1 << 1;
    private static final int GROUP_MEMBERS = 1 << 2;
    private static final int GROUP_ADMINS = 1 << 3;
    private static final Map<Integer, WiredMenuSettings> CACHE = new ConcurrentHashMap<>();

    public WiredMenuSettings {
        modifyMask = normalizeModify(modifyMask);
        readMask = normalizeRead(readMask, modifyMask);
        timezone = sanitizeTimezone(timezone);
    }

    public static WiredMenuSettings load(int roomId) {
        return CACHE.computeIfAbsent(roomId, WiredMenuSettings::loadUncached);
    }

    private static WiredMenuSettings loadUncached(int roomId) {
        String sql = "SELECT modify_permission_mask, read_permission_mask, timezone "
                + "FROM room_wired_settings WHERE room_id = ? LIMIT 1";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new WiredMenuSettings(result.getInt(1), result.getInt(2),
                            result.getString(3));
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load Wired Menu settings for room {}", roomId, exception);
        }
        return new WiredMenuSettings(1 << 3, VALID_MASK, "UTC");
    }

    public boolean save(int roomId) {
        String sql = "INSERT INTO room_wired_settings "
                + "(room_id, modify_permission_mask, read_permission_mask, timezone) "
                + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "modify_permission_mask = VALUES(modify_permission_mask), "
                + "read_permission_mask = VALUES(read_permission_mask), "
                + "timezone = VALUES(timezone)";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            statement.setInt(2, this.modifyMask);
            statement.setInt(3, this.readMask);
            statement.setString(4, this.timezone);
            boolean saved = statement.executeUpdate() > 0;
            if (saved) {
                CACHE.put(roomId, this);
            }
            return saved;
        } catch (Exception exception) {
            LOGGER.error("Failed to save Wired Menu settings for room {}", roomId, exception);
            return false;
        }
    }

    public boolean canModify(Room room, Habbo habbo) {
        return hasPermission(room, habbo, this.modifyMask);
    }

    public boolean canRead(Room room, Habbo habbo) {
        return this.canModify(room, habbo) || hasPermission(room, habbo, this.readMask);
    }

    private static boolean hasPermission(Room room, Habbo habbo, int mask) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null) {
            return false;
        }
        if (habbo.hasPermission(Permission.ACC_SUPERWIRED)
                || room.getOwnerId() == habbo.getHabboInfo().getId()) {
            return true;
        }
        if ((mask & EVERYONE) != 0) {
            return true;
        }
        if ((mask & RIGHTS) != 0 && room.hasRights(habbo)) {
            return true;
        }
        if (room.getGuildId() <= 0 || habbo.getHabboStats() == null
                || !habbo.getHabboStats().hasGuild(room.getGuildId())) {
            return false;
        }
        if ((mask & GROUP_MEMBERS) != 0) {
            return true;
        }
        return (mask & GROUP_ADMINS) != 0
                && room.getGuildRightLevel(habbo)
                .isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN);
    }

    private static int normalizeModify(int mask) {
        int normalized = mask & VALID_MASK & ~1;
        // July's settings UI models these as an inclusive hierarchy:
        // group members includes group admins, while group admins can stand alone.
        if ((normalized & (1 << 2)) != 0) {
            normalized |= 1 << 3;
        }
        return normalized;
    }

    private static int normalizeRead(int mask, int modifyMask) {
        int normalized = mask & VALID_MASK;
        if ((normalized & 1) != 0) {
            normalized = VALID_MASK;
        }
        if ((normalized & (1 << 2)) != 0) {
            normalized |= 1 << 3;
        }
        return normalized | modifyMask;
    }

    private static String sanitizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        String value = timezone.trim();
        return value.length() <= 64 && value.matches("[A-Za-z0-9_+\\-/:]+")
                ? value : "UTC";
    }
}
