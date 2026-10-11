package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.WiredValidationErrorMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedMessageComposer;

import java.util.Collections;

public class UpdateTriggerMessageEvent extends MessageHandler {
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

        // Client sends the room-visible id (BC furni use virtual ids); resolve to db id.
        int itemId = room.getItemManager().resolveVisibleId(visibleId);
        InteractionWiredTrigger trigger = room.getRoomSpecialTypes().getTrigger(itemId);
        if (!WiredFeatureCapabilityGuard.isEditorReady(this.client, room, trigger)
                || !WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                this.client,
                room,
                trigger,
                WiredCategoryType.TRIGGER)) {
            return;
        }

        // Wired 2.0: deterministic dispatch (no reflection). saveData is the
        // typed abstract on InteractionWiredTrigger; settings come from the
        // 2.0 reader bridged to the legacy DTO.
        WiredSettings settings = InteractionWired.readSettingsV2(this.packet, WiredCategoryType.TRIGGER, room).toLegacy();
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected trigger settings payload");
        }

        if (trigger.saveData(settings)) {
            trigger.setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
            this.client.sendResponse(new WiredSavedMessageComposer());
            trigger.needsUpdate(true);
            Emulator.getThreading().run(trigger);
            WiredManager.invalidateRoom(room);
        } else {
            String localizationKey = trigger.getSaveErrorLocalizationKey();
            if (localizationKey != null) {
                this.client.sendResponse(new WiredValidationErrorMessageComposer(
                        localizationKey,
                        Collections.emptyMap()));
            } else {
                this.client.sendResponse(new WiredValidationErrorMessageComposer(
                        "There was an error while saving that trigger"));
            }
        }
    }
}
