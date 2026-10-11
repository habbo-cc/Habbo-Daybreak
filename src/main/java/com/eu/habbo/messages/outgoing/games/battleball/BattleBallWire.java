package com.eu.habbo.messages.outgoing.games.battleball;

import com.eu.habbo.habbohotel.games.battleball.BattleBallEvent;
import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallInstanceSummary;
import com.eu.habbo.habbohotel.games.battleball.BattleBallFinishedGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPin;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPlayer;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPowerUp;
import com.eu.habbo.habbohotel.games.battleball.BattleBallTile;
import com.eu.habbo.habbohotel.games.battleball.BattleBallUpdate;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class BattleBallWire {
    static final int STATUS_VERSION = 2;
    private static final int MAX_TILES = 1600;
    private static final int MAX_OBJECTS = 256;
    private static final int MAX_COORDINATE = 127;

    private BattleBallWire() {}

    static void writeInstance(ServerMessage response, BattleBallGame game) {
        writeInstance(response, BattleBallInstanceSummary.live(game));
    }

    static void writeInstance(ServerMessage response, BattleBallInstanceSummary game) {
        response.appendInt(game.instanceId()); response.appendString(game.name()); response.appendInt(game.ownerUserId());
        response.appendString(game.ownerName()); response.appendByte(game.mapId()); response.appendByte(game.state());
        response.appendBoolean(game.privateGame()); response.appendByte(game.teamCount()); response.appendByte(game.playerCount());
        response.appendByte(game.capacity()); response.appendShort(game.roundSeconds());
    }

    static void writeFinishedResult(ServerMessage response, BattleBallFinishedGame game) {
        response.appendByte(game.winningTeam());
        writeScores(response, game.teamScores());
        response.appendShort(game.players().size());
        for (BattleBallFinishedGame.PlayerResult player : game.players()) {
            response.appendInt(player.userId()); response.appendString(player.name());
            response.appendByte(player.teamId()); response.appendInt(player.score());
        }
    }

    static void writePlayer(ServerMessage response, BattleBallPlayer player) {
        response.appendByte(0); response.appendInt(player.objectId()); response.appendInt(player.userId());
        writePosition(response, player.position().x(), player.position().y());
        response.appendByte(player.position().direction()); response.appendByte(player.state().id());
        response.appendInt(player.colouringForUserId()); response.appendString(player.name());
        response.appendString(player.figure()); response.appendByte(player.teamId());
    }

    static void writePower(ServerMessage response, BattleBallPowerUp power) {
        response.appendByte(1); response.appendInt(power.objectId()); response.appendInt(power.holderObjectId());
        response.appendByte(power.type().id()); response.appendByte(remainingPowerSeconds(power));
        writePosition(response, power.position().x(), power.position().y());
    }

    static void writePin(ServerMessage response, BattleBallPin pin) {
        response.appendByte(2); response.appendInt(pin.objectId()); writePosition(response, pin.position().x(), pin.position().y());
    }

    static void writeFullObjects(ServerMessage response, BattleBallUpdate update) {
        int count = update.players().size() + update.powers().size() + update.pins().size();
        if (count > MAX_OBJECTS) throw new IllegalArgumentException("Too many BattleBall objects");
        response.appendShort(count);
        update.players().forEach(player -> writePlayer(response, player));
        update.powers().forEach(power -> writePower(response, power));
        update.pins().forEach(pin -> writePin(response, pin));
    }

    static void writeObjectChanges(ServerMessage response, BattleBallUpdate update) {
        LinkedHashSet<Integer> spawned = new LinkedHashSet<>();
        LinkedHashSet<Integer> updated = new LinkedHashSet<>();
        LinkedHashSet<Integer> removed = new LinkedHashSet<>();
        for (BattleBallEvent event : update.events()) {
            switch (event.type()) {
                case 0 -> spawned.add(event.objectId());
                case 1 -> removed.add(event.objectId());
                case 2, 4, 6 -> updated.add(event.objectId());
                case 3 -> { updated.add(event.objectId()); updated.add(event.secondaryId()); }
                case 5 -> updated.add(event.objectId());
                default -> throw new IllegalStateException("Unknown BattleBall event type " + event.type());
            }
        }
        // A reset replaces the board atomically. Send every player as a compact full object so a
        // participant added immediately before Start is present even if resetBoard cleared its
        // queued spawn event before the next scheduled delta.
        if (update.boardReset()) update.players().forEach(player -> spawned.add(player.objectId()));

        Set<Integer> transientIds = new HashSet<>(spawned);
        transientIds.retainAll(removed);
        spawned.removeAll(transientIds); removed.removeAll(transientIds);
        updated.removeAll(spawned); updated.removeAll(removed);

        List<Object> spawnObjects = resolveObjects(update, spawned);
        List<Object> updateObjects = resolveObjects(update, updated);
        if (spawnObjects.size() > MAX_OBJECTS || updateObjects.size() > MAX_OBJECTS || removed.size() > MAX_OBJECTS) {
            throw new IllegalArgumentException("Too many BattleBall object changes");
        }
        response.appendShort(spawnObjects.size());
        spawnObjects.forEach(object -> writeObject(response, object));
        response.appendShort(updateObjects.size());
        updateObjects.forEach(object -> writeDynamicObject(response, object));
        response.appendShort(removed.size());
        removed.forEach(response::appendInt);
    }

    /** Full snapshots are dense and row-major, so coordinates and a tile count would be redundant. */
    static void writeFullTiles(ServerMessage response, List<BattleBallTile> tiles, int width, int height) {
        long expectedLong = (long) width * height;
        if (width <= 0 || height <= 0 || expectedLong > MAX_TILES || tiles.size() != expectedLong) {
            throw new IllegalArgumentException("Invalid BattleBall full tile grid");
        }
        int expected = (int) expectedLong;
        for (int index = 0; index < expected; index++) {
            BattleBallTile tile = tiles.get(index);
            if (tile.x() != index % width || tile.y() != index / width) {
                throw new IllegalArgumentException("BattleBall full tiles must be row-major");
            }
            response.appendShort(packFullTile(tile));
        }
    }

    /** Deltas contain only mutable state; floor height and walkability remain from the full snapshot. */
    static void writeDeltaTiles(ServerMessage response, List<BattleBallTile> tiles) {
        if (tiles.size() > MAX_TILES) throw new IllegalArgumentException("Too many BattleBall delta tiles");
        response.appendShort(tiles.size());
        for (BattleBallTile tile : tiles) {
            if (tile.x() < 0 || tile.x() > MAX_COORDINATE || tile.y() < 0 || tile.y() > MAX_COORDINATE) {
                throw new IllegalArgumentException("BattleBall tile coordinate exceeds compact encoding");
            }
            response.appendByte(tile.x()); response.appendByte(tile.y()); response.appendByte(packDynamicTile(tile));
        }
    }

    static int packFullTile(BattleBallTile tile) {
        int floorHeight = tile.floorHeight();
        if (floorHeight < 0 || floorHeight > 15) {
            throw new IllegalArgumentException("BattleBall floor height exceeds compact encoding");
        }
        return packDynamicTile(tile) | (floorHeight << 6) | (tile.walkable() ? 1 << 10 : 0);
    }

    static int packDynamicTile(BattleBallTile tile) {
        int colour = tile.colour() + 2;
        int state = tile.state().id();
        if (colour < 0 || colour > 5 || state < 0 || state > 4) {
            throw new IllegalArgumentException("Invalid BattleBall tile state");
        }
        return colour | (state << 3);
    }

    static void writeScores(ServerMessage response, Map<Integer, Integer> scores) {
        response.appendByte(scores.size());
        scores.forEach((team, score) -> { response.appendByte(team); response.appendInt(score); });
    }

    static void writePlayerScores(ServerMessage response, Map<Integer, Integer> scores) {
        if (scores.size() > MAX_OBJECTS) throw new IllegalArgumentException("Too many BattleBall player scores");
        response.appendShort(scores.size());
        scores.forEach((userId, score) -> { response.appendInt(userId); response.appendInt(score); });
    }

    static void writeEffects(ServerMessage response, BattleBallUpdate update) {
        List<Integer> effects = new ArrayList<>();
        for (BattleBallEvent event : update.events()) {
            if (event.type() == 3 || event.type() == 5 || (event.type() == 4 && event.value() == 1)) effects.add(event.type());
        }
        response.appendShort(effects.size());
        effects.forEach(response::appendByte);
    }

    private static List<Object> resolveObjects(BattleBallUpdate update, Set<Integer> objectIds) {
        List<Object> result = new ArrayList<>();
        for (int objectId : objectIds) {
            Object object = findObject(update, objectId);
            if (object != null) result.add(object);
        }
        return result;
    }

    private static Object findObject(BattleBallUpdate update, int objectId) {
        for (BattleBallPlayer player : update.players()) if (player.objectId() == objectId) return player;
        for (BattleBallPowerUp power : update.powers()) if (power.objectId() == objectId) return power;
        for (BattleBallPin pin : update.pins()) if (pin.objectId() == objectId) return pin;
        return null;
    }

    private static void writeObject(ServerMessage response, Object object) {
        if (object instanceof BattleBallPlayer player) writePlayer(response, player);
        else if (object instanceof BattleBallPowerUp power) writePower(response, power);
        else if (object instanceof BattleBallPin pin) writePin(response, pin);
        else throw new IllegalArgumentException("Unknown BattleBall object");
    }

    private static void writeDynamicObject(ServerMessage response, Object object) {
        if (object instanceof BattleBallPlayer player) {
            response.appendByte(0); response.appendInt(player.objectId());
            writePosition(response, player.position().x(), player.position().y());
            response.appendByte(player.position().direction()); response.appendByte(player.state().id());
            response.appendInt(player.colouringForUserId()); response.appendByte(player.teamId());
        } else if (object instanceof BattleBallPowerUp power) {
            response.appendByte(1); response.appendInt(power.objectId()); response.appendInt(power.holderObjectId());
            response.appendByte(power.type().id()); response.appendByte(remainingPowerSeconds(power));
            writePosition(response, power.position().x(), power.position().y());
        } else if (object instanceof BattleBallPin pin) {
            response.appendByte(2); response.appendInt(pin.objectId()); writePosition(response, pin.position().x(), pin.position().y());
        } else throw new IllegalArgumentException("Unknown BattleBall object");
    }

    private static void writePosition(ServerMessage response, int x, int y) {
        if (x < 0 || x > MAX_COORDINATE || y < 0 || y > MAX_COORDINATE) {
            throw new IllegalArgumentException("BattleBall position exceeds compact encoding");
        }
        response.appendByte(x); response.appendByte(y);
    }

    /** Havana includes the held/ground lifetime in every power object; one byte covers its 30s maximum. */
    private static int remainingPowerSeconds(BattleBallPowerUp power) {
        long remaining = Math.max(0L, power.expiresAt() - System.currentTimeMillis());
        return (int) Math.min(255L, (remaining + 999L) / 1_000L);
    }
}
