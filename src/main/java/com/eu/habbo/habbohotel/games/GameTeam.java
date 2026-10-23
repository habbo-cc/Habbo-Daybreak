package com.eu.habbo.habbohotel.games;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import gnu.trove.set.hash.THashSet;

public class GameTeam {

    public final GameTeamColors teamColor;
    private final THashSet<GamePlayer> members;
    private int teamScore;
    private Room room;


    public GameTeam(GameTeamColors teamColor) {
        this.teamColor = teamColor;

        this.members = new THashSet<>();
    }


    public synchronized void initialise() {
        for (GamePlayer player : this.members) {
            player.reset();
        }

        this.teamScore = 0;
    }


    public synchronized void reset() {
        this.members.clear();
    }


    public void addTeamScore(int teamScore) {
        addTeamScore(teamScore, null);
    }

    public void addTeamScore(int amount, RoomUnit actor) {
        int previous, current;
        synchronized (this) {
            previous = getTotalScore();
            this.teamScore += amount;
            current = getTotalScore();
        }
        // Dispatch outside the score lock: Wired effects can award more points.
        if (room != null) {
            WiredManager.triggerScoreAchieved(room, actor, teamColor, previous, current);
        }
    }

    void setRoom(Room room) {
        this.room = room;
    }


    public synchronized int getTeamScore() {
        return this.teamScore;
    }


    public synchronized int getTotalScore() {
        int score = this.teamScore;

        for (GamePlayer player : this.members) {
            score += player.getScore();
        }

        return score;
    }


    public synchronized void addMember(GamePlayer gamePlayer) {
        this.members.add(gamePlayer);
    }


    public synchronized void removeMember(GamePlayer gamePlayer) {
        this.members.remove(gamePlayer);
    }

    public synchronized void clearMembers() {
        for (GamePlayer player : this.members) {
            if (player == null || player.getHabbo() == null) continue;

            if (player.getHabbo().getHabboInfo().getGamePlayer() != null) player.getHabbo().getHabboInfo().getGamePlayer().reset();
            player.getHabbo().getHabboInfo().setCurrentGame(null);
            player.getHabbo().getHabboInfo().setGamePlayer(null);
        }

        this.members.clear();
    }

    public synchronized void resetScores() {
        for (GamePlayer player : this.members) {
            if (player == null) continue;

            player.reset();
        }

        this.teamScore = 0;
    }


    public THashSet<GamePlayer> getMembers() {
        return this.members;
    }


    public synchronized boolean isMember(Habbo habbo) {
        for (GamePlayer p : this.members) {
            if (p.getHabbo().equals(habbo)) {
                return true;
            }
        }

        return false;
    }


    @Deprecated
    public GamePlayer getPlayerForHabbo(Habbo habbo) {
        for (GamePlayer p : this.members) {
            if (p.getHabbo().equals(habbo)) {
                return p;
            }
        }

        return null;
    }
}
