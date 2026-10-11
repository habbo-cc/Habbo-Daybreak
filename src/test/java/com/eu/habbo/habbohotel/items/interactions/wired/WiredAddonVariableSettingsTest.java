package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredAddonVariableSettingsTest {
    @Test
    void addonUsesExactJulyCommonSettingsOrder() {
        assertCommonSettingsRoundTrip(WiredCategoryType.ADDON);
    }

    @Test
    void variableUsesExactJulyCommonSettingsOrder() {
        assertCommonSettingsRoundTrip(WiredCategoryType.VARIABLE);
    }

    private static void assertCommonSettingsRoundTrip(WiredCategoryType category) {
        ByteBuf body = Unpooled.buffer();
        writeInts(body, 3, -4);
        writeString(body, "wired value");
        writeInts(body, 101, 102);
        writeInts(body, 5);
        writeInts(body, 8, 9);
        writeStrings(body, "room.counter", "user.score");
        writeInts(body, 201);

        ClientMessage packet = new ClientMessage(0, body);
        WiredSettingsV2 settings = com.eu.habbo.habbohotel.items.interactions.InteractionWired
                .readSettingsV2(packet, category, null);

        assertArrayEquals(new int[] {3, -4}, settings.getIntParams());
        assertEquals("wired value", settings.getStringParam());
        assertArrayEquals(new int[] {101, 102}, settings.getFurniIds());
        assertArrayEquals(new int[] {5}, settings.getFurniSourceTypes());
        assertArrayEquals(new int[] {8, 9}, settings.getUserSourceTypes());
        assertArrayEquals(new String[] {"room.counter", "user.score"}, settings.getVariableIds());
        assertArrayEquals(new int[] {201}, settings.getFurniIds2());
        assertEquals(0, packet.bytesAvailable());
    }

    private static void writeInts(ByteBuf buffer, int... values) {
        buffer.writeInt(values.length);
        for (int value : values) {
            buffer.writeInt(value);
        }
    }

    private static void writeStrings(ByteBuf buffer, String... values) {
        buffer.writeInt(values.length);
        for (String value : values) {
            writeString(buffer, value);
        }
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }
}
