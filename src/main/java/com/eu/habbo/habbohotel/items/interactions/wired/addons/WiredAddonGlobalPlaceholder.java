package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** July AIR add-on 2000: literal or same-owner cross-room global placeholder. */
public final class WiredAddonGlobalPlaceholder extends InteractionWiredAddon {
    private static final int VERSION = 1;
    private static final int MODE_VALUE = 0;
    private static final int MODE_REFERENCE = 1;
    private static final int MAX_NAME = 32;
    private static final int MAX_VALUE = 100;
    private static final int MAX_REFERENCES = 512;
    private static final int MAX_DEPTH = 16;

    private String name = "";
    private String value = "";
    private int mode;
    private int referenceRoomId;

    public WiredAddonGlobalPlaceholder(ResultSet set, Item item) throws SQLException { super(set, item); }
    public WiredAddonGlobalPlaceholder(int id, int userId, Item item, String extraData,
                                       int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }
    @Override public WiredAddonType getType() { return WiredAddonType.GLOBAL_PLACEHOLDER; }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 3
                || settings.getFurniIds().length != 0 || settings.getFurniIds2().length != 0
                || settings.getVariableIds().length != 0 || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 0 || settings.getDelay() != 0) return false;
        int[] params = settings.getIntParams();
        if ((params[0] != MODE_VALUE && params[0] != MODE_REFERENCE) || params[1] != 0
                || (params[0] == MODE_VALUE && params[2] != 0)) return false;
        String[] strings = settings.getStringParam().split("\\t", -1);
        if (strings.length != 2 || !validName(strings[0]) || strings[1].length() > MAX_VALUE) return false;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null) return false;
        if (params[0] == MODE_REFERENCE) {
            Room referenced = activeRoom(params[2]);
            if (referenced == null || referenced.getId() == room.getId()
                    || referenced.getOwnerId() != room.getOwnerId()
                    || find(referenced, strings[1]) == null) return false;
        }
        this.name = strings[0]; this.value = strings[1]; this.mode = params[0];
        this.referenceRoomId = params[0] == MODE_REFERENCE ? params[2] : 0;
        return true;
    }

    public String apply(WiredContext context, String input) {
        if (context == null || input == null || this.name.isEmpty()) return input;
        String token = "$(" + this.name + ")";
        if (!input.contains(token)) return input;
        String resolved = resolve(context.room(), new HashSet<>(), 0);
        return resolved == null ? input : input.replace(token, resolved);
    }

    private String resolve(Room origin, Set<Long> visited, int depth) {
        if (origin == null || depth >= MAX_DEPTH || !visited.add((((long)origin.getId()) << 32) ^ getId())) return null;
        if (this.mode == MODE_VALUE) return this.value;
        Room targetRoom = activeRoom(this.referenceRoomId);
        if (targetRoom == null || targetRoom.getOwnerId() != origin.getOwnerId()) return null;
        WiredAddonGlobalPlaceholder target = find(targetRoom, this.value);
        return target == null ? null : target.resolve(origin, visited, depth + 1);
    }

    @Override public String getWiredData() {
        return WiredManager.getGson().toJson(new Data(VERSION, this.name, this.value, this.mode, this.referenceRoomId));
    }
    @Override public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            Data data = WiredManager.getGson().fromJson(set == null ? null : set.getString("wired_data"), Data.class);
            if (data != null && data.version == VERSION && validName(data.name)
                    && data.value != null && data.value.length() <= MAX_VALUE
                    && (data.mode == MODE_VALUE || data.mode == MODE_REFERENCE)
                    && (data.mode != MODE_VALUE || data.referenceRoomId == 0)
                    && (data.mode != MODE_REFERENCE || data.referenceRoomId > 0)) {
                this.name=data.name; this.value=data.value; this.mode=data.mode;
                this.referenceRoomId=data.referenceRoomId;
            }
        } catch (RuntimeException ignored) { onPickUp(); }
    }
    @Override public void onPickUp() { this.name=""; this.value=""; this.mode=MODE_VALUE; this.referenceRoomId=0; }
    @Override protected int[] getWiredIntParams() { return new int[] {this.mode, 0, this.mode == MODE_REFERENCE ? this.referenceRoomId : 0}; }
    @Override protected String getWiredStringParam() { return this.name + "\t" + this.value; }
    @Override protected int getMaxFurniSelection() { return 0; }

    /** July context block 6: bounded same-owner active-room placeholder catalogue. */
    @Override protected void serializeWiredContext(ServerMessage message, Room room) {
        List<Entry> entries = new ArrayList<>();
        if (room != null) {
            for (Room candidate : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                if (candidate == null || candidate.getId() == room.getId()
                        || candidate.getOwnerId() != room.getOwnerId()) continue;
                for (InteractionWiredAddon addon : candidate.getRoomSpecialTypes().getAddons(WiredAddonType.GLOBAL_PLACEHOLDER)) {
                    if (addon instanceof WiredAddonGlobalPlaceholder placeholder && !placeholder.name.isEmpty()) {
                        entries.add(new Entry(candidate.getId(), candidate.getName(), placeholder.name));
                        if (entries.size() >= MAX_REFERENCES) break;
                    }
                }
                if (entries.size() >= MAX_REFERENCES) break;
            }
        }
        entries.sort(Comparator.comparing(Entry::roomName).thenComparing(Entry::name).thenComparingInt(Entry::roomId));
        message.appendInt(2); serializeVariableCatalogContext(message, room);
        message.appendInt(6); message.appendInt(entries.size());
        for (Entry entry : entries) {
            message.appendInt(entry.roomId()); message.appendString(entry.roomName()); message.appendString(entry.name());
        }
    }

    private static Room activeRoom(int roomId) {
        return roomId <= 0 ? null : Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
    }
    private static WiredAddonGlobalPlaceholder find(Room room, String name) {
        if (room == null || name == null) return null;
        return room.getRoomSpecialTypes().getAddons(WiredAddonType.GLOBAL_PLACEHOLDER).stream()
                .filter(WiredAddonGlobalPlaceholder.class::isInstance)
                .map(WiredAddonGlobalPlaceholder.class::cast)
                .filter(value -> name.equals(value.name))
                .min(Comparator.comparingInt(WiredAddonGlobalPlaceholder::getId)).orElse(null);
    }
    private static boolean validName(String value) {
        return value != null && value.length() >= 1 && value.length() <= MAX_NAME && value.matches("[a-z0-9_]+");
    }
    private record Entry(int roomId, String roomName, String name) { }
    private static final class Data {
        int version; String name, value; int mode, referenceRoomId;
        Data(int version,String name,String value,int mode,int referenceRoomId){this.version=version;this.name=name;this.value=value;this.mode=mode;this.referenceRoomId=referenceRoomId;}
    }
}
