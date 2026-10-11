package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;

/**
 * Central fail-closed authorization boundary for Wired editor operations.
 */
public final class WiredAuthorizationService {
    public enum Operation {
        VIEW_EDITOR,
        ACK_EDITOR,
        EDIT_CONFIGURATION,
        APPLY_SNAPSHOT,
        INSPECT,
        VARIABLE_CRUD,
        ENVIRONMENT_EDIT,
        VIEW_LOGS,
        POLICY_EDIT
    }

    /** Pure policy input used by the live adapter and focused unit tests. */
    public record Facts(
            boolean authenticated,
            boolean exactCurrentRoom,
            boolean inRoom,
            boolean hasRoomRights,
            boolean hasMoveRotatePermission,
            boolean hasSuperWiredPermission,
            boolean itemInRoom,
            boolean itemCategoryMatches,
            WiredCategoryType itemCategory) {
    }

    private WiredAuthorizationService() {
    }

    public static boolean isAuthorized(
            Operation operation,
            GameClient client,
            Room room,
            HabboItem item,
            WiredCategoryType expectedCategory) {
        if (operation == null || client == null || room == null || item == null || expectedCategory == null) {
            return false;
        }

        Habbo habbo = client.getHabbo();
        if (habbo == null || habbo.getHabboInfo() == null) {
            return false;
        }

        Room currentRoom = habbo.getHabboInfo().getCurrentRoom();
        boolean exactCurrentRoom = currentRoom == room;
        boolean inRoom = habbo.getRoomUnit() != null && habbo.getRoomUnit().isInRoom();
        boolean itemInRoom = item.getRoomId() == room.getId();
        boolean itemCategoryMatches = itemInRoom
                && matchesCategory(room.getRoomSpecialTypes(), item, expectedCategory);

        // July's room policy is authoritative for editor writes. This replaces
        // the old hard-coded room-rights check while preserving the separate
        // staff moverotate/superwired overrides.
        boolean canModify = WiredMenuSettings.load(room.getId()).canModify(room, habbo);
        return isAuthorized(operation, new Facts(
                true,
                exactCurrentRoom,
                inRoom,
                canModify,
                habbo.hasPermission(Permission.ACC_MOVEROTATE),
                habbo.hasPermission(Permission.ACC_SUPERWIRED),
                itemInRoom,
                itemCategoryMatches,
                expectedCategory));
    }

    public static boolean isAuthorized(Operation operation, Facts facts) {
        if (operation == null
                || facts == null
                || !facts.authenticated()
                || !facts.exactCurrentRoom()
                || !facts.inRoom()
                || !facts.itemInRoom()
                || !facts.itemCategoryMatches()
                || facts.itemCategory() == null) {
            return false;
        }

        return switch (operation) {
            case VIEW_EDITOR -> facts.hasRoomRights()
                    || (facts.itemCategory() == WiredCategoryType.SELECTOR
                    && facts.hasMoveRotatePermission());
            case ACK_EDITOR -> facts.hasRoomRights() || facts.hasMoveRotatePermission();
            case EDIT_CONFIGURATION -> facts.hasRoomRights() || facts.hasMoveRotatePermission();
            case APPLY_SNAPSHOT -> facts.hasRoomRights();
            // These operations have no live server path yet. They remain closed until
            // their feature-specific ownership and policy rules are implemented.
            case INSPECT, VARIABLE_CRUD, ENVIRONMENT_EDIT, VIEW_LOGS, POLICY_EDIT -> false;
        };
    }

    private static boolean matchesCategory(
            RoomSpecialTypes specialTypes,
            HabboItem item,
            WiredCategoryType expectedCategory) {
        if (specialTypes == null) {
            return false;
        }

        return switch (expectedCategory) {
            case TRIGGER -> specialTypes.getTrigger(item.getId()) == item;
            case EFFECT -> specialTypes.getEffect(item.getId()) == item;
            case CONDITION -> specialTypes.getCondition(item.getId()) == item;
            case SELECTOR -> specialTypes.getSelector(item.getId()) == item;
            case ADDON -> specialTypes.getAddon(item.getId()) == item;
            case VARIABLE -> specialTypes.getVariable(item.getId()) == item;
        };
    }
}
