package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormSkillLevelChangedComposer extends MessageComposer {
    private final int skillLevel;
    private final int rankedPoints;

    public SnowStormSkillLevelChangedComposer(int skillLevel, int rankedPoints) {
        this.skillLevel = skillLevel;
        this.rankedPoints = rankedPoints;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormSkillLevelChangedComposer);
        response.appendInt(Math.max(1, skillLevel));
        response.appendInt(Math.max(0, rankedPoints));
        return response;
    }
}
