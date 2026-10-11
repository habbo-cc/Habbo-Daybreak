package com.eu.habbo.habbohotel.games.gamehall.games;

import com.eu.habbo.habbohotel.games.gamehall.GamehallGame;
import com.eu.habbo.habbohotel.games.gamehall.GamehallGameType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

public class PokerGame extends GamehallGame {
    public PokerGame(Room room, int stationId) {
        super(room, stationId, GamehallGameType.POKER);
    }

    @Override
    public void handleCommand(Habbo habbo, String command, String[] args) {
    }
}
