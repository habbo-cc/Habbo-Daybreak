package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

import java.util.Objects;
import java.util.function.Consumer;

/** Typed writer used by the fixed-shape July Wired Menu responses. */
public final class WiredMenuMessageComposer extends MessageComposer {
    private final int header;
    private final Consumer<ServerMessage> payload;

    public WiredMenuMessageComposer(int header, Consumer<ServerMessage> payload) {
        this.header = header;
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(this.header);
        this.payload.accept(this.response);
        return this.response;
    }
}
