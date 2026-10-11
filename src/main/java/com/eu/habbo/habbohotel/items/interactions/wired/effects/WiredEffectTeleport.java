package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.AvatarEffectMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateMessageComposer;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectTeleport extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT;
    private static final int NORMAL_TELEPORT_EFFECT = 4;
    private static final int FAST_TELEPORT_EFFECT = 235;
    private static final long ROOM_TICK_MS = 500L;
    private static final long STOP_BEFORE_TELEPORT_MS = 100L;
    private static final int FAST_TELEPORT_TICKS = 1;
    private static final int REGULAR_TELEPORT_TICKS = 3;

    protected List<HabboItem> items;
    protected int[] furniSourceTypes = new int[0];
    protected int[] userSourceTypes = new int[0];
    private boolean fastTeleport;

    public WiredEffectTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new ArrayList<>();
        this.fastTeleport = false;
    }

    public WiredEffectTeleport(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new ArrayList<>();
        this.fastTeleport = false;
    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        teleportUnitToTile(roomUnit, tile, false);
    }

    /**
     * Runtime behaviour adapted from Seth's Arcturus Wired implementation. The
     * packet layout remains the May/July AIR format used by this project.
     */
    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile, boolean fastTeleport) {
        if (roomUnit == null || tile == null || roomUnit.isWiredTeleporting) {
            return;
        }

        Room room = roomUnit.getRoom();
        if (room == null) {
            return;
        }

        roomUnit.isWiredTeleporting = true;

        // Keep a riding pet aligned with its rider until the delayed teleport fires.
        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() != null) {
                RideablePet ridingPet = habbo.getHabboInfo().getRiding();
                RoomUnit petUnit = ridingPet.getRoomUnit();
                if (petUnit != null) {
                    RoomTile riderTile = roomUnit.getCurrentLocation();
                    petUnit.setLocation(riderTile);
                    petUnit.setZ(roomUnit.getZ() - 1.0);
                    petUnit.setPreviousLocation(riderTile);
                    petUnit.setGoalLocation(riderTile);
                    petUnit.removeStatus(RoomUnitStatus.MOVE);
                    petUnit.setCanWalk(false);
                    room.sendComposer(new UserUpdateMessageComposer(petUnit).compose());
                }
            }
        }

        room.unIdle(room.getHabbo(roomUnit));
        int teleportEffect = fastTeleport ? FAST_TELEPORT_EFFECT : NORMAL_TELEPORT_EFFECT;
        long teleportDelay = getDelayToRoomTick(
                room, fastTeleport ? FAST_TELEPORT_TICKS : REGULAR_TELEPORT_TICKS);

        room.sendComposer(new AvatarEffectMessageComposer(roomUnit, teleportEffect).compose());
        Emulator.getThreading().run(
                new SendRoomUnitEffectComposer(room, roomUnit), teleportDelay + 1000);

        boolean canWalkBeforeTeleport = roomUnit.canWalk();
        short targetX = tile.x;
        short targetY = tile.y;
        Emulator.getThreading().run(
                () -> stopUnitBeforeTeleport(room, roomUnit),
                Math.max(0, teleportDelay - STOP_BEFORE_TELEPORT_MS));
        Emulator.getThreading().run(
                () -> finishTeleportToTile(room, roomUnit, targetX, targetY, canWalkBeforeTeleport),
                teleportDelay);
    }

    private static void stopUnitBeforeTeleport(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null || roomUnit.getRoom() != room) {
            return;
        }

        roomUnit.stopWalking();
        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setCanWalk(false);
        room.sendComposer(new UserUpdateMessageComposer(roomUnit).compose());
    }

    private static void finishTeleportToTile(Room room, RoomUnit roomUnit, short targetX, short targetY,
                                             boolean canWalkBeforeTeleport) {
        try {
            if (room == null || roomUnit == null || roomUnit.getRoom() != room || room.getLayout() == null) {
                return;
            }

            RoomTile targetTile = room.getLayout().getTile(targetX, targetY);
            RoomTile destinationTile = resolveTeleportDestination(room, roomUnit, targetTile);
            if (destinationTile == null) {
                return;
            }

            if (destinationTile.equals(roomUnit.getCurrentLocation())) {
                roomUnit.setPath(new LinkedList<>());
                roomUnit.removeStatus(RoomUnitStatus.MOVE);
                roomUnit.statusUpdate(true);
                return;
            }

            double z = destinationTile.getStackHeight()
                    + (destinationTile.state == RoomTileState.SIT ? -0.5 : 0);
            new RoomUnitTeleport(roomUnit, room, destinationTile.x, destinationTile.y, z,
                    roomUnit.getEffectId()).run();
        } finally {
            if (roomUnit != null) {
                if (roomUnit.getRoom() == room) {
                    roomUnit.setCanWalk(canWalkBeforeTeleport);
                }
                roomUnit.isWiredTeleporting = false;
            }
        }
    }

    private static RoomTile resolveTeleportDestination(Room room, RoomUnit roomUnit, RoomTile targetTile) {
        if (targetTile == null) {
            return null;
        }

        if (isTeleportLandingTile(room, roomUnit, targetTile)) {
            return targetTile;
        }

        if (targetTile.state == RoomTileState.INVALID
                || targetTile.state == RoomTileState.BLOCKED
                || hasOtherUnits(roomUnit, targetTile)) {
            List<RoomTile> optionalTiles = room.getLayout().getTilesAround(targetTile);
            Collections.reverse(optionalTiles);
            for (RoomTile optionalTile : optionalTiles) {
                if (isTeleportLandingTile(room, roomUnit, optionalTile)) {
                    return optionalTile;
                }
            }
        }

        return targetTile.state == RoomTileState.INVALID ? null : targetTile;
    }

    private static boolean isTeleportLandingTile(Room room, RoomUnit roomUnit, RoomTile tile) {
        return tile != null
                && tile.state != RoomTileState.INVALID
                && tile.state != RoomTileState.BLOCKED
                && (room.isAllowWalkthrough() || !hasOtherUnits(roomUnit, tile));
    }

    private static boolean hasOtherUnits(RoomUnit roomUnit, RoomTile tile) {
        if (tile == null || !tile.hasUnits()) {
            return false;
        }

        for (RoomUnit unit : tile.getUnits()) {
            if (unit != roomUnit) {
                return true;
            }
        }
        return false;
    }

    private static long getDelayToRoomTick(Room room, int ticksFromNow) {
        long elapsedSinceLastTick = Math.max(0L, System.currentTimeMillis() - room.getCycleTimestamp());
        long delayToNextTick = ROOM_TICK_MS - (elapsedSinceLastTick % ROOM_TICK_MS);
        return delayToNextTick + ((long) Math.max(0, ticksFromNow - 1) * ROOM_TICK_MS);
    }

    // Wired 2.0 getters
    @Override
    protected java.util.Collection<HabboItem> getSelectedItems() { return this.items; }
    @Override
    protected boolean supportsFurniPicking() { return true; }
    @Override
    protected boolean supportsUserPicking() { return true; }
    @Override
    protected boolean isWiredAdvancedMode() { return true; }
    @Override
    protected int[] getWiredFurniSourceTypes() { return this.furniSourceTypes; }
    @Override
    protected int[] getWiredUserSourceTypes() { return this.userSourceTypes; }
    @Override
    protected int[] getWiredIntParams() { return new int[] { this.fastTeleport ? 1 : 0 }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId()
                || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItemByDatabaseId(item.getId()) == null);
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

        int[] intParams = settings.getIntParams();
        if (intParams.length > 1
                || (intParams.length == 1 && intParams[0] != 0 && intParams[0] != 1)) {
            throw new WiredSaveException("Invalid fast teleport option");
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.furniSourceTypes = settings.getFurniSourceTypes() != null ? settings.getFurniSourceTypes() : new int[0];
        this.userSourceTypes = settings.getUserSourceTypes() != null ? settings.getUserSourceTypes() : new int[0];
        this.fastTeleport = intParams.length == 1 && intParams[0] == 1;
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            return;
        }
        
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId()
                || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItemByDatabaseId(item.getId()) == null);

        List<HabboItem> sourceItems = new ArrayList<>(resolveFurniSource(ctx, this.furniSourceTypes, 0, this.items, null));
        if (!sourceItems.isEmpty()) {
            int i = Emulator.getRandom().nextInt(sourceItems.size());
            HabboItem item = sourceItems.get(i);
            
            if (item == null) return;

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile != null) {
                for (RoomUnit roomUnit : resolveUserSource(ctx, this.userSourceTypes, 0)) {
                    teleportUnitToTile(roomUnit, tile, this.fastTeleport);
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
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.getDelay(),
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
            this.furniSourceTypes,
            this.userSourceTypes,
            this.fastTeleport
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new ArrayList<>();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.furniSourceTypes = data.furniSourceTypes != null ? data.furniSourceTypes : new int[0];
            this.userSourceTypes = data.userSourceTypes != null ? data.userSourceTypes : new int[0];
            this.fastTeleport = data.fastTeleport;
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
            if (wiredDataOld.length >= 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (String s : wiredDataOld[1].split(";")) {
                        HabboItem item = room.getHabboItemByDatabaseId(Integer.parseInt(s));

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }
            this.fastTeleport = wiredDataOld.length >= 3 && "1".equals(wiredDataOld[2]);
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.fastTeleport = false;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return false;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_DEFAULT;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int[] furniSourceTypes;
        int[] userSourceTypes;
        boolean fastTeleport;

        public JsonData(int delay, List<Integer> itemIds, int[] furniSourceTypes, int[] userSourceTypes,
                        boolean fastTeleport) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSourceTypes = furniSourceTypes;
            this.userSourceTypes = userSourceTypes;
            this.fastTeleport = fastTeleport;
        }
    }
}
