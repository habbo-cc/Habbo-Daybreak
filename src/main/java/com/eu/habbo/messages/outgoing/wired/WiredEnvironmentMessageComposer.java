package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonAchievementEnabler;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredEnvironmentMessageComposer extends MessageComposer {
    private final Room room;

    public WiredEnvironmentMessageComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredEnvironmentMessageComposer);
        this.response.appendBoolean(this.room != null
                && !this.room.getRoomSpecialTypes().getTriggers(WiredTriggerType.CLICK_USER).isEmpty());
        java.util.SortedSet<String> achievements = new java.util.TreeSet<>();
        if (this.room != null) for (var addon : this.room.getRoomSpecialTypes().getAddons(WiredAddonType.ACHIEVEMENT_ENABLER))
            if (addon instanceof WiredAddonAchievementEnabler enabler) achievements.addAll(enabler.identifiers());
        this.response.appendInt(achievements.size());
        achievements.forEach(this.response::appendString);
        return this.response;
    }
}
