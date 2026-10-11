package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerBotReachedHabbo extends InteractionWiredTrigger {
    public final static WiredTriggerType type = WiredTriggerType.BOT_REACHED_AVTR;

    private String botName = "";
    private static final int USER_SOURCE_PICKED_BOT = 100;

    public WiredTriggerBotReachedHabbo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerBotReachedHabbo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected String getWiredStringParam() { return this.botName; }

    @Override
    protected int getUserSourceSlotCount() { return 1; }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] { USER_SOURCE_TRIGGERING_USER, USER_SOURCE_PICKED_BOT, USER_SOURCE_SIGNAL };
    }

    @Override
    protected int getDefaultUserSourceForSlot(int slot) { return USER_SOURCE_PICKED_BOT; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings == null || settings.getStringParam() == null
                || settings.getStringParam().trim().length() > 32) {
            return false;
        }
        this.botName = settings.getStringParam().trim();

        return true;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit botUnit = event.getActor().orElse(null);
        RoomUnit reachedUser = event.getTargetUnit().orElse(null);
        Room room = event.getRoom();
        if (botUnit == null || room.getBot(botUnit) == null
                || reachedUser == null || room.getHabbo(reachedUser) == null) {
            return false;
        }
        java.util.Collection<RoomUnit> bots = switch (getWiredUserSourceTypes()[0]) {
            case USER_SOURCE_TRIGGERING_USER -> java.util.List.of(botUnit);
            case USER_SOURCE_SIGNAL -> event.getSignalPayload().users(room);
            default -> room.getBots(this.botName).stream()
                    .map(bot -> bot.getRoomUnit())
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
        };
        return bots.contains(botUnit);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.botName
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data == null || data.botName == null ? "" : data.botName.trim();
        } else {
            this.botName = wiredData;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        String botName;

        public JsonData(String botName) {
            this.botName = botName;
        }
    }
}
