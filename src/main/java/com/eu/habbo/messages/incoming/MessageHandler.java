package com.eu.habbo.messages.incoming;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ClientMessage;

public abstract class MessageHandler {
    public GameClient client;
    public ClientMessage packet;
    public boolean isCancelled = false;

    public abstract void handle() throws Exception;

    public int getRatelimit() {
        return 0;
    }

    /**
     * Identifies the rate-limit bucket used for this handler. Most handlers
     * have one packet header and therefore share a bucket by handler class.
     * Multiplexed handlers may override this to isolate their packet headers.
     */
    public Object getRatelimitKey(int messageId) {
        return this.getClass();
    }
}
