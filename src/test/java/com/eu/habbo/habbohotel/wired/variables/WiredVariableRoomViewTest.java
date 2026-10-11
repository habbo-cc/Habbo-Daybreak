package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredVariableRoomViewTest {
    @Test
    void translatesStableFurniAndHabboIdsOnlyAtTheRoomBoundary() {
        WiredVariableDefinition furni = WiredVariableDefinition.create(
                9, 71, WiredVariableType.FURNI, "furni_count",
                WiredVariableAvailability.PERMANENT, false);
        WiredVariableManager.VariableSnapshot furniSnapshot = new WiredVariableManager.VariableSnapshot(
                furni, List.of(
                new WiredVariableValue(furni.variableId(), WiredVariableHolder.furni(100), 7, 1, 2, 3),
                new WiredVariableValue(furni.variableId(), WiredVariableHolder.furni(200), -4, 1, 2, 3)), 10);
        assertEquals(List.of(new WiredVariableRoomView.VisibleValue(800, -4),
                        new WiredVariableRoomView.VisibleValue(900, 7)),
                WiredVariableRoomView.furniValues(furniSnapshot,
                        stableId -> stableId == 100 ? 900 : stableId == 200 ? 800 : null));

        WiredVariableDefinition user = WiredVariableDefinition.create(
                9, 72, WiredVariableType.USER, "user_count",
                WiredVariableAvailability.SHARED_PERMANENT, false);
        WiredVariableManager.VariableSnapshot userSnapshot = new WiredVariableManager.VariableSnapshot(
                user, List.of(
                new WiredVariableValue(user.variableId(), WiredVariableHolder.user(50), 6, 1, 2, 3),
                new WiredVariableValue(user.variableId(), WiredVariableHolder.user(60), 9, 1, 2, 3)), 11);
        assertEquals(List.of(new WiredVariableRoomView.VisibleValue(33, 6)),
                WiredVariableRoomView.userValues(userSnapshot,
                        stableId -> stableId == 50 ? 33 : null));
    }
}
