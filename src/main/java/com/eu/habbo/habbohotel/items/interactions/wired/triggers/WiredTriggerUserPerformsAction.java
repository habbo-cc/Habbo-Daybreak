package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

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

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerUserPerformsAction extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.USER_PERFORMS_ACTION;

    private int actionCode = 0;
    private String extra = "";

    public WiredTriggerUserPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUserPerformsAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event.getType() != WiredEvent.Type.USER_PERFORMS_ACTION || event.getScore() != this.actionCode) {
            return false;
        }

        String actualExtra = event.getText().orElse("");
        return this.extra == null || this.extra.isEmpty() || this.extra.equalsIgnoreCase(actualExtra);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.actionCode, this.extra));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.actionCode = 0;
        this.extra = "";

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.actionCode = data.actionCode;
            this.extra = data.extra == null ? "" : data.extra;
        }
    }

    @Override
    public void onPickUp() {
        this.actionCode = 0;
        this.extra = "";
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    protected String getWiredStringParam() {
        return this.extra;
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[]{ this.actionCode };
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.actionCode = settings.getIntParams().length > 0 ? settings.getIntParams()[0] : 0;
        this.extra = settings.getStringParam() == null ? "" : settings.getStringParam();
        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    static class JsonData {
        int actionCode;
        String extra;

        public JsonData(int actionCode, String extra) {
            this.actionCode = actionCode;
            this.extra = extra;
        }
    }
}
