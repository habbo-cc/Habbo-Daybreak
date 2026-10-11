package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WeeklyGameRewardWinnersMessageComposer extends MessageComposer {
    private final int gameId;

    public WeeklyGameRewardWinnersMessageComposer(int gameId) {
        this.gameId = gameId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WeeklyGameRewardWinnersMessageComposer);
        this.response.appendInt(this.gameId);
        this.response.appendInt(0);
        return this.response;
    }
}
