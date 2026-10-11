package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class GameAchievementsMessageComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GameAchievementsMessageComposer);
        this.response.appendInt(1);
        this.response.appendInt(3);
        this.response.appendInt(5);
        this.appendAchievement(1, "BaseJumpBigParachute", 20);
        this.appendAchievement(2, "BaseJumpDaysPlayed", 20);
        this.appendAchievement(3, "BaseJumpMissile", 20);
        this.appendAchievement(4, "BaseJumpShield", 20);
        this.appendAchievement(5, "BaseJumpWins", 20);
        return this.response;
    }

    private void appendAchievement(int achievementId, String name, int levels) {
        this.response.appendInt(achievementId);
        this.response.appendString(name);
        this.response.appendInt(levels);
    }
}
