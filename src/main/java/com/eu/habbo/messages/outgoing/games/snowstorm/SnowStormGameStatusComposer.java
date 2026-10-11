package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormSimulation;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class SnowStormGameStatusComposer extends MessageComposer {
    private final SnowStormGame game;
    private final SnowStormSimulation.TurnResult turn;
    private final int baseSequence;
    private final int sequence;
    private final int timeRemainingMs;

    public SnowStormGameStatusComposer(
            SnowStormGame game,
            SnowStormSimulation.TurnResult turn,
            int baseSequence,
            int sequence,
            int timeRemainingMs) {
        this.game = game;
        this.turn = turn;
        this.baseSequence = baseSequence;
        this.sequence = sequence;
        this.timeRemainingMs = timeRemainingMs;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormGameStatusComposer);
        response.appendInt(SnowStormProtocol.VERSION);
        response.appendInt(game.id());
        response.appendInt(baseSequence);
        response.appendInt(sequence);
        response.appendInt(turn.turnNumber());
        response.appendInt(timeRemainingMs);
        response.appendByte(game.state().id());
        response.appendInt(turn.startChecksum());
        SnowStormWire.writeTurn(response, turn);
        SnowStormWire.writeScores(response, turn.teamScores());
        return response;
    }
}
