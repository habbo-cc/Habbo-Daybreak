package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.wired.WiredSelectorDataMessageComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredSelectorRemoteWireTest {
    @Test
    void serializesAirCodeNineteenWithExactlyTwoCustomIntegers() throws Exception {
        WiredSelectorRemote selector = selector();
        selector.loadWiredData(resultSet("{" +
                "\"aggregation\":1," +
                "\"randomAmount\":7," +
                "\"filter\":true," +
                "\"invert\":false," +
                "\"referenceItemIds\":[7,7,-2,52]}"), null);

        assertEquals(java.util.List.of(7), selector.referenceItemIdsForTest());

        ByteBuf packet = new WiredSelectorDataMessageComposer(selector, null).compose().get();
        try {
            int declaredLength = packet.readInt();
            assertEquals(packet.readableBytes(), declaredLength);
            assertEquals(Outgoing.WiredSelectorDataMessageComposer, packet.readUnsignedShort());

            assertEquals(com.eu.habbo.habbohotel.wired.core.WiredManager.MAXIMUM_FURNI_SELECTION,
                    packet.readInt());
            assertEquals(0, packet.readInt()); // no live room: no stale selected ids emitted
            assertEquals(0, packet.readInt()); // second furni ids
            assertEquals(901, packet.readInt());
            assertEquals(52, packet.readInt());
            assertEquals("", readString(packet));
            assertEquals(2, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(7, packet.readInt());
            assertEquals(0, packet.readInt()); // variable ids
            assertEquals(1, packet.readInt());
            assertEquals(100, packet.readInt()); // selected reference stacks (default)
            assertEquals(0, packet.readInt()); // user sources
            assertEquals(19, packet.readInt());
            assertTrue(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertFalse(packet.readBoolean()); // advanced mode
            assertEquals(1, packet.readInt()); // one generic reference-furni source slot
            assertEquals(3, packet.readInt());
            assertEquals(100, packet.readInt());
            assertEquals(200, packet.readInt());
            assertEquals(201, packet.readInt());
            assertEquals(0, packet.readInt()); // allowed user source slots
            assertEquals(1, packet.readInt()); // default furni sources
            assertEquals(100, packet.readInt());
            assertEquals(0, packet.readInt()); // default user sources
            assertFalse(packet.readBoolean()); // allow wall furni
            assertEquals(1, packet.readInt()); // WiredContext blocks
            assertEquals(0, packet.readInt()); // catalog block
            assertEquals(0, packet.readInt()); // unavailable catalog hash
            assertEquals(0, packet.readInt()); // default int params
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    @Test
    void corruptPersistenceIsBoundedAndPickupClearsEverything() throws Exception {
        WiredSelectorRemote selector = selector();
        selector.loadWiredData(resultSet("{" +
                "\"aggregation\":99," +
                "\"randomAmount\":2147483647," +
                "\"filter\":true," +
                "\"invert\":true," +
                "\"referenceItemIds\":[1,1,2]}"), null);

        assertArrayEquals(new int[] {0, WiredSelectorRemote.MAX_RANDOM_STACKS}, wiredInts(selector));
        assertEquals(java.util.List.of(1, 2), selector.referenceItemIdsForTest());
        assertTrue(selector.isFilter());
        assertTrue(selector.isInvert());

        selector.onPickUp();
        assertArrayEquals(new int[] {0, 0}, wiredInts(selector));
        assertEquals(java.util.List.of(), selector.referenceItemIdsForTest());
        assertFalse(selector.isFilter());
        assertFalse(selector.isInvert());
    }

    @Test
    void darkCapabilityRejectsSaveAndRuntimeWithoutMutatingState() throws Exception {
        WiredSelectorRemote selector = selector();
        WiredSettingsV2 settings = new WiredSettingsV2(
                new int[] {1, 1}, "", new int[0], new int[0], new String[0],
                new int[0], new int[0], 0, 0, false, false);

        assertFalse(selector.saveData(settings));
        assertArrayEquals(new int[] {0, 0}, wiredInts(selector));
        assertFalse(selector.resolve(null, null).hasTargets());
    }

    @Test
    void rejectsUnprovenDynamicSourceAndSecondaryReferenceFields() {
        assertTrue(WiredSelectorRemote.hasSupportedCommonFields(new WiredSettingsV2(
                new int[] {0, 0}, "", new int[0], new int[0], new String[0],
                new int[] {100}, new int[0], 0, 0, false, false)));
        assertTrue(WiredSelectorRemote.hasSupportedCommonFields(new WiredSettingsV2(
                new int[] {0, 0}, "", new int[0], new int[0], new String[0],
                new int[] {200}, new int[0], 0, 0, false, false)));
        assertTrue(WiredSelectorRemote.hasSupportedCommonFields(new WiredSettingsV2(
                new int[] {0, 0}, "", new int[0], new int[0], new String[0],
                new int[] {201}, new int[0], 0, 0, false, false)));
        assertFalse(WiredSelectorRemote.hasSupportedCommonFields(new WiredSettingsV2(
                new int[] {0, 0}, "", new int[0], new int[0], new String[0],
                new int[0], new int[0], 0, 0, false, false)));
        assertFalse(WiredSelectorRemote.hasSupportedCommonFields(new WiredSettingsV2(
                new int[] {0, 0}, "", new int[0], new int[0], new String[0],
                new int[] {999}, new int[0], 0, 0, false, false)));
        assertFalse(WiredSelectorRemote.hasSupportedCommonFields(new WiredSettingsV2(
                new int[] {0, 0}, "", new int[0], new int[] {7}, new String[0],
                new int[] {100}, new int[0], 0, 0, false, false)));
    }

    @Test
    void genericReferenceSourceRoundTripsThroughWiredSourcesColumn() throws Exception {
        WiredSelectorRemote selector = selector();
        selector.setWiredSourceTypes(new int[] {201}, new int[0]);
        String persisted = selector.getWiredSourcesData();
        assertTrue(persisted.contains("201"));

        WiredSelectorRemote restored = selector();
        restored.loadWiredSourcesData(persisted);
        assertArrayEquals(new int[] {201}, wiredFurniSources(restored));

        restored.setWiredSourceTypes(new int[] {999}, new int[0]);
        assertArrayEquals(new int[] {100}, wiredFurniSources(restored));
        assertEquals("", restored.getWiredSourcesData());
    }

    private static int[] wiredInts(WiredSelectorRemote selector) throws Exception {
        java.lang.reflect.Method method = WiredSelectorRemote.class.getDeclaredMethod("getWiredIntParams");
        method.setAccessible(true);
        return (int[]) method.invoke(selector);
    }

    private static int[] wiredFurniSources(WiredSelectorRemote selector) throws Exception {
        java.lang.reflect.Method method = com.eu.habbo.habbohotel.items.interactions.InteractionWired.class
                .getDeclaredMethod("getWiredFurniSourceTypes");
        method.setAccessible(true);
        return (int[]) method.invoke(selector);
    }

    private static WiredSelectorRemote selector() throws Exception {
        WiredSelectorRemote selector = new WiredSelectorRemote(52, 1, baseItem(901), "0", 0, 0);
        selector.setRoomId(99);
        return selector;
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

    private static ResultSet resultSet(String wiredData) {
        return (ResultSet) Proxy.newProxyInstance(
                WiredSelectorRemoteWireTest.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getString")
                            && args != null
                            && args.length == 1
                            && "wired_data".equals(args[0])) {
                        return wiredData;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
