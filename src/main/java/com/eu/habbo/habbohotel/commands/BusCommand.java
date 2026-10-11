package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.infobus.InfobusManager;

/**
 * :bus open / :bus close — staff toggle for the Infobus (The Park) doors. The bus boots closed, so
 * staff open it to let users board and close it to end the event. Closing only blocks new boardings;
 * users already inside the bus are left alone.
 */
public class BusCommand extends Command {
    public BusCommand() {
        super("cmd_bus", Emulator.getTexts().getValue("commands.keys.cmd_bus").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_bus"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        switch (params[1].toLowerCase()) {
            case "open":
                InfobusManager.setDoorOpen(true);
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_bus.opened"), RoomChatMessageBubbles.ALERT);
                break;
            case "close":
                InfobusManager.setDoorOpen(false);
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_bus.closed"), RoomChatMessageBubbles.ALERT);
                break;
            default:
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_bus"), RoomChatMessageBubbles.ALERT);
                break;
        }

        return true;
    }
}
