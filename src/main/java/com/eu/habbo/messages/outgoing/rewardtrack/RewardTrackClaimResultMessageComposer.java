package com.eu.habbo.messages.outgoing.rewardtrack;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RewardTrackClaimResultMessageComposer extends MessageComposer {
    private final String trackId;
    private final String rewardId;
    private final int resultCode;

    public RewardTrackClaimResultMessageComposer(String trackId, String rewardId, int resultCode) {
        this.trackId = trackId;
        this.rewardId = rewardId;
        this.resultCode = resultCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackClaimResultMessageComposer);
        this.response.appendString(this.trackId);
        this.response.appendString(this.rewardId);
        this.response.appendInt(this.resultCode);
        return this.response;
    }
}
