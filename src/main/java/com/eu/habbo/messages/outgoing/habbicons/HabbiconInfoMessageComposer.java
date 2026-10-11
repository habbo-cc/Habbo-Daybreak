package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconDefinition;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.habbohotel.habbicons.HabbiconUserState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class HabbiconInfoMessageComposer extends MessageComposer {
    private final boolean enabled;
    private final int requestedHabbiconId;
    private final HabbiconDefinition habbicon;
    private final HabbiconUserState state;
    private final HabbiconManager manager;

    public HabbiconInfoMessageComposer(boolean enabled, int requestedHabbiconId, HabbiconDefinition habbicon, HabbiconUserState state, HabbiconManager manager) {
        this.enabled = enabled;
        this.requestedHabbiconId = requestedHabbiconId;
        this.habbicon = habbicon;
        this.state = state != null ? state : new HabbiconUserState();
        this.manager = manager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconInfoMessageComposer);
        if (!this.enabled || this.habbicon == null) {
            this.response.appendInt(this.requestedHabbiconId);
            this.response.appendString("");
            this.response.appendInt(0);
            this.response.appendInt(HabbiconManager.STATE_LOCKED);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            return this.response;
        }

        this.response.appendInt(this.habbicon.getId());
        this.response.appendString(this.habbicon.getName());
        this.response.appendInt(this.habbicon.getCollectionId());
        this.response.appendInt(this.manager == null ? HabbiconManager.STATE_PURCHASABLE : this.manager.getState(this.habbicon, this.state));
        this.response.appendInt(this.habbicon.getPriceCredits());
        this.response.appendInt(this.habbicon.getPriceActivityPoints());
        this.response.appendInt(this.habbicon.getActivityPointType());
        return this.response;
    }
}
