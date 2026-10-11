package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.List;

public record SnowStormMapItem(
        String id,
        String member,
        String assetName,
        int resourceId,
        int castId,
        int tileX,
        int tileY,
        int worldX,
        int worldY,
        int worldZ,
        int direction,
        boolean flipX,
        int sourceLocZ,
        int drawOrder,
        int walkingHeight,
        int projectileCollisionHeight,
        boolean blocksWalking,
        List<SnowStormTile> footprint,
        int paletteId,
        String paletteName,
        int registrationX,
        int registrationY) {

    public SnowStormMapItem {
        footprint = List.copyOf(footprint);
    }
}
