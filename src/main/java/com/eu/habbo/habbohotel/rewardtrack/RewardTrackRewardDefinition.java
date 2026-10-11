package com.eu.habbo.habbohotel.rewardtrack;

public class RewardTrackRewardDefinition {
    private final String id;
    private final int requiredPoints;
    private final int productItemTypeId;
    private final String rewardType;
    private final String extraParams;
    private final int rewardAmount;
    private final boolean premium;
    private final int sortOrder;

    public RewardTrackRewardDefinition(String id, int requiredPoints, int productItemTypeId, String rewardType, String extraParams, int rewardAmount, boolean premium, int sortOrder) {
        this.id = id;
        this.requiredPoints = requiredPoints;
        this.productItemTypeId = productItemTypeId;
        this.rewardType = rewardType;
        this.extraParams = extraParams;
        this.rewardAmount = rewardAmount;
        this.premium = premium;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return this.id;
    }

    public int getRequiredPoints() {
        return this.requiredPoints;
    }

    public int getProductItemTypeId() {
        return this.productItemTypeId;
    }

    public String getRewardType() {
        return this.rewardType;
    }

    public String getExtraParams() {
        return this.extraParams;
    }

    public int getRewardAmount() {
        return this.rewardAmount;
    }

    public boolean isPremium() {
        return this.premium;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }
}
