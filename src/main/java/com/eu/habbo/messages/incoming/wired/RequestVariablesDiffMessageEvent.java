package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredAllVariablesDiffMessageComposer;

import java.util.LinkedHashMap;
import java.util.Map;

/** AIR variable-catalog diff request on local header 7006. */
public final class RequestVariablesDiffMessageEvent extends MessageHandler {
    public static final int MAX_VARIABLE_ID_LENGTH = 64;

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() {
        Map<String, Integer> clientHashes = parseHashes(this.packet);
        WiredVariableCatalogRequestSupport.RequestContext context =
                WiredVariableCatalogRequestSupport.resolve(this.client);
        if (context == null) {
            return;
        }

        WiredVariableManager.Snapshot snapshot = WiredVariableCatalogRequestSupport.snapshot(context);
        WiredVariableCatalog.Catalog catalog = WiredVariableCatalog.build(context.room(), snapshot);
        for (WiredAllVariablesDiffMessageComposer chunk
                : WiredAllVariablesDiffMessageComposer.chunks(catalog, clientHashes)) {
            this.client.sendResponse(chunk);
        }
    }

    static Map<String, Integer> parseHashes(ClientMessage packet) {
        int count = packet.readBoundedCount(WiredVariableManager.MAX_DIFF_HASHES, 6);
        Map<String, Integer> hashes = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String variableId = packet.readBoundedString(MAX_VARIABLE_ID_LENGTH);
            if (variableId.isEmpty() || hashes.containsKey(variableId)) {
                throw new MalformedPacketException("invalid or duplicate variable ID");
            }
            hashes.put(variableId, packet.readRequiredInt());
        }
        if (packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected variable diff request payload");
        }
        return Map.copyOf(hashes);
    }
}
