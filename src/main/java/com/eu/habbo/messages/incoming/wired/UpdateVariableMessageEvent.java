package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.WiredValidationErrorMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedMessageComposer;

/** Strict July-compatible save handler for Wired 2.0 variables (header 7002). */
public class UpdateVariableMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int visibleId = this.packet.readRequiredInt();
        if (this.client.getHabbo() == null) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }
        if (!this.client.getWiredCapabilityState().supportsRoom(
                WiredCapabilityService.CAPABILITY_VARIABLES, room.getId())) {
            return;
        }
        if (room.getRoomSpecialTypes().getWiredVariableManager() == null
                || !room.getRoomSpecialTypes().getWiredVariableManager().isOperational()) {
            return;
        }

        int itemId = room.getItemManager().resolveVisibleId(visibleId);
        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(itemId);
        if (!WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                this.client,
                room,
                variable,
                WiredCategoryType.VARIABLE)) {
            return;
        }

        WiredSettingsV2 settings = InteractionWired.readSettingsV2(this.packet, WiredCategoryType.VARIABLE, room);
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected variable settings payload");
        }

        if (variable.saveData(settings)) {
            variable.setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
            room.getRoomSpecialTypes().refreshVariableDefinition(variable);
            this.client.sendResponse(new WiredSavedMessageComposer());
            variable.needsUpdate(true);
            Emulator.getThreading().run(variable);
            WiredManager.invalidateRoom(room);
        } else {
            this.client.sendResponse(new WiredValidationErrorMessageComposer(
                    "Invalid Wired variable configuration"));
        }
    }
}
