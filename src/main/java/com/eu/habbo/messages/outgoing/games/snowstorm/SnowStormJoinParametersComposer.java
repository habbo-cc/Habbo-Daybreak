package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormPlayer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Comparator;
import java.util.List;

/** Complete waiting-roster snapshot for the original team/game-info screen. */
public final class SnowStormJoinParametersComposer extends MessageComposer {
    private final SnowStormGame game;
    private final int selectedTeam;

    public SnowStormJoinParametersComposer(SnowStormGame game, int selectedTeam) {
        this.game = game;
        this.selectedTeam = selectedTeam;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ShockwaveSnowStormJoinParametersComposer);
        response.appendInt(game.id());
        response.appendByte(game.teamCount());
        response.appendByte(selectedTeam);
        for (int team = 0; team < game.teamCount(); team++) {
            final int teamId = team;
            List<SnowStormPlayer> players = game.players().stream()
                    .filter(player -> player.teamId() == teamId)
                    .sorted(Comparator.comparingInt(SnowStormPlayer::userId))
                    .toList();
            response.appendByte(players.size());
            for (SnowStormPlayer player : players) {
                response.appendInt(player.userId());
                response.appendString(player.name());
            }
        }
        return response;
    }
}
