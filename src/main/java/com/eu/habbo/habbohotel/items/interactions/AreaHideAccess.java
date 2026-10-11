package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.menu.WiredMenuSettings;

public final class AreaHideAccess {
    private AreaHideAccess() {
    }

    public static boolean canModify(GameClient client, Room room, InteractionAreaHide item) {
        if (client == null || room == null || item == null) {
            return false;
        }
        Habbo habbo = client.getHabbo();
        return habbo != null
                && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getCurrentRoom() == room
                && habbo.getRoomUnit() != null
                && habbo.getRoomUnit().isInRoom()
                && item.getRoomId() == room.getId()
                && WiredMenuSettings.load(room.getId()).canModify(room, habbo);
    }
}
