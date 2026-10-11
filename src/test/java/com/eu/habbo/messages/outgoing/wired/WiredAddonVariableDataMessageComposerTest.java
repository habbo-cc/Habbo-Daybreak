package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WiredAddonVariableDataMessageComposerTest {
    @Test
    void addonWritesExact7101GenericPayloadWithoutCategorySuffix() throws Exception {
        DummyAddon addon = new DummyAddon(41, baseItem(321));
        ByteBuf packet = new WiredAddonDataMessageComposer(addon, null).compose().get();
        assertGenericPayload(packet, Outgoing.WiredAddonDataMessageComposer, 321, 41, WiredAddonType.EFFECT_PICK_AND_SKIP.code);
    }

    @Test
    void variableWritesExact7102GenericPayloadWithoutCategorySuffix() throws Exception {
        DummyVariable variable = new DummyVariable(42, baseItem(654));
        ByteBuf packet = new WiredVariableDataMessageComposer(variable, null).compose().get();
        assertGenericPayload(packet, Outgoing.WiredVariableDataMessageComposer, 654, 42, WiredVariableType.ROOM.code);
    }

    private static void assertGenericPayload(ByteBuf packet, int header, int spriteId, int itemId, int typeCode) {
        try {
            int declaredLength = packet.readInt();
            assertEquals(packet.readableBytes(), declaredLength);
            assertEquals(header, packet.readUnsignedShort());
            assertEquals(2, packet.readInt());                 // furniLimit
            assertEquals(0, packet.readInt());                 // stuffIds
            assertEquals(0, packet.readInt());                 // stuffIds2
            assertEquals(spriteId, packet.readInt());
            assertEquals(itemId, packet.readInt());
            assertEquals("wired value", readString(packet));
            assertEquals(2, packet.readInt());
            assertEquals(7, packet.readInt());
            assertEquals(-2, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals("room.counter", readString(packet));
            assertEquals(0, packet.readInt());                 // furniSourceTypes
            assertEquals(0, packet.readInt());                 // userSourceTypes
            assertEquals(typeCode, packet.readInt());
            assertFalse(packet.readBoolean());                 // advancedMode
            assertEquals(0, packet.readInt());                 // furni source slots
            assertEquals(0, packet.readInt());                 // user source slots
            assertEquals(0, packet.readInt());                 // furni defaults
            assertEquals(0, packet.readInt());                 // user defaults
            assertFalse(packet.readBoolean());                 // allowWallFurni
            assertEquals(1, packet.readInt());                 // WiredContext blocks
            assertEquals(0, packet.readInt());                 // catalog block
            assertEquals(0, packet.readInt());                 // unavailable catalog hash
            assertEquals(0, packet.readInt());                 // defaultIntParams
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
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

    private static final class DummyAddon extends InteractionWiredAddon {
        private DummyAddon(int id, Item item) {
            super(id, 1, item, "0", 0, 0);
        }

        @Override public WiredAddonType getType() { return WiredAddonType.EFFECT_PICK_AND_SKIP; }
        @Override public boolean saveData(WiredSettingsV2 settings) { return false; }
        @Override public String getWiredData() { return ""; }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
        @Override protected int getMaxFurniSelection() { return 2; }
        @Override protected String getWiredStringParam() { return "wired value"; }
        @Override protected int[] getWiredIntParams() { return new int[] {7, -2}; }
        @Override protected String[] getWiredVariableIds() { return new String[] {"room.counter"}; }
    }

    private static final class DummyVariable extends InteractionWiredVariable {
        private DummyVariable(int id, Item item) {
            super(id, 1, item, "0", 0, 0);
        }

        @Override public WiredVariableType getType() { return WiredVariableType.ROOM; }
        @Override public boolean saveData(WiredSettingsV2 settings) { return false; }
        @Override public String getWiredData() { return ""; }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
        @Override protected int getMaxFurniSelection() { return 2; }
        @Override protected String getWiredStringParam() { return "wired value"; }
        @Override protected int[] getWiredIntParams() { return new int[] {7, -2}; }
        @Override protected String[] getWiredVariableIds() { return new String[] {"room.counter"}; }
    }
}
