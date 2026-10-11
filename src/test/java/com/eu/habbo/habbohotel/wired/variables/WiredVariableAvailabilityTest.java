package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WiredVariableAvailabilityTest {
    @Test
    void userVariableWhileInRoomResolvesAndIsNotPersistent() {
        WiredVariableAvailability availability = WiredVariableAvailability.fromCode(0);
        assertEquals(WiredVariableAvailability.USER_IN_ROOM, availability);
        assertFalse(availability.isPersistent());

        WiredVariableDefinition user = WiredVariableDefinition.create(
                9, 71, WiredVariableType.USER, "score", availability.code, true, false);
        assertEquals(0, user.availabilityCode());
        assertFalse(user.persistsValues());
    }

    @Test
    void whileInRoomStaysRejectedForFurniAndRoomVariables() {
        assertThrows(IllegalArgumentException.class, () -> WiredVariableDefinition.create(
                9, 72, WiredVariableType.FURNI, "f", 0, true, false));
        assertThrows(IllegalArgumentException.class, () -> WiredVariableDefinition.create(
                9, 73, WiredVariableType.ROOM, "r", 0, true, false));
    }
}
