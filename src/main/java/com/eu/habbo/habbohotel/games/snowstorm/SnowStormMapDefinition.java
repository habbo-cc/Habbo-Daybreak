package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.List;
import java.util.Optional;

public record SnowStormMapDefinition(
        int mapId,
        int revision,
        int width,
        int height,
        int worldUnitsPerTile,
        int tileOriginX,
        int tileOriginY,
        int worldOriginX,
        int worldOriginY,
        int worldOriginZ,
        String baseRoomModel,
        List<String> backgroundMembers,
        List<String> floorRows,
        List<String> collisionRows,
        List<SnowStormMapItem> items,
        List<SnowStormMachineDefinition> machines,
        List<SnowStormSpawnCluster> spawnClusters,
        int checksum,
        String sha256) {

    public SnowStormMapDefinition {
        backgroundMembers = List.copyOf(backgroundMembers);
        floorRows = List.copyOf(floorRows);
        collisionRows = List.copyOf(collisionRows);
        items = List.copyOf(items);
        machines = List.copyOf(machines);
        spawnClusters = List.copyOf(spawnClusters);
    }

    public boolean contains(SnowStormTile tile) {
        return tile.x() >= 0 && tile.x() < width && tile.y() >= 0 && tile.y() < height;
    }

    public boolean isFloor(SnowStormTile tile) {
        return tile != null && isFloor(tile.x(), tile.y());
    }

    public boolean isFloor(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height
                && floorRows.get(y).charAt(x) != 'x';
    }

    public boolean isWalkable(SnowStormTile tile) {
        return tile != null && isWalkable(tile.x(), tile.y());
    }

    public boolean isWalkable(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height
                && collisionRows.get(y).charAt(x) != 'x';
    }

    public int toWorldX(int tileX) {
        return worldOriginX + (tileX - tileOriginX) * worldUnitsPerTile;
    }

    public int toWorldY(int tileY) {
        return worldOriginY + (tileY - tileOriginY) * worldUnitsPerTile;
    }

    public Optional<SnowStormTile> toTile(int worldX, int worldY) {
        long relativeX = (long) worldX - worldOriginX;
        long relativeY = (long) worldY - worldOriginY;
        if (relativeX < -(worldUnitsPerTile / 2L) || relativeY < -(worldUnitsPerTile / 2L)) {
            return Optional.empty();
        }
        int tileX = (int) ((relativeX + worldUnitsPerTile / 2L) / worldUnitsPerTile) + tileOriginX;
        int tileY = (int) ((relativeY + worldUnitsPerTile / 2L) / worldUnitsPerTile) + tileOriginY;
        SnowStormTile tile = new SnowStormTile(tileX, tileY);
        return contains(tile) ? Optional.of(tile) : Optional.empty();
    }

    public int projectileCollisionHeight(SnowStormTile tile) {
        int heightAtTile = 0;
        for (SnowStormMapItem item : items) {
            if (item.footprint().contains(tile)) {
                heightAtTile = Math.max(heightAtTile, item.projectileCollisionHeight());
            }
        }
        for (SnowStormMachineDefinition machine : machines) {
            if (machine.collisionTiles().contains(tile)) {
                heightAtTile = Math.max(heightAtTile, machine.collisionHeight());
            }
        }
        return heightAtTile;
    }
}
