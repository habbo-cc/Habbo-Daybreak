package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.RoomInviteMessageComposer;
import com.eu.habbo.plugin.events.users.friends.UserInviteFriendEvent;

public class SendRoomInviteMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboStats().allowTalk()) {
            int[] userIds = new int[this.packet.readBoundedCount(300, Integer.BYTES)];

            for (int i = 0; i < userIds.length; i++) {
                userIds[i] = this.packet.readRequiredInt();
            }

            String message = this.packet.readString();

            message = Emulator.getGameEnvironment().getWordFilter().filter(message, this.client.getHabbo());

            boolean sentInvite = false;
            for (int i : userIds) {
                if (i == 0)
                    continue;

                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(i);

                if (habbo != null) {
                    if (!habbo.getHabboStats().blockRoomInvites) {
                        habbo.getClient().sendResponse(new RoomInviteMessageComposer(this.client.getHabbo().getHabboInfo().getId(), message));
                        sentInvite = true;
                    }
                }
            }
            Emulator.getPluginManager().fireEvent(new UserInviteFriendEvent(this.client.getHabbo()));
            if (sentInvite && Emulator.getGameEnvironment().getRewardTrackManager() != null) {
                Emulator.getGameEnvironment().getRewardTrackManager().progress(this.client.getHabbo(), "send_messenger_invite");
            }
        }
    }
}
