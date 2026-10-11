package com.eu.habbo.messages.outgoing.rewardtrack;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackDefinition;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackRewardDefinition;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackTaskDefinition;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackTaskLevelDefinition;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackUserState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RewardTracksMessageComposer extends MessageComposer {
    private final boolean disabled;
    private final List<RewardTrackDefinition> tracks;
    private final Map<String, RewardTrackUserState> states;
    private final boolean reload;

    public RewardTracksMessageComposer(boolean disabled, List<RewardTrackDefinition> tracks, Map<String, RewardTrackUserState> states, boolean reload) {
        this.disabled = disabled;
        this.tracks = tracks != null ? tracks : Collections.emptyList();
        this.states = states != null ? states : Collections.emptyMap();
        this.reload = reload;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTracksMessageComposer);
        this.response.appendBoolean(this.disabled);
        this.response.appendInt(this.disabled ? 0 : this.tracks.size());

        if (!this.disabled) {
            for (RewardTrackDefinition track : this.tracks) {
                this.serializeTrack(track);
            }
        }

        this.response.appendBoolean(this.reload);
        return this.response;
    }

    private void serializeTrack(RewardTrackDefinition track) {
        RewardTrackUserState state = this.states.get(track.getId());
        if (state == null) {
            state = new RewardTrackUserState();
        }

        this.response.appendString(track.getId());
        this.response.appendString(track.getTheme());
        this.response.appendInt(state.getPoints());
            this.response.appendBoolean(track.isPremiumEnabled());
        if (track.isPremiumEnabled()) {
            this.response.appendDouble(track.getTaskPointsBoost());
            this.response.appendInt(track.getInstantPoints());
            this.response.appendInt(track.getPremiumCostPoints());
            this.response.appendInt(track.getPremiumCostCredits());
            this.response.appendInt(track.getPremiumCostPointsType());
        }

        this.response.appendBoolean(state.isPremium());
        this.response.appendBoolean(state.isComplete());
        this.response.appendBoolean(state.isPremiumComplete());

        this.response.appendInt(track.getTasks().size());
        for (RewardTrackTaskDefinition task : track.getTasks()) {
            this.serializeTask(task, state);
        }

        this.response.appendInt(track.getRewards().size());
        for (RewardTrackRewardDefinition reward : track.getRewards()) {
            this.serializeReward(reward, state);
        }
    }

    private void serializeTask(RewardTrackTaskDefinition task, RewardTrackUserState state) {
        this.response.appendString(task.getId());
        this.response.appendString(task.getActionType());
        this.response.appendString(task.getParameter());
        this.response.appendInt(state.getTaskProgress(task.getId()));
        this.response.appendBoolean(task.isPremium());
        this.response.appendInt(task.getLevels().size());

        for (RewardTrackTaskLevelDefinition level : task.getLevels()) {
            this.response.appendInt(level.getRequiredCount());
            this.response.appendInt(level.getPointsReward());
            this.response.appendBoolean(level.isPremium());
        }
    }

    private void serializeReward(RewardTrackRewardDefinition reward, RewardTrackUserState state) {
        boolean available = !reward.isPremium() || state.isPremium();
        available = available && state.getPoints() >= reward.getRequiredPoints();

        this.response.appendString(reward.getId());
        this.response.appendInt(reward.getRequiredPoints());
        this.response.appendShort(this.getDisplayProductTypeId(reward));
        this.response.appendString(this.getDisplayItemTypeId(reward));
        this.response.appendString(this.getDisplayExtraParams(reward));
        this.response.appendInt(reward.getRewardAmount());
        this.response.appendBoolean(reward.isPremium());
        this.response.appendBoolean(available);
        this.response.appendBoolean(state.isRewardClaimed(reward.getId()));
    }

    private int getDisplayProductTypeId(RewardTrackRewardDefinition reward) {
        if ("badge".equalsIgnoreCase(reward.getRewardType())) {
            return 5;
        }

        CatalogDisplay catalogDisplay = this.getCatalogDisplay(reward);
        if (catalogDisplay != null) {
            return catalogDisplay.productTypeId;
        }

        return reward.getProductItemTypeId();
    }

    private String getDisplayItemTypeId(RewardTrackRewardDefinition reward) {
        if ("badge".equalsIgnoreCase(reward.getRewardType())) {
            return reward.getExtraParams();
        }

        CatalogDisplay catalogDisplay = this.getCatalogDisplay(reward);
        if (catalogDisplay != null) {
            return String.valueOf(catalogDisplay.itemTypeId);
        }

        return reward.getRewardType();
    }

    private String getDisplayExtraParams(RewardTrackRewardDefinition reward) {
        CatalogDisplay catalogDisplay = this.getCatalogDisplay(reward);
        if (catalogDisplay != null) {
            return catalogDisplay.extraData;
        }

        return reward.getExtraParams();
    }

    private CatalogDisplay getCatalogDisplay(RewardTrackRewardDefinition reward) {
        if (!"catalog_item".equalsIgnoreCase(reward.getRewardType()) && !"catalog.item".equalsIgnoreCase(reward.getRewardType())) {
            return null;
        }

        int catalogItemId;
        try {
            String value = reward.getExtraParams();
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            String[] parts = value.split("\\|", 2);
            catalogItemId = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }

        CatalogItem catalogItem = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(catalogItemId);
        if (catalogItem == null || catalogItem.getBaseItems().isEmpty()) {
            return null;
        }

        Item baseItem = catalogItem.getBaseItems().iterator().next();
        int productTypeId = this.getProductTypeId(baseItem.getType());
        if (productTypeId == 0) {
            return null;
        }

        return new CatalogDisplay(productTypeId, baseItem.getId(), catalogItem.getExtradata() == null ? "" : catalogItem.getExtradata());
    }

    private int getProductTypeId(FurnitureType type) {
        if (type == FurnitureType.WALL) {
            return 1;
        }
        if (type == FurnitureType.FLOOR) {
            return 2;
        }
        if (type == FurnitureType.BADGE) {
            return 5;
        }
        if (type == FurnitureType.PET) {
            return 11;
        }
        return 0;
    }

    private static class CatalogDisplay {
        private final int productTypeId;
        private final int itemTypeId;
        private final String extraData;

        private CatalogDisplay(int productTypeId, int itemTypeId, String extraData) {
            this.productTypeId = productTypeId;
            this.itemTypeId = itemTypeId;
            this.extraData = extraData;
        }
    }
}
