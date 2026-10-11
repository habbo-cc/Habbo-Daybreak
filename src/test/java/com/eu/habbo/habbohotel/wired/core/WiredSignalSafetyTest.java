package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignalNegative;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFurniFromSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorUsersFromSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredNegativeEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredSignalSafetyTest {
    @Test
    void julyAirCodesAreNotDonorNitroCodes() {
        assertEquals(17, WiredTriggerType.RECEIVE_SIGNAL.code);
        assertEquals(22, WiredTriggerType.VARIABLE_CHANGED.code);
        assertEquals(30, WiredEffectType.SEND_SIGNAL.code);
        assertEquals(37, WiredEffectType.NEGATIVE_SEND_SIGNAL.code);
        assertEquals(5, WiredSelectorType.FURNI_FROM_SIGNAL.code);
        assertEquals(10, WiredSelectorType.USERS_FROM_SIGNAL.code);
    }

    @Test
    void positiveAndNegativeEffectLanesAreMutuallyExclusive() {
        IWiredEffect positive = context -> { };
        IWiredNegativeEffect negative = context -> { };
        List<IWiredEffect> mixed = List.of(positive, negative);

        List<IWiredEffect> successLane = WiredEngine.effectsForOutcome(mixed, false);
        List<IWiredEffect> failureLane = WiredEngine.effectsForOutcome(mixed, true);

        assertEquals(1, successLane.size());
        assertSame(positive, successLane.get(0));
        assertEquals(1, failureLane.size());
        assertSame(negative, failureLane.get(0));
        assertTrue(WiredEngine.effectsForOutcome(List.of(positive), true).isEmpty());
        assertTrue(WiredEngine.effectsForOutcome(List.of(negative), false).isEmpty());
    }

    @Test
    void ordinaryStacksDispatchSignalsAfterOtherEffects() {
        IWiredEffect first = context -> { };
        IWiredEffect second = context -> { };
        WiredEffectSendSignal signal = new WiredEffectSendSignal(2, 1, null, "0", 0, 0);

        assertEquals(List.of(first, second, signal),
                WiredEngine.orderSignalEffectsLast(List.of(signal, first, second)));
    }

    @Test
    void everySignalFurnitureTypeRequiresTheLiveSignalCapability() {
        WiredTriggerReceiveSignal trigger = new WiredTriggerReceiveSignal(1, 1, null, "0", 0, 0);
        WiredEffectSendSignal positive = new WiredEffectSendSignal(2, 1, null, "0", 0, 0);
        WiredEffectSendSignalNegative negative = new WiredEffectSendSignalNegative(3, 1, null, "0", 0, 0);
        WiredSelectorFurniFromSignal furni = new WiredSelectorFurniFromSignal(4, 1, null, "0", 0, 0);
        WiredSelectorUsersFromSignal users = new WiredSelectorUsersFromSignal(5, 1, null, "0", 0, 0);

        assertEquals(WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredFeatureCapabilityGuard.requiredRoomCapability(trigger));
        assertEquals(WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredFeatureCapabilityGuard.requiredRoomCapability(positive));
        assertEquals(WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredFeatureCapabilityGuard.requiredRoomCapability(negative));
        assertEquals(WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredFeatureCapabilityGuard.requiredRoomCapability(furni));
        assertEquals(WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredFeatureCapabilityGuard.requiredRoomCapability(users));

        assertEquals(WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK
                        & WiredCapabilityService.CAPABILITY_SIGNALS);
        assertTrue(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_SIGNALS,
                WiredCapabilityService.COMPILED_CONNECTION_CAPABILITY_MASK,
                WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK));
    }

    @Test
    void variableChangedTriggerRequiresTheLiveVariablesCapability() {
        WiredTriggerVariableChanged trigger = new WiredTriggerVariableChanged(1, 1, null, "0", 0, 0);

        assertEquals(WiredCapabilityService.CAPABILITY_VARIABLES,
                WiredFeatureCapabilityGuard.requiredRoomCapability(trigger));
        assertEquals(WiredCapabilityService.CAPABILITY_VARIABLES,
                WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK
                        & WiredCapabilityService.CAPABILITY_VARIABLES);
        assertTrue(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_VARIABLES,
                WiredCapabilityService.COMPILED_CONNECTION_CAPABILITY_MASK,
                WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK));
    }

    @Test
    void forgedEditorTrafficFailsUntilExactRoomNegotiatesSignals() {
        WiredEffectSendSignal signal = new WiredEffectSendSignal(2, 1, null, "0", 0, 0);
        assertFalse(WiredFeatureCapabilityGuard.isEditorReady(
                WiredCapabilityState.unsupported(), 77, signal));

        WiredCapabilityState wrongRoom = new WiredCapabilityState(
                1, 17, 17, 76, WiredCapabilityService.CAPABILITY_SIGNALS);
        assertFalse(WiredFeatureCapabilityGuard.isEditorReady(wrongRoom, 77, signal));

        WiredCapabilityState explicitlyReady = new WiredCapabilityState(
                1, 17, 17, 77, WiredCapabilityService.CAPABILITY_SIGNALS);
        assertTrue(WiredFeatureCapabilityGuard.isEditorReady(explicitlyReady, 77, signal));
    }
}
