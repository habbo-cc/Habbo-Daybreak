package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** July trigger 21: fires when an avatar clicks one of the selected invisible click tiles. */
public class WiredTriggerAvatarClicksTile extends InteractionWiredTrigger {
    private static final WiredTriggerType TYPE = WiredTriggerType.CLICK_TILE;
    private static final String CLICK_TILE_ITEM_NAME = "room_invisible_click_tile";
    private static final String INVALID_SELECTION_KEY = "wiredfurni.error.require_click_tiles";
    private static final int MAX_CLICK_TILES = 5;

    private final THashSet<HabboItem> items = new THashSet<>();

    public WiredTriggerAvatarClicksTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerAvatarClicksTile(int id, int userId, Item item, String extradata,
                                        int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return TYPE;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event.getType() == WiredEvent.Type.USER_CLICKS_TILE
                && this.items.contains(event.getSourceItem().orElse(null));
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null || data.itemIds == null) {
                return;
            }

            for (Integer itemId : data.itemIds) {
                HabboItem item = itemId == null ? null : room.getHabboItemByDatabaseId(itemId);
                if (isInvisibleClickTile(item) && this.items.size() < MAX_CLICK_TILES) {
                    this.items.add(item);
                }
            }
        } catch (RuntimeException ignored) {
            this.items.clear();
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings == null || settings.getFurniIds().length > MAX_CLICK_TILES) {
            return false;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        THashSet<HabboItem> selected = new THashSet<>();
        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItemByDatabaseId(itemId);
            if (!isInvisibleClickTile(item)) {
                return false;
            }
            selected.add(item);
        }

        this.items.clear();
        this.items.addAll(selected);
        return true;
    }

    @Override
    public String getSaveErrorLocalizationKey() {
        return INVALID_SELECTION_KEY;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.items;
    }

    @Override
    protected boolean supportsFurniPicking() {
        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.items.removeIf(item -> item.getRoomId() != this.getRoomId()
                || room.getHabboItemByDatabaseId(item.getId()) == null
                || !isInvisibleClickTile(item));

        message.appendBoolean(false);
        message.appendInt(MAX_CLICK_TILES);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getRoomVisibleId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(TYPE.code);
        message.appendInt(0);
    }

    private static boolean isInvisibleClickTile(HabboItem item) {
        return item != null
                && item.getBaseItem() != null
                && CLICK_TILE_ITEM_NAME.equalsIgnoreCase(item.getBaseItem().getName());
    }

    static final class JsonData {
        List<Integer> itemIds = Collections.emptyList();

        JsonData() {
        }

        JsonData(List<Integer> itemIds) {
            this.itemIds = itemIds;
        }
    }
}
