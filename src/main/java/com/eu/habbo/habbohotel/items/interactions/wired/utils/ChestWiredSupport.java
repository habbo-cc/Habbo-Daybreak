package com.eu.habbo.habbohotel.items.interactions.wired.utils;

import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared, fail-closed helpers for July's chest-Wired types. */
public final class ChestWiredSupport {
    public static final int TARGET_FURNI = 0;
    public static final int TARGET_USER = 1;
    public static final int TARGET_GLOBAL = -10;
    public static final int TARGET_CONTEXT = -20;

    private ChestWiredSupport() {
    }

    public static boolean validTarget(int target) {
        return target == TARGET_FURNI || target == TARGET_USER
                || target == TARGET_GLOBAL || target == TARGET_CONTEXT;
    }

    public static boolean definitionMatches(Room room, String variableId, int target) {
        if (room == null
                || variableId == null
                || variableId.isBlank()
                || variableId.length() > 128
                || !validTarget(target)) {
            return false;
        }
        if (WiredInternalVariableRuntime.matches(variableId, target)) {
            return true;
        }
        WiredVariableManager manager =
                room.getRoomSpecialTypes().getWiredVariableManager();
        WiredVariableDefinition definition =
                manager == null || !manager.isOperational()
                        ? null
                        : manager.runtimeDefinition(variableId);
        if (definition == null) {
            definition = WiredGeneratedVariableRuntime.parentDefinition(room, variableId);
        }
        return definition != null
                && switch (target) {
                    case TARGET_FURNI ->
                            definition.holderScope() == WiredVariableHolder.Scope.FURNI;
                    case TARGET_USER ->
                            definition.holderScope() == WiredVariableHolder.Scope.USER;
                    case TARGET_GLOBAL ->
                            definition.holderScope() == WiredVariableHolder.Scope.ROOM;
                    case TARGET_CONTEXT ->
                            definition.type()
                                    == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT;
                    default -> false;
                };
    }

    public static Integer resolveValue(WiredContext context, String variableId, int target,
            Collection<HabboItem> furni, Collection<RoomUnit> users) {
        if (context == null || variableId == null || variableId.isBlank() || !validTarget(target)) {
            return null;
        }
        if (target == TARGET_CONTEXT) {
            return context.contextVariables().get(variableId);
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes()
                .getWiredVariableManager();
        if (manager == null) {
            return null;
        }
        WiredVariableHolder holder;
        if (target == TARGET_GLOBAL) {
            holder = WiredVariableHolder.room();
        } else if (target == TARGET_FURNI) {
            HabboItem item = furni == null ? null : furni.stream()
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.comparingInt(HabboItem::getId)).orElse(null);
            if (item == null) {
                return null;
            }
            holder = WiredVariableHolder.furni(item.getId());
        } else {
            RoomUnit unit = users == null ? null : users.stream()
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.comparingInt(RoomUnit::getId)).orElse(null);
            Habbo habbo = unit == null ? null : context.room().getHabbo(unit);
            if (habbo == null) {
                return null;
            }
            holder = WiredVariableHolder.user(habbo.getHabboInfo().getId());
        }
        WiredVariableValue value = manager.get(variableId, holder);
        return value == null ? null : value.value();
    }

    public static List<Habbo> habbos(Room room, Collection<RoomUnit> units) {
        if (room == null || units == null) {
            return List.of();
        }
        LinkedHashSet<Habbo> result = new LinkedHashSet<>();
        for (RoomUnit unit : units) {
            Habbo habbo = unit == null ? null : room.getHabbo(unit);
            if (habbo != null) {
                result.add(habbo);
            }
        }
        return List.copyOf(result);
    }

    public static List<HabboItem> chests(ChestManager manager, Collection<HabboItem> items,
            ChestType type) {
        if (manager == null || items == null) {
            return List.of();
        }
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        List<HabboItem> result = new ArrayList<>();
        for (HabboItem item : items) {
            if (item != null && ids.add(item.getId()) && manager.isWiredUsable(item, type)) {
                result.add(item);
            }
        }
        return result;
    }

    public static List<HabboItem> nonChests(ChestManager manager, Collection<HabboItem> items) {
        if (items == null) {
            return List.of();
        }
        List<HabboItem> result = new ArrayList<>();
        for (HabboItem item : items) {
            if (item != null && (manager == null || !manager.isChest(item))) {
                result.add(item);
            }
        }
        return result;
    }

    public static boolean compare(long left, long right, int operation) {
        return switch (operation) {
            case 0 -> left < right;
            case 1 -> left == right;
            case 2 -> left > right;
            case 3 -> left <= right;
            case 4 -> left != right;
            case 5 -> left >= right;
            default -> false;
        };
    }
}
