package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonMovementPhysics;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.items.WiredMovementsMessageComposer;
import com.eu.habbo.threading.runnables.WiredCollissionRunnable;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wired effect: move to closest user
 * Confirmed as working exactly like Habbo.com 03/05/2019 04:00
 *
 * @author Beny.
 */
public class WiredEffectMoveFurniTowards extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CHASE;

    /**
     * Minimum gap between bot/user collision events this chase emits for the same furni while a user
     * stays wedged against it. The chase effect re-runs every tick (≈50ms) via a fast repeater, but
     * habbo.com only fires the collision (and the downstream Send Message) at roughly this cadence —
     * not per tick. Without this throttle a stuck chase emits ~20 collision events/second, which trips
     * the wired abuse rate-limiter and false-bans the room. Matches the observed .com ~500ms cadence.
     */
    private static final long BOT_COLLISION_MIN_INTERVAL_MS = 500L;

    private THashSet<HabboItem> items;
    private int[] furniSourceTypes = new int[0];

    private THashMap<Integer, RoomUserRotation> lastDirections;

    /** Last time (ms) this chase emitted a collision event per furni id, to throttle the emission. */
    private THashMap<Integer, Long> lastCollisionMs;


    public WiredEffectMoveFurniTowards(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
        this.lastDirections = new THashMap<>();
        this.lastCollisionMs = new THashMap<>();
    }

    public WiredEffectMoveFurniTowards(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
        this.lastDirections = new THashMap<>();
        this.lastCollisionMs = new THashMap<>();
    }

    public List<RoomUserRotation> getAvailableDirections(HabboItem item, Room room) {
        List<RoomUserRotation> availableDirections = new ArrayList<>();
        RoomLayout layout = room.getLayout();

        if (layout == null) return availableDirections;

        RoomTile currentTile = layout.getTile(item.getX(), item.getY());
        if (currentTile == null) return availableDirections;

        RoomUserRotation[] rotations = new RoomUserRotation[]{RoomUserRotation.NORTH, RoomUserRotation.EAST, RoomUserRotation.SOUTH, RoomUserRotation.WEST};

        for (RoomUserRotation rot : rotations) {
            RoomTile tile = layout.getTileInFront(currentTile, rot.getValue());

            if (tile == null || tile.state == RoomTileState.INVALID)
                continue;

            if (!layout.tileExists(tile.x, tile.y))
                continue;

            WiredAddonMovementPhysics movementPhysics = WiredMovementAddonRuntime.activePhysics();
            boolean ignoreFurniStacking = WiredMovementAddonRuntime.bypassFurniCollision(movementPhysics);
            if (room.furnitureFitsAt(tile, item, item.getRotation(), false,
                    ignoreFurniStacking) != FurnitureMovementError.NONE)
                continue;

            if (tile.getAllowStack() || ignoreFurniStacking) {
                availableDirections.add(rot);
            }
        }

        return availableDirections;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items) {
            if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItemByDatabaseId(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        for (HabboItem item : WiredMovementAddonRuntime.furniTargets(ctx,
                resolveFurniSource(ctx, this.getWiredFurniSourceTypes(), 0, this.items, null))) {

            if (item == null)
                continue;

            // direction the furni will move in
            RoomUserRotation moveDirection = null;
            RoomUserRotation lastDirection = lastDirections.get(item.getId());

            // 1. Check if any user is within 3 tiles from the item
            RoomUnit target = null; // closest found user
            RoomLayout layout = room.getLayout();
            boolean collided = false;

            if (layout == null) {
                break;
            }

            for (int i = 0; i < 3; i++) {
                if (target != null)
                    break;

                RoomUserRotation[] rotations = new RoomUserRotation[]{RoomUserRotation.NORTH, RoomUserRotation.EAST, RoomUserRotation.SOUTH, RoomUserRotation.WEST};

                for (RoomUserRotation rot : rotations) {
                    RoomTile startTile = layout.getTile(item.getX(), item.getY());

                    for (int ii = 0; ii <= i; ii++) {
                        if (startTile == null)
                            break;

                        startTile = layout.getTileInFront(startTile, rot.getValue());
                    }

                    if (startTile != null && layout.tileExists(startTile.x, startTile.y)) {
                        Collection<RoomUnit> roomUnitsAtTile = room.getRoomUnitsAt(startTile);
                        if (roomUnitsAtTile.size() > 0) {
                            target = roomUnitsAtTile.iterator().next();
                            if (i == 0) { // i = 0 means right next to it
                                collided = true;
                                // Throttle collision emission so a furni a user is wedged against does not
                                // fire a collision event every tick (which would trip the abuse limiter).
                                long now = System.currentTimeMillis();
                                Long lastCollision = this.lastCollisionMs.get(item.getId());
                                if (lastCollision == null || now - lastCollision >= BOT_COLLISION_MIN_INTERVAL_MS) {
                                    this.lastCollisionMs.put(item.getId(), now);
                                    Emulator.getThreading().run(new WiredCollissionRunnable(target, room));
                                }
                            }
                            break;
                        }
                    }
                }
            }

            if (collided)
                continue;

            if (target != null) {
                if (target.getX() == item.getX()) {
                    if (item.getY() < target.getY())
                        moveDirection = RoomUserRotation.SOUTH;
                    else
                        moveDirection = RoomUserRotation.NORTH;
                } else if (target.getY() == item.getY()) {
                    if (item.getX() < target.getX())
                        moveDirection = RoomUserRotation.EAST;
                    else
                        moveDirection = RoomUserRotation.WEST;
                } else if (target.getX() - item.getX() > target.getY() - item.getY()) {
                    if (target.getX() - item.getX() > 0)
                        moveDirection = RoomUserRotation.EAST;
                    else
                        moveDirection = RoomUserRotation.WEST;
                } else {
                    if (target.getY() - item.getY() > 0)
                        moveDirection = RoomUserRotation.SOUTH;
                    else
                        moveDirection = RoomUserRotation.NORTH;
                }
            }


            // 2. Get a random direction
            /*
            getAvailableDirections:
                0 available - don't move
                1 available - move in that direction
                2 available - if lastdirection = null move in random possible direction
                              else if direction[0] = lastdirection opposite, move in direction[1]
                              else move in direction[0]
                3+ available - move in random direction, but never the opposite
             */

            List<RoomUserRotation> availableDirections = this.getAvailableDirections(item, room);

            if (moveDirection != null && !availableDirections.contains(moveDirection))
                moveDirection = null;

            if (moveDirection == null) {
                if (availableDirections.size() == 0) {
                    continue;
                } else if (availableDirections.size() == 1) {
                    moveDirection = availableDirections.iterator().next();
                } else if (availableDirections.size() == 2) {
                    if (lastDirection == null) {
                        moveDirection = availableDirections.get(Emulator.getRandom().nextInt(availableDirections.size()));
                    } else {
                        RoomUserRotation oppositeLast = lastDirection.getOpposite();

                        if (availableDirections.get(0) == oppositeLast) {
                            moveDirection = availableDirections.get(1);
                        } else {
                            moveDirection = availableDirections.get(0);
                        }
                    }
                } else {
                    if (lastDirection != null) {
                        RoomUserRotation opposite = lastDirection.getOpposite();
                        availableDirections.remove(opposite);
                    }
                    moveDirection = availableDirections.get(Emulator.getRandom().nextInt(availableDirections.size()));
                }
            }

            RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
            if (oldLocation == null) continue;

            RoomTile newTile = room.getLayout().getTileInFront(oldLocation, moveDirection.getValue());

            double oldZ = item.getZ();

            if(newTile != null) {
                lastDirections.put(item.getId(), moveDirection);
                WiredAddonMovementPhysics movementPhysics = WiredMovementAddonRuntime.activePhysics();
                boolean ignoreFurniStacking = WiredMovementAddonRuntime.bypassFurniCollision(movementPhysics);
                if(newTile.state != RoomTileState.INVALID && newTile != oldLocation
                        && room.furnitureFitsAt(newTile, item, item.getRotation(), true,
                                ignoreFurniStacking) == FurnitureMovementError.NONE) {
                    if (WiredMovementAddonRuntime.move(ctx, room, item, newTile, item.getRotation(), false) == FurnitureMovementError.NONE) {
                        // Wired 2.0: stream a smooth WiredMovements slide instead of the legacy roller hop.
                        WiredMovementAddonRuntime.moved(ctx, room, item, oldLocation, oldZ, newTile);
                    }
                }
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        Room room = ctx.room();
        RoomLayout layout = room.getLayout();
        if (layout == null) return true;
        
        for (HabboItem item : this.items) {
            if (item == null) continue;
            
            WiredSimulation.SimulatedPosition currentPos = simulation.getItemPosition(item);
            RoomTile currentTile = layout.getTile(currentPos.x, currentPos.y);
            if (currentTile == null) continue;
            
            RoomUnit target = null;
            
            for (int i = 0; i < 3; i++) {
                if (target != null) break;
                
                RoomUserRotation[] rotations = new RoomUserRotation[]{RoomUserRotation.NORTH, RoomUserRotation.EAST, RoomUserRotation.SOUTH, RoomUserRotation.WEST};
                
                for (RoomUserRotation rot : rotations) {
                    RoomTile startTile = currentTile;
                    
                    for (int ii = 0; ii <= i; ii++) {
                        if (startTile == null) break;
                        startTile = layout.getTileInFront(startTile, rot.getValue());
                    }
                    
                    if (startTile != null && layout.tileExists(startTile.x, startTile.y)) {
                        Collection<RoomUnit> roomUnitsAtTile = room.getRoomUnitsAt(startTile);
                        if (!roomUnitsAtTile.isEmpty()) {
                            target = roomUnitsAtTile.iterator().next();
                            break;
                        }
                    }
                }
            }
            
            if (target != null) {
                RoomUserRotation moveDirection;
                
                if (target.getX() == currentPos.x) {
                    moveDirection = currentPos.y < target.getY() ? RoomUserRotation.SOUTH : RoomUserRotation.NORTH;
                } else if (target.getY() == currentPos.y) {
                    moveDirection = currentPos.x < target.getX() ? RoomUserRotation.EAST : RoomUserRotation.WEST;
                } else if (target.getX() - currentPos.x > target.getY() - currentPos.y) {
                    moveDirection = target.getX() - currentPos.x > 0 ? RoomUserRotation.EAST : RoomUserRotation.WEST;
                } else {
                    moveDirection = target.getY() - currentPos.y > 0 ? RoomUserRotation.SOUTH : RoomUserRotation.NORTH;
                }
                
                RoomTile newTile = layout.getTileInFront(currentTile, moveDirection.getValue());
                if (newTile != null && newTile.state != RoomTileState.INVALID) {
                    if (!simulation.isTileValidForItem(newTile.x, newTile.y, item)) {
                        return false;
                    }
                    if (!simulation.moveItem(item, newTile.x, newTile.y, currentPos.z, currentPos.rotation)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSourceTypes
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new THashSet<>();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.furniSourceTypes = new int[] { normalizeFurniSource(
                    data.furniSourceTypes != null && data.furniSourceTypes.length > 0
                            ? data.furniSourceTypes[0] : FURNI_SOURCE_PICKED_1) };

            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItemByDatabaseId(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (String s : wiredDataOld[1].split(";")) {
                        HabboItem item = room.getHabboItemByDatabaseId(Integer.parseInt(s));

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSourceTypes = new int[0];
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected java.util.Collection<HabboItem> getSelectedItems() { return this.items; }
    @Override
    protected int[] getWiredFurniSourceTypes() { return this.furniSourceTypes; }
    @Override
    protected boolean isWiredAdvancedMode() { return true; }
    @Override
    protected boolean supportsFurniPicking() { return true; }
    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] { FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL };
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.add(it);
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.items.addAll(newItems);
        this.furniSourceTypes = new int[] { normalizeFurniSource(
                settings.getFurniSourceTypes() != null && settings.getFurniSourceTypes().length > 0
                        ? settings.getFurniSourceTypes()[0] : FURNI_SOURCE_PICKED_1) };
        this.setDelay(delay);

        return true;
    }

    private static int normalizeFurniSource(int source) {
        return source == FURNI_SOURCE_SELECTOR || source == FURNI_SOURCE_SIGNAL
                ? source : FURNI_SOURCE_PICKED_1;
    }

    @Override
    protected long requiredCooldown() {
        return 495;
    }

    @Override
    public boolean bypassExecutionCooldown() {
        return true; // chase streams smooth WiredMovements every tick; not gated by the 495ms cooldown
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int[] furniSourceTypes;

        public JsonData(int delay, List<Integer> itemIds, int[] furniSourceTypes) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSourceTypes = furniSourceTypes;
        }
    }
}
