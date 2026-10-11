package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.habbohotel.rooms.pathfinding.impl.GridPathfinder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Adapts the hotel's shared A* grid pathfinder to the private SnowStorm arena. */
public final class SnowStormMovementPlanner {
    private SnowStormMovementPlanner() {
    }

    public static Optional<NextTile> chooseNextTile(
            SnowStormMapDefinition map,
            SnowStormTile currentTile,
            int finalWorldX,
            int finalWorldY,
            Set<SnowStormTile> reservedTiles) {
        SnowStormTile targetTile = map.toTile(finalWorldX, finalWorldY).orElse(null);
        if (targetTile == null) {
            return Optional.empty();
        }

        SearchGrid grid = new SearchGrid(map, reservedTiles);
        GridPathfinder.Step step = GridPathfinder.findNextStep(grid, currentTile.x(), currentTile.y(),
                targetTile.x(), targetTile.y(), 0, true, false);
        if (step == null) {
            return Optional.empty();
        }
        SnowStormTile tile = new SnowStormTile(step.x(), step.y());
        int direction = SnowStormMath.direction360To8(SnowStormMath.componentAngle(
                tile.x() - currentTile.x(), tile.y() - currentTile.y()));
        return Optional.of(new NextTile(
                direction, tile, map.toWorldX(tile.x()), map.toWorldY(tile.y())));
    }

    static List<GridPathfinder.Step> findPath(
            SnowStormMapDefinition map,
            SnowStormTile start,
            SnowStormTile target,
            Set<SnowStormTile> reservedTiles) {
        return findPath(new SearchGrid(map, reservedTiles), start, target);
    }

    static List<GridPathfinder.Step> findPath(
            SearchGrid grid,
            SnowStormTile start,
            SnowStormTile target) {
        return GridPathfinder.findPath(grid, start.x(), start.y(), target.x(), target.y(),
                0, true, false);
    }

    static final class SearchGrid implements GridPathfinder.Grid {
        private final SnowStormMapDefinition map;
        private final boolean[] occupied;

        SearchGrid(SnowStormMapDefinition map, Set<SnowStormTile> reservedTiles) {
            this.map = map;
            this.occupied = new boolean[map.width() * map.height()];
            for (SnowStormTile tile : reservedTiles) {
                if (map.contains(tile)) occupied[tile.y() * map.width() + tile.x()] = true;
            }
        }

        @Override public int width() { return map.width(); }
        @Override public int height() { return map.height(); }
        @Override public boolean isWalkable(int x, int y) { return map.isWalkable(x, y); }
        @Override public boolean isOccupied(int x, int y) { return occupied[y * map.width() + x]; }
        @Override public double floorHeight(int x, int y) { return 0; }
    }

    public record NextTile(int direction, SnowStormTile tile, int worldX, int worldY) {
    }
}
