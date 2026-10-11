package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class BattleBallGameStartComposer extends MessageComposer {
    private final BattleBallGame game;
    public BattleBallGameStartComposer(BattleBallGame game) { this.game = game; }
    @Override protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallGameStartComposer); this.response.appendInt(this.game.id()); this.response.appendInt(this.game.sequence());
        this.response.appendShort(3000); this.response.appendShort(this.game.roundSeconds()); return this.response;
    }
}
