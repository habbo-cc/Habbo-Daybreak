package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Sent when a user tries to enter the Infobus while its doors are closed.
 */
public class CannotEnterBusMessageComposer extends MessageComposer {
    private final String message;

    public CannotEnterBusMessageComposer(String message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CannotEnterBusMessageComposer);
        this.response.appendString(this.message);
        return this.response;
    }
}
