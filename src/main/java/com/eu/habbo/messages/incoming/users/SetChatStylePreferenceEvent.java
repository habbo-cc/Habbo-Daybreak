package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SetChatStylePreferenceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatBubble = this.packet.readInt();
        int chatSizePreference = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : this.client.getHabbo().getHabboStats().chatSizePreference;
        RoomChatMessageBubbles bubble = RoomChatMessageBubbles.getBubble(chatBubble);

        if (chatBubble != RoomChatMessageBubbles.NORMAL.getType() && bubble == RoomChatMessageBubbles.NORMAL) {
            return;
        }

        if (!this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            for (String s : Emulator.getConfig().getValue("commands.cmd_chatcolor.banned_numbers").split(";")) {
                if (Integer.parseInt(s) == chatBubble) {
                    return;
                }
            }

            if (Emulator.getGameEnvironment().getCatalogManager().isPurchasableChatStyle(chatBubble)
                    && !Emulator.getGameEnvironment().getCatalogManager().habboOwnsChatStyle(this.client.getHabbo(), chatBubble)) {
                return;
            }
        }

        this.client.getHabbo().getHabboStats().chatColor = bubble;
        this.client.getHabbo().getHabboStats().chatSizePreference = Math.max(0, Math.min(4, chatSizePreference));
        this.client.getHabbo().getHabboStats().run();
    }
}
