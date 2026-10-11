package com.eu.habbo.habbohotel.habbicons;

public class HabbiconDefinition {
    private final int id;
    private final int collectionId;
    private final String name;
    private final boolean enabled;
    private final boolean reward;
    private final int priceCredits;
    private final int priceActivityPoints;
    private final int activityPointType;
    private final int sortOrder;

    public HabbiconDefinition(int id, int collectionId, String name, boolean enabled, boolean reward, int priceCredits, int priceActivityPoints, int activityPointType, int sortOrder) {
        this.id = id;
        this.collectionId = collectionId;
        this.name = name;
        this.enabled = enabled;
        this.reward = reward;
        this.priceCredits = priceCredits;
        this.priceActivityPoints = priceActivityPoints;
        this.activityPointType = activityPointType;
        this.sortOrder = sortOrder;
    }

    public int getId() {
        return this.id;
    }

    public int getCollectionId() {
        return this.collectionId;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isReward() {
        return this.reward;
    }

    public int getPriceCredits() {
        return this.priceCredits;
    }

    public int getPriceActivityPoints() {
        return this.priceActivityPoints;
    }

    public int getActivityPointType() {
        return this.activityPointType;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }
}
