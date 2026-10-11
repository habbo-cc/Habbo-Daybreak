package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonMovementPhysics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WiredMovementAddonRuntimeTest {
    private static WiredSettingsV2 physicsSettings(int... parameters) {
        return new WiredSettingsV2(
                parameters,
                "",
                new int[0],
                new int[0],
                new String[0],
                new int[] {900, 900},
                new int[] {900},
                0,
                0,
                false,
                false);
    }

    @Test void physicsCollisionPolicyIsOptInAndBounded() {
        assertFalse(WiredMovementAddonRuntime.bypassFurniCollision(null));
        assertFalse(WiredMovementAddonRuntime.bypassUnitCollision(null));
        WiredAddonMovementPhysics physics = new WiredAddonMovementPhysics(1, 1, null, "0", 0, 0);
        assertTrue(physics.saveData(physicsSettings(0, 1, 1, 0)));
        assertTrue(WiredMovementAddonRuntime.bypassFurniCollision(physics));
        assertTrue(WiredMovementAddonRuntime.bypassUnitCollision(physics));
        assertFalse(WiredMovementAddonRuntime.blocksFurni(physics));
        assertTrue(physics.saveData(physicsSettings(0, 1, 0, 1)));
        assertFalse(WiredMovementAddonRuntime.bypassFurniCollision(physics));
        assertTrue(WiredMovementAddonRuntime.blocksFurni(physics));
        assertFalse(physics.saveData(physicsSettings(0, 1, 1, 2)));
    }
}
