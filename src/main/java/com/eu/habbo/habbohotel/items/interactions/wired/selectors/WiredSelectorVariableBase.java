package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared July AIR shape for selectors 17 and 18.
 *
 * <p>The payload is {@code [comparison, valueFilterMode, valueSign,
 * valueInt, referenceScope]} plus exactly two variable IDs.  A reference
 * owner is resolved from generic source slot zero.  AIR's behaviour when that
 * source yields several owners has not been captured; this implementation
 * fails closed unless it resolves exactly one stable holder.</p>
 */
abstract class WiredSelectorVariableBase extends WiredSelectorConfigBase {
    static final int COMPARISON_LESS_THAN = 0;
    static final int COMPARISON_EQUAL = 1;
    static final int COMPARISON_GREATER_THAN = 2;
    static final int COMPARISON_LESS_OR_EQUAL = 3;
    static final int COMPARISON_NOT_EQUAL = 4;
    static final int COMPARISON_GREATER_OR_EQUAL = 5;

    static final int FILTER_OFF = 0;
    static final int FILTER_LITERAL = 1;
    static final int FILTER_REFERENCE = 2;

    static final int SCOPE_FURNI = 0;
    static final int SCOPE_USER = 1;
    static final int SCOPE_GLOBAL = -10;
    static final int SCOPE_CONTEXT = -20;
    static final String NO_VARIABLE = "n";

    private static final int MAX_SCAN_CANDIDATES = 10_000;
    private static final int MAX_SELECTED_TARGETS = 1_000;

    WiredSelectorVariableBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    WiredSelectorVariableBase(int id, int userId, Item item, String extradata,
                              int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    abstract WiredVariableHolder.Scope selectedScope();

    @Override
    public final boolean saveData(WiredSettingsV2 settings) {
        if (!WiredCapabilityService.isRoomCapabilityReady(WiredCapabilityService.CAPABILITY_VARIABLES)
                || !hasExactAirShape(settings)) {
            return false;
        }

        Room room = currentRoom();
        WiredVariableManager manager = room != null && room.getRoomSpecialTypes() != null
                ? room.getRoomSpecialTypes().getWiredVariableManager() : null;
        if (manager == null || !manager.isOperational()
                || !matchesDefinition(room, manager,
                        settings.getVariableIds()[0], selectedScope())) {
            return false;
        }

        int mode = settings.getIntParams()[1];
        int referenceScope = settings.getIntParams()[4];
        if (mode == FILTER_REFERENCE
                && !(referenceScope == SCOPE_CONTEXT
                ? contextDefinition(room, settings.getVariableIds()[1]) != null
                : matchesDefinition(room, manager, settings.getVariableIds()[1],
                        scopeFor(referenceScope)))) {
            return false;
        }

        return super.saveData(settings);
    }

    @Override
    public final WiredTargets resolve(Room room, WiredContext context) {
        WiredTargets targets = targets();
        if (room == null || context == null || !WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || !hasStoredExactAirShape()) {
            return targets;
        }

        WiredVariableManager manager = room.getRoomSpecialTypes() != null
                ? room.getRoomSpecialTypes().getWiredVariableManager() : null;
        if (manager == null || !manager.isOperational()
                || !matchesDefinition(room, manager,
                        this.variableIds[0], selectedScope())) {
            return targets;
        }

        Integer referenceValue = resolveReferenceValue(manager, room, context);
        if (this.intParams[1] != FILTER_OFF && referenceValue == null) {
            return targets;
        }

        if (selectedScope() == WiredVariableHolder.Scope.FURNI) {
            List<HabboItem> candidates = new ArrayList<>();
            int examined = 0;
            for (HabboItem item : allFloorItems(room)) {
                if (++examined > MAX_SCAN_CANDIDATES) {
                    break;
                }
                if (isCurrent(room, item)) {
                    candidates.add(item);
                }
            }
            candidates.sort(Comparator.comparingInt(HabboItem::getId));
            for (HabboItem item : candidates) {
                if (targets.itemCount() >= MAX_SELECTED_TARGETS) {
                    break;
                }
                WiredVariableValue value = WiredVariableAliasOperations.readValue(room,
                        this.variableIds[0], WiredVariableHolder.furni(item.getId()));
                Integer candidate = value == null
                        ? WiredInternalVariableRuntime.read(room, this.variableIds[0],
                                WiredVariableHolder.furni(item.getId()))
                        : value.value();
                if (candidate != null && matches(candidate, referenceValue)) {
                    targets.addItem(item);
                }
            }
        } else {
            List<RoomUnit> candidates = new ArrayList<>();
            int examined = 0;
            for (RoomUnit unit : allRoomUnits(room)) {
                if (++examined > MAX_SCAN_CANDIDATES) {
                    break;
                }
                if (isCurrent(room, unit)) {
                    candidates.add(unit);
                }
            }
            candidates.sort(Comparator.comparingInt(RoomUnit::getId));
            WiredInternalVariableRuntime.Definition internal =
                    WiredInternalVariableRuntime.definition(
                            this.variableIds[0], SCOPE_USER);
            for (RoomUnit unit : candidates) {
                if (targets.userCount() >= MAX_SELECTED_TARGETS) {
                    break;
                }
                Integer candidate = internal == null
                        ? storedUserValue(room, unit, this.variableIds[0])
                        : WiredInternalVariableRuntime.unitValues(room, unit)
                                .get(internal.name());
                if (candidate != null && matches(candidate, referenceValue)) {
                    targets.addUser(unit);
                }
            }
        }
        return targets;
    }

    @Override
    protected final int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected final int getUserSourceSlotCount() {
        return 1;
    }

    @Override
    protected final boolean isWiredAdvancedMode() {
        return true;
    }

    @Override
    protected final int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SIGNAL};
    }

    @Override
    protected final int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }

    @Override
    protected final int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_PICKED_1;
    }

    @Override
    protected final int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_TRIGGERING_USER;
    }

    @Override
    public final void loadWiredData(ResultSet set, Room room) throws SQLException {
        super.loadWiredData(set, room);
        if (!hasStoredExactAirShape()) {
            onPickUp();
        }
    }

    @Override
    public final void onPickUp() {
        super.onPickUp();
        this.variableIds = new String[0];
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.furniIds2 = new int[0];
    }

    static boolean hasExactAirShape(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams() == null || settings.getIntParams().length != 5
                || settings.getVariableIds() == null || settings.getVariableIds().length != 2
                || settings.getStringParam() == null || !settings.getStringParam().isEmpty()
                || settings.getFurniIds() == null || settings.getFurniIds().length != 0
                || settings.getFurniIds2() == null || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes() == null || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes() == null || settings.getUserSourceTypes().length != 1
                || settings.getDelay() != 0 || settings.getQuantifierCode() != 0) {
            return false;
        }
        int[] values = settings.getIntParams();
        if (!isComparison(values[0]) || !isFilterMode(values[1])
                || !validSignedIntParts(values[2], values[3]) || !isReferenceScope(values[4])
                || !isFurniSource(settings.getFurniSourceTypes()[0])
                || !isUserSource(settings.getUserSourceTypes()[0])
                || !isVariableId(settings.getVariableIds()[0])) {
            return false;
        }
        return values[1] == FILTER_REFERENCE
                ? isVariableId(settings.getVariableIds()[1])
                : NO_VARIABLE.equals(settings.getVariableIds()[1]);
    }

    static boolean matchesComparison(int comparison, int candidate, int reference) {
        return switch (comparison) {
            case COMPARISON_LESS_THAN -> candidate < reference;
            case COMPARISON_EQUAL -> candidate == reference;
            case COMPARISON_GREATER_THAN -> candidate > reference;
            case COMPARISON_LESS_OR_EQUAL -> candidate <= reference;
            case COMPARISON_NOT_EQUAL -> candidate != reference;
            case COMPARISON_GREATER_OR_EQUAL -> candidate >= reference;
            default -> false;
        };
    }

    private boolean hasStoredExactAirShape() {
        return this.intParams.length == 5 && this.variableIds.length == 2
                && this.furniSourceTypes.length == 1 && this.userSourceTypes.length == 1
                && isComparison(this.intParams[0]) && isFilterMode(this.intParams[1])
                && validSignedIntParts(this.intParams[2], this.intParams[3])
                && isReferenceScope(this.intParams[4]) && isFurniSource(this.furniSourceTypes[0])
                && isUserSource(this.userSourceTypes[0]) && isVariableId(this.variableIds[0])
                && (this.intParams[1] == FILTER_REFERENCE
                ? isVariableId(this.variableIds[1]) : NO_VARIABLE.equals(this.variableIds[1]));
    }

    private Integer resolveReferenceValue(WiredVariableManager manager, Room room, WiredContext context) {
        return switch (this.intParams[1]) {
            case FILTER_OFF -> null;
            case FILTER_LITERAL -> this.intParams[3];
            case FILTER_REFERENCE -> referenceVariableValue(manager, room, context);
            default -> null;
        };
    }

    private Integer referenceVariableValue(WiredVariableManager manager, Room room, WiredContext context) {
        if (this.intParams[4] == SCOPE_CONTEXT) {
            return context.contextVariables().get(this.variableIds[1]);
        }
        WiredVariableHolder.Scope scope = scopeFor(this.intParams[4]);
        if (scope == null || scope == WiredVariableHolder.Scope.ROOM
                && !matchesDefinition(room, manager,
                        this.variableIds[1], WiredVariableHolder.Scope.ROOM)) {
            return null;
        }
        if (scope == WiredVariableHolder.Scope.ROOM) {
            WiredVariableValue value = WiredVariableAliasOperations.readValue(room,
                    this.variableIds[1], WiredVariableHolder.room());
            return value == null
                    ? WiredInternalVariableRuntime.read(room, this.variableIds[1],
                            WiredVariableHolder.room())
                    : value.value();
        }
        if (!matchesDefinition(room, manager, this.variableIds[1], scope)) {
            return null;
        }

        Set<WiredVariableHolder> owners = new LinkedHashSet<>();
        if (scope == WiredVariableHolder.Scope.FURNI) {
            Collection<HabboItem> resolved = resolveFurniSource(context, this.furniSourceTypes, 0,
                    this.items, List.of());
            for (HabboItem item : resolved) {
                if (isCurrent(room, item)) {
                    owners.add(WiredVariableHolder.furni(item.getId()));
                }
            }
        } else if (scope == WiredVariableHolder.Scope.USER) {
            WiredInternalVariableRuntime.Definition internal =
                    WiredInternalVariableRuntime.definition(
                            this.variableIds[1], SCOPE_USER);
            if (internal != null) {
                List<RoomUnit> units = resolveUserSource(
                        context, this.userSourceTypes, 0).stream()
                        .filter(unit -> isCurrent(room, unit)).toList();
                return units.size() == 1
                        ? WiredInternalVariableRuntime.unitValues(room, units.get(0))
                                .get(internal.name())
                        : null;
            }
            for (RoomUnit unit : resolveUserSource(context, this.userSourceTypes, 0)) {
                Habbo habbo = isCurrent(room, unit) ? room.getHabbo(unit) : null;
                if (habbo != null && habbo.getHabboInfo() != null) {
                    owners.add(WiredVariableHolder.user(habbo.getHabboInfo().getId()));
                }
            }
        }
        if (owners.size() != 1) {
            return null;
        }
        WiredVariableValue value = WiredVariableAliasOperations.readValue(room,
                this.variableIds[1], owners.iterator().next());
        return value == null
                ? WiredInternalVariableRuntime.read(
                        room, this.variableIds[1], owners.iterator().next())
                : value.value();
    }

    private boolean matches(int candidate, Integer reference) {
        return this.intParams[1] == FILTER_OFF
                || reference != null && matchesComparison(this.intParams[0], candidate, reference);
    }

    private static boolean matchesDefinition(Room room, WiredVariableManager manager,
                                             String variableId,
                                             WiredVariableHolder.Scope scope) {
        if (manager == null || scope == null || !isVariableId(variableId)) {
            return false;
        }
        int target = switch (scope) {
            case FURNI -> SCOPE_FURNI;
            case USER -> SCOPE_USER;
            case ROOM -> SCOPE_GLOBAL;
        };
        if (WiredInternalVariableRuntime.matches(variableId, target)) {
            return true;
        }
        WiredVariableDefinition definition = manager.runtimeDefinition(variableId);
        return definition != null && definition.holderScope() == scope;
    }

    private static Integer storedUserValue(
            Room room, RoomUnit unit, String variableId) {
        Habbo habbo = room.getHabbo(unit);
        if (habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }
        WiredVariableValue value = WiredVariableAliasOperations.readValue(
                room, variableId,
                WiredVariableHolder.user(habbo.getHabboInfo().getId()));
        return value == null ? null : value.value();
    }

    private static WiredVariableHolder.Scope scopeFor(int scope) {
        return switch (scope) {
            case SCOPE_FURNI -> WiredVariableHolder.Scope.FURNI;
            case SCOPE_USER -> WiredVariableHolder.Scope.USER;
            case SCOPE_GLOBAL -> WiredVariableHolder.Scope.ROOM;
            default -> null;
        };
    }

    private static WiredVariableContext contextDefinition(Room room, String id) {
        if (room == null || id == null || !id.startsWith("room:")) return null;
        try { return room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5))) instanceof WiredVariableContext v ? v : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean isComparison(int value) {
        return value >= COMPARISON_LESS_THAN && value <= COMPARISON_GREATER_OR_EQUAL;
    }

    private static boolean isFilterMode(int value) {
        return value >= FILTER_OFF && value <= FILTER_REFERENCE;
    }

    private static boolean isReferenceScope(int value) {
        return value == SCOPE_FURNI || value == SCOPE_USER || value == SCOPE_GLOBAL
                || value == SCOPE_CONTEXT;
    }

    private static boolean validSignedIntParts(int high, int low) {
        return (high == 0 && low >= 0) || (high == -1 && low < 0);
    }

    private static boolean isVariableId(String value) {
        return value != null && !value.isBlank() && !NO_VARIABLE.equals(value) && value.length() <= 128;
    }

    private static boolean isFurniSource(int value) {
        return value == FURNI_SOURCE_PICKED_1 || value == FURNI_SOURCE_SELECTOR
                || value == FURNI_SOURCE_TRIGGERING_ITEM || value == FURNI_SOURCE_SIGNAL;
    }

    private static boolean isUserSource(int value) {
        return value == USER_SOURCE_TRIGGERING_USER || value == USER_SOURCE_SELECTOR
                || value == USER_SOURCE_SIGNAL;
    }

    private static boolean isCurrent(Room room, HabboItem item) {
        return room != null && item != null && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item && room.getFloorItems().contains(item);
    }

    private static boolean isCurrent(Room room, RoomUnit unit) {
        return room != null && unit != null && unit.getRoom() == room && unit.isInRoom()
                && room.getRoomUnits().contains(unit);
    }

    private Room currentRoom() {
        if (this.getRoomId() <= 0 || Emulator.getGameEnvironment() == null
                || Emulator.getGameEnvironment().getRoomManager() == null) {
            return null;
        }
        return Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
    }
}
