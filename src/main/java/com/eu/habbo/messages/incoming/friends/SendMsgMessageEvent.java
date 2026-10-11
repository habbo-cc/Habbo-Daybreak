package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.friends.UserFriendChatEvent;

public class SendMsgMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = this.packet.readString();

        if (!this.client.getHabbo().getHabboStats().allowTalk()) {
            return;
        }

        long millis = System.currentTimeMillis();
        if (Messenger.CHAT_DELAY_MILLIS > 0
                && millis - this.client.getHabbo().getHabboStats().lastMessengerChat < Messenger.CHAT_DELAY_MILLIS) {
            return;
        }
        this.client.getHabbo().getHabboStats().lastMessengerChat = millis;

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriend(userId);
        if (buddy == null)
            return;

        if (message.length() > 255) message = message.substring(0, 255);

        UserFriendChatEvent event = new UserFriendChatEvent(this.client.getHabbo(), buddy, message);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled())
            return;

        buddy.onMessageReceived(this.client.getHabbo(), message);
        if (Emulator.getGameEnvironment().getRewardTrackManager() != null) {
            Emulator.getGameEnvironment().getRewardTrackManager().progress(this.client.getHabbo(), "send_messenger_message");
        }
    }
}
