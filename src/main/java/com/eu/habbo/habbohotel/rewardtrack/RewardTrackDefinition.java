package com.eu.habbo.habbohotel.rewardtrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RewardTrackDefinition {
    private final String id;
    private final String theme;
    private final int sortOrder;
    private final boolean premiumEnabled;
    private final double taskPointsBoost;
    private final int instantPoints;
    private final int premiumCostPoints;
    private final int premiumCostPointsType;
    private final int premiumCostCredits;
    private final List<RewardTrackTaskDefinition> tasks;
    private final List<RewardTrackRewardDefinition> rewards;

    public RewardTrackDefinition(String id, String theme, int sortOrder, boolean premiumEnabled, double taskPointsBoost, int instantPoints, int premiumCostPoints, int premiumCostPointsType, int premiumCostCredits) {
        this.id = id;
        this.theme = theme;
        this.sortOrder = sortOrder;
        this.premiumEnabled = premiumEnabled;
        this.taskPointsBoost = taskPointsBoost;
        this.instantPoints = instantPoints;
        this.premiumCostPoints = premiumCostPoints;
        this.premiumCostPointsType = premiumCostPointsType;
        this.premiumCostCredits = premiumCostCredits;
        this.tasks = new ArrayList<>();
        this.rewards = new ArrayList<>();
    }

    public String getId() {
        return this.id;
    }

    public String getTheme() {
        return this.theme;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public boolean isPremiumEnabled() {
        return this.premiumEnabled;
    }

    public double getTaskPointsBoost() {
        return this.taskPointsBoost;
    }

    public int getInstantPoints() {
        return this.instantPoints;
    }

    public int getPremiumCostPoints() {
        return this.premiumCostPoints;
    }

    public int getPremiumCostPointsType() {
        return this.premiumCostPointsType;
    }

    public int getPremiumCostCredits() {
        return this.premiumCostCredits;
    }

    public List<RewardTrackTaskDefinition> getTasks() {
        return Collections.unmodifiableList(this.tasks);
    }

    public List<RewardTrackRewardDefinition> getRewards() {
        return Collections.unmodifiableList(this.rewards);
    }

    public void addTask(RewardTrackTaskDefinition task) {
        this.tasks.add(task);
    }

    public void addReward(RewardTrackRewardDefinition reward) {
        this.rewards.add(reward);
    }
}
