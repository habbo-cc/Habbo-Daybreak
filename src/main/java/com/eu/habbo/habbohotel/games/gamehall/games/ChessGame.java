package com.eu.habbo.habbohotel.games.gamehall.games;

import com.eu.habbo.habbohotel.games.gamehall.GamehallGame;
import com.eu.habbo.habbohotel.games.gamehall.GamehallGameType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

public class ChessGame extends GamehallGame {
    public ChessGame(Room room, int stationId) {
        super(room, stationId, GamehallGameType.CHESS);
    }

    @Override
    public void handleCommand(Habbo habbo, String command, String[] args) {
    }
}
