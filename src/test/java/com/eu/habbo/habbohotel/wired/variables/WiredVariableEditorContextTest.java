package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonGlobalPlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.*;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WiredVariableEditorContextTest {
    @Test
    void allOverridesPreserveTheirBlocksAndDeliverTheSharedCatalogHash() throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes types = new RoomSpecialTypes();
        when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredVariableManager manager = mock(WiredVariableManager.class);
        var snapshot = new WiredVariableManager.Snapshot(true, 0, 0, Map.of());
        when(manager.snapshot(any())).thenReturn(snapshot);
        setField(types, "wiredVariableManager", manager);
        int catalogHash = WiredVariableCatalog.build(room, snapshot).aggregateHash();

        assertContext(new WiredVariableContext(70, 1, null, "0", 0, 0), room, catalogHash, -1);
        WiredVariableUser user = new WiredVariableUser(71, 1, null, "0", 0, 0);
        // An unconfigured scoped box still carries block 0.
        assertContext(user, room, catalogHash, -1);
        var definition = WiredVariableDefinition.create(99, 71, WiredVariableType.USER, "user_value", 0, false);
        when(manager.variableSnapshot("room:71")).thenReturn(
                new WiredVariableManager.VariableSnapshot(definition, List.of(), 1));
        setField(user, "manager", manager);
        assertContext(user, room, catalogHash, 2);
        WiredVariableFurni furni = new WiredVariableFurni(75, 1, null, "0", 0, 0);
        var furniDefinition = WiredVariableDefinition.create(99, 75, WiredVariableType.FURNI, "furni_value", 1, false);
        when(manager.variableSnapshot("room:75")).thenReturn(
                new WiredVariableManager.VariableSnapshot(furniDefinition, List.of(), 1));
        setField(furni, "manager", manager);
        assertContext(furni, room, catalogHash, 1);
        assertContext(new WiredVariableGlobal(72, 1, null, "0", 0, 0), room, catalogHash, 3);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(null);
            assertContext(new WiredVariableReference(73, 1, null, "0", 0, 0), room, catalogHash, 4);
            // Null room is a valid unavailable-catalog serialization fixture.
            assertContext(new WiredAddonGlobalPlaceholder(74, 1, null, "0", 0, 0), null, 0, 6);
        }
    }

    private static void assertContext(InteractionWired item, Room room, int hash, int extraBlock) throws Exception {
        ServerMessage message = new ServerMessage(1);
        Method serializer = method(item.getClass(), "serializeWiredContext");
        serializer.invoke(item, message, room);
        ByteBuf packet = message.get();
        try {
            packet.skipBytes(6);
            assertEquals(extraBlock < 0 ? 1 : 2, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(hash, packet.readInt());
            if (extraBlock >= 0) {
                assertEquals(extraBlock, packet.readInt());
                if (extraBlock == 1 || extraBlock == 2 || extraBlock == 3) {
                    skipMetadata(packet);
                    assertEquals(0, packet.readInt()); // empty holders or zero singleton
                } else {
                    assertEquals(0, packet.readInt()); // shared references / placeholders
                }
            }
            assertEquals(0, packet.readableBytes());
        } finally { packet.release(); }
    }

    private static void skipMetadata(ByteBuf packet) {
        string(packet);
        packet.readInt();
        string(packet);
        packet.skipBytes(8);
        packet.skipBytes(8);
        assertFalse(packet.readBoolean());
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8).toString();
    }

    private static Method method(Class<?> type, String name) throws Exception {
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name, ServerMessage.class, Room.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchMethodException(name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
