package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestTradeSessionTest {
    @Test
    void enforcesAddingCountdownAndMinimumConfirmTime() {
        ChestTradeSession session = new ChestTradeSession(
                7, 8, 9, 10, ChestType.FURNI, 1_000, 60);
        HabboItem item = item(123, 7);

        assertTrue(session.add(item));
        assertFalse(session.add(item));
        assertTrue(session.beginCountdown(2_000));
        assertFalse(session.canConfirm(4_799, 2_800));
        assertTrue(session.canConfirm(4_800, 2_800));
        assertNull(session.remove(123));

        session.resetAcceptance();
        assertEquals(item, session.remove(123));
        assertTrue(session.isEmpty());
    }

    @Test
    void expiresAtTheServerDeadline() {
        ChestTradeSession session = new ChestTradeSession(
                7, 8, 9, 10, ChestType.COINS, 1_000, 60);
        assertFalse(session.expired(60_999));
        assertTrue(session.expired(61_000));
    }

    private static HabboItem item(int id, int userId) {
        return new HabboItem(id, userId, null, "0", 0, 0) {
            @Override
            public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
                return false;
            }

            @Override
            public boolean isWalkable() {
                return false;
            }

            @Override
            public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
            }
        };
    }
}
