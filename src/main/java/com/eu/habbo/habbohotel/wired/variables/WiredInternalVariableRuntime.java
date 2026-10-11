package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionInvisControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionInvisibleFurni;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * July AIR-compatible internal {@code @...} variables.
 *
 * <p>The inventory and runtime meanings are adapted from Seth/iSetht's
 * GPL-3.0 {@code WiredInternalVariableHelper} and Creator Tools inspection
 * providers. The wire representation is deliberately different: July AIR
 * advertises these as variable type {@code 1} through the ordinary variable
 * catalogue and represents values as signed 32-bit integers.</p>
 */
public final class WiredInternalVariableRuntime {
    public static final int TARGET_FURNI = 0;
    public static final int TARGET_USER = 1;
    public static final int TARGET_GLOBAL = -10;
    public static final int TARGET_CONTEXT = -20;
    public static final int AVAILABILITY_INTERNAL = 999;

    private static final List<Definition> DEFINITIONS = buildDefinitions();
    private static final Map<String, Definition> BY_ID_AND_TARGET = index(DEFINITIONS);

    private WiredInternalVariableRuntime() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition definition(String variableId, int target) {
        return BY_ID_AND_TARGET.get(key(variableId, target));
    }

    public static boolean matches(String variableId, int target) {
        return definition(variableId, target) != null;
    }

    public static Integer read(Room room, String variableId,
                               WiredVariableHolder holder) {
        if (room == null || variableId == null || holder == null) {
            return null;
        }
        Definition definition = definition(variableId, target(holder));
        if (definition == null) {
            return null;
        }
        return switch (holder.scope()) {
            case FURNI -> furniValues(
                    room, room.getHabboItemByDatabaseId(holder.stableId()))
                    .get(definition.name());
            case USER -> {
                Habbo habbo = room.getHabbo(holder.stableId());
                yield habbo == null ? null
                        : unitValues(room, habbo.getRoomUnit()).get(definition.name());
            }
            case ROOM -> globalValues(room).get(definition.name());
        };
    }

