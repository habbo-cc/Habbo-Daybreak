package com.eu.habbo.habbohotel.leaderboards;

public record LeaderboardRow(
        int userId,
        int rank,
        String username,
        String figure,
        String gender,
        int value,
        boolean own) {

    public LeaderboardRow {
        username = username == null ? "" : username;
        figure = figure == null ? "" : figure;
        gender = gender == null ? "" : gender;
    }
}
