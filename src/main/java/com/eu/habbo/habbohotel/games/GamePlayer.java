package com.eu.habbo.habbohotel.games;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class GamePlayer {

    private final Habbo habbo;


    private GameTeamColors teamColor;


    private int score;
    private int wiredScore;


    public GamePlayer(Habbo habbo, GameTeamColors teamColor) {
        this.habbo = habbo;
        this.teamColor = teamColor;
    }


    public void reset() {
        this.score = 0;
        this.wiredScore = 0;
    }

    public void addScore(int amount) {
        addScore(amount, false);
    }

    public void addScore(int amount, boolean isWired) {
        Room room = this.habbo.getHabboInfo().getCurrentRoom();
        if (room == null || this.habbo.getHabboInfo().getGamePlayer() != this
                || this.habbo.getHabboInfo().getCurrentGame() == null) {
            return;
        }
        Game game = room.getGame(this.habbo.getHabboInfo().getCurrentGame());
        GameTeam team = game == null ? null : game.getTeamForHabbo(this.habbo);
        if (team == null) {
            return;
        }
        int previous, current;
        synchronized (team) {
            previous = team.getTotalScore();
            this.score += amount;

            if (this.score < 0) this.score = 0;

            if(isWired && this.score > 0) {
                this.wiredScore += amount;
            }

            current = team.getTotalScore();
        }
        WiredManager.triggerScoreAchieved(room, this.habbo.getRoomUnit(), team.teamColor, previous, current);
    }

    public Habbo getHabbo() {
        return this.habbo;
    }


    public GameTeamColors getTeamColor() {
        return this.teamColor;
    }


    public int getScore() {
        return this.score;
    }

    public int getScoreAchievementValue() {
        return this.score - this.wiredScore;
    }
}
