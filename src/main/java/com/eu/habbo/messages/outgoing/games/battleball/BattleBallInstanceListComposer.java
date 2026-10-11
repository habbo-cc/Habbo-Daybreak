package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallInstanceSummary;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public final class BattleBallInstanceListComposer extends MessageComposer {
    private final List<BattleBallInstanceSummary> games;
    public BattleBallInstanceListComposer(List<BattleBallInstanceSummary> games) { this.games = games; }
    @Override protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallInstanceListComposer); this.response.appendShort(this.games.size());
        this.games.forEach(game -> BattleBallWire.writeInstance(this.response, game)); return this.response;
    }
}
