package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredAllVariablesHashMessageComposer;

/** Empty AIR request for the current authorized variable-catalog aggregate hash. */
public final class RequestAllVariablesHashMessageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() {
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected variable hash request payload");
        }
        WiredVariableCatalogRequestSupport.RequestContext context =
                WiredVariableCatalogRequestSupport.resolve(this.client);
        if (context == null) {
            return;
        }
        WiredVariableManager.Snapshot snapshot =
                WiredVariableCatalogRequestSupport.snapshot(context);
        WiredVariableCatalog.Catalog catalog = WiredVariableCatalog.build(context.room(), snapshot);
        this.client.sendResponse(new WiredAllVariablesHashMessageComposer(catalog.aggregateHash()));
    }
}
