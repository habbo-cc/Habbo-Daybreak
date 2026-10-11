package com.eu.habbo.messages.incoming.games.leaderboards;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.leaderboards.LeaderboardData;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.games.leaderboards.OpenLeaderboardMessageComposer;

public final class RequestLeaderboardMessageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        Room room = habbo == null ? null : habbo.getHabboInfo().getCurrentRoom();
        if (room == null || Emulator.getGameEnvironment() == null
                || Emulator.getGameEnvironment().getLeaderboardManager() == null) {
            return;
        }

        int leaderboardId = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 0;
        String period = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "WEEKLY";
        int offset = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 0;
        int limit = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 10;

        LeaderboardData data = Emulator.getGameEnvironment().getLeaderboardManager()
                .getLeaderboard(room.getId(), leaderboardId, period, offset, limit, habbo);
        if (data != null) {
            this.client.sendResponse(new OpenLeaderboardMessageComposer(data));
        }
    }
}
