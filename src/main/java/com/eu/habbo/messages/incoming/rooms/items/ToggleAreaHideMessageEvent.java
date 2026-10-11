package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.AreaHideAccess;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ToggleAreaHideMessageEvent extends MessageHandler {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        int reserved = this.packet.readRequiredInt(); // July sends a reserved zero.
        if (reserved != 0 || this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected area-hide toggle payload");
        }

        if (this.client.getHabbo() == null) {
            return;
        }
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }
        int itemId = room.getItemManager().resolveVisibleId(visibleId);
        HabboItem item = room.getHabboItem(itemId);
        if (item instanceof InteractionAreaHide areaHide
                && AreaHideAccess.canModify(this.client, room, areaHide)) {
            areaHide.toggle(room);
        }
    }
}
