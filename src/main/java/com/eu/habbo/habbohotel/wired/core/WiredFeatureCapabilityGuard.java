package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.WiredAddonType;

/** Fail-closed feature gate for new types living inside legacy Wired categories. */
public final class WiredFeatureCapabilityGuard {
    private WiredFeatureCapabilityGuard() {
    }

    public static boolean isEditorReady(GameClient client, Room room, InteractionWired wired) {
        return isEditorReady(
                client != null ? client.getWiredCapabilityState() : WiredCapabilityState.unsupported(),
                room != null ? room.getId() : 0,
                wired);
    }

    static boolean isEditorReady(WiredCapabilityState state, int roomId, InteractionWired wired) {
        int capability = requiredRoomCapability(wired);
        return capability == 0
                || (state != null && state.supportsRoom(capability, roomId));
    }

    public static boolean isRuntimeReady(InteractionWired wired) {
        int capability = requiredRoomCapability(wired);
        return capability == 0 || WiredCapabilityService.isRoomCapabilityReady(capability);
    }

    static int requiredRoomCapability(InteractionWired wired) {
        if (wired instanceof InteractionWiredTrigger trigger
                && trigger.getType() == WiredTriggerType.RECEIVE_SIGNAL) {
            return WiredCapabilityService.CAPABILITY_SIGNALS;
        }
        if (wired instanceof InteractionWiredTrigger trigger
                && trigger.getType() == WiredTriggerType.VARIABLE_CHANGED) {
            return WiredCapabilityService.CAPABILITY_VARIABLES;
        }
        if (wired instanceof InteractionWiredTrigger trigger
                && (trigger.getType() == WiredTriggerType.TRANSACTION_COMPLETED
                || trigger.getType() == WiredTriggerType.TRANSACTION_FAILED)) {
            return WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                    | WiredCapabilityService.CAPABILITY_CONTRACTS;
        }
        if (wired instanceof InteractionWiredEffect effect
                && (effect.getType() == WiredEffectType.SEND_SIGNAL
                || effect.getType() == WiredEffectType.NEGATIVE_SEND_SIGNAL)) {
            return WiredCapabilityService.CAPABILITY_SIGNALS;
        }
        if (wired instanceof InteractionWiredEffect effect
                && (effect.getType() == WiredEffectType.GIVE_VARIABLE
                || effect.getType() == WiredEffectType.REMOVE_VARIABLE
                || effect.getType() == WiredEffectType.CHANGE_VARIABLE)) {
            return WiredCapabilityService.CAPABILITY_VARIABLES;
        }
        if (wired instanceof InteractionWiredEffect effect
                && (effect.getType() == WiredEffectType.GIVE_CURRENCY_FROM_CHEST
                || effect.getType() == WiredEffectType.GIVE_FURNI_FROM_CHEST)) {
            return WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED;
        }
        if (wired instanceof InteractionWiredEffect effect
                && effect.getType() == WiredEffectType.INITIATE_TRANSACTION) {
            return WiredCapabilityService.CAPABILITY_VARIABLES
                    | WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                    | WiredCapabilityService.CAPABILITY_CONTRACTS;
        }
        if (wired instanceof InteractionWiredEffect effect
                && effect.getType() == WiredEffectType.CANCEL_TRANSACTION) {
            return WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                    | WiredCapabilityService.CAPABILITY_CONTRACTS;
        }
        if (wired instanceof InteractionWiredEffect effect
                && (effect.getType() == WiredEffectType.WRITE_TO_LOGS
                || effect.getType() == WiredEffectType.NEGATIVE_WRITE_TO_LOGS)) {
            return WiredCapabilityService.CAPABILITY_MENU_LOGS;
        }
        if (wired instanceof InteractionWiredCondition condition
                && (condition.getType() == WiredConditionType.HAS_VARIABLE
                || condition.getType() == WiredConditionType.NOT_HAS_VARIABLE
                || condition.getType() == WiredConditionType.VARIABLE_VALUE
                || condition.getType() == WiredConditionType.VARIABLE_AGE)) {
            return WiredCapabilityService.CAPABILITY_VARIABLES;
        }
        if (wired instanceof InteractionWiredCondition condition
                && (condition.getType() == WiredConditionType.CHEST_HAS_ITEMS
                || condition.getType() == WiredConditionType.CHEST_HAS_ITEM_TYPES)) {
            return WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED;
        }
        if (wired instanceof InteractionWiredAddon addon
                && addon.getType() == WiredAddonType.CHEST_ITEM_TYPE_SCANNER) {
            return WiredCapabilityService.CAPABILITY_ADDONS
                    | WiredCapabilityService.CAPABILITY_VARIABLES
                    | WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED;
        }
        if (wired instanceof InteractionWiredAddon addon
                && addon.getType() == WiredAddonType.CUSTOM_CONTRACT) {
            return WiredCapabilityService.CAPABILITY_ADDONS
                    | WiredCapabilityService.CAPABILITY_VARIABLES
                    | WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                    | WiredCapabilityService.CAPABILITY_CONTRACTS;
        }
        if (wired instanceof InteractionWiredSelector selector
                && (selector.getType() == WiredSelectorType.FURNI_FROM_SIGNAL
                || selector.getType() == WiredSelectorType.USERS_FROM_SIGNAL)) {
            return WiredCapabilityService.CAPABILITY_SIGNALS;
        }
        if (wired instanceof InteractionWiredSelector selector
                && (selector.getType() == WiredSelectorType.FURNI_WITH_VARIABLE
                || selector.getType() == WiredSelectorType.USERS_WITH_VARIABLE)) {
            return WiredCapabilityService.CAPABILITY_VARIABLES;
        }
        if (wired instanceof InteractionWiredSelector selector
                && selector.getType() == WiredSelectorType.REMOTE_SELECTOR) {
            return WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR;
        }
        return 0;
    }
}
