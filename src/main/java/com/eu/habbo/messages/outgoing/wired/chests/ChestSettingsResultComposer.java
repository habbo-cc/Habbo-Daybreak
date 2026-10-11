package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July S2C 1957 shape on local header 7124. */
public final class ChestSettingsResultComposer extends MessageComposer {
    private final int chestVisibleId;
    private final boolean notificationPreferences;

    public ChestSettingsResultComposer(int chestVisibleId, boolean notificationPreferences) {
        this.chestVisibleId = chestVisibleId;
        this.notificationPreferences = notificationPreferences;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestSettingsResultComposer);
        this.response.appendInt(this.chestVisibleId);
        this.response.appendBoolean(this.notificationPreferences);
        return this.response;
    }
}
