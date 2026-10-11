package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredSavedMessageComposer;

/** Strict July-compatible save handler for Wired 2.0 add-ons (header 7001). */
public class UpdateAddonMessageEvent extends MessageHandler {
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
                WiredCapabilityService.CAPABILITY_ADDONS, room.getId())) {
            return;
        }

        int itemId = room.getItemManager().resolveVisibleId(visibleId);
        InteractionWiredAddon addon = room.getRoomSpecialTypes().getAddon(itemId);
        if (!WiredFeatureCapabilityGuard.isEditorReady(this.client, room, addon)
                || !WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                this.client,
                room,
                addon,
                WiredCategoryType.ADDON)) {
            return;
        }

        WiredSettingsV2 settings = InteractionWired.readSettingsV2(this.packet, WiredCategoryType.ADDON, room);
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected add-on settings payload");
        }

        if (addon.saveData(settings)) {
            addon.setWiredSourceTypes(settings.getFurniSourceTypes(), settings.getUserSourceTypes());
            this.client.sendResponse(new WiredSavedMessageComposer());
            addon.needsUpdate(true);
            Emulator.getThreading().run(addon);
            WiredManager.invalidateRoom(room);
        }
    }
}
