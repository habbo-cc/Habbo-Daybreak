package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.core.WiredCapabilityState;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredCapabilitiesMessageComposerTest {
    @Test
    void writesExactRevisionOnePayload() {
        WiredCapabilityState state = new WiredCapabilityState(1, 0x101, 1, 42, 0);
        ByteBuf packet = new WiredCapabilitiesMessageComposer(state).compose().get();

        try {
            assertEquals(18, packet.readInt());
            assertEquals(Outgoing.WiredCapabilitiesMessageComposer, packet.readUnsignedShort());
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(42, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    @Test
    void nullStateFailsClosed() {
        ByteBuf packet = new WiredCapabilitiesMessageComposer(null).compose().get();

        try {
            packet.skipBytes(Integer.BYTES + Short.BYTES);
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }
}
