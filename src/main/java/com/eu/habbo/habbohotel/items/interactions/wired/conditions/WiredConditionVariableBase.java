package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared, AIR-shaped validation and owner resolution for Core Variables
 * conditions 40--43.
 *
 * <p>The July editor exposes one owner source and no all/any quantifier.
 * Conditions therefore require that source to resolve to exactly one owner;
 * zero or multiple owners do not produce a synthetic value.</p>
 */
abstract class WiredConditionVariableBase extends WiredConditionConfigBase {
    static final int TARGET_FURNI = 0;
    static final int TARGET_USER = 1;
    static final int TARGET_GLOBAL = -10;
    static final int TARGET_CONTEXT = -20;

    WiredConditionVariableBase(ResultSet set, com.eu.habbo.habbohotel.items.Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    WiredConditionVariableBase(int id, int userId, com.eu.habbo.habbohotel.items.Item item,
                               String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return sourceSlotCount();
    }

    @Override
    protected int getUserSourceSlotCount() {
        return sourceSlotCount();
    }

    protected int sourceSlotCount() {
        return 1;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_TRIGGERING_ITEM;
    }

    @Override
    protected int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_TRIGGERING_USER;
    }

    /** Validates the common July packet fields without accepting hidden legacy payloads. */
    final Room validateCommon(WiredSettings settings, int intCount, int variableCount) {
        int slots = sourceSlotCount();
        if (settings == null
                || !hasLength(settings.getIntParams(), intCount)
                || !hasLength(settings.getVariableIds(), variableCount)
                || !hasLength(settings.getFurniSourceTypes(), slots)
                || !hasLength(settings.getUserSourceTypes(), slots)
                || !isEmpty(settings.getStringParam())
                || !isEmpty(settings.getFurniIds())
                || !isEmpty(settings.getFurniIds2())
                || settings.getDelay() != 0
                || !sourcesAllowed(settings.getFurniSourceTypes(), true)
                || !sourcesAllowed(settings.getUserSourceTypes(), false)) {
            return null;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return null;
        }
        this.furniSourceTypes = settings.getFurniSourceTypes().clone();
        this.userSourceTypes = settings.getUserSourceTypes().clone();
        return room;
    }

