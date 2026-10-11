package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredSavedMessageComposer;

public class UpdateSelectorMessageEvent extends MessageHandler {
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
        InteractionWiredSelector selector = room.getRoomSpecialTypes().getSelector(itemId);
        if (!WiredFeatureCapabilityGuard.isEditorReady(this.client, room, selector)
                || !WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                this.client,
                room,
                selector,
                WiredCategoryType.SELECTOR)) {
            return;
        }

        WiredSettingsV2 settings = InteractionWired.readSettingsV2(this.packet, WiredCategoryType.SELECTOR, room);
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected selector settings payload");
        }
        if (selector.saveData(settings)) {
            selector.setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
            this.client.sendResponse(new WiredSavedMessageComposer());
            selector.needsUpdate(true);
            Emulator.getThreading().run(selector);
            WiredManager.invalidateRoom(room);
        }
    }
}
