package com.eu.habbo.habbohotel.rewardtrack;

public class RewardTrackTaskLevelDefinition {
    private final int requiredCount;
    private final int pointsReward;
    private final boolean premium;

    public RewardTrackTaskLevelDefinition(int requiredCount, int pointsReward, boolean premium) {
        this.requiredCount = requiredCount;
        this.pointsReward = pointsReward;
        this.premium = premium;
    }

    public int getRequiredCount() {
        return this.requiredCount;
    }

    public int getPointsReward() {
        return this.pointsReward;
    }

    public boolean isPremium() {
        return this.premium;
    }
}
