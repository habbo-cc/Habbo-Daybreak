package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class UpdateRewardTrackCommand extends Command {
    public UpdateRewardTrackCommand() {
        super("cmd_update_rewardtrack", Emulator.getTexts().getValue("commands.keys.cmd_update_rewardtrack").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        RewardTrackManager rewardTrackManager = Emulator.getGameEnvironment().getRewardTrackManager();
        rewardTrackManager.reload();

        int pushedUsers = 0;
        for (GameClient client : Emulator.getGameServer().getGameClientManager().getSessions().values()) {
            Habbo habbo = client.getHabbo();
            if (habbo == null || habbo.getClient() == null) {
                continue;
            }

            rewardTrackManager.sendFullState(habbo, true);
            pushedUsers++;
        }

        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_update_rewardtrack")
                        .replace("%tracks%", String.valueOf(rewardTrackManager.getTracks().size()))
                        .replace("%users%", String.valueOf(pushedUsers)),
                RoomChatMessageBubbles.ALERT);

        return true;
    }
}
