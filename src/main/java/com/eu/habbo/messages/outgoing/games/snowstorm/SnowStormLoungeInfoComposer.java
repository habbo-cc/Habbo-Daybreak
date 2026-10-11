package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public final class SnowStormLoungeInfoComposer extends MessageComposer {
    private final int ticketCost, skillLevel, rankedPoints, ticketBalance;
    private final List<Integer> mapIds;
    public SnowStormLoungeInfoComposer(int ticketCost, int skillLevel, int rankedPoints,
                                      int ticketBalance, List<Integer> mapIds) {
        this.ticketCost=ticketCost; this.skillLevel=skillLevel; this.rankedPoints=rankedPoints;
        this.ticketBalance=ticketBalance; this.mapIds=List.copyOf(mapIds);
    }
    @Override protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormLoungeInfoComposer);
        response.appendInt(SnowStormProtocol.VERSION);
        response.appendInt(ticketCost); response.appendInt(skillLevel); response.appendInt(rankedPoints);
        response.appendInt(Math.max(0,ticketBalance)); response.appendByte(mapIds.size());
        mapIds.forEach(response::appendByte); return response;
    }
}
