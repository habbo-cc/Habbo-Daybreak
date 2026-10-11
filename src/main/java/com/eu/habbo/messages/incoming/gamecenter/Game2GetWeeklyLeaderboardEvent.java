package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.gamecenter.Game2LeaderboardMessageComposer;

public class Game2GetWeeklyLeaderboardEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new Game2LeaderboardMessageComposer(Outgoing.Game2WeeklyLeaderboardComposer, this.packet.readInt()));
    }
}
