package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Map;

public final class BattleBallGameEndComposer extends MessageComposer {
    private final BattleBallGame game;
    public BattleBallGameEndComposer(BattleBallGame game) { this.game = game; }
    @Override protected ServerMessage composeInternal() {
        Map<Integer,Integer> scores = this.game.scores(); int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        long winners = scores.values().stream().filter(score -> score == best).count();
        int winner = !this.game.roundAborted() && winners == 1
                ? scores.entrySet().stream().filter(entry -> entry.getValue() == best).findFirst().orElseThrow().getKey() : -1;
        this.response.init(Outgoing.BattleBallGameEndComposer); this.response.appendInt(this.game.id()); this.response.appendInt(this.game.sequence());
        this.response.appendByte(winner);
        this.response.appendInt(this.game.timeRemainingMs(System.currentTimeMillis()));
        BattleBallWire.writeScores(this.response, scores);
        // Team totals alone cannot populate the original final-standings rows or its Best Player panel.
        // Include every match-local player score, including bots; persistence still excludes bot ids.
        BattleBallWire.writePlayerScores(this.response, this.game.playerScores());
        return this.response;
    }
}
