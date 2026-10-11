package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WiredJulyAddonRuntimeTest {
    private static WiredSettingsV2 settings(int... ints) {
        return new WiredSettingsV2(ints, "", new int[0], new int[0], new String[0], new int[0], new int[0], 0, 0, false, false);
    }

    private static WiredSettingsV2 textSettings(String text, int... ints) {
        return new WiredSettingsV2(ints, text, new int[0], new int[0], new String[0], new int[0], new int[0], 0, 0, false, false);
    }

    private static WiredSettingsV2 settingsWithUserSource(int source, int... ints) {
        return new WiredSettingsV2(
                ints, "", new int[0], new int[0], new String[0],
                new int[0], new int[] {source}, 0, 0, false, false);
    }

    private static WiredSettingsV2 movementPhysicsSettings(int... ints) {
        return new WiredSettingsV2(
                ints, "", new int[0], new int[0], new String[0],
                new int[] {900, 900}, new int[] {900}, 0, 0, false, false);
    }

    @Test void conditionModesAreBoundedAndLegacyDefaultIsAny() {
        WiredAddonConditionEvaluation addon = new WiredAddonConditionEvaluation(1, 1, null, "0", 0, 0);
        assertTrue(addon.evaluate(1, 2));
        assertTrue(addon.saveData(settings(0, 0, 0)));
        assertTrue(addon.evaluate(2, 2)); assertFalse(addon.evaluate(1, 2));
        assertTrue(addon.saveData(settings(-1, 1, 2))); assertTrue(addon.evaluate(2, 3));
        assertFalse(addon.saveData(settings(-1, 3, 2))); assertFalse(addon.saveData(settings(4, 0, 0)));
        addon.onPickUp(); assertTrue(addon.evaluate(1, 2));
    }

    @Test void randomHistorySkipsPriorSelectionsAndSaveResetsIt() {
        WiredAddonRandomEffect addon = new WiredAddonRandomEffect(2, 1, null, "0", 0, 0);
        assertTrue(addon.saveData(settings(1, 1)));
        IWiredEffect a = ctx -> {}; IWiredEffect b = ctx -> {};
        List<IWiredEffect> all = List.of(a, b);
        IWiredEffect first = addon.selectEffects(all, new Random(4)).get(0);
        IWiredEffect second = addon.selectEffects(all, new Random(4)).get(0);
        assertNotSame(first, second);
        assertTrue(addon.saveData(settings(0, 2)));
        assertEquals(2, addon.selectEffects(all, new Random(4)).size());
        assertFalse(addon.saveData(settings(101, 1))); assertFalse(addon.saveData(settings(0, 0)));
    }

    @Test void unseenCyclesAndResets() {
        WiredAddonUnseenEffect addon = new WiredAddonUnseenEffect(3, 1, null, "0", 0, 0);
        IWiredEffect a = ctx -> {}; IWiredEffect b = ctx -> {};
        assertSame(a, addon.selectEffect(List.of(a, b))); assertSame(b, addon.selectEffect(List.of(a, b)));
        addon.onPickUp(); assertSame(a, addon.selectEffect(List.of(a, b)));
        assertTrue(addon.saveData(settings())); assertFalse(addon.saveData(settings(1)));
    }

    @Test void limitUsesInclusivePulsesAndSaveResetsBudget() {
        WiredAddonExecutionLimit addon = new WiredAddonExecutionLimit(4, 1, null, "0", 0, 0);
        assertTrue(addon.saveData(settings(2, 2)));
        assertTrue(addon.allowExecution(0)); assertTrue(addon.allowExecution(999)); assertTrue(addon.allowExecution(1000));
        assertFalse(addon.allowExecution(1000)); assertTrue(addon.allowExecution(2000));
        assertTrue(addon.saveData(settings(1, 1))); assertTrue(addon.allowExecution(2000));
        assertFalse(addon.saveData(settings(0, 1))); assertFalse(addon.saveData(settings(1, 21)));
    }

    @Test void movementAddonFieldsAreExactBoundedAndReset() {
        WiredAddonNoMoveAnimation noMove = new WiredAddonNoMoveAnimation(5, 1, null, "0", 0, 0);
        assertTrue(noMove.saveData(settings())); assertFalse(noMove.saveData(settings(1)));
        WiredAddonMovementPhysics physics = new WiredAddonMovementPhysics(6, 1, null, "0", 0, 0);
        assertFalse(physics.saveData(settings(1, 1, 0, 1)));
        assertTrue(physics.saveData(movementPhysicsSettings(1, 1, 0, 1))); assertTrue(physics.keepAltitude()); assertTrue(physics.throughFurni()); assertTrue(physics.blockByFurni());
        assertFalse(physics.saveData(movementPhysicsSettings(1, 1, 0, 2))); physics.onPickUp(); assertFalse(physics.keepAltitude());
        WiredAddonCarryUsers carry = new WiredAddonCarryUsers(7, 1, null, "0", 0, 0);
        assertFalse(carry.saveData(settings(1)));
        assertTrue(carry.saveData(settingsWithUserSource(900, 1))); assertEquals(1, carry.mode());
        assertFalse(carry.saveData(settingsWithUserSource(900, 2)));
        WiredAddonAnimationTime time = new WiredAddonAnimationTime(8, 1, null, "0", 0, 0);
        assertTrue(time.saveData(settings(150))); assertEquals(150, time.milliseconds());
        assertFalse(time.saveData(settings(175))); assertFalse(time.saveData(settings(2050))); time.onPickUp(); assertEquals(500, time.milliseconds());

        WiredAddonJumpStrength jump = new WiredAddonJumpStrength(9, 1, null, "0", 0, 0);
        WiredSettingsV2 literalJump = new WiredSettingsV2(
                new int[] {0, 150, 0}, "", new int[0], new int[0],
                new String[] {""}, new int[] {0}, new int[] {0},
                0, 0, false, false);
        assertTrue(jump.saveData(literalJump));
        assertEquals(150, jump.resolve(null));
        WiredSettingsV2 userVariableJump = new WiredSettingsV2(
                new int[] {1, 80, 1}, "", new int[0], new int[0],
                new String[] {"user.score"}, new int[] {0}, new int[] {0},
                0, 0, false, false);
        assertTrue(jump.saveData(userVariableJump));
        assertFalse(jump.saveData(new WiredSettingsV2(
                new int[] {1, 80, 2}, "", new int[0], new int[0],
                new String[] {"bad.target"}, new int[] {0}, new int[] {0},
                0, 0, false, false)));

        WiredAddonProjectile projectile = new WiredAddonProjectile(10, 1, null, "0", 0, 0);
        int[] projectileParams = {
                1, 1, 1, 1, 500, 1, 1, 1, 0, 0,
                0, 0, 0, 0, 1, 1, 3, 0, 50
        };
        assertTrue(projectile.saveData(new WiredSettingsV2(
                projectileParams, "", new int[0], new int[0],
                new String[] {"user.speed", "furni.distance"},
                new int[] {901, 0, 0}, new int[] {0, 0, 0},
                0, 0, false, false)));
        projectileParams[17] = 2;
        assertFalse(projectile.saveData(new WiredSettingsV2(
                projectileParams, "", new int[0], new int[0],
                new String[] {"user.speed", "bad.target"},
                new int[] {901, 0, 0}, new int[] {0, 0, 0},
                0, 0, false, false)));
    }

    @Test void selectorFiltersUseExactJulyFieldsAndBoundedAmounts() {
        WiredSettingsV2 literal = new WiredSettingsV2(new int[] {1000, 0, 0}, "", new int[0], new int[0], new String[] {""}, new int[0], new int[0], 0, 0, false, false);
        WiredAddonFurniSelectorFilter furni = new WiredAddonFurniSelectorFilter(9, 1, null, "0", 0, 0);
        WiredAddonUserSelectorFilter users = new WiredAddonUserSelectorFilter(10, 1, null, "0", 0, 0);
        assertTrue(furni.saveData(literal)); assertEquals(1000, furni.filterAmount()); assertTrue(users.saveData(literal));
        assertFalse(furni.saveData(new WiredSettingsV2(new int[] {0, 0, 0}, "", new int[0], new int[0], new String[] {""}, new int[0], new int[0], 0, 0, false, false)));
        assertFalse(users.saveData(new WiredSettingsV2(new int[] {1, 1, 0}, "", new int[0], new int[0], new String[] {""}, new int[0], new int[0], 0, 0, false, false)));
    }

    @Test void textPlaceholdersPersistStrictConfigurationAndReset() {
        WiredAddonUsernamePlaceholder users = new WiredAddonUsernamePlaceholder(14, 1, null, "0", 0, 0);
        WiredAddonFurniNamePlaceholder furni = new WiredAddonFurniNamePlaceholder(19, 1, null, "0", 0, 0);

        assertTrue(users.saveData(textSettings("%user%\t, ", 1)));
        assertTrue(furni.saveData(textSettings("%furni%\t / ", 1)));
        assertEquals("%user%", users.placeholder()); assertEquals(", ", users.delimiter()); assertTrue(users.multiple());
        assertEquals("%furni%", furni.placeholder()); assertEquals(" / ", furni.delimiter()); assertTrue(furni.multiple());
        assertTrue(furni.getWiredData().contains("%furni%"));
        assertFalse(users.saveData(textSettings("%user%\t, ", 0)));
        assertFalse(furni.saveData(textSettings("", 0)));
        assertFalse(furni.saveData(textSettings("%furni%", 2)));

        furni.onPickUp();
        assertEquals("$", furni.placeholder()); assertEquals("", furni.delimiter()); assertFalse(furni.multiple());
    }
}
