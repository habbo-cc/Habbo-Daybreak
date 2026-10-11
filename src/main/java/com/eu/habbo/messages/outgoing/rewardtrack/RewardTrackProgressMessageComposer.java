package com.eu.habbo.messages.outgoing.rewardtrack;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RewardTrackProgressMessageComposer extends MessageComposer {
    private final String trackId;
    private final String taskId;
    private final int progressCount;
    private final int points;

    public RewardTrackProgressMessageComposer(String trackId, String taskId, int progressCount, int points) {
        this.trackId = trackId;
        this.taskId = taskId;
        this.progressCount = progressCount;
        this.points = points;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackProgressMessageComposer);
        this.response.appendString(this.trackId);
        this.response.appendString(this.taskId);
        this.response.appendInt(this.progressCount);
        this.response.appendInt(this.points);
        return this.response;
    }
}
