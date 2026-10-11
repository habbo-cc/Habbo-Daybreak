package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class BattleBallGameLocationComposer extends MessageComposer {
    private final BattleBallGame game; private final boolean spectator;
    public BattleBallGameLocationComposer(BattleBallGame game, boolean spectator) { this.game = game; this.spectator = spectator; }
    @Override protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattleBallGameLocationComposer); this.response.appendInt(this.game.id());
        this.response.appendByte(this.game.map().id()); this.response.appendBoolean(this.spectator); return this.response;
    }
}
