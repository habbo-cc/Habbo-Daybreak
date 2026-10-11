package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;

/**
 * Wired 2.0 protocol-revision negotiation and centralized runtime readiness.
 */
public final class WiredCapabilityService {
    public static final String CONFIG_ENABLED = "wired2.protocol.enabled";

    public static final int SERVER_PROTOCOL_REVISION = 1;
    public static final int CAPABILITY_PROTOCOL = 1;
    public static final int CAPABILITY_ADDONS = 1 << 1;
    public static final int CAPABILITY_VARIABLES = 1 << 2;
    public static final int CAPABILITY_VARIABLE_SYNC = 1 << 3;
    public static final int CAPABILITY_SIGNALS = 1 << 4;
    public static final int CAPABILITY_REMOTE_SELECTOR = 1 << 5;
    public static final int CAPABILITY_WIRED_MENU = 1 << 6;
    public static final int CAPABILITY_MENU_INSPECTION = 1 << 7;
    public static final int CAPABILITY_MENU_LOGS = 1 << 8;
    public static final int CAPABILITY_MENU_SETTINGS = 1 << 9;
    public static final int CAPABILITY_MENU_VARIABLES = 1 << 10;
    public static final int CAPABILITY_MENU_CHESTS = 1 << 11;
    public static final int CAPABILITY_ENVIRONMENT_V2 = 1 << 12;
    public static final int CAPABILITY_CLICK_SETTINGS_V2 = 1 << 13;
    public static final int CAPABILITY_WIRED_MOVEMENTS = 1 << 14;
    public static final int CAPABILITY_CREATOR_TOOLS = 1 << 15;
    public static final int CAPABILITY_CHESTS = 1 << 16;
    public static final int CAPABILITY_CHEST_WIRED = 1 << 17;
    public static final int CAPABILITY_CONTRACTS = 1 << 18;
    public static final int KNOWN_CAPABILITY_MASK = (1 << 19) - 1;

    /**
     * Wired 2.0 services which are implemented end-to-end in this server.
     *
     * <p>Keep this deliberately narrower than {@link #KNOWN_CAPABILITY_MASK}.
     * The July Wired Menu, inspection, logs, settings and variable views are
     * implemented end-to-end. Chests, chest Wired, contracts and the chest menu
     * are advertised as one dependency-safe family. Creator Tools remain dark
     * because they are not part of the July AIR surface. Wired movement keeps
     * the established local 7115 transport while this bit advertises the
     * completed July add-on runtime layered on top of it.</p>
     */
    public static final int FUNCTIONAL_ROOM_CAPABILITY_MASK = CAPABILITY_ADDONS
            | CAPABILITY_VARIABLES
            | CAPABILITY_VARIABLE_SYNC
            | CAPABILITY_SIGNALS
            | CAPABILITY_REMOTE_SELECTOR
            | CAPABILITY_WIRED_MENU
            | CAPABILITY_MENU_INSPECTION
            | CAPABILITY_MENU_LOGS
            | CAPABILITY_MENU_SETTINGS
            | CAPABILITY_MENU_VARIABLES
            | CAPABILITY_MENU_CHESTS
            | CAPABILITY_ENVIRONMENT_V2
            | CAPABILITY_CLICK_SETTINGS_V2
            | CAPABILITY_WIRED_MOVEMENTS
            | CAPABILITY_CHESTS
            | CAPABILITY_CHEST_WIRED
            | CAPABILITY_CONTRACTS;

    /** Protocol plus every currently functional service accepted by the client. */
    public static final int COMPILED_CONNECTION_CAPABILITY_MASK = CAPABILITY_PROTOCOL
            | FUNCTIONAL_ROOM_CAPABILITY_MASK;

    /** Services safe to execute in a room after a successful room negotiation. */
    public static final int COMPILED_ROOM_CAPABILITY_MASK = FUNCTIONAL_ROOM_CAPABILITY_MASK;

    private WiredCapabilityService() {
    }

    /**
     * Server-global readiness check for room runtime features.
     *
     * <p>This deliberately does not inspect an individual client. Room Wired
     * execution can be triggered without the room owner being the initiating
     * client, so runtime modifiers must follow the server's fail-closed master
     * switch and the same compiled masks used by negotiation.</p>
     */
    public static boolean isRoomCapabilityReady(int capability) {
        boolean masterEnabled = Emulator.getConfig() != null
                && Emulator.getConfig().getBoolean(CONFIG_ENABLED, false);
        return isRoomCapabilityReady(
                masterEnabled,
                capability,
                COMPILED_CONNECTION_CAPABILITY_MASK,
                COMPILED_ROOM_CAPABILITY_MASK);
    }

