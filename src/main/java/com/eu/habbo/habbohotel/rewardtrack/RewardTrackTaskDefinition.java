package com.eu.habbo.habbohotel.rewardtrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RewardTrackTaskDefinition {
    private final String id;
    private final String actionType;
    private final String parameter;
    private final boolean premium;
    private final int sortOrder;
    private final List<RewardTrackTaskLevelDefinition> levels;

    public RewardTrackTaskDefinition(String id, String actionType, String parameter, boolean premium, int sortOrder) {
        this.id = id;
        this.actionType = actionType;
        this.parameter = parameter;
        this.premium = premium;
        this.sortOrder = sortOrder;
        this.levels = new ArrayList<>();
    }

    public String getId() {
        return this.id;
    }

    public String getActionType() {
        return this.actionType;
    }

    public String getParameter() {
        return this.parameter;
    }

    public boolean isPremium() {
        return this.premium;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public List<RewardTrackTaskLevelDefinition> getLevels() {
        return Collections.unmodifiableList(this.levels);
    }

    public void addLevel(RewardTrackTaskLevelDefinition level) {
        this.levels.add(level);
    }
}
