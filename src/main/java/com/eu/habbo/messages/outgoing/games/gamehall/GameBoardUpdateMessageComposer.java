package com.eu.habbo.messages.outgoing.games.gamehall;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Broadcasts a board delta to a station's seated players. The {@code verb} names the per-game
 * action (BOARDDATA, TURN, HIT, SINK, ...) and the {@code args} carry that verb's payload.
 */
public class GameBoardUpdateMessageComposer extends MessageComposer {
    private final int stationId;
    private final String verb;
    private final String[] args;

    public GameBoardUpdateMessageComposer(int stationId, String verb, String... args) {
        this.stationId = stationId;
        this.verb = verb;
        this.args = args;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GameBoardUpdateMessageComposer);
        this.response.appendInt(this.stationId);
        this.response.appendString(this.verb);
        this.response.appendInt(this.args.length);
        for (String arg : this.args) {
            this.response.appendString(arg);
        }
        return this.response;
    }
}
