package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallUpdate;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class BattleBallFullGameStatusComposer extends MessageComposer {
    private final BattleBallGame game; private final BattleBallUpdate update;
    public BattleBallFullGameStatusComposer(BattleBallGame game, BattleBallUpdate update) { this.game = game; this.update = update; }
    @Override protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallFullGameStatusComposer); this.response.appendInt(BattleBallWire.STATUS_VERSION); this.response.appendInt(this.game.id());
        this.response.appendInt(this.update.sequence()); this.response.appendInt(this.update.state().id()); this.response.appendInt((int) this.update.timeRemainingMs());
        BattleBallWire.writeFullObjects(this.response, this.update);
        this.response.appendInt(this.game.map().width()); this.response.appendInt(this.game.map().height());
        BattleBallWire.writeFullTiles(this.response, this.update.priorityTiles(), this.game.map().width(), this.game.map().height());
        BattleBallWire.writeScores(this.response, this.update.scores()); return this.response;
    }
}
