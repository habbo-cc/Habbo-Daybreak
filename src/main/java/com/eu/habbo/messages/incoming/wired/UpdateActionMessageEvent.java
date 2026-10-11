package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
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

public class UpdateActionMessageEvent extends MessageHandler {
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
        InteractionWiredEffect effect = room.getRoomSpecialTypes().getEffect(itemId);
        if (!WiredFeatureCapabilityGuard.isEditorReady(this.client, room, effect)
                || !WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                this.client,
                room,
                effect,
                WiredCategoryType.EFFECT)) {
            return;
        }

        try {
            // Wired 2.0: deterministic dispatch (no reflection). saveData is the
            // typed abstract on InteractionWiredEffect (takes GameClient, may throw
            // WiredSaveException); settings come from the 2.0 reader bridged to legacy.
            WiredSettings settings = InteractionWired.readSettingsV2(this.packet, WiredCategoryType.EFFECT, room).toLegacy();
            if (this.packet.bytesAvailable() != 0) {
                throw new MalformedPacketException("unexpected effect settings payload");
            }
            if (effect.saveData(settings, this.client)) {
                effect.setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
                this.client.sendResponse(new WiredSavedMessageComposer());
                effect.needsUpdate(true);
                Emulator.getThreading().run(effect);
                WiredManager.invalidateRoom(room);
            }
        } catch (WiredSaveException e) {
            this.client.sendResponse(new WiredValidationErrorMessageComposer(e.getMessage()));
        }
    }
}
