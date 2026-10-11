package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormStartingRoster;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormGameLocationComposer extends MessageComposer {
    private final SnowStormGame game;
    private final int userId;
    private final boolean spectator;

    public SnowStormGameLocationComposer(SnowStormGame game, int userId, boolean spectator) {
        this.game = game;
        this.userId = userId;
        this.spectator = spectator;
    }

    @Override
    protected ServerMessage composeInternal() {
        SnowStormStartingRoster roster = game.startingRoster();
        int objectId = -1;
        if (!spectator && roster != null) {
            objectId = roster.players().stream()
                    .filter(player -> player.userId() == userId)
                    .mapToInt(SnowStormStartingRoster.Player::objectId)
                    .findFirst()
                    .orElse(-1);
        }
        response.init(Outgoing.ShockwaveSnowStormGameLocationComposer);
        response.appendInt(game.id());
        response.appendByte(game.map().mapId());
        response.appendBoolean(spectator);
        response.appendInt(objectId);
        response.appendInt(game.map().revision());
        response.appendInt(game.map().checksum());
        return response;
    }
}
