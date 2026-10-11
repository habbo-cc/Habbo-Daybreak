package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionHanditemBlock;
import com.eu.habbo.habbohotel.items.interactions.InteractionInvisControl;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** July packet 2942: room configuration-item state booleans. */
public class ConfigurationItemStatesMessageComposer extends MessageComposer {
    private final Room room;

    public ConfigurationItemStatesMessageComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ConfigurationItemStatesMessageComposer);
        this.response.appendBoolean(InteractionHanditemBlock.isHanditemControlBlocked(this.room));
        this.response.appendBoolean(false); // chooser disabled
        this.response.appendBoolean(false); // free furni movements
        this.response.appendBoolean(InteractionInvisControl.isInvisibleFurniHidden(this.room));
        return this.response;
    }
}
