package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.games.gamehall.GamehallManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * Synthetic public-room chair that joins/leaves its configured gamehall station.
 */
public class InteractionGamehallSeat extends InteractionPublicItem {
    private final GamehallManager.SeatAssignment assignment;

    public InteractionGamehallSeat(int id, Item baseItem, GamehallManager.SeatAssignment assignment) {
        super(id, baseItem);
        this.assignment = assignment;
    }

    public GamehallManager.SeatAssignment getAssignment() {
        return this.assignment;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit == null || roomUnit.getRoomUnitType() != RoomUnitType.USER) {
            super.onWalkOn(roomUnit, room, objects);
            return;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null) {
            room.getGamehallManager().onSeat(habbo, this);
        }
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
        if (roomUnit == null || roomUnit.getRoomUnitType() != RoomUnitType.USER) {
            return;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null) {
            room.getGamehallManager().onLeave(habbo, this);
        }
    }
}
