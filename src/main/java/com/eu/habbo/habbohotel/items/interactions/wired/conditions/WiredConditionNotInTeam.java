package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotInTeam extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.NOT_ACTOR_IN_TEAM;

    private GameTeamColors teamColor = GameTeamColors.NONE;

    public WiredConditionNotInTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotInTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        RoomUnit roomUnit = ctx.actor().orElse(null);
        Room room = ctx.room();
        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null) {
            GameTeamColors actual = habbo.getHabboInfo().getGamePlayer() == null
                    ? GameTeamColors.NONE : habbo.getHabboInfo().getGamePlayer().getTeamColor();
            return this.teamColor == GameTeamColors.NONE ? actual == GameTeamColors.NONE : actual != this.teamColor;
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
                this.teamColor
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        try {
            String wiredData = set.getString("wired_data");

            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                this.teamColor = data == null || data.teamColor == null ? GameTeamColors.NONE : data.teamColor;
            } else {
                if (!wiredData.equals(""))
                    this.teamColor = GameTeamColors.values()[Integer.parseInt(wiredData)];
            }
        } catch (Exception e) {
            this.teamColor = GameTeamColors.NONE;
        }
    }

    @Override
    public void onPickUp() {
        this.teamColor = GameTeamColors.NONE;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.teamColor.type }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.teamColor.type);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        int value = settings.getIntParams()[0];
        if (value < 0 || value > 4) return false;
        GameTeamColors selected = GameTeamColors.fromType(value);
        this.teamColor = selected == null ? GameTeamColors.NONE : selected;

        return true;
    }

    static class JsonData {
        GameTeamColors teamColor;

        public JsonData(GameTeamColors teamColor) {
            this.teamColor = teamColor;
        }
    }
}
