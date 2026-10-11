package com.eu.habbo.habbohotel.games.battleball;

import java.util.List;
import java.util.Map;

public record BattleBallUpdate(
        int sequence,
        BattleBallState state,
        long timeRemainingMs,
        List<BattleBallPlayer> players,
        List<BattleBallPowerUp> powers,
        List<BattleBallPin> pins,
        List<BattleBallTile> priorityTiles,
        List<BattleBallTile> fillTiles,
        Map<Integer, Integer> scores,
        List<BattleBallEvent> events,
        boolean boardReset) {}
