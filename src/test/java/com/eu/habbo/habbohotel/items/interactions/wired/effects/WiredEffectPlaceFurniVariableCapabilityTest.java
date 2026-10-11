package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.variables.*;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WiredEffectPlaceFurniVariableCapabilityTest {
    @Test
    void valuelessSpawnCreatesPresenceWithZeroAndRejectsAliasDestinations() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(99);
        RoomSpecialTypes types = new RoomSpecialTypes();
        when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredVariableManager manager = mock(WiredVariableManager.class);
        when(manager.isOperational()).thenReturn(true);
        Field managerField = RoomSpecialTypes.class.getDeclaredField("wiredVariableManager");
        managerField.setAccessible(true);
        managerField.set(types, manager);
        WiredVariableDefinition flag = WiredVariableDefinition.create(99, 51, WiredVariableType.FURNI,
                "spawn_flag", 1, false, false);
        when(manager.runtimeDefinition("room:51")).thenReturn(flag);
        types.addVariable(new WiredVariableFurni(51, 1, null, "0", 0, 0));
        types.addVariable(new WiredVariableReference(52, 1, null, "0", 0, 0));
        when(manager.runtimeDefinition("room:52")).thenReturn(WiredVariableDefinition.create(99, 52,
                WiredVariableType.FURNI, "reference_flag", 1, false, false));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(mock(ConfigurationManager.class));
            WiredEffectPlaceFurni effect = new WiredEffectPlaceFurni(60, 1, null, "0", 0, 0);
            effect.intParams = new int[]{0, 0, 0, 0, 0, 0, 1, 1, 999, -20};
            effect.variableIds = new String[]{"room:51", "n"};
            Method validate = method("validateVariables", Room.class, int[].class, String[].class);
            assertDoesNotThrow(() -> validate.invoke(effect, room, effect.intParams, effect.variableIds));
            WiredContext context = mock(WiredContext.class);
            when(context.room()).thenReturn(room);
            HabboItem placed = mock(HabboItem.class);
            when(placed.getId()).thenReturn(-100);
            Method apply = method("applySpawnVariable", WiredContext.class, HabboItem.class);
            apply.invoke(effect, context, placed);
            verify(manager).setAndDispatch(eq("room:51"), eq(WiredVariableHolder.furni(-100)), eq(0L), any());
            clearInvocations(manager);
            effect.variableIds = new String[]{"room:52", "n"};
            InvocationTargetException rejected = assertThrows(InvocationTargetException.class,
                    () -> validate.invoke(effect, room, effect.intParams, effect.variableIds));
            assertInstanceOf(WiredSaveException.class, rejected.getCause());
            apply.invoke(effect, context, placed);
            verify(manager, never()).setAndDispatch(anyString(), any(), anyLong(), any());
        }
    }

    private static Method method(String name, Class<?>... arguments) throws Exception {
        Method method = WiredEffectPlaceFurni.class.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }
}
