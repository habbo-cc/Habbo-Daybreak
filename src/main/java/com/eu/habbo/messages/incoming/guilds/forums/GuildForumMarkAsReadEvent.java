package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class GuildForumMarkAsReadEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int count = this.packet.readBoundedCount(500, Integer.BYTES * 2 + 1);
        int userId = this.client.getHabbo().getHabboInfo().getId();

        for (int i = 0; i < count; i++) {
            int guildId = this.packet.readRequiredInt();
            this.packet.readRequiredInt(); // Last-read message marker; timestamps are used server-side.
            this.packet.readRequiredBoolean(); // True when the client marks an entire forum as read.

            Emulator.getGameEnvironment().getGuildManager().addView(userId, guildId);
        }
    }
}
