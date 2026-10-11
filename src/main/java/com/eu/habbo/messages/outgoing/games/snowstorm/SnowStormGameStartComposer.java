package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormStartingRoster;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormGameStartComposer extends MessageComposer {
    private final SnowStormGame game;

    public SnowStormGameStartComposer(SnowStormGame game) {
        this.game = game;
    }

    @Override
    protected ServerMessage composeInternal() {
        SnowStormStartingRoster roster = game.startingRoster();
        response.init(Outgoing.ShockwaveSnowStormGameStartComposer);
        response.appendInt(game.id());
        response.appendInt(game.sequence());
        response.appendInt(game.roundNumber());
        response.appendShort(SnowStormGame.COUNTDOWN_MS);
        response.appendShort(game.roundSeconds());
        response.appendBoolean(roster != null && roster.ranked());
        return response;
    }
}
