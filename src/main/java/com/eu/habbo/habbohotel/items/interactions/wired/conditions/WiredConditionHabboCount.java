package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionHabboCount extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.USER_COUNT;

    private int lowerLimit = 0;
    private int upperLimit = 125;

    public WiredConditionHabboCount(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboCount(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int count = ctx.room().getUserCount();

        return count >= this.lowerLimit && count <= this.upperLimit;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.lowerLimit,
                this.upperLimit
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            String wiredData = set.getString("wired_data");
            if (wiredData != null && wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                if (data != null) {
                    this.lowerLimit = clamp(data.lowerLimit);
                    this.upperLimit = clamp(data.upperLimit);
                }
            } else if (wiredData != null) {
                String[] data = wiredData.split(":");
                if (data.length >= 2) {
                    this.lowerLimit = clamp(Integer.parseInt(data[0]));
                    this.upperLimit = clamp(Integer.parseInt(data[1]));
                }
            }
            if (this.lowerLimit > this.upperLimit) {
                onPickUp();
            }
        } catch (RuntimeException ignored) {
            onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.lowerLimit = 0;
        this.upperLimit = 125;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.lowerLimit, this.upperLimit }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.lowerLimit);
        message.appendInt(this.upperLimit);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 2) return false;
        int lower = settings.getIntParams()[0];
        int upper = settings.getIntParams()[1];
        if (lower < 0 || upper < 0 || lower > 125 || upper > 125 || lower > upper) return false;
        this.lowerLimit = lower;
        this.upperLimit = upper;

        return true;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(125, value)); }

    static class JsonData {
        int lowerLimit;
        int upperLimit;

        public JsonData(int lowerLimit, int upperLimit) {
            this.lowerLimit = lowerLimit;
            this.upperLimit = upperLimit;
        }
    }
}
