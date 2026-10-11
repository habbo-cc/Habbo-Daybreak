package com.eu.habbo.messages;

import com.eu.habbo.util.PacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ClientMessage {
    private final int header;
    private final ByteBuf buffer;

    public ClientMessage(int messageId, ByteBuf buffer) {
        this.header = messageId;
        this.buffer = ((buffer == null) || (buffer.readableBytes() == 0) ? Unpooled.EMPTY_BUFFER : buffer);
    }

    public ByteBuf getBuffer() {
        return this.buffer;
    }

    public int getMessageId() {
        return this.header;
    }
    
    
    /**
     *
     * @return
     * @throws CloneNotSupportedException
     */
    @Override
    public ClientMessage clone() throws CloneNotSupportedException {
        return new ClientMessage(this.header, this.buffer.duplicate());
    }

    public int readShort() {
        try {
            return this.buffer.readShort();
        } catch (Exception e) {
        }

        return 0;
    }

    public Integer readInt() {
        try {
            return this.buffer.readInt();
        } catch (Exception e) {
        }

        return 0;
    }

    public boolean readBoolean() {
        try {
            return this.buffer.readByte() == 1;
        } catch (Exception e) {
        }

        return false;
    }

    public String readString() {
        try {
            int length = this.readShort();
            byte[] data = new byte[length];
            this.buffer.readBytes(data);
            return new String(data);
        } catch (Exception e) {
            return "";
        }
    }

    public String getMessageBody() {
        return PacketUtils.formatPacket(this.buffer);
    }

    public int bytesAvailable() {
        return this.buffer.readableBytes();
    }

    public int readRequiredInt() {
        if (this.bytesAvailable() < Integer.BYTES) {
            throw new MalformedPacketException("missing required int");
        }
        return this.buffer.readInt();
    }

    public long readRequiredLong() {
        if (this.bytesAvailable() < Long.BYTES) {
            throw new MalformedPacketException("missing required long");
        }
        return this.buffer.readLong();
    }

    public int readRequiredShort() {
        if (this.bytesAvailable() < Short.BYTES) {
            throw new MalformedPacketException("missing required short");
        }
        return this.buffer.readShort();
    }

    public int readRequiredByte() {
        if (this.bytesAvailable() < 1) {
            throw new MalformedPacketException("missing required byte");
        }
        return this.buffer.readByte();
    }

    public boolean readRequiredBoolean() {
        if (this.bytesAvailable() < 1) {
            throw new MalformedPacketException("missing required boolean");
        }
        return this.buffer.readByte() == 1;
    }

    public int readBoundedCount(int maximum, int minimumBytesPerEntry) {
        if (maximum < 0 || minimumBytesPerEntry < 0) {
            throw new IllegalArgumentException("packet bounds must be non-negative");
        }

        int count = this.readRequiredInt();
        if (count < 0) {
            throw new MalformedPacketException("negative list count");
        }
        if (count > maximum) {
            throw new MalformedPacketException("list count " + count + " exceeds limit " + maximum);
        }
        if (minimumBytesPerEntry > 0 && (long) count * minimumBytesPerEntry > this.bytesAvailable()) {
            throw new MalformedPacketException("list count exceeds remaining packet bytes");
        }
        return count;
    }

    public String readBoundedString(int maximumLength) {
        if (maximumLength < 0) {
            throw new IllegalArgumentException("maximum string length must be non-negative");
        }
        if (this.bytesAvailable() < Short.BYTES) {
            throw new MalformedPacketException("missing string length");
        }

        int length = this.buffer.getShort(this.buffer.readerIndex());
        if (length < 0 || length > maximumLength) {
            throw new MalformedPacketException("invalid string length " + length);
        }
        if ((long) Short.BYTES + length > this.bytesAvailable()) {
            throw new MalformedPacketException("string length exceeds remaining packet bytes");
        }
        return this.readString();
    }

    public boolean release() {
        return this.buffer.release();
    }

}
