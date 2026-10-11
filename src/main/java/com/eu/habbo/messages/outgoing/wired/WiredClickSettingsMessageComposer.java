package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July click settings payload: exactly user option then furni option. */
public class WiredClickSettingsMessageComposer extends MessageComposer {
    private final int userOption;
    private final int furniOption;

    public WiredClickSettingsMessageComposer(int userOption, int furniOption) {
        this.userOption = userOption;
        this.furniOption = furniOption;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickSettingsMessageComposer);
        this.response.appendInt(this.userOption);
        this.response.appendInt(this.furniOption);
        return this.response;
    }
}
