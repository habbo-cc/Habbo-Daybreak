package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredClickUserOutcome;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredHeldDownContext;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredClickUserResponseMessageComposer;

public class WiredClickUserMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomUnitId = this.packet.readRequiredInt();
        int heldTicks = this.packet.bytesAvailable() >= Integer.BYTES
                ? Math.max(0, Math.min(WiredHeldDownContext.MAX_DURATION_TICKS,
                this.packet.readRequiredInt()))
                : 0;
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected Wired click-user payload");
        }

        Habbo clicker = this.client.getHabbo();
        if (clicker == null || clicker.getHabboInfo() == null || clicker.getRoomUnit() == null) {
            return;
        }

        Room room = clicker.getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        Habbo target = room.getHabboByRoomUnitId(roomUnitId);
        if (target == null || target.getRoomUnit() == null || target.getHabboInfo().getCurrentRoom() != room) {
            return;
        }

        WiredClickUserOutcome outcome = WiredManager.triggerUserClicksUserWithOutcome(
                room, clicker.getRoomUnit(), target.getRoomUnit(), heldTicks);
        if (outcome.rotate()) {
            clicker.getRoomUnit().lookAtPoint(target.getRoomUnit().getCurrentLocation());
            clicker.getRoomUnit().statusUpdate(true);
        }

        this.client.sendResponse(new WiredClickUserResponseMessageComposer(
                roomUnitId, outcome.openMenu()));
    }
}
