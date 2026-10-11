package com.eu.habbo.messages.outgoing.rewardtrack;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RewardTrackPremiumResultMessageComposer extends MessageComposer {
    private final String trackId;
    private final int resultCode;
    private final int points;

    public RewardTrackPremiumResultMessageComposer(String trackId, int resultCode, int points) {
        this.trackId = trackId;
        this.resultCode = resultCode;
        this.points = points;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackPremiumResultMessageComposer);
        this.response.appendString(this.trackId);
        this.response.appendInt(this.resultCode);
        this.response.appendInt(this.points);
        return this.response;
    }
}
