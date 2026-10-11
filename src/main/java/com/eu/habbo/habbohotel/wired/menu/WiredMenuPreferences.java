package com.eu.habbo.habbohotel.wired.menu;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user fields appended by July to AccountPreferences.
 *
 * <p>The playtest rights behavior is adapted from Seth/iSetht's GPL-3.0
 * Creator Tools implementation, scoped here to July's per-user preference.</p>
 */
public record WiredMenuPreferences(boolean menuButton, boolean inspectButton,
                                   boolean playtestMode, boolean whisperDisabled,
                                   boolean allNotifications, String uiStyle) {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredMenuPreferences.class);
    private static final Map<Integer, WiredMenuPreferences> CACHE =
            new ConcurrentHashMap<>();

    public WiredMenuPreferences {
        uiStyle = sanitizeStyle(uiStyle);
    }

    public static WiredMenuPreferences load(int userId) {
        return CACHE.computeIfAbsent(userId, WiredMenuPreferences::loadUncached);
    }

    private static WiredMenuPreferences loadUncached(int userId) {
        String sql = "SELECT menu_button, inspect_button, playtest_mode, "
                + "whisper_disabled, all_notifications, ui_style "
                + "FROM users_wired_preferences WHERE user_id = ? LIMIT 1";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new WiredMenuPreferences(result.getBoolean(1),
                            result.getBoolean(2), result.getBoolean(3),
                            result.getBoolean(4), result.getBoolean(5),
                            result.getString(6));
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load Wired Menu preferences for user {}", userId, exception);
        }
        return defaults();
    }

    public boolean save(int userId) {
        String sql = "INSERT INTO users_wired_preferences "
                + "(user_id, menu_button, inspect_button, playtest_mode, "
                + "whisper_disabled, all_notifications, ui_style) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "menu_button=VALUES(menu_button), inspect_button=VALUES(inspect_button), "
                + "playtest_mode=VALUES(playtest_mode), whisper_disabled=VALUES(whisper_disabled), "
                + "all_notifications=VALUES(all_notifications), ui_style=VALUES(ui_style)";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setBoolean(2, this.menuButton);
            statement.setBoolean(3, this.inspectButton);
            statement.setBoolean(4, this.playtestMode);
            statement.setBoolean(5, this.whisperDisabled);
            statement.setBoolean(6, this.allNotifications);
            statement.setString(7, this.uiStyle);
            boolean saved = statement.executeUpdate() > 0;
            if (saved) {
                CACHE.put(userId, this);
            }
            return saved;
        } catch (Exception exception) {
            LOGGER.error("Failed to save Wired Menu preferences for user {}", userId, exception);
            return false;
        }
    }

    public static WiredMenuPreferences defaults() {
        return new WiredMenuPreferences(true, true, false, false, false, "");
    }

    /** July's per-user playtest toggle temporarily removes room-owner rights. */
    public static boolean isPlaytesting(int userId) {
        return userId > 0 && load(userId).playtestMode();
    }

    public static void evict(int userId) {
        if (userId > 0) {
            CACHE.remove(userId);
        }
    }

    private static String sanitizeStyle(String style) {
        if (style == null || style.isBlank()) {
            return "";
        }
        String value = style.trim().toLowerCase();
        return value.length() <= 32 && value.matches("[a-z0-9_-]+") ? value : "";
    }
}
