package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.MalformedPacketException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestVariablesDiffMessageEventTest {
    @Test
    void parsesExactPairsAndRejectsDuplicateIds() {
        ByteBuf valid = Unpooled.buffer();
        valid.writeInt(2);
        writeString(valid, "room:1");
        valid.writeInt(11);
        writeString(valid, "room:2");
        valid.writeInt(22);
        ClientMessage validPacket = new ClientMessage(7006, valid);
        try {
            assertEquals(Map.of("room:1", 11, "room:2", 22),
                    RequestVariablesDiffMessageEvent.parseHashes(validPacket));
        } finally {
            validPacket.release();
        }

        ByteBuf duplicate = Unpooled.buffer();
        duplicate.writeInt(2);
        writeString(duplicate, "room:1");
        duplicate.writeInt(11);
        writeString(duplicate, "room:1");
        duplicate.writeInt(22);
        ClientMessage duplicatePacket = new ClientMessage(7006, duplicate);
        try {
            assertThrows(MalformedPacketException.class,
                    () -> RequestVariablesDiffMessageEvent.parseHashes(duplicatePacket));
        } finally {
            duplicatePacket.release();
        }
    }

    @Test
    void rejectsMalformedCountsAndTrailingBytes() {
        ByteBuf negative = Unpooled.buffer();
        negative.writeInt(-1);
        ClientMessage negativePacket = new ClientMessage(7006, negative);
        try {
            assertThrows(MalformedPacketException.class,
                    () -> RequestVariablesDiffMessageEvent.parseHashes(negativePacket));
        } finally {
            negativePacket.release();
        }

        ByteBuf trailing = Unpooled.buffer();
        trailing.writeInt(0);
        trailing.writeByte(1);
        ClientMessage trailingPacket = new ClientMessage(7006, trailing);
        try {
            assertThrows(MalformedPacketException.class,
                    () -> RequestVariablesDiffMessageEvent.parseHashes(trailingPacket));
        } finally {
            trailingPacket.release();
        }
    }

    @Test
    void catalogOnlyExposesRoomVisibleStoredScopes() {
        assertTrue(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.FURNI));
        assertTrue(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.USER));
        assertTrue(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.ROOM));
        assertTrue(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.CONTEXT));
        assertTrue(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.QUEST));
        assertTrue(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.QUEST_CHAIN));
        assertFalse(WiredVariableCatalogRequestSupport
                .isRoomVisibleStoredDefinition(WiredVariableType.UNKNOWN));
    }

    private static void writeString(ByteBuf packet, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        packet.writeShort(bytes.length);
        packet.writeBytes(bytes);
    }
}
