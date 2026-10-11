package com.eu.habbo.habbohotel.games.snowstorm;

/** Waiting-roster member. Runtime object IDs are assigned only after the roster is frozen. */
public final class SnowStormPlayer {
    private final int userId;
    private final String name;
    private final String figure;
    private final String sex;
    private int teamId;
    private long lastActionAt;
    private boolean idleWarningSent;

    SnowStormPlayer(int userId, String name, String figure, String sex, int teamId) {
        if (userId == 0 || name == null || name.isBlank() || figure == null || sex == null) {
            throw new IllegalArgumentException("Invalid SnowStorm player");
        }
        this.userId = userId;
        this.name = name;
        this.figure = figure;
        this.sex = sex;
        this.teamId = teamId;
    }

    public int userId() { return userId; }
    public String name() { return name; }
    public String figure() { return figure; }
    public String sex() { return sex; }
    public int teamId() { return teamId; }
    /** Game-scoped bots use negative ids; real account ids are always positive. */
    public boolean isBot() { return userId < 0; }
    public long lastActionAt() { return lastActionAt; }
    public boolean idleWarningSent() { return idleWarningSent; }

    void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    void markActive(long now) {
        this.lastActionAt = now;
        this.idleWarningSent = false;
    }

    void setIdleWarningSent(boolean idleWarningSent) {
        this.idleWarningSent = idleWarningSent;
    }
}
