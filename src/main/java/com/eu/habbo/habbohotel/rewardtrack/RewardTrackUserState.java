package com.eu.habbo.habbohotel.rewardtrack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RewardTrackUserState {
    private int points;
    private boolean premium;
    private boolean complete;
    private boolean premiumComplete;
    private final Map<String, Integer> taskProgress;
    private final Set<String> claimedRewards;

    public RewardTrackUserState() {
        this.points = 0;
        this.premium = false;
        this.complete = false;
        this.premiumComplete = false;
        this.taskProgress = new HashMap<>();
        this.claimedRewards = new HashSet<>();
    }

    public int getPoints() {
        return this.points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isPremium() {
        return this.premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public boolean isComplete() {
        return this.complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isPremiumComplete() {
        return this.premiumComplete;
    }

    public void setPremiumComplete(boolean premiumComplete) {
        this.premiumComplete = premiumComplete;
    }

    public int getTaskProgress(String taskId) {
        Integer progress = this.taskProgress.get(taskId);
        return progress != null ? progress : 0;
    }

    public void setTaskProgress(String taskId, int progress) {
        this.taskProgress.put(taskId, progress);
    }

    public boolean isRewardClaimed(String rewardId) {
        return this.claimedRewards.contains(rewardId);
    }

    public void addClaimedReward(String rewardId) {
        this.claimedRewards.add(rewardId);
    }
}
