package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconCollectionDefinition;
import com.eu.habbo.habbohotel.habbicons.HabbiconDefinition;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.habbohotel.habbicons.HabbiconUserState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collections;
import java.util.List;

public class HabbiconShopDataMessageComposer extends MessageComposer {
    private final boolean disabled;
    private final List<HabbiconCollectionDefinition> collections;
    private final HabbiconUserState state;
    private final HabbiconManager manager;

    public HabbiconShopDataMessageComposer(boolean disabled, String assetRoot, String assetHash, List<HabbiconCollectionDefinition> collections, HabbiconUserState state, HabbiconManager manager) {
        this.disabled = disabled;
        this.collections = collections == null ? Collections.emptyList() : collections;
        this.state = state != null ? state : new HabbiconUserState();
        this.manager = manager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconShopDataMessageComposer);
        this.response.appendInt(this.disabled ? 0 : this.collections.size());

        if (!this.disabled) {
            for (HabbiconCollectionDefinition collection : this.collections) {
                this.serializeCollection(collection);
            }
        }

        return this.response;
    }

    private void serializeCollection(HabbiconCollectionDefinition collection) {
        this.response.appendInt(collection.getId());
        this.response.appendString(collection.getName());
        this.response.appendBoolean(this.manager != null && this.manager.isCollectionComplete(collection.getId(), this.state));
        HabbiconDefinition reward = this.getReward(collection);
        this.response.appendInt(reward == null ? 0 : reward.getId());
        this.response.appendInt(reward == null ? HabbiconManager.STATE_LOCKED : this.manager.getState(reward, this.state));
        this.response.appendInt(collection.getPriceCredits());
        this.response.appendInt(collection.getPriceActivityPoints());
        this.response.appendInt(collection.getActivityPointType());
        this.response.appendInt(this.countNonReward(collection));

        for (HabbiconDefinition habbicon : collection.getHabbicons()) {
            if (habbicon.isReward()) {
                continue;
            }
            this.serializeHabbicon(habbicon);
        }
    }

    private void serializeHabbicon(HabbiconDefinition habbicon) {
        this.response.appendInt(habbicon.getId());
        this.response.appendString(habbicon.getName());
        this.response.appendInt(habbicon.getCollectionId());
        this.response.appendInt(this.manager == null ? HabbiconManager.STATE_PURCHASABLE : this.manager.getState(habbicon, this.state));
        this.response.appendInt(habbicon.getPriceCredits());
        this.response.appendInt(habbicon.getPriceActivityPoints());
        this.response.appendInt(habbicon.getActivityPointType());
    }

    private HabbiconDefinition getReward(HabbiconCollectionDefinition collection) {
        for (HabbiconDefinition habbicon : collection.getHabbicons()) {
            if (habbicon.isReward()) {
                return habbicon;
            }
        }
        return null;
    }

    private int countNonReward(HabbiconCollectionDefinition collection) {
        int total = 0;
        for (HabbiconDefinition habbicon : collection.getHabbicons()) {
            if (!habbicon.isReward()) {
                total++;
            }
        }
        return total;
    }
}
