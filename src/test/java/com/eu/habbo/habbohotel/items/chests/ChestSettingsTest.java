package com.eu.habbo.habbohotel.items.chests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestSettingsTest {
    @Test
    void clampsJulyEditorDomainsAndTextLimits() {
        ChestSettings settings = new ChestSettings(true, true,
                "x".repeat(40) + '\u0000', "y".repeat(250),
                20, -5, 99, -10, -3, false,
                true, false, 20, false, false, false, false, false, -1);

        assertEquals(30, settings.name().length());
        assertEquals(200, settings.description().length());
        assertEquals(3, settings.stateControlMode());
        assertEquals(0, settings.previewMode());
        assertEquals(4, settings.previewAmount());
        assertEquals(0, settings.capacity());
        assertEquals(0, settings.capacityLevel());
        assertEquals(1, settings.notifyMode());
        assertEquals(0, settings.revision());
    }

    @Test
    void wiredUpgradeIsOneWay() {
        ChestSettings initial = ChestSettings.defaults(1000);
        ChestSettings enabled = initial.withGeneral("", "", true, false,
                0, 0, 1, true);
        ChestSettings attemptedDisable = enabled.withGeneral("", "", true, false,
                0, 0, 1, false);

        assertFalse(initial.wiredEnabled());
        assertTrue(enabled.wiredEnabled());
        assertTrue(attemptedDisable.wiredEnabled());
        assertEquals(2, attemptedDisable.revision());
    }
}
