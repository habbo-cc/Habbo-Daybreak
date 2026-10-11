package com.eu.habbo.habbohotel.rooms.infobus;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.rooms.BusDoorMessageComposer;

/**
 * Tracks whether the Infobus (The Park) doors are open. The bus starts CLOSED on every boot — a
 * staff member must run {@code :bus open} to let users board. State is in-memory only (intentionally
 * not persisted, so the bus is always closed again after a restart until staff open it).
 */
public class InfobusManager {

    private static final String PARK_MODEL = "park_a";

    // Doors start closed on boot; opened only by the :bus command.
    private static volatile boolean doorOpen = false;

    public static boolean isDoorOpen() {
        return doorOpen;
    }

    /**
     * Set the door state and push it live to everyone currently in the park so the bus art and the
     * boarding gate update immediately.
     */
    public static void setDoorOpen(boolean open) {
        doorOpen = open;
        broadcastDoorState();
    }

    private static void broadcastDoorState() {
        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            if (room.getLayout() != null && PARK_MODEL.equals(room.getLayout().getName())) {
                room.sendComposer(new BusDoorMessageComposer(doorOpen).compose());
            }
        }
    }
}
