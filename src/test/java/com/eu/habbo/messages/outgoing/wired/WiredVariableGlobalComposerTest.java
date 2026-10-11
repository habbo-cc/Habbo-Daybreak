package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableGlobal;
import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableGlobalComposerTest {
    @Test
    void writesExactJulyBlockThreeShapeAndDocumentedMetadataAdapter() throws Exception {
        WiredVariableGlobal variable = new WiredVariableGlobal(52, 1, baseItem(901), "0", 0, 0);
        variable.setRoomId(99);
        // Golden packet fixture only: production deliberately rejects unbound
        // saves/mutations, so seed the already-validated serialization state.
        seedField(variable, "variableName", "room_count");
        seedField(variable, "availability", WiredVariableAvailability.ROOM_ACTIVE);
        seedField(variable, "value", 73);

        ByteBuf packet = new WiredVariableDataMessageComposer(variable, null).compose().get();
        try {
            int declaredLength = packet.readInt();
            assertEquals(packet.readableBytes(), declaredLength);
            assertEquals(Outgoing.WiredVariableDataMessageComposer, packet.readUnsignedShort());

            assertEquals(0, packet.readInt()); // furniLimit
            assertEquals(0, packet.readInt()); // primary furni
            assertEquals(0, packet.readInt()); // secondary furni
            assertEquals(901, packet.readInt());
            assertEquals(52, packet.readInt());
            assertEquals("room_count", readString(packet));
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readInt()); // availability
            assertEquals(0, packet.readInt()); // variable ids
            assertEquals(0, packet.readInt()); // furni sources
            assertEquals(0, packet.readInt()); // user sources
            assertEquals(2, packet.readInt()); // July Global Variable definition code
            assertFalse(packet.readBoolean());
            assertEquals(0, packet.readInt()); // allowed furni source slots
            assertEquals(0, packet.readInt()); // allowed user source slots
            assertEquals(0, packet.readInt()); // default furni sources
            assertEquals(0, packet.readInt()); // default user sources
            assertFalse(packet.readBoolean());

            assertEquals(2, packet.readInt()); // WiredContext block count
            assertEquals(0, packet.readInt()); // catalog block
            assertEquals(0, packet.readInt()); // no room fixture: unavailable catalog
            assertEquals(3, packet.readInt()); // Global variable + value block
            assertEquals("room:52", readString(packet));
            assertEquals(0, packet.readInt()); // stored/user-defined metadata type
            assertEquals("room_count", readString(packet));
            assertEquals(1, packet.readInt()); // availability
            assertEquals(-10, packet.readInt()); // global target
            assertTrue(packet.readBoolean());  // alwaysAvailable
            assertFalse(packet.readBoolean()); // canCreateAndDelete
            assertTrue(packet.readBoolean());  // hasValue
            assertTrue(packet.readBoolean());  // canWriteValue
            assertTrue(packet.readBoolean());  // canInterceptChanges
            assertFalse(packet.readBoolean()); // invisible
            assertTrue(packet.readBoolean());  // canReadCreationTime
            assertTrue(packet.readBoolean());  // canReadLastUpdateTime
            assertFalse(packet.readBoolean()); // hasTextConnector
            assertEquals(73, packet.readInt());
            assertEquals(0, packet.readInt()); // defaultIntParams
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    private static Item baseItem(int spriteId) throws Exception {
        Constructor<Item> constructor = Item.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Item item = constructor.newInstance();
        Field sprite = Item.class.getDeclaredField("spriteId");
        sprite.setAccessible(true);
        sprite.setInt(item, spriteId);
        return item;
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void seedField(WiredVariableGlobal variable, String name, Object value)
            throws Exception {
        Field field = WiredVariableGlobal.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(variable, value);
    }
}
