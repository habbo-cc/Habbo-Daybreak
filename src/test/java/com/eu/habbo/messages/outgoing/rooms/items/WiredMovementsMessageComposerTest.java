package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredMovementsMessageComposerTest {
    @Test
    void preservesLocal7115FurnitureMoveRecord() {
        WiredMovementsMessageComposer.FurniMove move =
                new WiredMovementsMessageComposer.FurniMove(123, 1, 2, 0.5, 3, 4, 1.25, 500, 6);
        move.overshootAnimationTime = 250;
        move.curveStrength = 7;

        ByteBuf packet = new WiredMovementsMessageComposer(move).compose().get();
        try {
            assertEquals(59, packet.readInt());
            assertEquals(Outgoing.WiredMovementsMessageComposer, packet.readUnsignedShort());
            assertEquals(7115, Outgoing.WiredMovementsMessageComposer);
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(3, packet.readInt());
            assertEquals(4, packet.readInt());
            assertEquals("0.5", readString(packet));
            assertEquals("1.25", readString(packet));
            assertEquals(123, packet.readInt());
            assertEquals(500, packet.readInt());
            assertEquals(6, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(250, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(7, packet.readInt());
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    @Test
    void optionalMovementFieldsRemainAbsentWhenUnset() {
        WiredMovementsMessageComposer.FurniMove move =
                new WiredMovementsMessageComposer.FurniMove(9, 0, 0, 0, 1, 1, 0, 100, 0);

        ByteBuf packet = new WiredMovementsMessageComposer(move).compose().get();
        try {
            packet.skipBytes(4 + 2 + 4 + 4 + 16);
            readString(packet);
            readString(packet);
            packet.skipBytes(12);
            assertFalse(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesEveryJulyRecordTypeInOnePacket() {
        WiredMovementsMessageComposer.UserMove userMove =
                new WiredMovementsMessageComposer.UserMove(
                        77, 1, 2, 0.25, 3, 4, 1.5,
                        true, 600, 5, 6, 125);
        WiredMovementsMessageComposer.FurniMove furniMove =
                new WiredMovementsMessageComposer.FurniMove(
                        88, 7, 8, 2.25, 9, 10, 3.5, 700, 4);
        WiredMovementsMessageComposer.WallItemMove wallItemMove =
                new WiredMovementsMessageComposer.WallItemMove(
                        99, true, 11, 12, 13, 14, 15, 16, 17, 18, 800);
        WiredMovementsMessageComposer.UserDirection userDirection =
                new WiredMovementsMessageComposer.UserDirection(111, 2, 3);

        ByteBuf packet = new WiredMovementsMessageComposer(
                List.of(furniMove), List.of(userMove), List.of(wallItemMove), List.of(userDirection))
                .compose()
                .get();
        try {
            assertEquals(packet.readInt(), packet.readableBytes());
            assertEquals(7115, packet.readUnsignedShort());
            assertEquals(4, packet.readInt());

            assertEquals(0, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(3, packet.readInt());
            assertEquals(4, packet.readInt());
            assertEquals("0.25", readString(packet));
            assertEquals("1.5", readString(packet));
            assertEquals(77, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(600, packet.readInt());
            assertEquals(5, packet.readInt());
            assertEquals(6, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(125, packet.readInt());

            assertEquals(1, packet.readInt());
            assertEquals(7, packet.readInt());
            assertEquals(8, packet.readInt());
            assertEquals(9, packet.readInt());
            assertEquals(10, packet.readInt());
            assertEquals("2.25", readString(packet));
            assertEquals("3.5", readString(packet));
            assertEquals(88, packet.readInt());
            assertEquals(700, packet.readInt());
            assertEquals(4, packet.readInt());
            assertFalse(packet.readBoolean());
            assertFalse(packet.readBoolean());

            assertEquals(2, packet.readInt());
            assertEquals(99, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(11, packet.readInt());
            assertEquals(12, packet.readInt());
            assertEquals(13, packet.readInt());
            assertEquals(14, packet.readInt());
            assertEquals(15, packet.readInt());
            assertEquals(16, packet.readInt());
            assertEquals(17, packet.readInt());
            assertEquals(18, packet.readInt());
            assertEquals(800, packet.readInt());

            assertEquals(3, packet.readInt());
            assertEquals(111, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(3, packet.readInt());
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, java.nio.charset.StandardCharsets.UTF_8).toString();
    }
}
