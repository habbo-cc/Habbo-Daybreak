package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.MalformedPacketException;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormOperationFailedComposer;

abstract class SnowStormHandler extends MessageHandler {
    protected final SnowStormManager manager() {
        return Emulator.getGameEnvironment().getSnowStormManager();
    }

    protected final Habbo habbo() { return client.getHabbo(); }

    protected final int requiredByte(int minimum, int maximum) {
        int value = packet.readRequiredByte() & 0xFF;
        if (value < minimum || value > maximum) {
            throw new MalformedPacketException("SnowStorm byte is outside its protocol range");
        }
        return value;
    }

    protected final int requiredPositiveInt() {
        int value = packet.readRequiredInt();
        if (value <= 0) throw new MalformedPacketException("SnowStorm identifier must be positive");
        return value;
    }

    protected final int requiredNegativeInt() {
        int value = packet.readRequiredInt();
        if (value >= 0) throw new MalformedPacketException("SnowStorm bot identifier must be negative");
        return value;
    }

    protected final int requiredNonNegativeInt() {
        int value = packet.readRequiredInt();
        if (value < 0) throw new MalformedPacketException("SnowStorm value must be non-negative");
        return value;
    }

    protected final int requiredSequenceCursor() {
        int value = packet.readRequiredInt();
        if (value < -1) throw new MalformedPacketException("SnowStorm sequence cursor is invalid");
        return value;
    }

    protected final void requireEnd() {
        if (packet.bytesAvailable() != 0) {
            throw new MalformedPacketException("unexpected trailing SnowStorm payload");
        }
    }

    protected final void failure(int operation, int instanceId, int reason) {
        if (reason >= 0) client.sendResponse(new SnowStormOperationFailedComposer(
                operation, instanceId, reason));
    }

    /** Operation-specific manager buckets own the actual limits. */
    @Override
    public int getRatelimit() { return 0; }
}
