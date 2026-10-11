package com.eu.habbo.habbohotel.wired.core;

/**
 * Immutable result of Wired 2.0 capability negotiation for one connection.
 *
 * <p>The client mask is retained for later feature-specific negotiation, while
 * the remaining fields are the exact revision-1 response payload.</p>
 */
public record WiredCapabilityState(
        int negotiatedRevision,
        int clientCapabilityMask,
        int connectionCapabilityMask,
        int roomId,
        int roomCapabilityMask) {

    private static final WiredCapabilityState UNSUPPORTED =
            new WiredCapabilityState(0, 0, 0, 0, 0);

    public static WiredCapabilityState unsupported() {
        return UNSUPPORTED;
    }

    public boolean supportsConnection(int capability) {
        return capability != 0 && (this.connectionCapabilityMask & capability) == capability;
    }

    public boolean supportsRoom(int capability, int currentRoomId) {
        return currentRoomId > 0
                && this.roomId == currentRoomId
                && capability != 0
                && (this.roomCapabilityMask & capability) == capability;
    }
}
