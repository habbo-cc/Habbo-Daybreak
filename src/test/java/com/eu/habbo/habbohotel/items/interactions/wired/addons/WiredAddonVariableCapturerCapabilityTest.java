package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableStore;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WiredAddonVariableCapturerCapabilityTest {
    @Test
    void savesAndCapturesOnlyNumericWritableContextDefinitions() throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes types = new RoomSpecialTypes();
        when(room.getRoomSpecialTypes()).thenReturn(types);
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(99)).thenReturn(room);
        WiredVariableContext variable = new WiredVariableContext(51, 1, null, "0", 0, 0);
        assertTrue(variable.saveData(contextSettings(true)));
        types.addVariable(variable);
        types.addVariable(new WiredVariableEcho(52, 1, null, "0", 0, 0));
        WiredAddonVariableCapturer capturer = new WiredAddonVariableCapturer(60, 1, null, "0", 0, 0);
        capturer.setRoomId(99);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertFalse(capturer.saveData(captureSettings("internal:-20:@variable.before")));
            assertFalse(capturer.saveData(captureSettings("room:52")));
            assertTrue(capturer.saveData(captureSettings("room:51")));
            WiredContext context = mock(WiredContext.class);
            WiredContextVariableStore values = new WiredContextVariableStore();
            when(context.room()).thenReturn(room);
            when(context.contextVariables()).thenReturn(values);
            assertTrue(capturer.capture(context, "count #(count)", "count -17", 1));
            assertEquals(-17, values.get("room:51"));
            assertTrue(variable.saveData(contextSettings(false)));
            assertFalse(capturer.saveData(captureSettings("room:51")));
            assertFalse(capturer.capture(context, "count #(count)", "count 99", 1));
            assertEquals(-17, values.get("room:51"));
            types.removeVariable(variable);
            assertFalse(capturer.capture(context, "count #(count)", "count 100", 1));
            assertFalse(values.contains("room:52"));
            assertFalse(values.contains("internal:-20:@variable.before"));
        }
    }

    @Test
    void oldReadonlyTargetPersistenceDoesNotLoad() throws Exception {
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(new RoomSpecialTypes());
        WiredAddonVariableCapturer capturer = new WiredAddonVariableCapturer(60, 1, null, "0", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(
                "{\"version\":1,\"name\":\"count\",\"variableId\":\"internal:-20:@variable.before\",\"textMode\":false}");
        capturer.loadWiredData(row, room);
        assertFalse(capturer.appliesTo("count #(count)"));
    }

    private static WiredSettingsV2 captureSettings(String id) {
        return new WiredSettingsV2(new int[]{0}, "count", new int[0], new int[0],
                new String[]{id}, new int[0], new int[0], 0, 0, false, false);
    }
    private static WiredSettingsV2 contextSettings(boolean numeric) {
        return new WiredSettingsV2(new int[]{numeric ? 1 : 0}, "count", new int[0], new int[0],
                new String[0], new int[0], new int[0], 0, 0, false, false);
    }
}
