package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerHabboSaysKeyword extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.SAY_SOMETHING;

    private boolean hide = false;
    private int matchType = 0;
    private boolean ownerOnly = false;
    private String key = "";

    public WiredTriggerHabboSaysKeyword(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerHabboSaysKeyword(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        String text = event.getText().orElse(null);
        if (text == null) {
            return false;
        }

        if (!this.matchesText(text)) {
            return false;
        }

        return matchesActorGate(event);
    }

    private boolean matchesText(String text) {
        if (this.matchType == 2) {
            return true;
        }

        if (this.key.length() == 0) {
            return false;
        }

        if (this.matchType == 1) {
            return text.equalsIgnoreCase(this.key);
        }

        return text.toLowerCase().contains(this.key.toLowerCase());
    }

    public String getKey() { return this.key; }
    public int getMatchType() { return this.matchType; }

    public boolean matchesActorGate(WiredEvent event) {
        if (event == null) return false;
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();
        Habbo habbo = roomUnit == null ? null : room.getHabbo(roomUnit);
        return !this.ownerOnly || (habbo != null && room.getOwnerId() == habbo.getHabboInfo().getId());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.hide,
            this.matchType,
            this.ownerOnly,
            this.key
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.hide = data.hide;
            this.matchType = data.matchType;
            this.ownerOnly = data.ownerOnly;
            this.key = data.key;
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 2) {
                this.ownerOnly = data[0].equalsIgnoreCase("1");
                this.key = data[1];
            }
        }
    }

    @Override
    public void onPickUp() {
        this.ownerOnly = false;
        this.hide = false;
        this.matchType = 0;
        this.key = "";
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    protected String getWiredStringParam() { return this.key; }

    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.hide ? 1 : 0, this.matchType, this.ownerOnly ? 1 : 0 }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString(this.key);
        message.appendInt(3);
        message.appendInt(this.hide ? 1 : 0);
        message.appendInt(this.matchType);
        message.appendInt(this.ownerOnly ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        this.hide = settings.getIntParams()[0] == 1;
        this.matchType = settings.getIntParams().length > 1 ? settings.getIntParams()[1] : 0;
        this.ownerOnly = settings.getIntParams().length > 2 ? settings.getIntParams()[2] == 1 : settings.getIntParams()[0] == 1;
        this.key = settings.getStringParam();

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    public boolean shouldHideMessage() {
        return this.hide;
    }

    static class JsonData {
        boolean hide;
        int matchType;
        boolean ownerOnly;
        String key;

        public JsonData(boolean hide, int matchType, boolean ownerOnly, String key) {
            this.hide = hide;
            this.matchType = matchType;
            this.ownerOnly = ownerOnly;
            this.key = key;
        }
    }
}
