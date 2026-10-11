package com.eu.habbo.messages.outgoing.games.gamehall;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Tells a seated gamehall player that their station's board has opened. The client uses this to
 * build the themed game window for the given game type and to learn which seat it occupies.
 */
public class OpenGameBoardMessageComposer extends MessageComposer {
    private final int stationId;
    private final String gameType;
    private final int localSeat;
    private final int seatCount;

    public OpenGameBoardMessageComposer(int stationId, String gameType, int localSeat, int seatCount) {
        this.stationId = stationId;
        this.gameType = gameType;
        this.localSeat = localSeat;
        this.seatCount = seatCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.OpenGameBoardMessageComposer);
        this.response.appendInt(this.stationId);
        this.response.appendString(this.gameType);
        this.response.appendInt(this.localSeat);
        this.response.appendInt(this.seatCount);
        return this.response;
    }
}
