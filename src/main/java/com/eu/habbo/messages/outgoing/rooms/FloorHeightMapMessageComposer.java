package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FloorHeightMapMessageComposer extends MessageComposer {
    private final Room room;

    public FloorHeightMapMessageComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FloorHeightMapMessageComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(this.room.getWallHeight()); //FixedWallsHeight
        this.response.appendString(this.room.getLayout().getRelativeMap());
        var areaHides = this.room.getRoomSpecialTypes().getItemsOfType(InteractionAreaHide.class);
        this.response.appendInt(areaHides.size());
        for (HabboItem item : areaHides) {
            InteractionAreaHide areaHide = (InteractionAreaHide) item;
            this.response.appendInt(areaHide.getRoomVisibleId());
            this.response.appendBoolean(areaHide.isEnabled());
            this.response.appendInt(areaHide.getRootX());
            this.response.appendInt(areaHide.getRootY());
            this.response.appendInt(areaHide.getAreaWidth());
            this.response.appendInt(areaHide.getAreaLength());
            this.response.appendBoolean(areaHide.isInverted());
        }
        this.response.appendInt(this.room.getLayout().getDoorX());
        this.response.appendInt(this.room.getLayout().getDoorY());
        this.response.appendFloat(this.room.getLayout().getDoorZ());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
