package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class Game2LeaderboardMessageComposer extends MessageComposer {
    private final int header;
    private final int gameId;

    public Game2LeaderboardMessageComposer(int header, int gameId) {
        this.header = header;
        this.gameId = gameId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(this.header);
        if (this.header == Outgoing.Game2WeeklyLeaderboardComposer ||
                this.header == Outgoing.WeeklyCompetitiveFriendsLeaderboardMessageComposer) {
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
        }
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(this.gameId);
        return this.response;
    }
}
