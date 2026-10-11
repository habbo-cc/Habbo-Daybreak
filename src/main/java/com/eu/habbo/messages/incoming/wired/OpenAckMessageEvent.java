package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredAddonDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredConditionDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredEffectDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSelectorDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredTriggerDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableDataMessageComposer;

/**
 * Wired 2.0 open/ack handshake (client -> server). The server's wired onClick now
 * sends WiredOpen(itemId); the client acks here with the same id, and the server
 * validates rights + resolves the wired element, then sends its data composer.
 *
 * Header 768 (0x0300) = the clean client's existing wired-open ack composer.
 */
public class OpenAckMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readRequiredInt();
        if (this.packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected Wired open acknowledgement payload");
        }

        if (this.client.getHabbo() == null) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();

        InteractionWiredTrigger trigger = specialTypes.getTrigger(itemId);
        if (WiredFeatureCapabilityGuard.isEditorReady(this.client, room, trigger)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.ACK_EDITOR,
                this.client,
                room,
                trigger,
                WiredCategoryType.TRIGGER)) {
            this.client.sendResponse(new WiredTriggerDataMessageComposer(trigger, room));
            return;
        }

        InteractionWiredEffect effect = specialTypes.getEffect(itemId);
        if (WiredFeatureCapabilityGuard.isEditorReady(this.client, room, effect)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.ACK_EDITOR,
                this.client,
                room,
                effect,
                WiredCategoryType.EFFECT)) {
            this.client.sendResponse(new WiredEffectDataMessageComposer(effect, room));
            return;
        }

        InteractionWiredCondition condition = specialTypes.getCondition(itemId);
        if (WiredFeatureCapabilityGuard.isEditorReady(this.client, room, condition)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.ACK_EDITOR,
                this.client,
                room,
                condition,
                WiredCategoryType.CONDITION)) {
            this.client.sendResponse(new WiredConditionDataMessageComposer(condition, room));
            return;
        }

        InteractionWiredSelector selector = specialTypes.getSelector(itemId);
        if (WiredFeatureCapabilityGuard.isEditorReady(this.client, room, selector)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.ACK_EDITOR,
                this.client,
                room,
                selector,
                WiredCategoryType.SELECTOR)) {
            this.client.sendResponse(new WiredSelectorDataMessageComposer(selector, room));
            return;
        }

        InteractionWiredAddon addon = specialTypes.getAddon(itemId);
        if (this.client.getWiredCapabilityState().supportsRoom(
                WiredCapabilityService.CAPABILITY_ADDONS, room.getId())
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.ACK_EDITOR,
                this.client,
                room,
                addon,
                WiredCategoryType.ADDON)) {
            this.client.sendResponse(new WiredAddonDataMessageComposer(addon, room));
            return;
        }

        InteractionWiredVariable variable = specialTypes.getVariable(itemId);
        if (this.client.getWiredCapabilityState().supportsRoom(
                WiredCapabilityService.CAPABILITY_VARIABLES, room.getId())
                && specialTypes.getWiredVariableManager() != null
                && specialTypes.getWiredVariableManager().isOperational()
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.ACK_EDITOR,
                this.client,
                room,
                variable,
                WiredCategoryType.VARIABLE)) {
            this.client.sendResponse(new WiredVariableDataMessageComposer(variable, room));
        }
    }
}
