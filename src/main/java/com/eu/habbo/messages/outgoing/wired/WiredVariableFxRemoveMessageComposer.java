package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.*;

/** AIR header 236: complete opaque keys, including entity suffix. */
public final class WiredVariableFxRemoveMessageComposer extends MessageComposer {
    private final List<String> keys;
    public WiredVariableFxRemoveMessageComposer(Collection<String> keys) { this.keys=List.copyOf(keys); }
    @Override protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredVariableFxRemoveMessageComposer); response.appendInt(keys.size());
        keys.forEach(response::appendString); return response;
    }
}
