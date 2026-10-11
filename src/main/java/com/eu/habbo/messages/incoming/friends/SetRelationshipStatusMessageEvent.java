package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendListUpdateMessageComposer;
import com.eu.habbo.plugin.events.users.friends.UserRelationShipEvent;

public class SetRelationshipStatusMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int relationId = this.packet.readInt();

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriends().get(userId);
        if (buddy != null && relationId >= 0 && relationId <= 3) {
            short oldRelation = buddy.getRelation();
            UserRelationShipEvent event = new UserRelationShipEvent(this.client.getHabbo(), buddy, relationId);
            Emulator.getPluginManager().fireEvent(event);
            if (!event.isCancelled()) {
                buddy.setRelation(event.relationShip);
                this.client.sendResponse(new FriendListUpdateMessageComposer(this.client.getHabbo(), buddy, 0));
                if (oldRelation != buddy.getRelation() && Emulator.getGameEnvironment().getRewardTrackManager() != null) {
                    Emulator.getGameEnvironment().getRewardTrackManager().progress(this.client.getHabbo(), "set_relationship_status");
                }
            }
        }
    }
}
