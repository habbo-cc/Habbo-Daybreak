package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxData.Status;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.*;

/** AIR header 754: packet and row initialization flags are independent. */
public final class WiredVariableFxStatusMessageComposer extends MessageComposer {
    private final boolean initialize;
    private final List<Status> statuses;
    public WiredVariableFxStatusMessageComposer(boolean initialize,Collection<Status> statuses) {
        this.initialize=initialize; this.statuses=List.copyOf(statuses);
    }
    @Override protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredVariableFxStatusMessageComposer);
        response.appendBoolean(initialize); response.appendInt(statuses.size());
        for(Status s:statuses) {
            response.appendString(s.key()); response.appendBoolean(s.initialize()); response.appendBoolean(s.user());
            response.appendInt(s.entity()); response.appendLong(s.value()); response.appendBoolean(s.min()!=null);
            if(s.min()!=null) { response.appendLong(s.min()); response.appendLong(s.max()); }
            WiredVariableFxConfigMessageComposer.extras(response,s.extra());
        }
        return response;
    }
}
