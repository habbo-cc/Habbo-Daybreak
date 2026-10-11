package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.RemoveFriendComposer;
import gnu.trove.list.array.TIntArrayList;

public class RemoveFriendMessageEvent extends MessageHandler {

    private final TIntArrayList removedFriends;

    public RemoveFriendMessageEvent() {
        this.removedFriends = new TIntArrayList();
    }

    @Override
    public void handle() throws Exception {
        int count = this.packet.readBoundedCount(500, Integer.BYTES);
        for (int i = 0; i < count; i++) {
            int habboId = this.packet.readRequiredInt();
            this.removedFriends.add(habboId);

            Messenger.unfriend(this.client.getHabbo().getHabboInfo().getId(), habboId);
            this.client.getHabbo().getMessenger().removeBuddy(habboId);

            Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(habboId);

            if (habbo != null) {
                habbo.getMessenger().removeBuddy(this.client.getHabbo());
                habbo.getClient().sendResponse(new RemoveFriendComposer(this.client.getHabbo().getHabboInfo().getId()));
            }
        }

        this.client.sendResponse(new RemoveFriendComposer(this.removedFriends));
    }
}
