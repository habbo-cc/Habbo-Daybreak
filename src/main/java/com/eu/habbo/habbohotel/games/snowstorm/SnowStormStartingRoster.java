package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.List;

/** Immutable accounting and simulation roster frozen immediately before an atomic ticket charge. */
public record SnowStormStartingRoster(
        int instanceId,
        int roundNumber,
        boolean ranked,
        List<Player> players) {

    public SnowStormStartingRoster {
        if (instanceId <= 0 || roundNumber <= 0 || players.isEmpty() || players.size() > 12) {
            throw new IllegalArgumentException("Invalid SnowStorm starting roster");
        }
        players = List.copyOf(players);
    }

    public record Player(
            int objectId,
            int userId,
            String name,
            String figure,
            String sex,
            int teamId,
            boolean bot,
            SnowStormTile spawn) {
        public Player {
            if (objectId <= 0 || userId == 0 || bot != (userId < 0)
                    || name == null || figure == null || sex == null
                    || teamId < 0 || teamId > 3 || spawn == null) {
                throw new IllegalArgumentException("Invalid SnowStorm starting player");
            }
        }
    }
}
