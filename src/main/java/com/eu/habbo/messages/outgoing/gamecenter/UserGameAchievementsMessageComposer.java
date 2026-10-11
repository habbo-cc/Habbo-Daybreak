package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class UserGameAchievementsMessageComposer extends MessageComposer {
    private final int gameId;

    public UserGameAchievementsMessageComposer(int gameId) {
        this.gameId = gameId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserGameAchievementsMessageComposer);
        this.response.appendInt(this.gameId);
        this.response.appendInt(0);
        this.response.appendString("games");
        return this.response;
    }
}
