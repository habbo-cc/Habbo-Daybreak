package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityState;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCapabilitiesMessageComposer;

/** Handles the revision-1 Wired 2.0 capability request (header 7099). */
public class RequestWiredCapabilitiesMessageEvent extends MessageHandler {
    @Override
    public void handle() {
        int protocolRevision = this.packet.readRequiredInt();
        int clientCapabilityMask = this.packet.readRequiredInt();
        int requestedRoomId = this.packet.readRequiredInt();

        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected Wired capability request payload");
        }
        if (this.client.getHabbo() == null) {
            return;
        }

        int validatedRoomId = this.validateCurrentRoom(requestedRoomId);
        boolean masterEnabled = Emulator.getConfig().containsKey(WiredCapabilityService.CONFIG_ENABLED)
                && Emulator.getConfig().getBoolean(WiredCapabilityService.CONFIG_ENABLED, false);
        int availableRoomCapabilityMask = this.availableRoomCapabilityMask(validatedRoomId);

        WiredCapabilityState state = WiredCapabilityService.negotiate(
                masterEnabled,
                protocolRevision,
                clientCapabilityMask,
                validatedRoomId,
                availableRoomCapabilityMask);

        this.client.setWiredCapabilityState(state);
        this.client.sendResponse(new WiredCapabilitiesMessageComposer(state));
    }

    private int validateCurrentRoom(int requestedRoomId) {
        if (requestedRoomId <= 0 || this.client.getHabbo() == null) {
            return 0;
        }

        Room currentRoom = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        return currentRoom != null && currentRoom.getId() == requestedRoomId
                ? requestedRoomId
                : 0;
    }

    private int availableRoomCapabilityMask(int validatedRoomId) {
        if (validatedRoomId <= 0) {
            return 0;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        boolean variableServiceOperational = room != null
                && room.getRoomSpecialTypes() != null
                && room.getRoomSpecialTypes().getWiredVariableManager() != null
                && room.getRoomSpecialTypes().getWiredVariableManager().isOperational();
        return WiredCapabilityService.availableRoomCapabilityMask(variableServiceOperational);
    }
}
