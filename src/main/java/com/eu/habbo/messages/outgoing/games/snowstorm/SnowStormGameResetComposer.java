package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormGameResetComposer extends MessageComposer {
    private final SnowStormGame game;

    public SnowStormGameResetComposer(SnowStormGame game) { this.game = game; }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormGameResetComposer);
        response.appendInt(game.id());
        response.appendInt(game.sequence());
        response.appendInt(game.roundNumber());
        return response;
    }
}
