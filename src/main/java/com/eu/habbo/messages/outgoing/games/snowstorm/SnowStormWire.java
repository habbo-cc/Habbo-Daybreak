package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormInstanceSummary;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormSimulation;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormSimulationEvent;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormStartingRoster;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormFinishedGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormRoundResult;
import com.eu.habbo.messages.ServerMessage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SnowStormWire {
    private static final int OBJECT_TYPE_SNOWBALL = 1;

    private SnowStormWire() {}

    static void writeInstance(ServerMessage response, SnowStormInstanceSummary game) {
        response.appendByte(game.rowKind()); response.appendInt(game.id());
        response.appendString(game.name()); response.appendInt(game.ownerUserId());
        response.appendString(game.ownerName()); response.appendByte(game.mapId());
        response.appendByte(game.state()); response.appendByte(game.teamCount());
        response.appendByte(game.playerCount()); response.appendShort(game.spectatorCount());
        response.appendByte(game.lengthChoice()); response.appendInt(game.resultId());
        if (game.playerNames().size() > SnowStormGame.MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many SnowStorm lobby-row players");
        }
        response.appendByte(game.playerNames().size());
        game.playerNames().forEach(response::appendString);
    }

    static void writeScores(ServerMessage response, Map<Integer, Integer> scores) {
        if (scores.size() > 4) throw new IllegalArgumentException("Too many SnowStorm team scores");
        response.appendByte(scores.size());
        scores.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            response.appendByte(entry.getKey()); response.appendInt(entry.getValue());
        });
    }

    static void writeLiveResult(ServerMessage response, SnowStormRoundResult result, int nextGameCountdownMs) {
        response.appendInt(result.instanceId());
        response.appendInt(result.resultId());
        response.appendInt(result.sequence());
        response.appendInt(result.roundNumber());
        writeResultBody(response, result.endReason(), result.winningTeam(), result.ranked(),
                result.teams(), result.players());
        response.appendInt(Math.max(0, nextGameCountdownMs));
    }

    static void writeFinishedResult(ServerMessage response, SnowStormFinishedGame result) {
        response.appendInt(result.instanceId());
        response.appendInt(result.resultId());
        response.appendInt(result.roundNumber());
        writeResultBody(response, result.endReason(), result.winningTeam(), result.ranked(),
                result.teams(), result.players());
    }

    private static void writeResultBody(
            ServerMessage response,
            int endReason,
            int winningTeam,
            boolean ranked,
            Map<Integer, SnowStormRoundResult.TeamResult> teams,
            List<SnowStormRoundResult.PlayerResult> players) {
        if (teams.isEmpty() || teams.size() > 4 || players.isEmpty()
                || players.size() > SnowStormGame.MAX_PLAYERS) {
            throw new IllegalArgumentException("Invalid SnowStorm final result size");
        }
        response.appendByte(endReason);
        response.appendByte(winningTeam);
        response.appendBoolean(ranked);
        response.appendByte(teams.size());
        teams.values().stream().sorted(java.util.Comparator.comparingInt(
                SnowStormRoundResult.TeamResult::placement)).forEach(team -> {
            response.appendByte(team.teamId());
            response.appendInt(team.score());
            response.appendByte(team.placement());
        });
        response.appendShort(players.size());
        players.stream().sorted(java.util.Comparator.comparingInt(
                SnowStormRoundResult.PlayerResult::placement)).forEach(player -> {
            response.appendInt(player.userId());
            response.appendString(player.name());
            response.appendByte(player.teamId());
            response.appendInt(player.score());
            response.appendByte(player.placement());
            response.appendBoolean(player.winner());
        });
    }

    static void writeObjects(
            ServerMessage response,
            SnowStormStartingRoster roster,
            List<SnowStormSimulation.ObjectSnapshot> objects) {
        if (objects.size() > SnowStormProtocol.MAX_OBJECTS) {
            throw new IllegalArgumentException("Too many SnowStorm objects");
        }
        Set<Integer> seen = new HashSet<>();
        response.appendShort(objects.size());
        objects.stream().sorted(java.util.Comparator.comparingInt(
                SnowStormSimulation.ObjectSnapshot::objectId)).forEach(object -> {
            if (!seen.add(object.objectId())) throw new IllegalArgumentException("Duplicate SnowStorm object");
            writeObject(response, roster, object);
        });
    }

    static void writeObject(
            ServerMessage response,
            SnowStormStartingRoster roster,
            SnowStormSimulation.ObjectSnapshot object) {
        List<Integer> value = object.values();
        response.appendByte(object.type()); response.appendInt(object.objectId());
        if (object.type() == 0) {
            if (value.size() != 18 || value.get(15) != object.objectId()) {
                throw new IllegalArgumentException("Invalid SnowStorm avatar snapshot");
            }
            SnowStormStartingRoster.Player player = roster.players().stream()
                    .filter(candidate -> candidate.objectId() == object.objectId()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("SnowStorm avatar is outside the roster"));
            response.appendInt(player.userId()); response.appendString(player.name());
            response.appendString(player.figure()); response.appendString(player.sex());
            response.appendByte(player.teamId()); response.appendBoolean(value.get(5) != 0);
            response.appendInt(value.get(0)); response.appendInt(value.get(1));
            response.appendByte(value.get(2)); response.appendByte(value.get(3));
            response.appendByte(value.get(4)); response.appendByte(value.get(7));
            response.appendShort(value.get(6)); response.appendInt(value.get(8));
            response.appendInt(value.get(9)); response.appendInt(value.get(10));
            response.appendInt(value.get(11)); response.appendInt(value.get(12));
            response.appendByte(value.get(16)); response.appendByte(value.get(17));
        } else if (object.type() == 1) {
            if (value.size() != 8) throw new IllegalArgumentException("Invalid SnowStorm snowball snapshot");
            response.appendInt(value.get(6)); response.appendInt(value.get(0));
            response.appendInt(value.get(1)); response.appendInt(value.get(2));
            response.appendShort(value.get(3)); response.appendByte(value.get(4));
            response.appendShort(value.get(5)); response.appendShort(value.get(7));
        } else if (object.type() == 2) {
            if (value.size() != 3) throw new IllegalArgumentException("Invalid SnowStorm machine snapshot");
            response.appendInt(value.get(0)); response.appendInt(value.get(1));
            response.appendByte(value.get(2)); response.appendShort(object.recoveryTimer());
        } else {
            throw new IllegalArgumentException("Unknown SnowStorm object type");
        }
    }

    static void writeTurn(ServerMessage response, SnowStormSimulation.TurnResult turn) {
        if (turn.subturns().size() != 5) throw new IllegalArgumentException("SnowStorm requires five subturns");
        response.appendByte(turn.subturns().size());
        int total = 0;
        for (List<SnowStormSimulationEvent> subturn : turn.subturns()) {
            if (subturn.size() > SnowStormProtocol.MAX_EVENTS_PER_SUBTURN
                    || (total += subturn.size()) > SnowStormProtocol.MAX_EVENTS_PER_TURN) {
                throw new IllegalArgumentException("Too many SnowStorm events");
            }
            response.appendShort(subturn.size());
            for (SnowStormSimulationEvent event : subturn) writeEvent(response, event);
        }
    }

    private static void writeEvent(ServerMessage response, SnowStormSimulationEvent event) {
        response.appendByte(event.type().id());
        switch (event) {
            case SnowStormSimulationEvent.RemoveObject value -> response.appendInt(value.objectId());
            case SnowStormSimulationEvent.AvatarMove value -> {
                response.appendInt(value.objectId());
                response.appendInt(value.worldX()); response.appendInt(value.worldY());
                response.appendInt(value.targetWorldX()); response.appendInt(value.targetWorldY());
                response.appendInt(value.goalTileX()); response.appendInt(value.goalTileY());
                response.appendByte(value.direction());
            }
            case SnowStormSimulationEvent.AvatarThrow value -> {
                response.appendInt(value.objectId()); response.appendByte(value.trajectory());
                response.appendInt(value.targetWorldX()); response.appendInt(value.targetWorldY());
            }
            case SnowStormSimulationEvent.LaunchSnowball value -> {
                // Launch embeds a full object; subtype 1 identifies the payload as a snowball.
                response.appendByte(OBJECT_TYPE_SNOWBALL);
                response.appendInt(value.objectId()); response.appendInt(value.throwerObjectId());
                response.appendInt(value.worldX()); response.appendInt(value.worldY());
                response.appendInt(value.worldZ()); response.appendShort(value.direction());
                response.appendByte(value.trajectory()); response.appendShort(value.ttl());
                response.appendShort(value.parabolaOffset());
            }
            case SnowStormSimulationEvent.Hit value -> {
                response.appendInt(value.throwerObjectId()); response.appendInt(value.targetObjectId());
                response.appendInt(value.impactWorldX()); response.appendInt(value.impactWorldY());
                response.appendInt(value.impactWorldZ()); response.appendByte(value.direction());
                response.appendByte(value.health());
            }
            case SnowStormSimulationEvent.Stun value -> {
                response.appendInt(value.throwerObjectId()); response.appendInt(value.targetObjectId());
                response.appendByte(value.direction()); response.appendByte(value.fallPose());
            }
            case SnowStormSimulationEvent.ActivityState value -> {
                response.appendInt(value.objectId()); response.appendByte(value.activity());
                response.appendShort(value.timer()); response.appendByte(value.health());
            }
            case SnowStormSimulationEvent.SnowballCount value -> {
                response.appendInt(value.objectId()); response.appendByte(value.count());
            }
            case SnowStormSimulationEvent.MachineRestock value -> {
                response.appendInt(value.machineObjectId()); response.appendByte(value.count());
            }
            case SnowStormSimulationEvent.MachineTransfer value -> {
                response.appendInt(value.machineObjectId()); response.appendInt(value.playerObjectId());
                response.appendByte(value.machineCount()); response.appendByte(value.playerCount());
            }
            case SnowStormSimulationEvent.PlayerScore value -> {
                response.appendInt(value.objectId()); response.appendInt(value.score());
            }
            case SnowStormSimulationEvent.SnowballMiss value -> response.appendInt(value.objectId());
        }
    }
}
