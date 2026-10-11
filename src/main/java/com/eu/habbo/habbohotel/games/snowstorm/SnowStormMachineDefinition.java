package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.List;

public record SnowStormMachineDefinition(
        int id,
        int tileX,
        int tileY,
        int worldX,
        int worldY,
        int worldZ,
        int collectionTileX,
        int collectionTileY,
        int direction,
        List<SnowStormTile> collisionTiles,
        int collisionRadius,
        int collisionHeight,
        int initialStock,
        int maximumStock,
        int refillIntervalMs,
        int transferIntervalMs,
        boolean immediateRefillOnCollection,
        boolean immediateTransferOnCollection,
        String arbitration) {

    public SnowStormMachineDefinition {
        collisionTiles = List.copyOf(collisionTiles);
    }

    public SnowStormTile collectionTile() {
        return new SnowStormTile(collectionTileX, collectionTileY);
    }
}
