package com.eu.habbo.habbohotel.wired.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredCapabilityServiceTest {
    @Test
    void disabledMasterSwitchNegotiatesNothing() {
        WiredCapabilityState state = WiredCapabilityService.negotiate(false, 1, 1, 42);

        assertSame(WiredCapabilityState.unsupported(), state);
    }

    @Test
    void invalidRevisionNegotiatesNothing() {
        assertSame(
                WiredCapabilityState.unsupported(),
                WiredCapabilityService.negotiate(true, 0, 1, 42));
        assertSame(
                WiredCapabilityState.unsupported(),
                WiredCapabilityService.negotiate(true, -1, 1, 42));
    }

    @Test
    void clientMustAdvertiseProtocolCapability() {
        WiredCapabilityState state = WiredCapabilityService.negotiate(true, 1, 1 << 8, 42);

        assertSame(WiredCapabilityState.unsupported(), state);
    }

    @Test
    void futureRevisionNegotiatesNothing() {
        int clientMask = WiredCapabilityService.CAPABILITY_PROTOCOL | (1 << 24);

        WiredCapabilityState state = WiredCapabilityService.negotiate(true, 7, clientMask, 42);

        assertSame(WiredCapabilityState.unsupported(), state);
    }

    @Test
    void negativeValidatedRoomIdIsNormalizedToNoRoom() {
        WiredCapabilityState state = WiredCapabilityService.negotiate(true, 1, 1, -42);

        assertEquals(0, state.roomId());
    }

    @Test
    void negativeClientMaskNegotiatesNothing() {
        assertSame(
                WiredCapabilityState.unsupported(),
                WiredCapabilityService.negotiate(true, 1, -1, 42));
    }

    @Test
    void authenticationNegotiationHasNoRoomCapabilities() {
        WiredCapabilityState state = WiredCapabilityService.negotiate(true, 1, 1, 0);

        assertEquals(0, state.roomId());
        assertEquals(0, state.roomCapabilityMask());
        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_PROTOCOL, 42));
    }

    @Test
    void roomSupportRequiresExactCurrentRoom() {
        WiredCapabilityState state = new WiredCapabilityState(1, 1, 1, 42, 1);

        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_PROTOCOL, 42));
        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_PROTOCOL, 41));
        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_PROTOCOL, 0));
    }

    @Test
    void unknownBitsAndOrphanCapabilitiesAreRemoved() {
        int clientMask = WiredCapabilityService.CAPABILITY_PROTOCOL
                | WiredCapabilityService.CAPABILITY_VARIABLE_SYNC
                | WiredCapabilityService.CAPABILITY_MENU_LOGS
                | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                | WiredCapabilityService.CAPABILITY_CONTRACTS
                | (1 << 24);

        WiredCapabilityState state = WiredCapabilityService.negotiate(true, 1, clientMask, 42);

        assertEquals(WiredCapabilityService.CAPABILITY_PROTOCOL, state.clientCapabilityMask());
        assertEquals(WiredCapabilityService.CAPABILITY_PROTOCOL, state.connectionCapabilityMask());
    }

    @Test
    void contractsRequireBothChestsAndChestWired() {
        int protocolAndContracts = WiredCapabilityService.CAPABILITY_PROTOCOL
                | WiredCapabilityService.CAPABILITY_CONTRACTS;
        int protocolChestsAndContracts = protocolAndContracts
                | WiredCapabilityService.CAPABILITY_CHESTS;
        int complete = protocolChestsAndContracts
                | WiredCapabilityService.CAPABILITY_CHEST_WIRED;

        assertEquals(
                WiredCapabilityService.CAPABILITY_PROTOCOL,
                WiredCapabilityService.applyDependencies(protocolAndContracts));
        assertEquals(
                WiredCapabilityService.CAPABILITY_PROTOCOL
                        | WiredCapabilityService.CAPABILITY_CHESTS,
                WiredCapabilityService.applyDependencies(protocolChestsAndContracts));
        assertEquals(complete, WiredCapabilityService.applyDependencies(complete));
    }

    @Test
    void roomRuntimeReadinessRequiresMasterAndBothCompiledMasks() {
        int protocolAndAddons = WiredCapabilityService.CAPABILITY_PROTOCOL
                | WiredCapabilityService.CAPABILITY_ADDONS;

        assertFalse(WiredCapabilityService.isRoomCapabilityReady(
                false,
                WiredCapabilityService.CAPABILITY_ADDONS,
                protocolAndAddons,
                WiredCapabilityService.CAPABILITY_ADDONS));
        assertFalse(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_ADDONS,
                WiredCapabilityService.CAPABILITY_PROTOCOL,
                WiredCapabilityService.CAPABILITY_ADDONS));
        assertFalse(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_ADDONS,
                protocolAndAddons,
                0));
        assertTrue(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_ADDONS,
                protocolAndAddons,
                WiredCapabilityService.CAPABILITY_ADDONS));
    }

    @Test
    void currentBuildAdvertisesExactlyTheFunctionalRoomServices() {
        int expected = WiredCapabilityService.CAPABILITY_ADDONS
                | WiredCapabilityService.CAPABILITY_VARIABLES
                | WiredCapabilityService.CAPABILITY_VARIABLE_SYNC
                | WiredCapabilityService.CAPABILITY_SIGNALS
                | WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR
                | WiredCapabilityService.CAPABILITY_WIRED_MENU
                | WiredCapabilityService.CAPABILITY_MENU_INSPECTION
                | WiredCapabilityService.CAPABILITY_MENU_LOGS
                | WiredCapabilityService.CAPABILITY_MENU_SETTINGS
                | WiredCapabilityService.CAPABILITY_MENU_VARIABLES
                | WiredCapabilityService.CAPABILITY_MENU_CHESTS
                | WiredCapabilityService.CAPABILITY_ENVIRONMENT_V2
                | WiredCapabilityService.CAPABILITY_CLICK_SETTINGS_V2
                | WiredCapabilityService.CAPABILITY_WIRED_MOVEMENTS
                | WiredCapabilityService.CAPABILITY_CHESTS
                | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                | WiredCapabilityService.CAPABILITY_CONTRACTS;

        assertEquals(expected, WiredCapabilityService.FUNCTIONAL_ROOM_CAPABILITY_MASK);
        assertEquals(WiredCapabilityService.CAPABILITY_PROTOCOL | expected,
                WiredCapabilityService.COMPILED_CONNECTION_CAPABILITY_MASK);
        assertEquals(expected, WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK);
        assertTrue(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_ADDONS,
                WiredCapabilityService.COMPILED_CONNECTION_CAPABILITY_MASK,
                WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK));
        assertTrue(WiredCapabilityService.isRoomCapabilityReady(
                true,
                WiredCapabilityService.CAPABILITY_WIRED_MENU
                        | WiredCapabilityService.CAPABILITY_MENU_INSPECTION
                        | WiredCapabilityService.CAPABILITY_MENU_LOGS
                        | WiredCapabilityService.CAPABILITY_MENU_SETTINGS
                        | WiredCapabilityService.CAPABILITY_MENU_VARIABLES
                        | WiredCapabilityService.CAPABILITY_MENU_CHESTS,
                WiredCapabilityService.COMPILED_CONNECTION_CAPABILITY_MASK,
                WiredCapabilityService.COMPILED_ROOM_CAPABILITY_MASK));
    }

    @Test
    void successfulNegotiationAdvertisesEveryImplementedServiceAndNoPlaceholderBits() {
        int clientMask = WiredCapabilityService.CAPABILITY_PROTOCOL
                | WiredCapabilityService.KNOWN_CAPABILITY_MASK;

        WiredCapabilityState state = WiredCapabilityService.negotiate(true, 1, clientMask, 42);

        assertEquals(WiredCapabilityService.COMPILED_CONNECTION_CAPABILITY_MASK,
                state.connectionCapabilityMask());
        assertEquals(WiredCapabilityService.FUNCTIONAL_ROOM_CAPABILITY_MASK,
                state.roomCapabilityMask());
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_WIRED_MENU, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_INSPECTION, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_LOGS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_SETTINGS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_VARIABLES, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_CHESTS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_ENVIRONMENT_V2, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CLICK_SETTINGS_V2, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_WIRED_MOVEMENTS, 42));
        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_CREATOR_TOOLS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CHESTS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CHEST_WIRED, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CONTRACTS, 42));
    }

    @Test
    void failedVariableServiceRemovesVariablesAndSyncButKeepsOtherHealthyFeaturesLive() {
        int clientMask = WiredCapabilityService.CAPABILITY_PROTOCOL
                | WiredCapabilityService.FUNCTIONAL_ROOM_CAPABILITY_MASK;
        int available = WiredCapabilityService.availableRoomCapabilityMask(false);

        WiredCapabilityState state = WiredCapabilityService.negotiate(
                true, 1, clientMask, 42, available);

        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_VARIABLES, 42));
        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_VARIABLE_SYNC, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_ADDONS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_SIGNALS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_WIRED_MENU, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_INSPECTION, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_LOGS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_SETTINGS, 42));
        assertFalse(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_VARIABLES, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_MENU_CHESTS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CHESTS, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CHEST_WIRED, 42));
        assertTrue(state.supportsRoom(WiredCapabilityService.CAPABILITY_CONTRACTS, 42));
    }
}
