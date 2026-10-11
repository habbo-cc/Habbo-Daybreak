package com.eu.habbo.messages.incoming.games.gamehall;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * A seated gamehall player played a move on their station's board. The {@code command} names the
 * per-game action (SHOOT, PLACESHIP, MOVEPIECE, ...) and the {@code args} carry its payload.
 */
public class GameBoardMoveMessageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 75;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        int stationId = this.packet.readInt();
        String command = this.packet.readString();
        int argCount = this.packet.readInt();
        if (command == null || command.length() == 0 || command.length() > 32
                || argCount < 0 || argCount > 32) {
            return;
        }

        String[] args = new String[argCount];
        for (int i = 0; i < argCount; i++) {
            args[i] = this.packet.readString();
            if (args[i] == null || args[i].length() > 256) {
                return;
            }
        }

        room.getGamehallManager().handleMove(habbo, stationId, command, args);
    }
}