    /** Converts provider names to the opaque, target-unique IDs sent to AIR. */
    public static Map<String, Integer> inspectionValues(
            int target, Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Definition definition : DEFINITIONS) {
            if (definition.target() == target
                    && values.containsKey(definition.name())) {
                result.put(definition.variableId(), values.get(definition.name()));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Integer> furniValues(Room room, HabboItem item) {
        if (room == null || item == null || item.getBaseItem() == null
                || room.getHabboItemByDatabaseId(item.getId()) != item) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        values.put("@id", item.getId());
        values.put("@class_id", item.getBaseItem().getId());
        values.put("@height", scaled(item.getBaseItem().getHeight()));
        values.put("@state", parseInt(item.getExtradata()));
        values.put("@position.x", (int) item.getX());
        values.put("@position.y", (int) item.getY());
        values.put("@rotation", item.getRotation());
        values.put("@altitude", scaled(item.getZ()));
        values.put("@type", item.getId() < 0 ? 2 : 0);
        values.put("@dimensions.x", item.getBaseItem().getWidth());
        values.put("@dimensions.y", item.getBaseItem().getLength());
        values.put("@owner_id", item.getUserId());
        if (item instanceof InteractionInvisibleFurni
                && InteractionInvisControl.isInvisibleFurniHidden(room)) {
            values.put("@is_invisible", 1);
        }
        if (item.getBaseItem().allowStack()) {
            values.put("@is_stackable", 1);
        }
        if (item.getBaseItem().allowWalk()) {
            values.put("@can_stand_on", 1);
        }
        if (item.getBaseItem().allowSit()) {
            values.put("@can_sit_on", 1);
        }
        if (item.getBaseItem().allowLay()) {
            values.put("@can_lay_on", 1);
        }
        WiredMovementAddonRuntime.appendProjectileVariables(room, item, values);
        return immutableSorted(values);
    }

    public static Map<String, Integer> unitValues(Room room, RoomUnit unit) {
        if (room == null || unit == null || unit.getRoom() != room
                || !unit.isInRoom() || !room.getRoomUnits().contains(unit)) {
            return Map.of();
        }
        Habbo habbo = room.getHabbo(unit);
        Bot bot = room.getBot(unit);
        Pet pet = room.getPet(unit);
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        values.put("@index", unit.getId());
        values.put("@type", unit.getRoomUnitType().getTypeId());
        values.put("@gender", gender(habbo, bot));
        values.put("@position.x", (int) unit.getX());
        values.put("@position.y", (int) unit.getY());
        values.put("@direction", unit.getBodyRotation().getValue());
        values.put("@altitude", scaled(unit.getZ()));
        if (unit.getHandItem() > 0) {
            values.put("@handitem", unit.getHandItem());
        }
        if (unit.getEffectId() > 0) {
            values.put("@effect", unit.getEffectId());
        }
        if (unit.getDanceType() != null && unit.getDanceType().getType() > 0) {
            values.put("@dance", unit.getDanceType().getType());
        }
        if (unit.hasStatus(RoomUnitStatus.SIGN)) {
            values.put("@sign", parseInt(unit.getStatus(RoomUnitStatus.SIGN)));
        }
        if (unit.isIdle()) {
            values.put("@is_idle", 1);
        }

        if (habbo != null && habbo.getHabboInfo() != null) {
            values.put("@user_id", habbo.getHabboInfo().getId());
            if (habbo.getHabboStats() != null) {
                values.put("@achievement_score",
                        habbo.getHabboStats().getAchievementScore());
                if (habbo.getHabboStats().hasActiveClub()) {
                    values.put("@is_hc", 1);
                }
                if (habbo.getHabboStats().guild > 0) {
                    values.put("@favourite_group_id", habbo.getHabboStats().guild);
                }
            }
            if (room.hasRights(habbo)) {
                values.put("@has_rights", 1);
            }
            if (room.getGuildRightLevel(habbo)
                    .isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN)) {
                values.put("@is_group_admin", 1);
            }
            if (room.isOwner(habbo)) {
                values.put("@is_owner", 1);
            }
            if (room.isMuted(habbo)) {
                values.put("@is_muted", 1);
            }
            if (room.getActiveTradeForHabbo(habbo) != null) {
                values.put("@is_trading", 1);
            }
            if (habbo.getHabboInfo().getGamePlayer() != null
                    && habbo.getHabboInfo().getGamePlayer().getTeamColor() != null) {
                values.put("@team.score",
                        habbo.getHabboInfo().getGamePlayer().getScore());
                values.put("@team.color",
                        habbo.getHabboInfo().getGamePlayer().getTeamColor().type);
            }
        } else if (bot != null) {
            values.put("@bot_id", bot.getId());
        } else if (pet != null) {
            values.put("@pet_id", pet.getId());
        }
        return immutableSorted(values);
    }

    public static Map<String, Integer> globalValues(Room room) {
        if (room == null) {
            return Map.of();
        }
        ZonedDateTime now = ZonedDateTime.ofInstant(
                Instant.now(), timezone(room));
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        values.put("@furni_count",
                room.getFloorItems().size() + room.getWallItems().size());
        values.put("@user_count", room.getUserCount());
        long elapsedHalfSeconds = Math.max(0L,
                System.currentTimeMillis() - room.getLastTimerReset() * 1000L) / 500L;
        values.put("@wired_timer", saturating(elapsedHalfSeconds));
        appendTeam(values, room, "red", GameTeamColors.RED);
        appendTeam(values, room, "green", GameTeamColors.GREEN);
        appendTeam(values, room, "blue", GameTeamColors.BLUE);
        appendTeam(values, room, "yellow", GameTeamColors.YELLOW);
        values.put("@room_id", room.getId());
        values.put("@group_id", room.getGuildId());
        // AIR variable values are int32. Epoch seconds retains official time
        // semantics without Seth's Nitro-only 64-bit millisecond string.
        values.put("@current_time", saturating(now.toEpochSecond()));
        values.put("@current_time.milliseconds_of_seconds",
                now.getNano() / 1_000_000);
        values.put("@current_time.seconds_of_minute", now.getSecond());
        values.put("@current_time.minute_of_hour", now.getMinute());
        values.put("@current_time.hour_of_day", now.getHour());
        values.put("@current_time.day_of_week", now.getDayOfWeek().getValue());
        values.put("@current_time.day_of_month", now.getDayOfMonth());
        values.put("@current_time.day_of_year", now.getDayOfYear());
        values.put("@current_time.week_of_year",
                now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        values.put("@current_time.month_of_year", now.getMonthValue());
        values.put("@current_time.year", now.getYear());
        return immutableSorted(values);
    }

    public static boolean write(Room room, String variableId,
                                WiredVariableHolder holder, int value) {
        return write(null, room, variableId, holder, value);
    }

    /** Applies a Wired action mutation with its movement add-ons and 7115 batch. */
    public static boolean write(WiredContext context, String variableId,
                                WiredVariableHolder holder, int value) {
        return context != null && write(
                context, context.room(), variableId, holder, value);
    }

    private static boolean write(WiredContext context, Room room,
                                 String variableId,
                                 WiredVariableHolder holder, int value) {
        if (room == null || holder == null) {
            return false;
        }
        Definition definition = definition(variableId, target(holder));
        if (definition == null || !definition.writable()) {
            return false;
        }
        return switch (holder.scope()) {
            case FURNI -> writeFurni(context, room,
                    room.getHabboItemByDatabaseId(holder.stableId()),
                    definition.name(), value);
            case USER -> {
                Habbo habbo = room.getHabbo(holder.stableId());
                yield habbo != null && writeUnit(
                        context, room, habbo.getRoomUnit(),
                        definition.name(), value);
            }
            case ROOM -> writeGlobal(room, definition.name(), value);
        };
    }

    public static boolean writeUnit(Room room, RoomUnit unit,
                                    String variableId, int value) {
        return writeUnit(null, room, unit, variableId, value);
    }

    public static boolean writeUnit(WiredContext context, RoomUnit unit,
                                    String variableId, int value) {
        return context != null && writeUnit(
                context, context.room(), unit, variableId, value);
    }

    private static boolean writeUnit(
            WiredContext context, Room room, RoomUnit unit,
            String variableId, int value) {
        if (room == null || unit == null || unit.getRoom() != room
                || !unit.isInRoom()) {
            return false;
        }
        if ("@direction".equals(variableId)) {
            if (value < 0 || value > 7) {
                return false;
            }
            if (context != null) {
                return WiredMovementAddonRuntime.updateUserDirection(
                        context, unit, value);
            }
            unit.setRotation(RoomUserRotation.fromValue(value));
            unit.statusUpdate(true);
            return true;
        }
        if ("@altitude".equals(variableId)) {
            double z = Math.max(0D,
                    Math.min(Room.MAXIMUM_FURNI_HEIGHT, value / 100D));
            if (context != null) {
                return WiredMovementAddonRuntime.moveUserAltitude(
                        context, unit, z);
            }
            unit.setPreviousLocationZ(unit.getZ());
            unit.setZ(z);
            unit.statusUpdate(true);
            return true;
        }
        if ("@handitem".equals(variableId)) {
            if (value < 0 || value > 10_000) {
                return false;
            }
            room.giveHandItem(unit, value);
            return true;
        }
        if ("@effect".equals(variableId)) {
            if (value < 0 || value > 100_000) {
                return false;
            }
            room.giveEffect(unit, value, Integer.MAX_VALUE);
            return true;
        }
        if (!"@position.x".equals(variableId)
                && !"@position.y".equals(variableId)) {
            return false;
        }
        int x = "@position.x".equals(variableId) ? value : unit.getX();
        int y = "@position.y".equals(variableId) ? value : unit.getY();
        if (!validCoordinate(x) || !validCoordinate(y)) {
            return false;
        }
        RoomTile tile = room.getLayout() == null ? null
                : room.getLayout().getTile((short) x, (short) y);
        if (tile == null || tile.state == RoomTileState.INVALID) {
            return false;
        }
        if (context != null) {
            return WiredMovementAddonRuntime.moveUser(
                    context, unit, tile, 0);
        }
        RoomTile previous = unit.getCurrentLocation();
        double previousZ = unit.getZ();
        unit.setLocation(tile);
        // RoomUnit#setLocation initializes all location fields for spawning and
        // therefore points previousLocation at the destination. Restore the
        // actual origin for movement serialization and roller/animation logic.
        unit.setPreviousLocation(previous);
        unit.setPreviousLocationZ(previousZ);
        unit.setZ(tile.getStackHeight());
        unit.statusUpdate(true);
        if (previous != null) {
            room.updateHabbosAt(previous.x, previous.y);
            room.updateBotsAt(previous.x, previous.y);
        }
        room.updateHabbosAt(tile.x, tile.y);
        room.updateBotsAt(tile.x, tile.y);
        return true;
    }

    private static boolean writeFurni(
                                      WiredContext context,
                                      Room room, HabboItem item,
                                      String variableId, int value) {
        if (item == null || item.getBaseItem() == null
                || room.getHabboItemByDatabaseId(item.getId()) != item) {
            return false;
        }
        if ("@state".equals(variableId)) {
            int states = Math.max(0, item.getBaseItem().getStateCount());
            if (value < 0 || value > states) {
                return false;
            }
            item.setExtradata(String.valueOf(value));
            room.updateItemState(item);
            return true;
        }
        if ("@altitude".equals(variableId)) {
            if (item.getBaseItem().getType() == FurnitureType.WALL) {
                return false;
            }
            double z = Math.max(0D,
                    Math.min(Room.MAXIMUM_FURNI_HEIGHT, value / 100D));
            if (context != null) {
                RoomTile tile = room.getLayout() == null ? null
                        : room.getLayout().getTile(item.getX(), item.getY());
                if (tile == null) {
                    return false;
                }
                double oldZ = item.getZ();
                if (Double.compare(oldZ, z) == 0) {
                    return true;
                }
                item.setZ(z);
                WiredMovementAddonRuntime.moved(
                        context, room, item, tile, oldZ, tile);
                if (Double.compare(oldZ, item.getZ()) != 0) {
                    item.needsUpdate(true);
                    Emulator.getThreading().run(item);
                    room.updateTiles(room.getLayout().getTilesAt(
                            tile,
                            item.getBaseItem().getWidth(),
                            item.getBaseItem().getLength(),
                            item.getRotation()));
                }
                return true;
            }
            item.setZ(z);
            room.updateItem(item);
            return true;
        }
        int x = item.getX();
        int y = item.getY();
        int rotation = item.getRotation();
        if ("@position.x".equals(variableId)) {
            x = value;
        } else if ("@position.y".equals(variableId)) {
            y = value;
        } else if ("@rotation".equals(variableId)) {
            if (value < 0 || value > 7) {
                return false;
            }
            rotation = value;
        } else {
            return false;
        }
        if (!validCoordinate(x) || !validCoordinate(y)) {
            return false;
        }
        RoomTile tile = room.getLayout() == null ? null
                : room.getLayout().getTile((short) x, (short) y);
        if (tile == null) {
            return false;
        }
        if (context != null) {
            RoomTile from = room.getLayout().getTile(item.getX(), item.getY());
            double fromZ = item.getZ();
            if (from == null || WiredMovementAddonRuntime.move(
                    context, room, item, tile, rotation, false)
                    != FurnitureMovementError.NONE) {
                return false;
            }
            WiredMovementAddonRuntime.moved(
                    context, room, item, from, fromZ, tile);
            return true;
        }
        return room.moveFurniTo(
                item, tile, rotation, null, true, true, false)
                == FurnitureMovementError.NONE;
    }

    private static boolean writeGlobal(Room room, String id, int value) {
        GameTeamColors color = switch (id) {
            case "@teams.red.score" -> GameTeamColors.RED;
            case "@teams.green.score" -> GameTeamColors.GREEN;
            case "@teams.blue.score" -> GameTeamColors.BLUE;
            case "@teams.yellow.score" -> GameTeamColors.YELLOW;
            default -> null;
        };
        if (color == null || value < 0) {
            return false;
        }
        for (Game game : room.getGames()) {
            if (game == null || game.getState() != GameState.RUNNING) {
                continue;
            }
            GameTeam team = game.getTeam(color);
            if (team != null) {
                team.addTeamScore(value - team.getTotalScore());
                return true;
            }
        }
        return false;
    }

    private static int target(WiredVariableHolder holder) {
        return switch (holder.scope()) {
            case FURNI -> TARGET_FURNI;
            case USER -> TARGET_USER;
            case ROOM -> TARGET_GLOBAL;
        };
    }

    private static ZoneId timezone(Room room) {
        String configured = com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings
                .load(room.getId()).timezone();
        try {
            return ZoneId.of(configured);
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private static void appendTeam(Map<String, Integer> values, Room room,
                                   String name, GameTeamColors color) {
        GameTeam team = null;
        for (Game game : room.getGames()) {
            if (game != null && game.getState() == GameState.RUNNING
                    && game.getTeam(color) != null) {
                team = game.getTeam(color);
                break;
            }
        }
        values.put("@teams." + name + ".score",
                team == null ? 0 : team.getTotalScore());
        values.put("@teams." + name + ".size",
                team == null ? 0 : team.getMembers().size());
    }

    private static int gender(Habbo habbo, Bot bot) {
        HabboGender gender = habbo != null && habbo.getHabboInfo() != null
                ? habbo.getHabboInfo().getGender()
                : bot == null ? null : bot.getGender();
        return gender == null ? -1 : gender == HabboGender.F ? 1 : 0;
    }

    private static int scaled(double value) {
        return saturating(Math.round(value * 100D));
    }

    private static boolean validCoordinate(int value) {
        return value >= 0 && value <= Short.MAX_VALUE;
    }

    private static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String value = raw;
        int separator = value.indexOf('\t');
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int saturating(long value) {
        return (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, value));
    }

    private static Map<String, Integer> immutableSorted(
            Map<String, Integer> values) {
        LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private static String key(String id, int target) {
        return target + "\0" + (id == null ? "" : id);
    }

    private static Map<String, Definition> index(List<Definition> definitions) {
        LinkedHashMap<String, Definition> result = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            result.put(key(definition.variableId(), definition.target()), definition);
        }
        return Map.copyOf(result);
    }

    private static List<Definition> buildDefinitions() {
        List<Definition> result = new ArrayList<>();
        add(result, TARGET_FURNI, false, true,
                "@id", "@class_id", "@height", "@type", "@dimensions.x",
                "@dimensions.y", "@owner_id");
        add(result, TARGET_FURNI, false, false,
                "@is_invisible", "@is_stackable", "@can_stand_on",
                "@can_sit_on", "@can_lay_on");
        add(result, TARGET_FURNI, true, true,
                "@state", "@position.x", "@position.y", "@rotation", "@altitude");
        addRuntime(result, TARGET_FURNI, true,
                "@projectile.animation.tiles_travelled",
                "@projectile.animation.user_collisions",
                "@projectile.animation.furni_collisions",
                "@projectile.animation.is_travelling");
        addRuntime(result, TARGET_FURNI, false,
                "@projectile.animation.position.x",
                "@projectile.animation.position.y",
                "@projectile.animation.position.altitude");
        add(result, TARGET_USER, false, true,
                "@index", "@type", "@gender");
        add(result, TARGET_USER, false, false,
                "@achievement_score", "@is_hc", "@favourite_group_id",
                "@has_rights", "@is_group_admin", "@is_owner", "@is_muted",
                "@is_trading", "@dance", "@sign", "@is_idle", "@user_id",
                "@pet_id", "@bot_id", "@team.score", "@team.color");
        add(result, TARGET_USER, true, true,
                "@position.x", "@position.y", "@direction", "@altitude");
        add(result, TARGET_USER, true, false, "@handitem", "@effect");
        add(result, TARGET_GLOBAL, false, true,
                "@furni_count", "@user_count", "@wired_timer",
                "@teams.red.size", "@teams.green.size", "@teams.blue.size",
                "@teams.yellow.size", "@room_id", "@group_id", "@current_time",
                "@current_time.milliseconds_of_seconds",
                "@current_time.seconds_of_minute",
                "@current_time.minute_of_hour", "@current_time.hour_of_day",
                "@current_time.day_of_week", "@current_time.day_of_month",
                "@current_time.day_of_year", "@current_time.week_of_year",
                "@current_time.month_of_year", "@current_time.year");
        add(result, TARGET_GLOBAL, true, true,
                "@teams.red.score", "@teams.green.score",
                "@teams.blue.score", "@teams.yellow.score");
        add(result, TARGET_CONTEXT, false, false,
                "@selector_furni_count", "@selector_user_count",
                "@signal_furni_count", "@signal_user_count",
                "@event.signal.antenna_id",
                "@event.chat.type", "@event.chat.style",
                "@event.variable_update.box_id",
                "@event.variable_update.change_type",
                "@event.variable_update.old_value",
                "@event.variable_update.new_value",
                "@event.variable_update.difference",
                "@event.variable_update.change_origin",
                "@event.transaction_complete.multiplier",
                "@event.transaction_complete.deposit.furni_count",
                "@event.transaction_complete.deposit.coins_count",
                "@event.transaction_complete.withdrawal.furni_count",
                "@event.transaction_complete.withdrawal.coins_count",
                "@event.transaction_failed.reason",
                "@held_down", "@held_down.total_duration_ticks",
                "@held_down.origin_type", "@held_down.origin_id",
                "@held_down.origin_x", "@held_down.origin_y",
                "@held_down.origin_valid", "@held_down.release_type",
                "@held_down.release_id", "@held_down.release_x",
                "@held_down.release_y");
        result.sort(Comparator.comparingInt(Definition::target)
                .thenComparing(Definition::variableId));
        return List.copyOf(result);
    }

    private static void add(List<Definition> target, int variableTarget,
                            boolean writable, boolean alwaysAvailable,
                            String... ids) {
        for (String name : ids) {
            target.add(new Definition(
                    internalVariableId(variableTarget, name),
                    name, variableTarget, writable, alwaysAvailable, false));
        }
    }

    private static void addRuntime(List<Definition> target, int variableTarget,
                                   boolean observable, String... ids) {
        for (String name : ids) {
            target.add(new Definition(
                    internalVariableId(variableTarget, name),
                    name, variableTarget, false, false, observable));
        }
    }

    public static String internalVariableId(int target, String name) {
        return "internal:" + target + ":" + name;
    }

    public record Definition(String variableId, String name,
                             int target, boolean writable,
                             boolean alwaysAvailable,
                             boolean runtimeObservable) {
        public int hash() {
            int hash = 17;
            hash = 31 * hash + this.variableId.hashCode();
            hash = 31 * hash + this.name.hashCode();
            hash = 31 * hash + this.target;
            hash = 31 * hash + (this.writable ? 1 : 0);
            hash = 31 * hash + (this.alwaysAvailable ? 1 : 0);
            hash = 31 * hash + (this.runtimeObservable ? 1 : 0);
            return hash;
        }
    }
}
