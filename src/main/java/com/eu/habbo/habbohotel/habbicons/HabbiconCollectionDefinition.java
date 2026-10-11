package com.eu.habbo.habbohotel.habbicons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HabbiconCollectionDefinition {
    private final int id;
    private final String name;
    private final boolean enabled;
    private final int priceCredits;
    private final int priceActivityPoints;
    private final int activityPointType;
    private final int sortOrder;
    private final List<HabbiconDefinition> habbicons;

    public HabbiconCollectionDefinition(int id, String name, boolean enabled, int priceCredits, int priceActivityPoints, int activityPointType, int sortOrder) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.priceCredits = priceCredits;
        this.priceActivityPoints = priceActivityPoints;
        this.activityPointType = activityPointType;
        this.sortOrder = sortOrder;
        this.habbicons = new ArrayList<>();
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled;
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

    public void addHabbicon(HabbiconDefinition habbicon) {
        if (habbicon != null) {
            this.habbicons.add(habbicon);
        }
    }

    public List<HabbiconDefinition> getHabbicons() {
        return Collections.unmodifiableList(this.habbicons);
    }
}
