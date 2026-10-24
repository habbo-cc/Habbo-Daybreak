package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.*;

/** AIR header 2588: configurations are removed independently of statuses. */
public final class WiredVariableFxConfigRemoveMessageComposer extends MessageComposer {
    private final List<Integer> ids;
    public WiredVariableFxConfigRemoveMessageComposer(Collection<Integer> ids) { this.ids=List.copyOf(ids); }
    @Override protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredVariableFxConfigRemoveMessageComposer); response.appendInt(ids.size());
        ids.forEach(response::appendInt); return response;
    }
}
