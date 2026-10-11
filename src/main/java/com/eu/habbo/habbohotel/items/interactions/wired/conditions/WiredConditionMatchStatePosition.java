package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredConditionMatchStatePosition extends InteractionWiredCondition
        implements InteractionWiredMatchFurniSettings {
    public static final WiredConditionType type = WiredConditionType.MATCH_SSHOT;

    private THashSet<WiredMatchFurniSetting> settings;

    private boolean state;
    private boolean position;
    private boolean direction;
    private boolean altitude;

    public WiredConditionMatchStatePosition(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new THashSet<>();
    }

    public WiredConditionMatchStatePosition(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new THashSet<>();
    }

    @Override
    public WiredConditionType getType() {
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
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 3)
            return false;
        this.state = settings.getIntParams()[0] == 1;
        this.direction = settings.getIntParams()[1] == 1;
        this.position = settings.getIntParams()[2] == 1;
        this.altitude = settings.getIntParams().length > 3 && settings.getIntParams()[3] == 1;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            return true;

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count"))
            return false;

        this.settings.clear();

        for (int i = 0; i < count; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem item = room.getHabboItem(itemId);

            if (item != null)
                this.settings.add(new WiredMatchFurniSetting(item.getId(), item.getExtradata(), item.getRotation(),
                        item.getX(), item.getY(), item.getZ()));
        }

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (this.settings.isEmpty())
            return true;

        THashSet<WiredMatchFurniSetting> s = new THashSet<>();

        for (WiredMatchFurniSetting setting : this.settings) {
            HabboItem item = room.getHabboItemByDatabaseId(setting.item_id);

            if (item != null) {
                if (this.state) {
                    if (!item.getExtradata().equals(setting.state))
                        return false;
                }

                if (this.position) {
                    if (!(setting.x == item.getX() && setting.y == item.getY()))
                        return false;
                }

                if (this.direction) {
                    if (setting.rotation != item.getRotation())
                        return false;
                }

                if (this.altitude) {
                    if (Double.compare(setting.z, item.getZ()) != 0)
                        return false;
                }
            } else {
                s.add(setting);
            }
        }

        if (!s.isEmpty()) {
            for (WiredMatchFurniSetting setting : s) {
                this.settings.remove(setting);
            }
        }

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.state,
                this.position,
                this.direction,
                this.altitude,
                new ArrayList<>(this.settings)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.state = data.state;
            this.position = data.position;
            this.direction = data.direction;
            this.altitude = data.altitude;
            this.settings.addAll(data.settings);
        } else {
            String[] data = wiredData.split(":");

            int itemCount = Integer.parseInt(data[0]);

            String[] items = data[1].split(";");

            for (int i = 0; i < itemCount; i++) {
                String[] stuff = items[i].split("-");

                if (stuff.length >= 5)
                    this.settings.add(new WiredMatchFurniSetting(Integer.parseInt(stuff[0]), stuff[1],
                            Integer.parseInt(stuff[2]), Integer.parseInt(stuff[3]), Integer.parseInt(stuff[4]),
                            stuff.length > 5 ? Double.parseDouble(stuff[5]) : 0));
            }

            this.state = data[2].equals("1");
            this.direction = data[3].equals("1");
            this.position = data[4].equals("1");
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.direction = false;
        this.position = false;
        this.state = false;
        this.altitude = false;
    }

    private void refresh() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null) {
            THashSet<WiredMatchFurniSetting> remove = new THashSet<>();

            for (WiredMatchFurniSetting setting : this.settings) {
                HabboItem item = room.getHabboItemByDatabaseId(setting.item_id);
                if (item == null) {
                    remove.add(setting);
                }
            }

            for (WiredMatchFurniSetting setting : remove) {
                this.settings.remove(setting);
            }
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
        boolean position;
        boolean direction;
        boolean altitude;
        List<WiredMatchFurniSetting> settings;

        public JsonData(boolean state, boolean position, boolean direction, boolean altitude, List<WiredMatchFurniSetting> settings) {
            this.state = state;
            this.position = position;
            this.direction = direction;
            this.altitude = altitude;
            this.settings = settings;
        }
    }
}
