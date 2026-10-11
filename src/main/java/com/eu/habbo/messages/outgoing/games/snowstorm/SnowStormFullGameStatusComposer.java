package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Serializes one immutable, lock-consistent recovery snapshot. */
public final class SnowStormFullGameStatusComposer extends MessageComposer {
    private final SnowStormGame.FullStatusSnapshot snapshot;

    public SnowStormFullGameStatusComposer(SnowStormGame.FullStatusSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("SnowStorm snapshot is required");
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormFullGameStatusComposer);
        response.appendInt(SnowStormProtocol.VERSION);
        response.appendInt(snapshot.instanceId());
        response.appendInt(snapshot.sequence());
        response.appendByte(snapshot.state().id());
        response.appendInt(snapshot.roundNumber());
        response.appendInt(snapshot.turnNumber());
        response.appendInt(snapshot.timeRemainingMs());
        response.appendInt(snapshot.playerObjectId());
        response.appendBoolean(snapshot.ranked());
        response.appendByte(snapshot.teamCount());
        SnowStormWire.writeScores(response, snapshot.teamScores());
        SnowStormWire.writeObjects(response, snapshot.roster(), snapshot.objects());
        response.appendInt(snapshot.checksum());
        return response;
    }
}
