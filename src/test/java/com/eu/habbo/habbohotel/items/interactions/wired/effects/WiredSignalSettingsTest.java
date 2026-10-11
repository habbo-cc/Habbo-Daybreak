package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFurniFromSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorUsersFromSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredSignalSettingsTest {
    @Test
    void splittingUsesDonorCartesianAxesAndEmptyAxisStillDispatchesOnce() {
        assertEquals(List.of(List.of("a", "b")),
                WiredEffectSendSignalBase.splitPayload(List.of("a", "b"), false));
        assertEquals(List.of(List.of("a"), List.of("b")),
                WiredEffectSendSignalBase.splitPayload(List.of("a", "b"), true));
        assertEquals(List.of(List.of()),
                WiredEffectSendSignalBase.splitPayload(List.of(), true));

        int cartesianDispatches = WiredEffectSendSignalBase
                .splitPayload(List.of("f1", "f2"), true).size()
                * WiredEffectSendSignalBase.splitPayload(List.of("u1", "u2", "u3"), true).size();
        assertEquals(6, cartesianDispatches);
    }

    @Test
    void sendSignalAcceptsExactlyTwoCustomIntsAndGenericSourceArrays() {
        WiredSettings exactAir = settings(new int[] {1, 0}, new int[] {100, 200}, new int[] {200});
        assertDoesNotThrow(() -> WiredEffectSendSignalBase.validateSettingsShape(exactAir));

        WiredSettings donorNitroFiveInts = settings(
                new int[] {1, 0, 100, 200, 200}, new int[] {100, 200}, new int[] {200});
        assertThrows(WiredSaveException.class,
                () -> WiredEffectSendSignalBase.validateSettingsShape(donorNitroFiveInts));
        assertThrows(WiredSaveException.class,
                () -> WiredEffectSendSignalBase.validateSettingsShape(
                        settings(new int[] {1, 2}, new int[] {100, 200}, new int[] {200})));
    }

    @Test
    void actionWireDefaultsExposeTwoIntsAndSignalSourceMode201() throws Exception {
        WiredEffectSendSignal signal = new WiredEffectSendSignal(1, 1, null, "0", 0, 0);
        Method intParams = WiredEffectSendSignalBase.class.getDeclaredMethod("getWiredIntParams");
        intParams.setAccessible(true);
        assertArrayEquals(new int[] {0, 0}, (int[]) intParams.invoke(signal));

        Method furniSources = WiredEffectSendSignalBase.class
                .getDeclaredMethod("getAllowedFurniSourcesForSlot", int.class);
        furniSources.setAccessible(true);
        assertTrue(java.util.Arrays.stream((int[]) furniSources.invoke(signal, 0))
                .anyMatch(value -> value == 201));

        Method userSources = WiredEffectSendSignalBase.class
                .getDeclaredMethod("getAllowedUserSourcesForSlot", int.class);
        userSources.setAccessible(true);
        assertTrue(java.util.Arrays.stream((int[]) userSources.invoke(signal, 0))
                .anyMatch(value -> value == 201));
    }

    @Test
    void signalSelectorsCarryOnlyFlagsOutsideTheirParameterlessCustomPayload() {
        WiredSettingsV2 valid = new WiredSettingsV2(
                new int[0], "", new int[0], new int[0], new String[0],
                new int[0], new int[0], 0, 0, true, false);
        WiredSelectorFurniFromSignal furni = new WiredSelectorFurniFromSignal(1, 1, null, "0", 0, 0);
        WiredSelectorUsersFromSignal users = new WiredSelectorUsersFromSignal(2, 1, null, "0", 0, 0);
        assertTrue(furni.saveData(valid));
        assertTrue(users.saveData(valid));
        assertTrue(furni.getWiredData().contains("\"filter\":true"));

        WiredSettingsV2 forged = new WiredSettingsV2(
                new int[] {1}, "", new int[0], new int[0], new String[0],
                new int[0], new int[0], 0, 0, false, false);
        assertFalse(furni.saveData(forged));
    }

    @Test
    void receiveSignalRejectsCustomIntsEvenBeforeRoomResolution() {
        WiredTriggerReceiveSignal trigger = new WiredTriggerReceiveSignal(1, 1, null, "0", 0, 0);
        assertFalse(trigger.saveData(settings(new int[] {1}, new int[] {100}, new int[0])));
    }

    private static WiredSettings settings(int[] customInts, int[] furniSources, int[] userSources) {
        return new WiredSettings(
                customInts,
                "",
                new int[0],
                new int[0],
                new String[0],
                furniSources,
                userSources,
                0,
                0);
    }
}
