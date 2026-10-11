package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.infobus.InfobusManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.CannotEnterBusMessageComposer;

/**
 * Player clicked the Infobus to enter it. If the doors are open we walk the player onto the bus
 * enter-square (a walkway tile); reaching it triggers the redirect into the bus interior. If the
 * doors are closed (the boot default until staff run :bus open) we refuse with a notice.
 */
public class TryBusMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.isPublicRoom()) {
            return;
        }

        if (!InfobusManager.isDoorOpen()) {
            this.client.sendResponse(new CannotEnterBusMessageComposer(Emulator.getTexts().getValue("infobus.doors.closed")));
            return;
        }

        RoomTile tile = Emulator.getGameEnvironment().getRoomManager().getFirstWalkwayTile(room);

        if (tile != null) {
            this.client.getHabbo().getRoomUnit().setGoalLocation(tile);
        }
    }
}
