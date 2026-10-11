package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.items.WiredMovementsMessageComposer;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class WiredEffectMatchFurni extends InteractionWiredEffect implements InteractionWiredMatchFurniSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectMatchFurni.class);

    private static final WiredEffectType type = WiredEffectType.MATCH_SSHOT;
    public boolean checkForWiredResetPermission = true;
    private THashSet<WiredMatchFurniSetting> settings;
    private boolean state = false;
    private boolean direction = false;
    private boolean position = false;
    private boolean altitude = false;

    public WiredEffectMatchFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new THashSet<>(0);
    }

    public WiredEffectMatchFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new THashSet<>(0);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (this.settings.isEmpty())
            return;

        if (room.getLayout() == null)
            return;

        List<WiredMatchFurniSetting> ordered = this.orderedSettings(room);
        List<HabboItem> configuredItems = new ArrayList<>();
        for (WiredMatchFurniSetting setting : ordered) {
            HabboItem configured = room.getHabboItemByDatabaseId(setting.item_id);
            if (configured != null) configuredItems.add(configured);
        }
        Set<HabboItem> movementTargets = new HashSet<>(
                WiredMovementAddonRuntime.furniTargets(ctx, configuredItems));

        for (WiredMatchFurniSetting setting : ordered) {
            HabboItem item = room.getHabboItemByDatabaseId(setting.item_id);
            if (item != null && movementTargets.contains(item)) {
                if (this.state && (this.checkForWiredResetPermission && item.allowWiredResetState())) {
                    if (!setting.state.equals(" ")) {
                        if (Emulator.getGameEnvironment().getChestManager().isChest(item)) {
                            Emulator.getGameEnvironment().getChestManager()
                                    .setStateFromWired(room, item, setting.state);
                        } else if (!item.getExtradata().equals(setting.state)) {
                            item.setExtradata(setting.state);
                            room.updateItemState(item);
                        }
                    }
                }

                RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
                if (oldLocation == null)
                    continue;
                double oldZ = item.getZ();

                boolean forceUpdate = false;
                boolean animatedAltitudeMove = false;

                if (this.direction && !this.position) {
                    if (item.getRotation() != setting.rotation && room.furnitureFitsAt(oldLocation, item,
                            setting.rotation, false) == FurnitureMovementError.NONE) {
                        WiredMovementAddonRuntime.move(ctx, room, item, oldLocation, setting.rotation, true);
                        forceUpdate = this.altitude;
                    }
                } else if (this.position) {
                    boolean slideAnimation = !this.altitude && (!this.direction || item.getRotation() == setting.rotation);
                    RoomTile newLocation = room.getLayout().getTile((short) setting.x, (short) setting.y);
                    int newRotation = this.direction ? setting.rotation : item.getRotation();
                    boolean shouldMove = newLocation != oldLocation || newRotation != item.getRotation() || !this.altitude
                            || (this.altitude && item.getZ() != setting.z);

                    if (newLocation != null && newLocation.state != RoomTileState.INVALID
                            && shouldMove
                            && room.furnitureFitsAt(newLocation, item, newRotation,
                                    !this.altitude) == FurnitureMovementError.NONE) {
                        if (WiredMovementAddonRuntime.move(ctx, room, item, newLocation, newRotation,
                                !this.altitude && !slideAnimation, !this.altitude) == FurnitureMovementError.NONE) {
                            forceUpdate = this.altitude;
                            if (this.altitude) {
                                item.setZ(setting.z);
                                item.needsUpdate(true);
                                Emulator.getThreading().run(item);
                                room.updateTiles(room.getLayout().getTilesAt(newLocation, item.getBaseItem().getWidth(),
                                        item.getBaseItem().getLength(), item.getRotation()));
                                // Wired 2.0: stream a smooth WiredMovements slide instead of the legacy roller hop.
                                WiredMovementAddonRuntime.moved(ctx, room, item, oldLocation, oldZ, newLocation);
                                animatedAltitudeMove = true;
                            } else if (slideAnimation) {
                                // Wired 2.0: stream a smooth WiredMovements slide instead of the legacy roller hop.
                                WiredMovementAddonRuntime.moved(ctx, room, item, oldLocation, oldZ, newLocation);
                            }
                        }
                    }
                }

                if (this.altitude && !animatedAltitudeMove) {
                    if (item.getZ() != setting.z) {
                        item.setZ(setting.z);
                        forceUpdate = true;
                    }
                    if (forceUpdate) {
                        item.needsUpdate(true);
                        room.updateItem(item);
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
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(this.state, this.direction, this.position, this.altitude,
                new ArrayList<WiredMatchFurniSetting>(this.settings), this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.state = data.state;
            this.direction = data.direction;
            this.position = data.position;
            this.altitude = data.altitude;
            this.settings.clear();
            this.settings.addAll(data.items);
        } else {
            String[] data = set.getString("wired_data").split(":");

            Integer.parseInt(data[0]); // itemCount - consumed but unused, data[1] contains actual items

            String[] items = data[1].split(Pattern.quote(";"));

            for (int i = 0; i < items.length; i++) {
                try {

                    String[] stuff = items[i].split(Pattern.quote("-"));

                    if (stuff.length >= 5) {
                        this.settings.add(new WiredMatchFurniSetting(Integer.parseInt(stuff[0]), stuff[1],
                                Integer.parseInt(stuff[2]), Integer.parseInt(stuff[3]), Integer.parseInt(stuff[4]),
                                stuff.length > 5 ? Double.parseDouble(stuff[5]) : 0));
                    }

                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }

            this.state = data[2].equals("1");
            this.direction = data[3].equals("1");
            this.position = data[4].equals("1");
            this.setDelay(Integer.parseInt(data[5]));
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.state = false;
        this.direction = false;
        this.position = false;
        this.altitude = false;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    // Wired 2.0 getters (settings-backed items resolved against the current room)
    @Override
    protected java.util.Collection<HabboItem> getSelectedItems() {
        Room room = Emulator.getGameEnvironment().getRoomManager()
                .getRoom(this.getRoomId());
        if (room == null) {
            return List.of();
        }
        return this.settings.stream()
                .map(setting -> room.getHabboItemByDatabaseId(setting.item_id))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    protected boolean sanitizeSelectedItems(Room room) {
        return room != null && this.settings.removeIf(setting -> {
            HabboItem item = setting == null ? null : room.getHabboItemByDatabaseId(setting.item_id);
            return !isCurrentSelectedItem(room, item);
        });
    }

    @Override
    protected int[] getSelectedItemVisibleIds(Room room) {
        if (room == null) {
            return new int[0];
        }
        return this.settings.stream()
                .map(setting -> setting == null ? null : room.getHabboItemByDatabaseId(setting.item_id))
                .filter(item -> isCurrentSelectedItem(room, item))
                .mapToInt(HabboItem::getRoomVisibleId)
                .toArray();
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[]{ this.state ? 1 : 0, this.direction ? 1 : 0, this.position ? 1 : 0, this.altitude ? 1 : 0 };
    }

    @Override
    protected boolean supportsFurniPicking() {
        return true;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.settings.size());

        for (WiredMatchFurniSetting setting : this.settings) {
            HabboItem matchItem = room.getHabboItemByDatabaseId(setting.item_id);
            message.appendInt(matchItem != null ? matchItem.getRoomVisibleId() : setting.item_id);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.state ? 1 : 0);
        message.appendInt(this.direction ? 1 : 0);
        message.appendInt(this.position ? 1 : 0);
        message.appendInt(this.altitude ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams().length < 3)
            throw new WiredSaveException("Invalid data");
        boolean setState = settings.getIntParams()[0] == 1;
        boolean setDirection = settings.getIntParams()[1] == 1;
        boolean setPosition = settings.getIntParams()[2] == 1;
        boolean setAltitude = settings.getIntParams().length > 3 && settings.getIntParams()[3] == 1;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            throw new WiredSaveException("Trying to save wired in unloaded room");

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<WiredMatchFurniSetting> newSettings = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = room.getHabboItem(itemId);

            if (it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newSettings.add(new WiredMatchFurniSetting(it.getId(),
                    this.checkForWiredResetPermission && it.allowWiredResetState() ? it.getExtradata() : " ",
                    it.getRotation(), it.getX(), it.getY(), it.getZ()));
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.state = setState;
        this.direction = setDirection;
        this.position = setPosition;
        this.altitude = setAltitude;
        this.settings.clear();
        this.settings.addAll(newSettings);
        this.setDelay(delay);

        return true;
    }

    private List<WiredMatchFurniSetting> orderedSettings(Room room) {
        List<WiredMatchFurniSetting> ordered = new ArrayList<>(this.settings);
        if (this.altitude) {
            ordered.sort(Comparator.comparingDouble(setting -> setting.z));
        } else if (this.position) {
            ordered.sort(Comparator
                    .comparingDouble((WiredMatchFurniSetting setting) -> this.distanceToSavedTile(room, setting))
                    .thenComparingDouble(setting -> this.currentZ(room, setting)));
        }
        return ordered;
    }

    private double distanceToSavedTile(Room room, WiredMatchFurniSetting setting) {
        HabboItem item = room.getHabboItemByDatabaseId(setting.item_id);
        if (item == null) {
            return Double.MAX_VALUE;
        }

        double dx = item.getX() - setting.x;
        double dy = item.getY() - setting.y;
        return (dx * dx) + (dy * dy);
    }

    private double currentZ(Room room, WiredMatchFurniSetting setting) {
        HabboItem item = room.getHabboItemByDatabaseId(setting.item_id);
        return item != null ? item.getZ() : Double.MAX_VALUE;
    }

    private void refresh() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null && room.isLoaded()) {
            // Use removeIf for O(n) instead of O(n²) with separate remove set
            this.settings.removeIf(setting -> room.getHabboItemByDatabaseId(setting.item_id) == null);
        }
    }

    @Override
    public THashSet<WiredMatchFurniSetting> getMatchFurniSettings() {
        return this.settings;
    }

    @Override
    public boolean shouldMatchState() {
        return this.state;
    }

    @Override
    public boolean shouldMatchRotation() {
        return this.direction;
    }

    @Override
    public boolean shouldMatchPosition() {
        return this.position;
    }

    @Override
    public boolean shouldMatchAltitude() {
        return this.altitude;
    }

    static class JsonData {
        boolean state;
        boolean direction;
        boolean position;
        boolean altitude;
        List<WiredMatchFurniSetting> items;
        int delay;

        public JsonData(boolean state, boolean direction, boolean position, boolean altitude, List<WiredMatchFurniSetting> items,
                int delay) {
            this.state = state;
            this.direction = direction;
            this.position = position;
            this.altitude = altitude;
            this.items = items;
            this.delay = delay;
        }
    }
}
