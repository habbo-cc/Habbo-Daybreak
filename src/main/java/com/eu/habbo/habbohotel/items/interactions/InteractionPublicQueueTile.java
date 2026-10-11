package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

/**
 * A walkable public-room queue tile (e.g. the park bus-queue chevrons). Visually a directional
 * arrow, functionally a normal can-stand-on collision tile. The room cycle auto-advances a player
 * standing on one to the tile its rotation points at (see RoomCycleManager#processPublicQueue),
 * forming a single-file queue that flows toward the exit so idlers cannot block the lane.
 *
 * Subclass of InteractionPublicItem so it inherits the synthetic, never-persisted behaviour
 * (negative id, no DB writes).
 */
public class InteractionPublicQueueTile extends InteractionPublicItem {

    public InteractionPublicQueueTile(int id, Item baseItem) {
        super(id, baseItem);
    }
}
