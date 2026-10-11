package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredHeldDownContext;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * July's independent room-object click notification (header 443).
 *
 * This is intentionally separate from UseFurnitureMessageEvent: a single click
 * must notify Wired without changing the furni state or invoking its normal use
 * behaviour. Wall items use a negative room-visible id on the wire.
 */
public class ClickFurniMessageEvent extends MessageHandler {
    @Override
    public void handle() {
        if (this.client.getHabbo() == null) {
            return;
        }

        int clickedVisibleId = this.packet.readRequiredInt();
        int heldTicks = Math.max(0, Math.min(
                WiredHeldDownContext.MAX_DURATION_TICKS, this.packet.readRequiredInt()));
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected ClickFurni payload");
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || this.client.getHabbo().getRoomUnit() == null) {
            return;
        }

        // The signed id distinguishes wall and floor furni; both resolve through
        // the room-visible-id lookup (including Builders Club virtual ids).
        HabboItem item = room.getHabboItem(Math.abs(clickedVisibleId));
        if (item != null) {
            WiredManager.triggerUserClicksFurni(
                    room, this.client.getHabbo().getRoomUnit(), item, heldTicks);
            WiredManager.triggerUserClicksTile(
                    room, this.client.getHabbo().getRoomUnit(), item, heldTicks);
        }
    }
}
