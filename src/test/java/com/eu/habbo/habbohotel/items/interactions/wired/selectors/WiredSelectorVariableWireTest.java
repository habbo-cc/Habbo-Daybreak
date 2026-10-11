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

class WiredSelectorVariableWireTest {
    @Test
    void restoresAndSerializesExactJulySelectorSeventeenShapeIncludingFlags() throws Exception {
        WiredSelectorFurniWithVariable selector = furniSelector();
        selector.loadWiredData(resultSet("{" +
                "\"filter\":true,\"invert\":true," +
                "\"intParams\":[2,2,0,0,-10],\"stringParam\":\"\"," +
                "\"itemIds\":[],\"furniSourceTypes\":[200],\"userSourceTypes\":[0]," +
                "\"variableIds\":[\"room:20\",\"room:21\"],\"furniIds2\":[]}"), null);

        assertTrue(selector.isFilter());
        assertTrue(selector.isInvert());
        assertArrayEquals(new int[] {2, 2, 0, 0, -10}, wiredInts(selector));
        assertArrayEquals(new String[] {"room:20", "room:21"}, wiredVariableIds(selector));

        selector.onPickUp();
        assertArrayEquals(new int[0], wiredInts(selector));
        assertArrayEquals(new String[0], wiredVariableIds(selector));

        selector.loadWiredData(resultSet("{" +
                "\"filter\":true,\"invert\":true," +
                "\"intParams\":[2,2,0,0,-10],\"stringParam\":\"\"," +
                "\"itemIds\":[],\"furniSourceTypes\":[200],\"userSourceTypes\":[0]," +
                "\"variableIds\":[\"room:20\",\"room:21\"],\"furniIds2\":[]}"), null);

        ByteBuf packet = new WiredSelectorDataMessageComposer(selector, null).compose().get();
        try {
            assertEquals(packet.readInt(), packet.readableBytes());
            assertEquals(Outgoing.WiredSelectorDataMessageComposer, packet.readUnsignedShort());
            packet.skipBytes(Integer.BYTES); // furni selection limit
            skipInts(packet); // stuff ids
            skipInts(packet); // stuff ids2
            packet.skipBytes(Integer.BYTES * 2); // sprite/id
            assertEquals("", readString(packet));
            assertEquals(5, packet.readInt());
            assertArrayEquals(new int[] {2, 2, 0, 0, -10}, readInts(packet, 5));
            assertEquals(2, packet.readInt());
            assertEquals("room:20", readString(packet));
            assertEquals("room:21", readString(packet));
            assertEquals(1, packet.readInt());
            assertEquals(200, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(17, packet.readInt());
            assertTrue(packet.readBoolean());
            assertTrue(packet.readBoolean());
        } finally {
            packet.release();
        }
    }

    @Test
    void malformedAndCrossModeFieldsFailClosed() {
        assertTrue(WiredSelectorVariableBase.hasExactAirShape(settings(
                new int[] {1, 0, 0, 0, 0}, new String[] {"room:1", "n"},
                new int[] {100}, new int[] {0})));
        assertTrue(WiredSelectorVariableBase.hasExactAirShape(settings(
                new int[] {5, 1, -1, -3, 0}, new String[] {"room:1", "n"},
                new int[] {200}, new int[] {0})));
        assertTrue(WiredSelectorVariableBase.hasExactAirShape(settings(
                new int[] {2, 2, 0, 0, -10}, new String[] {"room:1", "room:2"},
                new int[] {201}, new int[] {201})));
        assertFalse(WiredSelectorVariableBase.hasExactAirShape(settings(
                new int[] {1, 1, 0, 7, 0}, new String[] {"room:1", "room:2"},
                new int[] {100}, new int[] {0})));
        assertFalse(WiredSelectorVariableBase.hasExactAirShape(settings(
                new int[] {6, 0, 0, 0, 0}, new String[] {"room:1", "n"},
                new int[] {100}, new int[] {0})));
        assertFalse(WiredSelectorVariableBase.hasExactAirShape(settings(
                new int[] {1, 2, 0, 0, -20}, new String[] {"room:1", "n"},
                new int[] {100}, new int[] {0})));
    }

    @Test
    void runtimeComparisonAndDarkRuntimeAreFailClosed() throws Exception {
        assertTrue(WiredSelectorVariableBase.matchesComparison(0, 3, 4));
        assertTrue(WiredSelectorVariableBase.matchesComparison(1, 4, 4));
        assertTrue(WiredSelectorVariableBase.matchesComparison(2, 5, 4));
        assertTrue(WiredSelectorVariableBase.matchesComparison(3, 4, 4));
        assertTrue(WiredSelectorVariableBase.matchesComparison(4, 3, 4));
        assertTrue(WiredSelectorVariableBase.matchesComparison(5, 4, 4));
        assertFalse(WiredSelectorVariableBase.matchesComparison(2, 4, 4));
        assertFalse(WiredSelectorVariableBase.matchesComparison(77, 4, 4));
        assertFalse(furniSelector().resolve(null, null).hasTargets());
        assertFalse(usersSelector().resolve(null, null).hasTargets());
    }

    private static WiredSettingsV2 settings(int[] ints, String[] variables, int[] furniSources, int[] userSources) {
        return new WiredSettingsV2(ints, "", new int[0], new int[0], variables,
                furniSources, userSources, 0, 0, false, false);
    }

    private static WiredSelectorFurniWithVariable furniSelector() throws Exception {
        WiredSelectorFurniWithVariable selector = new WiredSelectorFurniWithVariable(
                52, 1, baseItem(901), "0", 0, 0);
        selector.setRoomId(99);
        return selector;
    }

    private static WiredSelectorUsersWithVariable usersSelector() throws Exception {
        WiredSelectorUsersWithVariable selector = new WiredSelectorUsersWithVariable(
                53, 1, baseItem(902), "0", 0, 0);
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
                WiredSelectorVariableWireTest.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getString") && args != null && args.length == 1
                            && "wired_data".equals(args[0])) {
                        return wiredData;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static int[] wiredInts(WiredSelectorVariableBase selector) throws Exception {
        Field field = WiredSelectorConfigBase.class.getDeclaredField("intParams");
        field.setAccessible(true);
        return (int[]) field.get(selector);
    }

    private static String[] wiredVariableIds(WiredSelectorVariableBase selector) throws Exception {
        Field field = WiredSelectorConfigBase.class.getDeclaredField("variableIds");
        field.setAccessible(true);
        return (String[]) field.get(selector);
    }

    private static void skipInts(ByteBuf packet) {
        int count = packet.readInt();
        packet.skipBytes(count * Integer.BYTES);
    }

    private static int[] readInts(ByteBuf packet, int count) {
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = packet.readInt();
        }
        return values;
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
