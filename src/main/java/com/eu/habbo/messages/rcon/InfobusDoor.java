package com.eu.habbo.messages.rcon;

import com.eu.habbo.habbohotel.rooms.infobus.InfobusManager;
import com.google.gson.Gson;

/**
 * RCON: open or close the Infobus (The Park) doors from the website / housekeeping.
 * Payload: { "open": true } to open the doors, { "open": false } to close them.
 *
 * Mirrors the in-game :bus command. The door state is in-memory, so it resets to closed on the next
 * emulator restart and must be re-opened.
 */
public class InfobusDoor extends RCONMessage<InfobusDoor.JSONInfobusDoor> {

    public InfobusDoor() {
        super(JSONInfobusDoor.class);
    }

    @Override
    public void handle(Gson gson, JSONInfobusDoor object) {
        InfobusManager.setDoorOpen(object.open);
        this.message = object.open ? "The Infobus doors are now open." : "The Infobus doors are now closed.";
    }

    static class JSONInfobusDoor {
        public boolean open;
    }
}
