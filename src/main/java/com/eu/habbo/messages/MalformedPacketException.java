package com.eu.habbo.messages;

/** Raised when an incoming packet is truncated or exceeds a protocol safety bound. */
public class MalformedPacketException extends RuntimeException {
    public MalformedPacketException(String message) {
        super(message);
    }
}
