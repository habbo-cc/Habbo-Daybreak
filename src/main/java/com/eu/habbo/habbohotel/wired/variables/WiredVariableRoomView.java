package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Converts stable variable-holder identities at a room packet boundary.
 *
 * <p>The manager deliberately retains database-item and Habbo IDs.  July
 * WiredContext blocks 1 and 2 instead carry room-visible furniture and room
 * user-unit IDs.  This adapter is the sole translation point for those
 * outbound blocks: an absent item/user is not serialized and no room-visible
 * ID is ever persisted back into the manager.</p>
 */
public final class WiredVariableRoomView {
    private WiredVariableRoomView() {
    }

    /** July context block 1 entries: {@code roomVisibleFurniId, signedIntValue}. */
    public static List<VisibleValue> furniValues(Room room,
                                                 WiredVariableManager.VariableSnapshot variable) {
        return furniValues(variable, stableItemId -> {
            HabboItem item = room == null ? null : room.getHabboItemByDatabaseId(stableItemId);
            return item == null ? null : item.getRoomVisibleId();
        });
    }

    /** Testable boundary form; a null mapping means that holder is not room-visible. */
    public static List<VisibleValue> furniValues(WiredVariableManager.VariableSnapshot variable,
                                                 IntFunction<Integer> roomVisibleId) {
        if (variable == null || roomVisibleId == null
                || variable.definition().holderScope() != WiredVariableHolder.Scope.FURNI) {
            return List.of();
        }
        List<VisibleValue> values = new ArrayList<>();
        for (WiredVariableValue value : variable.values()) {
            if (value.holder().scope() != WiredVariableHolder.Scope.FURNI) {
                continue;
            }
            Integer visibleId = roomVisibleId.apply(value.holder().stableId());
            if (visibleId != null) {
                values.add(new VisibleValue(visibleId, wireInt(value.value())));
            }
        }
        return immutableDistinct(values);
    }

    /** July context block 2 entries: {@code roomUserId, signedIntValue}. */
    public static List<VisibleValue> userValues(Room room,
                                                WiredVariableManager.VariableSnapshot variable) {
        return userValues(variable, stableHabboId -> {
            Habbo habbo = room == null ? null : room.getHabbo(stableHabboId);
            return habbo == null || habbo.getRoomUnit() == null || !habbo.getRoomUnit().isInRoom()
                    ? null : habbo.getRoomUnit().getId();
        });
    }

    /** Testable boundary form; a null mapping means that holder is not in this room. */
    public static List<VisibleValue> userValues(WiredVariableManager.VariableSnapshot variable,
                                                IntFunction<Integer> roomUserId) {
        if (variable == null || roomUserId == null
                || variable.definition().holderScope() != WiredVariableHolder.Scope.USER) {
            return List.of();
        }
        List<VisibleValue> values = new ArrayList<>();
        for (WiredVariableValue value : variable.values()) {
            if (value.holder().scope() != WiredVariableHolder.Scope.USER) {
                continue;
            }
            Integer visibleId = roomUserId.apply(value.holder().stableId());
            if (visibleId != null) {
                values.add(new VisibleValue(visibleId, wireInt(value.value())));
            }
        }
        return immutableDistinct(values);
    }

    private static List<VisibleValue> immutableDistinct(List<VisibleValue> values) {
        values.sort(Comparator.comparingInt(VisibleValue::roomVisibleId));
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).roomVisibleId() == values.get(index).roomVisibleId()) {
                throw new IllegalStateException("duplicate room-visible variable holder");
            }
        }
        return List.copyOf(values);
    }

    private static int wireInt(long value) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("variable value exceeds July signed-int wire range", exception);
        }
    }

    public record VisibleValue(int roomVisibleId, int value) {
    }
}