    static boolean isRoomCapabilityReady(
            boolean masterEnabled,
            int capability,
            int compiledConnectionCapabilityMask,
            int compiledRoomCapabilityMask) {
        if (!masterEnabled
                || capability <= 0
                || (capability & ~KNOWN_CAPABILITY_MASK) != 0
                || (compiledConnectionCapabilityMask & CAPABILITY_PROTOCOL) == 0) {
            return false;
        }

        int readyRoomMask = applyDependencies(
                compiledConnectionCapabilityMask
                        & compiledRoomCapabilityMask
                        & KNOWN_CAPABILITY_MASK);
        return (readyRoomMask & capability) == capability;
    }

    public static WiredCapabilityState negotiate(
            boolean masterEnabled,
            int protocolRevision,
            int clientCapabilityMask,
            int validatedRoomId) {
        return negotiate(masterEnabled, protocolRevision, clientCapabilityMask,
                validatedRoomId, COMPILED_ROOM_CAPABILITY_MASK);
    }

    /**
     * Negotiates a room capability state using the services actually healthy
     * for that room.  Variable persistence is the only current room-scoped
     * dependency: a failed manager must not expose Variable or Variable Sync
     * editors to a client even when the server has those features compiled.
     */
    public static WiredCapabilityState negotiate(
            boolean masterEnabled,
            int protocolRevision,
            int clientCapabilityMask,
            int validatedRoomId,
            int availableRoomCapabilityMask) {
        if (!masterEnabled
                || protocolRevision != SERVER_PROTOCOL_REVISION
                || clientCapabilityMask < 0
                || (clientCapabilityMask & CAPABILITY_PROTOCOL) == 0) {
            return WiredCapabilityState.unsupported();
        }

        int sanitizedClientCapabilityMask = applyDependencies(clientCapabilityMask & KNOWN_CAPABILITY_MASK);
        int negotiatedRevision = SERVER_PROTOCOL_REVISION;
        int connectionCapabilityMask = applyDependencies(
                sanitizedClientCapabilityMask & COMPILED_CONNECTION_CAPABILITY_MASK);
        int roomCapabilityMask = validatedRoomId > 0
                ? applyDependencies(sanitizedClientCapabilityMask
                        & availableRoomCapabilityMask
                        & COMPILED_ROOM_CAPABILITY_MASK
                        & connectionCapabilityMask)
                : 0;

        return new WiredCapabilityState(
                negotiatedRevision,
                sanitizedClientCapabilityMask,
                connectionCapabilityMask,
                Math.max(0, validatedRoomId),
                roomCapabilityMask);
    }

    public static int availableRoomCapabilityMask(boolean variableServiceOperational) {
        int available = COMPILED_ROOM_CAPABILITY_MASK;
        if (!variableServiceOperational) {
            available &= ~(CAPABILITY_VARIABLES | CAPABILITY_VARIABLE_SYNC);
        }
        return applyDependencies(available);
    }

    static int applyDependencies(int capabilityMask) {
        int sanitized = capabilityMask & KNOWN_CAPABILITY_MASK;
        if ((sanitized & CAPABILITY_VARIABLES) == 0) {
            sanitized &= ~(CAPABILITY_VARIABLE_SYNC | CAPABILITY_MENU_VARIABLES);
        }
        if ((sanitized & CAPABILITY_WIRED_MENU) == 0) {
            sanitized &= ~(CAPABILITY_MENU_INSPECTION
                    | CAPABILITY_MENU_LOGS
                    | CAPABILITY_MENU_SETTINGS
                    | CAPABILITY_MENU_VARIABLES
                    | CAPABILITY_MENU_CHESTS);
        }
        if ((sanitized & CAPABILITY_CHESTS) == 0) {
            sanitized &= ~(CAPABILITY_MENU_CHESTS
                    | CAPABILITY_CHEST_WIRED
                    | CAPABILITY_CONTRACTS);
        }
        if ((sanitized & CAPABILITY_CHEST_WIRED) == 0) {
            sanitized &= ~CAPABILITY_CONTRACTS;
        }
        if ((sanitized & CAPABILITY_ENVIRONMENT_V2) == 0) {
            sanitized &= ~CAPABILITY_CLICK_SETTINGS_V2;
        }
        return sanitized;
    }
}
