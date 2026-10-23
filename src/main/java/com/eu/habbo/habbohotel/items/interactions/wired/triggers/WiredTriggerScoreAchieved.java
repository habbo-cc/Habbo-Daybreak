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

public class WiredTriggerScoreAchieved extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.SCORE_ACHIEVED;
    private int score = 0;
    private int team = 0;

    public WiredTriggerScoreAchieved(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerScoreAchieved(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (this.score <= 0 || event.getType() != WiredEvent.Type.SCORE_ACHIEVED) {
            return false;
        }
        if (event.getScoreChange().isPresent()) {
            var change = event.getScoreChange().get();
            return (this.team == 0 || this.team == change.team().type) && change.crosses(this.score);
        }
        // Legacy plugins may still build an event with only score/delta.
        // With no team evidence only an any-team trigger can match.
        int points = event.getScore();
        int amountAdded = event.getScoreAdded();

        return this.team == 0 && (long) points - amountAdded < this.score && points >= this.score;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.score,
            this.team
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.score = data.score;
            this.team = data.team;
        } else {
            try {
                this.score = Integer.parseInt(wiredData);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onPickUp() {
        this.score = 0;
        this.team = 0;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.score, this.team }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.score);
        message.appendInt(this.team);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings == null || settings.getIntParams() == null
                || settings.getIntParams().length < 1 || settings.getIntParams().length > 2) return false;
        int score = settings.getIntParams()[0];
        int team = settings.getIntParams().length > 1 ? settings.getIntParams()[1] : 0;
        if (score < 1 || score > 1000 || team < 0 || team > 4) return false;
        this.score = score;
        this.team = team;
        return true;
    }

    @Override
    public boolean requiresActor() {
        // Predefined team awards and score-variable writes can have no user.
        return false;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int score;
        int team;

        public JsonData(int score, int team) {
            this.score = score;
            this.team = team;
        }
    }
}
