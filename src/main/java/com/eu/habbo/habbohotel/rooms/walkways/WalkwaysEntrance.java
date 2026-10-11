package com.eu.habbo.habbohotel.rooms.walkways;

import java.util.List;

/**
 * A public-room walkway: stepping on any of {@link #fromCoords} in room {@link #roomId} redirects
 * the user into room {@link #targetRoomId}, arriving at {@link #destination} (or the target room's
 * door when destination is null). Ported from Havana's public_roomwalkways flow.
 */
public class WalkwaysEntrance {
    private final int roomId;
    private final int targetRoomId;
    private final List<int[]> fromCoords; // each entry {x, y}
    private final int[] destination;      // {x, y, z, rotation} or null

    public WalkwaysEntrance(int roomId, int targetRoomId, List<int[]> fromCoords, int[] destination) {
        this.roomId = roomId;
        this.targetRoomId = targetRoomId;
        this.fromCoords = fromCoords;
        this.destination = destination;
    }

    public int getRoomId() {
        return this.roomId;
    }

    public int getTargetRoomId() {
        return this.targetRoomId;
    }

    public List<int[]> getFromCoords() {
        return this.fromCoords;
    }

    public int[] getDestination() {
        return this.destination;
    }

    public boolean hasDestination() {
        return this.destination != null;
    }

    public boolean matches(int x, int y) {
        for (int[] coord : this.fromCoords) {
            if (coord[0] == x && coord[1] == y) {
                return true;
            }
        }
        return false;
    }
}