    final boolean validateDefinition(Room room, String variableId, int targetScope) {
        if (room == null || !isValidVariableId(variableId) || !isSupportedTarget(targetScope)) {
            return false;
        }
        if (WiredInternalVariableRuntime.matches(variableId, targetScope)) {
            return true;
        }
        if (targetScope == TARGET_CONTEXT) {
            var alias = WiredVariableAliasOperations.resolve(room, variableId);
            return contextDefinition(room, variableId) != null || alias != null
                    && alias.definition().type() == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT;
        }
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) {
            return false;
        }
        WiredVariableDefinition definition = manager.runtimeDefinition(variableId);
        if (definition == null) {
            definition = WiredGeneratedVariableRuntime.parentDefinition(room, variableId);
        }
        return definition != null && matchesTarget(definition, targetScope);
    }

    final com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata metadata(Room room, String id) {
        var entry = com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog.forRoom(room).entries().get(id);
        return entry == null ? null : entry.metadata();
    }

    final boolean validateNumericDefinition(Room room, String id, int target) {
        var capabilities = metadata(room, id);
        return validateDefinition(room, id, target) && capabilities != null && capabilities.hasValue();
    }

    final boolean hasNumericValue(Room room, String id, int target) {
        if (WiredInternalVariableRuntime.matches(id, target)) return true;
        var context = target == TARGET_CONTEXT ? contextDefinition(room, id) : null;
        if (context != null) return context.hasValue();
        var source = WiredVariableAliasOperations.resolve(room, id);
        return source != null && source.definition().hasValue();
    }

    final WiredVariableValue resolveSingleValue(WiredContext context, String variableId,
                                                int targetScope, int sourceSlot) {
        if (context == null || context.room() == null || !isSupportedTarget(targetScope)) {
            return null;
        }
        if (targetScope == TARGET_CONTEXT) {
            Integer value = WiredVariableAliasOperations.readContext(context, variableId);
            return value == null ? null : new WiredVariableValue(variableId, WiredVariableHolder.room(), value, 0, 0, 0);
        }
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(variableId, targetScope);
        if (internal != null) {
            return resolveInternalValue(context, internal, sourceSlot);
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) {
            return null;
        }
        List<WiredVariableHolder> holders = resolveSingleOwner(context, targetScope, sourceSlot);
        if (holders.size() != 1) {
            return null;
        }
        // An absent source must never fall back to obsolete local alias storage.
        return WiredVariableAliasOperations.readValue(context.room(), variableId, holders.get(0));
    }

    final boolean hasRegisteredDefinition(WiredContext context, String variableId, int targetScope) {
        if (context == null || context.room() == null || !isValidVariableId(variableId)
                || !isSupportedTarget(targetScope)) {
            return false;
        }
        if (WiredInternalVariableRuntime.matches(variableId, targetScope)) {
            return true;
        }
        if (targetScope == TARGET_CONTEXT) return validateDefinition(context.room(), variableId, targetScope);
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) {
            return false;
        }
        WiredVariableDefinition definition = manager.runtimeDefinition(variableId);
        if (definition == null) {
            definition =
                    WiredGeneratedVariableRuntime.parentDefinition(
                            context.room(), variableId);
        }
        return definition != null && matchesTarget(definition, targetScope);
    }

    final List<WiredVariableHolder> resolveSingleOwner(WiredContext context, int targetScope, int sourceSlot) {
        if (context == null || context.room() == null || sourceSlot < 0 || sourceSlot >= sourceSlotCount()) {
            return List.of();
        }
        Room room = context.room();
        Set<WiredVariableHolder> holders = new LinkedHashSet<>();
        switch (targetScope) {
            case TARGET_FURNI -> resolveFurniSource(context, this.furniSourceTypes, sourceSlot, List.of(), List.of())
                    .stream()
                    .filter(item -> isCurrent(room, item))
                    .forEach(item -> holders.add(WiredVariableHolder.furni(item.getId())));
            case TARGET_USER -> resolveUserSource(context, this.userSourceTypes, sourceSlot).stream()
                    .filter(unit -> isCurrent(room, unit))
                    .map(room::getHabbo)
                    .filter(habbo -> habbo != null && habbo.getHabboInfo() != null)
                    .forEach(habbo -> holders.add(WiredVariableHolder.user(habbo.getHabboInfo().getId())));
            case TARGET_GLOBAL -> holders.add(WiredVariableHolder.room());
            default -> { }
        }
        return holders.size() == 1 ? List.copyOf(holders) : List.of();
    }

    final boolean hasSingleResolvedOwner(
            WiredContext context, String variableId,
            int targetScope, int sourceSlot) {
        if (context == null || context.room() == null) {
            return false;
        }
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(variableId, targetScope);
        if (internal == null || targetScope != TARGET_USER) {
            return resolveSingleOwner(context, targetScope, sourceSlot).size() == 1;
        }
        Room room = context.room();
        return resolveUserSource(context, this.userSourceTypes, sourceSlot).stream()
                .filter(unit -> isCurrent(room, unit))
                .limit(2)
                .count() == 1;
    }

    private WiredVariableValue resolveInternalValue(
            WiredContext context,
            WiredInternalVariableRuntime.Definition definition,
            int sourceSlot) {
        Room room = context.room();
        if (definition.target() == TARGET_GLOBAL) {
            Integer value = WiredInternalVariableRuntime.globalValues(room)
                    .get(definition.name());
            return value == null ? null : new WiredVariableValue(
                    definition.variableId(), WiredVariableHolder.room(),
                    value, 0L, 0L, 0L);
        }
        if (definition.target() == TARGET_FURNI) {
            List<HabboItem> items = resolveFurniSource(
                    context, this.furniSourceTypes, sourceSlot,
                    List.of(), List.of()).stream()
                    .filter(item -> isCurrent(room, item)).toList();
            if (items.size() != 1) {
                return null;
            }
            HabboItem item = items.get(0);
            Integer value = WiredInternalVariableRuntime
                    .furniValues(room, item).get(definition.name());
            return value == null ? null : new WiredVariableValue(
                    definition.variableId(), WiredVariableHolder.furni(item.getId()),
                    value, 0L, 0L, 0L);
        }
        List<RoomUnit> units = resolveUserSource(
                context, this.userSourceTypes, sourceSlot).stream()
                .filter(unit -> isCurrent(room, unit)).toList();
        if (units.size() != 1) {
            return null;
        }
        RoomUnit unit = units.get(0);
        Integer value = WiredInternalVariableRuntime
                .unitValues(room, unit).get(definition.name());
        return value == null ? null : new WiredVariableValue(
                definition.variableId(), WiredVariableHolder.user(unit.getId()),
                value, 0L, 0L, 0L);
    }

    final String encodeRecord(String... fields) {
        StringBuilder result = new StringBuilder("v1");
        for (String field : fields) {
            result.append('.').append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (field == null ? "" : field).getBytes(StandardCharsets.UTF_8)));
        }
        return result.toString();
    }

    final String[] decodeRecord(String data, int fieldCount) {
        if (data == null) {
            return null;
        }
        String[] encoded = data.split("\\.", -1);
        if (encoded.length != fieldCount + 1 || !"v1".equals(encoded[0])) {
            return null;
        }
        String[] fields = new String[fieldCount];
        try {
            for (int index = 0; index < fieldCount; index++) {
                fields[index] = new String(Base64.getUrlDecoder().decode(encoded[index + 1]),
                        StandardCharsets.UTF_8);
            }
            return fields;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isValidVariableId(String value) {
        return value != null && !value.isBlank() && value.length() <= 128;
    }

    static boolean validSignedIntParts(int sign, int value) {
        return (sign == 0 && value >= 0) || (sign == -1 && value < 0);
    }

    static boolean isSupportedTarget(int targetScope) {
        return targetScope == TARGET_FURNI || targetScope == TARGET_USER || targetScope == TARGET_GLOBAL
                || targetScope == TARGET_CONTEXT;
    }

    private boolean sourcesAllowed(int[] sources, boolean furni) {
        for (int source : sources) {
            int[] allowed = furni ? getAllowedFurniSourcesForSlot(0) : getAllowedUserSourcesForSlot(0);
            boolean matches = false;
            for (int candidate : allowed) {
                if (candidate == source) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesTarget(WiredVariableDefinition definition, int targetScope) {
        return definition != null && switch (targetScope) {
            case TARGET_FURNI -> definition.holderScope() == WiredVariableHolder.Scope.FURNI;
            case TARGET_USER -> definition.holderScope() == WiredVariableHolder.Scope.USER;
            case TARGET_GLOBAL -> definition.holderScope() == WiredVariableHolder.Scope.ROOM;
            case TARGET_CONTEXT -> definition.type() == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT;
            default -> false;
        };
    }

    private static WiredVariableContext contextDefinition(Room room, String id) {
        if (room == null || id == null || !id.startsWith("room:")) return null;
        try { return room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5))) instanceof WiredVariableContext v ? v : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean isCurrent(Room room, HabboItem item) {
        return room != null && item != null && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item;
    }

    private static boolean isCurrent(Room room, RoomUnit unit) {
        return unit != null && unit.getRoom() == room && unit.isInRoom() && room.getRoomUnits().contains(unit);
    }

    private static boolean hasLength(int[] values, int length) {
        return values != null && values.length == length;
    }

    private static boolean hasLength(String[] values, int length) {
        return values != null && values.length == length;
    }

    private static boolean isEmpty(int[] values) {
        return values != null && values.length == 0;
    }

    private static boolean isEmpty(String value) {
        return value != null && value.isEmpty();
    }
}
