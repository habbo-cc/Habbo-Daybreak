package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WeeklyGameRewardMessageComposer extends MessageComposer {
    private final int gameId;

    public WeeklyGameRewardMessageComposer(int gameId) {
        this.gameId = gameId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WeeklyGameRewardMessageComposer);
        this.response.appendInt(this.gameId);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendBoolean(false);
        return this.response;
    }
}
