package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconDefinition;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.habbohotel.habbicons.HabbiconUserState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Map;

public class OwnedHabbiconsMessageComposer extends MessageComposer {
    private final HabbiconUserState state;
    private final HabbiconManager manager;

    public OwnedHabbiconsMessageComposer(HabbiconUserState state, HabbiconManager manager) {
        this.state = state != null ? state : new HabbiconUserState();
        this.manager = manager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.OwnedHabbiconsMessageComposer);
        this.response.appendInt(this.state.getOwned().size());
        for (Map.Entry<Integer, Boolean> entry : this.state.getOwned().entrySet()) {
            HabbiconDefinition habbicon = this.manager == null ? null : this.manager.getHabbicon(entry.getKey());
            this.response.appendInt(entry.getKey());
            this.response.appendInt(habbicon == null ? (entry.getValue() ? HabbiconManager.STATE_FAVORITE_OWNED : HabbiconManager.STATE_OWNED) : this.manager.getState(habbicon, this.state));
        }

        this.response.appendInt(this.state.getRecents().size());
        for (Integer habbiconId : this.state.getRecents()) {
            this.response.appendInt(habbiconId);
        }

        return this.response;
    }
}
