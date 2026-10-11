package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable identity snapshot carried by one signal dispatch.
 *
 * <p>References are revalidated against the original room every time a signal
 * selector resolves them. Moving, picking up, replacing or removing a furni/user
 * therefore cannot turn a queued/nested signal into a cross-room or stale target.</p>
 */
public final class WiredSignalPayload {
    private static final WiredSignalPayload NONE = new WiredSignalPayload(0, List.of(), List.of());

    private final int roomId;
    private final List<HabboItem> items;
    private final List<RoomUnit> users;

    private WiredSignalPayload(int roomId, List<HabboItem> items, List<RoomUnit> users) {
        this.roomId = roomId;
        this.items = List.copyOf(items);
        this.users = List.copyOf(users);
    }

    public static WiredSignalPayload empty() {
        return NONE;
    }

    public static WiredSignalPayload capture(
            Room room,
            Collection<? extends HabboItem> candidateItems,
            Collection<? extends RoomUnit> candidateUsers) {
        if (room == null) {
            return NONE;
        }

        Map<Integer, HabboItem> itemsById = new LinkedHashMap<>();
        if (candidateItems != null) {
            for (HabboItem item : candidateItems) {
                if (isCurrentItem(room, item)) {
                    itemsById.putIfAbsent(item.getId(), item);
                }
            }
        }

        Map<Integer, RoomUnit> usersById = new LinkedHashMap<>();
        if (candidateUsers != null) {
            for (RoomUnit user : candidateUsers) {
                if (isCurrentUser(room, user)) {
                    usersById.putIfAbsent(user.getId(), user);
                }
            }
        }

        List<HabboItem> items = new ArrayList<>(itemsById.values());
        items.sort(Comparator.comparingInt(HabboItem::getId));
        List<RoomUnit> users = new ArrayList<>(usersById.values());
        users.sort(Comparator.comparingInt(RoomUnit::getId));
        return new WiredSignalPayload(room.getId(), items, users);
    }

    public int roomId() {
        return this.roomId;
    }

    public List<HabboItem> items(Room room) {
        if (!isOriginalRoom(room)) {
            return List.of();
        }
        return this.items.stream().filter(item -> isCurrentItem(room, item)).toList();
    }

    public List<RoomUnit> users(Room room) {
        if (!isOriginalRoom(room)) {
            return List.of();
        }
        return this.users.stream().filter(user -> isCurrentUser(room, user)).toList();
    }

    public int capturedItemCount() {
        return this.items.size();
    }

    public int capturedUserCount() {
        return this.users.size();
    }

    private boolean isOriginalRoom(Room room) {
        return room != null && this.roomId != 0 && room.getId() == this.roomId;
    }

    private static boolean isCurrentItem(Room room, HabboItem item) {
        return item != null
                && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item;
    }

    private static boolean isCurrentUser(Room room, RoomUnit user) {
        return user != null
                && user.isInRoom()
                && user.getRoom() == room
                && room.getRoomUnits().contains(user);
    }
}
