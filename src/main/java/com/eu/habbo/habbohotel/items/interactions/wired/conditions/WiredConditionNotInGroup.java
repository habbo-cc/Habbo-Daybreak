package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotInGroup extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.NOT_ACTOR_IN_GROUP;

    /**
     * May 2026 payload: stringParam = specific group id, or "" meaning the
     * room's own group (legacy behavior).
     */
    private int groupId = 0;

    public WiredConditionNotInGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotInGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        RoomUnit roomUnit = ctx.actor().orElse(null);
        Room room = ctx.room();

        int targetGroup = this.groupId != 0 ? this.groupId : room.getGuildId();
        if (targetGroup == 0)
            return false;

        Habbo habbo = room.getHabbo(roomUnit);

        return habbo == null || !habbo.getHabboStats().hasGuild(targetGroup);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    protected String getWiredStringParam() {
        return this.groupId == 0 ? "" : String.valueOf(this.groupId);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.groupId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.groupId = 0;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.groupId = data.groupId;
        }
    }

    @Override
    public void onPickUp() {
        this.groupId = 0;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString(this.getWiredStringParam());
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        String param = settings.getStringParam();
        if (param == null || param.isEmpty()) {
            this.groupId = 0;
            return true;
        }
        try {
            int parsed = Integer.parseInt(param.trim());
            if (parsed < 0) {
                return false;
            }
            this.groupId = parsed;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static class JsonData {
        int groupId;

        public JsonData(int groupId) {
            this.groupId = groupId;
        }
    }
}
