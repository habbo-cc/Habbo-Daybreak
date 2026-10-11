package com.eu.habbo.messages.incoming.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.habbicons.HabbiconManager;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconInstantMessageComposer;

public class SendHabbiconInstantMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatId = this.packet.readInt();
        int habbiconId = this.packet.readInt();
        int confirmationId = this.packet.readInt();

        if (!this.client.getHabbo().getHabboStats().allowTalk()) {
            return;
        }

        long millis = System.currentTimeMillis();
        if (Messenger.CHAT_DELAY_MILLIS > 0
                && millis - this.client.getHabbo().getHabboStats().lastMessengerChat < Messenger.CHAT_DELAY_MILLIS) {
            return;
        }
        this.client.getHabbo().getHabboStats().lastMessengerChat = millis;

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriend(chatId);
        if (buddy == null) {
            return;
        }

        HabbiconManager manager = Emulator.getGameEnvironment().getHabbiconManager();
        if (manager == null || !manager.useInMessenger(this.client.getHabbo(), habbiconId)) {
            return;
        }

        Habbo receiver = Emulator.getGameServer().getGameClientManager().getHabbo(buddy.getId());
        if (receiver == null || receiver.getClient() == null) {
            return;
        }

        Message message = new Message(this.client.getHabbo().getHabboInfo().getId(), receiver.getHabboInfo().getId(), habbiconId, confirmationId);
        Emulator.getThreading().run(message);

        receiver.getClient().sendResponse(new HabbiconInstantMessageComposer(this.client.getHabbo().getHabboInfo().getId(), message, this.client.getHabbo()));
        this.client.sendResponse(new HabbiconInstantMessageComposer(receiver.getHabboInfo().getId(), message, this.client.getHabbo()));

        if (Emulator.getGameEnvironment().getRewardTrackManager() != null) {
            Emulator.getGameEnvironment().getRewardTrackManager().progress(this.client.getHabbo(), "send_messenger_message");
        }
    }
}
