package com.eu.habbo.habbohotel.games.battleball;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntSupplier;

/** Resolves BattleBall power effects and their temporary objects against one game board. */
final class BattleBallPowerResolver {
    private static final int[][] DIRECTIONS = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};

    private final BattleBallMap map;
    private final BattleBallTile[][] tiles;
    private final Map<Integer, BattleBallPlayer> players;
    private final LinkedHashSet<BattleBallTile> priorityTiles;
    private final LinkedHashSet<BattleBallTile> fillTiles;
    private final List<BattleBallEvent> events;
    private final Random random;
    private final IntSupplier nextObjectId;
    private final Map<Integer, BattleBallPin> pins = new LinkedHashMap<>();
    private final Map<Integer, Long> cannonStunAt = new LinkedHashMap<>();

    BattleBallPowerResolver(BattleBallMap map, BattleBallTile[][] tiles,
                            Map<Integer, BattleBallPlayer> players,
                            LinkedHashSet<BattleBallTile> priorityTiles,
                            LinkedHashSet<BattleBallTile> fillTiles,
                            List<BattleBallEvent> events, Random random, IntSupplier nextObjectId) {
        this.map = map;
        this.tiles = tiles;
        this.players = players;
        this.priorityTiles = priorityTiles;
        this.fillTiles = fillTiles;
        this.events = events;
        this.random = random;
        this.nextObjectId = nextObjectId;
    }

    Collection<BattleBallPin> pins() { return List.copyOf(this.pins.values()); }

    void apply(BattleBallPlayer player, BattleBallPowerType type, long now) {
        switch (type) {
            case LIGHT_BULB -> this.paintRadius(player, 5);
            case SPRING -> this.setState(player, BattleBallPlayerState.HIGH_JUMPS, now + 10_000L, now);
            case FLASHLIGHT -> this.paintLine(player, false, now);
            case CANNON -> this.paintLine(player, true, now);
            case BOX_OF_PINS -> this.spawnPins(player, now);
            case HARLEQUIN -> this.harlequin(player, now);
            case BOMB -> this.bomb(player, now);
            case DRILL -> this.setState(player, BattleBallPlayerState.CLEANING_TILES, now + 10_000L, now);
            case QUESTION_MARK -> { }
        }
    }

    boolean hitPin(BattleBallPlayer player, int x, int y, long now) {
        BattleBallPin hit = this.pins.values().stream()
                .filter(pin -> pin.position().x() == x && pin.position().y() == y).findFirst().orElse(null);
        if (hit == null) return false;
        this.pins.remove(hit.objectId());
        this.events.add(BattleBallEvent.despawn(hit.objectId()));
        this.setState(player, BattleBallPlayerState.BALL_BROKEN, now + 5_000L, now);
        return true;
    }

    boolean expire(long now) {
        boolean changed = false;
        List<Integer> cannonDue = this.cannonStunAt.entrySet().stream()
                .filter(entry -> entry.getValue() <= now).map(Map.Entry::getKey).toList();
        for (int objectId : cannonDue) {
            this.cannonStunAt.remove(objectId);
            this.players.values().stream().filter(player -> player.objectId() == objectId).findFirst()
                    .ifPresent(player -> this.setState(player, BattleBallPlayerState.STUNNED, now + 5_000L, now));
            changed = true;
        }
        for (BattleBallPlayer player : this.players.values()) {
            if (player.state() != BattleBallPlayerState.NORMAL && player.stateUntil() > 0
                    && player.stateUntil() <= now) {
                player.setState(BattleBallPlayerState.NORMAL, 0);
                player.setColouringForUserId(BattleBallPlayer.NOT_COLOURING);
                this.events.add(BattleBallEvent.state(player.objectId(), BattleBallPlayerState.NORMAL.id(), 0));
                changed = true;
            }
        }
        List<Integer> expiredPins = this.pins.values().stream()
                .filter(pin -> pin.expiresAt() <= now).map(BattleBallPin::objectId).toList();
        for (int id : expiredPins) {
            this.pins.remove(id);
            this.events.add(BattleBallEvent.despawn(id));
            changed = true;
        }
        return changed;
    }

    void reset() {
        this.pins.clear();
        this.cannonStunAt.clear();
    }

    int scoringUser(BattleBallPlayer player) {
        return player.colouringForUserId() != BattleBallPlayer.NOT_COLOURING
                ? player.colouringForUserId() : player.userId();
    }

    int scoringTeam(BattleBallPlayer player) {
        if (player.colouringForUserId() == BattleBallPlayer.NOT_COLOURING) return player.teamId();
        BattleBallPlayer activator = this.players.get(player.colouringForUserId());
        return activator == null ? player.teamId() : activator.teamId();
    }

    void fillFrom(BattleBallTile sealed, int teamId, int scoringUserId) {
        for (int[] direction : DIRECTIONS) {
            int startX = sealed.x() + direction[0];
            int startY = sealed.y() + direction[1];
            BattleBallTile start = this.tile(startX, startY);
            if (start == null || (start.colour() == teamId && start.state() == BattleBallTileState.SEALED)) continue;
            Set<BattleBallTile> region = new LinkedHashSet<>();
            ArrayDeque<BattleBallTile> open = new ArrayDeque<>();
            open.add(start);
            boolean bounded = true;
            while (!open.isEmpty() && bounded) {
                BattleBallTile current = open.removeFirst();
                if (!region.add(current)) continue;
                for (int[] step : DIRECTIONS) {
                    BattleBallTile neighbour = this.tile(current.x() + step[0], current.y() + step[1]);
                    if (neighbour == null) { bounded = false; break; }
                    if (neighbour.colour() == teamId && neighbour.state() == BattleBallTileState.SEALED) continue;
                    if (!region.contains(neighbour)) open.addLast(neighbour);
                }
            }
            if (bounded) {
                for (BattleBallTile tile : region) {
                    if (tile.sealFromFill(teamId, scoringUserId)) this.fillTiles.add(tile);
                }
            }
        }
    }

    private void paintRadius(BattleBallPlayer player, int radius) {
        int effectiveTeam = this.scoringTeam(player);
        int scoringUser = this.scoringUser(player);
        int radiusSquared = radius * radius;
        for (BattleBallPosition position : this.map.playablePositions()) {
            if (position.distanceSquared(player.position()) > radiusSquared) continue;
            BattleBallTile tile = this.tiles[position.x()][position.y()];
            if (tile.state() == BattleBallTileState.SEALED) continue;
            BattleBallTileState target = paintedState(tile, effectiveTeam);
            if (tile.paint(effectiveTeam, target, scoringUser)) {
                this.priorityTiles.add(tile);
                if (target == BattleBallTileState.SEALED) this.fillFrom(tile, effectiveTeam, scoringUser);
            }
        }
    }

    private void paintLine(BattleBallPlayer player, boolean cannon, long now) {
        int effectiveTeam = this.scoringTeam(player);
        int scoringUser = this.scoringUser(player);
        int[] vector = directionVector(player.position().direction());
        int x = player.position().x();
        int y = player.position().y();
        BattleBallPosition last = player.position();
        while (this.map.isPlayable(x + vector[0], y + vector[1])) {
            x += vector[0]; y += vector[1];
            BattleBallTile tile = this.tiles[x][y];
            if (tile.state() != BattleBallTileState.SEALED) {
                BattleBallTileState target = cannon ? BattleBallTileState.SEALED : paintedState(tile, effectiveTeam);
                if (tile.paint(effectiveTeam, target, scoringUser)) {
                    this.priorityTiles.add(tile);
                    if (target == BattleBallTileState.SEALED) this.fillFrom(tile, effectiveTeam, scoringUser);
                }
            }
            if (cannon) {
                for (BattleBallPlayer other : this.players.values()) {
                    if (other.userId() != player.userId() && other.position().x() == x && other.position().y() == y) {
                        this.pushSideways(other, player.position().direction());
                        this.setState(other, BattleBallPlayerState.STUNNED, now + 5_000L, now);
                    }
                }
            }
            // Havana flies through occupied line tiles and pushes their occupants right, then left. Keep
            // the furthest collision-free landing candidate so a blocked final victim is never overlapped.
            if (!cannon || !this.isOccupiedByOther(player, x, y)) {
                last = new BattleBallPosition(x, y, 0.0, player.position().direction());
            }
        }
        if (cannon && last != player.position()) {
            player.clearMovementPath();
            player.setState(BattleBallPlayerState.FLYING_THROUGH_AIR, 0);
            this.events.add(BattleBallEvent.state(player.objectId(), BattleBallPlayerState.FLYING_THROUGH_AIR.id(), 800));
            this.cannonStunAt.put(player.objectId(), now + 800L);
            player.setPosition(last);
            this.events.add(BattleBallEvent.move(player.objectId(), last.x(), last.y()));
        }
    }

    private void spawnPins(BattleBallPlayer player, long now) {
        int[] vector = directionVector(player.position().direction());
        int centreX = player.position().x() + vector[0] * 6;
        int centreY = player.position().y() + vector[1] * 6;
        List<BattleBallPosition> candidates = new ArrayList<>();
        for (BattleBallPosition position : this.map.playablePositions()) {
            int dx = position.x() - centreX;
            int dy = position.y() - centreY;
            if (dx * dx + dy * dy <= 9
                    && (position.x() != player.position().x() || position.y() != player.position().y())) {
                candidates.add(position);
            }
        }
        Collections.shuffle(candidates, this.random);
        int count = Math.min(candidates.size(), 8 + this.random.nextInt(8));
        for (int i = 0; i < count; i++) {
            int objectId = this.nextObjectId.getAsInt();
            BattleBallPin pin = new BattleBallPin(objectId, candidates.get(i),
                    now + (12 + this.random.nextInt(5)) * 1_000L);
            this.pins.put(objectId, pin);
            this.events.add(BattleBallEvent.spawn(objectId));
        }
    }

    private void harlequin(BattleBallPlayer activator, long now) {
        for (BattleBallPlayer player : this.players.values()) {
            if (player.teamId() == activator.teamId() || player.state() != BattleBallPlayerState.NORMAL) continue;
            player.setColouringForUserId(activator.userId());
            this.setState(player, BattleBallPlayerState.COLORING_FOR_OPPONENT, now + 10_000L, now);
        }
    }

    private void bomb(BattleBallPlayer activator, long now) {
        for (BattleBallPosition position : this.map.playablePositions()) {
            if (position.distanceSquared(activator.position()) <= 9) {
                BattleBallTile tile = this.tiles[position.x()][position.y()];
                if (tile.reset()) this.priorityTiles.add(tile);
            }
        }
        for (BattleBallPlayer player : this.players.values()) {
            if (player.position().distanceSquared(activator.position()) <= 49) {
                if (player != activator) this.pushAway(player, activator.position());
                this.setState(player, BattleBallPlayerState.STUNNED, now + 5_000L, now);
            }
        }
    }

    private void pushAway(BattleBallPlayer player, BattleBallPosition centre) {
        int dx = Integer.compare(player.position().x(), centre.x());
        int dy = Integer.compare(player.position().y(), centre.y());
        int x = player.position().x() + dx;
        int y = player.position().y() + dy;
        if (this.canDisplaceTo(player, x, y)) {
            player.setPosition(new BattleBallPosition(x, y, 0.0, direction(centre.x(), centre.y(), x, y)));
            this.events.add(BattleBallEvent.move(player.objectId(), x, y));
        }
    }

    private void pushSideways(BattleBallPlayer player, int cannonDirection) {
        int[] right = directionVector(cannonDirection + 2);
        int x = player.position().x() + right[0];
        int y = player.position().y() + right[1];
        if (!this.canDisplaceTo(player, x, y)) {
            int[] left = directionVector(cannonDirection - 2);
            x = player.position().x() + left[0];
            y = player.position().y() + left[1];
        }
        if (this.canDisplaceTo(player, x, y)) {
            player.setPosition(new BattleBallPosition(x, y, 0.0, player.position().direction()));
            this.events.add(BattleBallEvent.move(player.objectId(), x, y));
        }
    }

    private boolean canDisplaceTo(BattleBallPlayer player, int x, int y) {
        return this.map.isPlayable(x, y) && !this.isOccupiedByOther(player, x, y);
    }

    private boolean isOccupiedByOther(BattleBallPlayer player, int x, int y) {
        return this.players.values().stream().anyMatch(other -> other != player
                && other.position().x() == x && other.position().y() == y);
    }

    private void setState(BattleBallPlayer player, BattleBallPlayerState state, long until, long now) {
        if (state == BattleBallPlayerState.STUNNED || state == BattleBallPlayerState.BALL_BROKEN
                || state == BattleBallPlayerState.FLYING_THROUGH_AIR) player.clearMovementPath();
        player.setState(state, until);
        this.events.add(BattleBallEvent.state(player.objectId(), state.id(), (int) Math.max(0, until - now)));
    }

    private BattleBallTile tile(int x, int y) {
        return this.map.isPlayable(x, y) ? this.tiles[x][y] : null;
    }

    private static BattleBallTileState paintedState(BattleBallTile tile, int team) {
        if (tile.colour() != team) return BattleBallTileState.CLICKED;
        return tile.state() == BattleBallTileState.DEFAULT ? BattleBallTileState.CLICKED : tile.state().next();
    }

    private static int direction(int fromX, int fromY, int toX, int toY) {
        int dx = Integer.compare(toX, fromX);
        int dy = Integer.compare(toY, fromY);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i][0] == dx && DIRECTIONS[i][1] == dy) return i;
        }
        return 0;
    }

    private static int[] directionVector(int direction) {
        return DIRECTIONS[Math.floorMod(direction, DIRECTIONS.length)];
    }
}
