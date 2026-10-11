package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Player clicked the bus exit ("goawaybus") inside the Infobus. Walk them onto this room's walkway
 * tile; reaching it redirects them back to the park.
 */
public class ChangeWorldMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.isPublicRoom()) {
            return;
        }

        RoomTile tile = Emulator.getGameEnvironment().getRoomManager().getFirstWalkwayTile(room);

        if (tile != null) {
            this.client.getHabbo().getRoomUnit().setGoalLocation(tile);
        }
    }
}
