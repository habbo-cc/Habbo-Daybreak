package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallFinishedGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallInstanceSummary;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class BattleBallGameInstanceComposer extends MessageComposer {
    private final BattleBallGame game;
    private final BattleBallFinishedGame finished;
    public BattleBallGameInstanceComposer(BattleBallGame game) { this.game = game; this.finished = null; }
    public BattleBallGameInstanceComposer(BattleBallFinishedGame finished) { this.game = null; this.finished = finished; }
    @Override protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallGameInstanceComposer);
        if (this.finished == null) BattleBallWire.writeInstance(this.response, this.game);
        else {
            BattleBallWire.writeInstance(this.response, BattleBallInstanceSummary.finished(this.finished));
            BattleBallWire.writeFinishedResult(this.response, this.finished);
        }
        return this.response;
    }
}
