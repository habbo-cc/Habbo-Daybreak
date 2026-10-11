package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendListUpdateMessageComposer;

import java.util.List;

/**
 * The client's periodic friend list refresh, sent every two minutes with an empty body.
 *
 * Status pushes cover most changes, but not all of them: a friend who changes their figure or motto
 * without moving rooms notifies nobody, so their entry goes stale in every other list until they
 * next log in or enter a room. This poll is what the original server used to close that gap, and
 * going unanswered is why those entries never caught up.
 *
 * Answers with the friends that actually moved since the client was last told, so a quiet list
 * costs no packet at all.
 */
public class FriendListUpdateMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || this.client.getHabbo().getMessenger() == null) {
            return;
        }

        List<MessengerBuddy> changed = this.client.getHabbo().getMessenger().refreshFriends();

        if (!changed.isEmpty()) {
            this.client.sendResponse(new FriendListUpdateMessageComposer(this.client.getHabbo(), changed, 0));
        }
    }
}
