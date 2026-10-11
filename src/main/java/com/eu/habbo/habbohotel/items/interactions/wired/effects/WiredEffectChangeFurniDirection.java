package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.*;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WiredEffectChangeFurniDirection extends InteractionWiredEffect {
    public static final int ACTION_WAIT = 0;
    public static final int ACTION_TURN_RIGHT_45 = 1;
    public static final int ACTION_TURN_RIGHT_90 = 2;
    public static final int ACTION_TURN_LEFT_45 = 3;
    public static final int ACTION_TURN_LEFT_90 = 4;
    public static final int ACTION_TURN_BACK = 5;
    public static final int ACTION_TURN_RANDOM = 6;

    public static final WiredEffectType type = WiredEffectType.MOVE_DIRECTION;

    private final THashMap<HabboItem, WiredChangeDirectionSetting> items = new THashMap<>(0);
    private RoomUserRotation startRotation = RoomUserRotation.NORTH;
    private int blockedAction = 0;
    private int blockOnCollision = 0;

    public WiredEffectChangeFurniDirection(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeFurniDirection(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;
        
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items.keySet()) {
            if (item == null || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItemByDatabaseId(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        if (this.items.isEmpty()) return;
        Set<HabboItem> movementTargets = new HashSet<>(
                WiredMovementAddonRuntime.furniTargets(ctx, this.items.keySet()));

        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> entry : this.items.entrySet()) {
            HabboItem item = entry.getKey();
            if (item == null || entry.getValue() == null || !movementTargets.contains(item)) continue;
            
            RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
            if (itemTile == null) continue;
            
            RoomTile targetTile = room.getLayout().getTileInFront(itemTile, entry.getValue().direction.getValue());
            int targetRotation = item.getRotation() != entry.getValue().rotation
                    ? entry.getValue().rotation
                    : item.getRotation();

            int count = 1;
            while ((targetTile == null
                    || targetTile.state == RoomTileState.INVALID
                    || room.furnitureFitsAt(targetTile, item, targetRotation, false)
                            != FurnitureMovementError.NONE)
                    && count < 8) {
                entry.getValue().direction = this.nextRotation(entry.getValue().direction);

                RoomTile tile = room.getLayout().getTileInFront(itemTile, entry.getValue().direction.getValue());
                if (tile != null && tile.state != RoomTileState.INVALID) {
                    targetTile = tile;
                }

                count++;
            }
        }

        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> entry : this.items.entrySet()) {
            HabboItem item = entry.getKey();
            if (item == null || entry.getValue() == null || !movementTargets.contains(item)) continue;
            
            int newDirection = entry.getValue().direction.getValue();

            RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
            if (itemTile == null) continue;
            
            RoomTile targetTile = room.getLayout().getTileInFront(itemTile, newDirection);
            if (targetTile == null || targetTile.state == RoomTileState.INVALID) continue;

            int targetRotation = item.getRotation() != entry.getValue().rotation
                    ? entry.getValue().rotation
                    : item.getRotation();
            RoomUnit collisionTarget = null;
            if (this.blockOnCollision != 0) {
                Set<RoomUnit> carryTargets = WiredMovementAddonRuntime.carryTargets(
                        ctx, room, item, itemTile);
                THashSet<RoomTile> newOccupiedTiles = room.getLayout().getTilesAt(
                        targetTile,
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        targetRotation);
                for (RoomTile tile : newOccupiedTiles) {
                    for (RoomUnit roomUnit : room.getRoomUnits(tile)) {
                        if (!carryTargets.contains(roomUnit)
                                && !WiredMovementAddonRuntime.bypassUnitCollision(
                                        WiredMovementAddonRuntime.activePhysics(), roomUnit)) {
                            collisionTarget = roomUnit;
                            break;
                        }
                    }
                    if (collisionTarget != null) {
                        break;
                    }
                }
            }

            if (collisionTarget != null) {
                RoomUnit blockedUnit = collisionTarget;
                Emulator.getThreading().run(
                        () -> WiredManager.triggerBotCollision(room, blockedUnit));
                continue;
            }

            if (room.furnitureFitsAt(targetTile, item, targetRotation, false)
                    != FurnitureMovementError.NONE) {
                continue;
            }

            RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
            double oldZ = item.getZ();
            if (oldLocation != null
                    && WiredMovementAddonRuntime.move(
                            ctx,
                            room,
                            item,
                            targetTile,
                            targetRotation,
                            false,
                            this.blockOnCollision != 0) == FurnitureMovementError.NONE) {
                // One authoritative move produces one July 7115 record. The previous
                // rotate-first path emitted a legacy update followed by a zero-distance slide.
                WiredMovementAddonRuntime.moved(
                        ctx, room, item, oldLocation, oldZ, targetTile);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        ArrayList<WiredChangeDirectionSetting> settings = new ArrayList<>(this.items.values());
        return WiredManager.getGson().toJson(new JsonData(this.startRotation, this.blockedAction, this.blockOnCollision, settings, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {

        this.items.clear();

        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.startRotation = data.start_direction;
            this.blockedAction = data.blocked_action;
            this.blockOnCollision = data.block_on_collision;

            for(WiredChangeDirectionSetting setting : data.items) {
                HabboItem item = room.getHabboItemByDatabaseId(setting.item_id);

                if (item != null) {
                    this.items.put(item, setting);
                }
            }
        }
        else {
            String[] data = wiredData.split("\t");

            if (data.length >= 4) {
                this.setDelay(Integer.parseInt(data[0]));
                this.startRotation = RoomUserRotation.fromValue(Integer.parseInt(data[1]));
                this.blockedAction = Integer.parseInt(data[2]);

                int itemCount = Integer.parseInt(data[3]);

                if (itemCount > 0) {
                    for (int i = 4; i < data.length; i++) {
                        String[] subData = data[i].split(":");

                        if (subData.length >= 2) {
                            HabboItem item = room.getHabboItemByDatabaseId(Integer.parseInt(subData[0]));

                            if (item != null) {
                                int rotation = item.getRotation();

                                if (subData.length > 2) {
                                    rotation = Integer.parseInt(subData[2]);
                                }

                                this.items.put(item, new WiredChangeDirectionSetting(item.getId(), rotation, RoomUserRotation.fromValue(Integer.parseInt(subData[1]))));
                            }
                        }
                    }
                }
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.items.clear();
        this.blockedAction = 0;
        this.blockOnCollision = 0;
        this.startRotation = RoomUserRotation.NORTH;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    // Wired 2.0 getters (items keyed in a Map; keys are live HabboItems)
    @Override
    protected java.util.Collection<HabboItem> getSelectedItems() { return this.items.keySet(); }

    @Override
    protected boolean supportsFurniPicking() { return true; }

    @Override
    protected int[] getWiredIntParams() {
        return new int[]{ this.startRotation != null ? this.startRotation.getValue() : 0, this.blockedAction, this.blockOnCollision };
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> item : this.items.entrySet()) {
            message.appendInt(item.getKey().getRoomVisibleId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.startRotation != null ? this.startRotation.getValue() : 0);
        message.appendInt(this.blockedAction);
        message.appendInt(this.blockOnCollision);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 2) throw new WiredSaveException("Invalid data");

        int startDirectionInt = settings.getIntParams()[0];

        if(startDirectionInt < 0 || startDirectionInt > 7) {
            throw new WiredSaveException("Start direction is invalid");
        }

        RoomUserRotation startDirection = RoomUserRotation.fromValue(startDirectionInt);

        int blockedActionInt = settings.getIntParams()[1];

        if(blockedActionInt < 0 || blockedActionInt > 6) {
            throw new WiredSaveException("Blocked action is invalid");
        }

        int blockOnCollision = settings.getIntParams().length > 2 ? settings.getIntParams()[2] : 0;

        if(blockOnCollision < 0 || blockOnCollision > 1) {
            throw new WiredSaveException("Block on collision is invalid");
        }

        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        THashMap<HabboItem, WiredChangeDirectionSetting> newItems = new THashMap<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.put(it, new WiredChangeDirectionSetting(it.getId(), it.getRotation(), startDirection));
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.items.putAll(newItems);
        this.startRotation = startDirection;
        this.blockedAction = blockedActionInt;
        this.blockOnCollision = blockOnCollision;
        this.setDelay(delay);

        return true;
    }

    private RoomUserRotation nextRotation(RoomUserRotation currentRotation) {
        switch (this.blockedAction) {
            case ACTION_TURN_BACK:
                return RoomUserRotation.fromValue(currentRotation.getValue()).getOpposite();
            case ACTION_TURN_LEFT_45:
                return RoomUserRotation.counterClockwise(currentRotation);
            case ACTION_TURN_LEFT_90:
                return RoomUserRotation.counterClockwise(RoomUserRotation.counterClockwise(currentRotation));
            case ACTION_TURN_RIGHT_45:
                return RoomUserRotation.clockwise(currentRotation);
            case ACTION_TURN_RIGHT_90:
                return RoomUserRotation.clockwise(RoomUserRotation.clockwise(currentRotation));
            case ACTION_TURN_RANDOM:
                return RoomUserRotation.fromValue(Emulator.getRandom().nextInt(8));
            case ACTION_WAIT:
            default:
                return currentRotation;
        }
    }

    @Override
    protected long requiredCooldown() {
        return 495;
    }

    static class JsonData {
        RoomUserRotation start_direction;
        int blocked_action;
        int block_on_collision;
        List<WiredChangeDirectionSetting> items;
        int delay;

        public JsonData(RoomUserRotation start_direction, int blocked_action, int block_on_collision, List<WiredChangeDirectionSetting> items, int delay) {
            this.start_direction = start_direction;
            this.blocked_action = blocked_action;
            this.block_on_collision = block_on_collision;
            this.items = items;
            this.delay = delay;
        }
    }
}
