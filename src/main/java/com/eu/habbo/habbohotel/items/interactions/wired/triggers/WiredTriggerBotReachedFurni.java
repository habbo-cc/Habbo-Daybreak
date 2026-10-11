package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredTriggerBotReachedFurni extends InteractionWiredTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredTriggerBotReachedFurni.class);

    public final static WiredTriggerType type = WiredTriggerType.BOT_REACHED_STF;

    private THashSet<HabboItem> items;
    private String botName = "";
    private static final int USER_SOURCE_PICKED_BOT = 100;

    public WiredTriggerBotReachedFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredTriggerBotReachedFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected java.util.Collection<HabboItem> getSelectedItems() { return this.items; }

    @Override
    protected boolean supportsFurniPicking() { return true; }

    @Override
    protected String getWiredStringParam() { return this.botName; }

    @Override
    protected int getFurniSourceSlotCount() { return 1; }

    @Override
    protected int getUserSourceSlotCount() { return 1; }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] { FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SIGNAL };
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] { USER_SOURCE_TRIGGERING_USER, USER_SOURCE_PICKED_BOT, USER_SOURCE_SIGNAL };
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) { return FURNI_SOURCE_PICKED_1; }

    @Override
    protected int getDefaultUserSourceForSlot(int slot) { return USER_SOURCE_PICKED_BOT; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings == null || settings.getStringParam() == null
                || settings.getStringParam().trim().length() > 32
                || settings.getFurniIds() == null || settings.getFurniIds().length > 20) {
            return false;
        }
        this.botName = settings.getStringParam().trim();

        this.items.clear();

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();
        
        // Get the furniture item the bot walked onto
        HabboItem sourceItem = event.getSourceItem().orElse(null);
        if (sourceItem == null || roomUnit == null || room.getBot(roomUnit) == null) {
            return false;
        }
        
        java.util.Collection<HabboItem> furni = switch (getWiredFurniSourceTypes()[0]) {
            case FURNI_SOURCE_TRIGGERING_ITEM -> java.util.List.of(sourceItem);
            case FURNI_SOURCE_SIGNAL -> event.getSignalPayload().items(room);
            default -> this.items;
        };
        boolean furniMatched = furni.stream().anyMatch(item -> item == sourceItem
                || event.getTile().map(tile -> item.getX() == tile.getX() && item.getY() == tile.getY()).orElse(false));

        java.util.Collection<RoomUnit> bots = switch (getWiredUserSourceTypes()[0]) {
            case USER_SOURCE_TRIGGERING_USER -> java.util.List.of(roomUnit);
            case USER_SOURCE_SIGNAL -> event.getSignalPayload().users(room);
            default -> room.getBots(this.botName).stream()
                    .map(bot -> bot.getRoomUnit())
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        };
        return furniMatched && bots.contains(roomUnit);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.botName,
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data == null || data.botName == null ? "" : data.botName.trim();
            for (Integer id: data == null || data.itemIds == null ? java.util.List.<Integer>of() : data.itemIds) {
                HabboItem item = room.getHabboItemByDatabaseId(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length == 1) {
                this.botName = data[0];
            } else if (data.length == 2) {
                this.botName = data[0];

                String[] items = data[1].split(";");

                for (String id : items) {
                    try {
                        HabboItem item = room.getHabboItemByDatabaseId(Integer.parseInt(id));

                        if (item != null)
                            this.items.add(item);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.botName = "";
    }

    static class JsonData {
        String botName;
        List<Integer> itemIds;

        public JsonData(String botName, List<Integer> itemIds) {
            this.botName = botName;
            this.itemIds = itemIds;
        }
    }
}
