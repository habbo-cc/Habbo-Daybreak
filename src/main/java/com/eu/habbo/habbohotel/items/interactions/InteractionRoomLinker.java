package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wired Room Linker (wf_room_linker). Inert pad furni sold in pairs like classic
 * teleports (items_teleports row created at purchase); the Wired 2.0 effect
 * TELEPORT_TO_ROOM picks a linker in the current room and sends users to the
 * room holding its pair partner.
 */
public class InteractionRoomLinker extends InteractionDefault {
    public InteractionRoomLinker(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRoomLinker(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}
