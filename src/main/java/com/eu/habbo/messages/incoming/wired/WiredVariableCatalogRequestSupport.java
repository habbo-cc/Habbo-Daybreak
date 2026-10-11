package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog;

/** Shared fail-closed room/capability/view boundary for variable catalog requests. */
final class WiredVariableCatalogRequestSupport {
    private static final int REQUIRED_CAPABILITIES =
            WiredCapabilityService.CAPABILITY_VARIABLES
                    | WiredCapabilityService.CAPABILITY_VARIABLE_SYNC;

    private WiredVariableCatalogRequestSupport() {
    }

    static RequestContext resolve(GameClient client) {
        if (client == null || client.getHabbo() == null
                || client.getHabbo().getHabboInfo() == null) {
            return null;
        }
        Habbo habbo = client.getHabbo();
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getRoomUnit() == null || !habbo.getRoomUnit().isInRoom()
                || !client.getWiredCapabilityState().supportsRoom(REQUIRED_CAPABILITIES, room.getId())
                || room.getRoomSpecialTypes() == null) {
            return null;
        }
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null) {
            return new RequestContext(room, null, false);
        }
        boolean canView = WiredMenuSettings.load(room.getId()).canRead(room, habbo)
                || habbo.hasPermission(Permission.ACC_MOVEROTATE);
        return new RequestContext(room, manager, canView);
    }

    static WiredVariableManager.Snapshot snapshot(RequestContext context) {
        if (context == null || context.manager() == null || !context.canView()) {
            return null;
        }
        return WiredVariableCatalog.visibleSnapshot(context.manager());
    }

    static boolean isRoomVisibleStoredDefinition(WiredVariableType type) {
        return WiredVariableCatalog.isRoomVisibleStoredDefinition(type);
    }

    record RequestContext(Room room, WiredVariableManager manager, boolean canView) {
    }
}
