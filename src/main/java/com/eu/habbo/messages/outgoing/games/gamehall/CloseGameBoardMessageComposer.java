package com.eu.habbo.messages.outgoing.games.gamehall;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Tells a seated gamehall player that their station's board has closed, with a {@code reason}
 * (e.g. game over, opponent left). The client tears down the game window on receipt.
 */
public class CloseGameBoardMessageComposer extends MessageComposer {
    private final int stationId;
    private final String reason;

    public CloseGameBoardMessageComposer(int stationId, String reason) {
        this.stationId = stationId;
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CloseGameBoardMessageComposer);
        this.response.appendInt(this.stationId);
        this.response.appendString(this.reason);
        return this.response;
    }
}
