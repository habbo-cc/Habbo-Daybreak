package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wired 2.0 trigger 24 (wf_trg_click_user): fires when a user clicks another user.
 * The clicking user is the actor; the clicked user travels as the target unit.
 */
public class WiredTriggerUserClicksUser extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.CLICK_USER;

    /**
     * May intParams: [blockMenuOpen, doNotRotate]. doNotRotate is a client
     * rotation suppression hint.
     */
    private int blockMenuOpen = 0;
    private int doNotRotate = 0;

    public WiredTriggerUserClicksUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUserClicksUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event.getType() == WiredEvent.Type.USER_CLICKS_USER;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return com.eu.habbo.habbohotel.wired.core.WiredManager.getGson().toJson(new JsonData(this.blockMenuOpen, this.doNotRotate));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.blockMenuOpen = 0;
        this.doNotRotate = 0;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = com.eu.habbo.habbohotel.wired.core.WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.blockMenuOpen = data.blockMenuOpen;
            this.doNotRotate = data.doNotRotate;
        }
    }

    @Override
    public void onPickUp() {
        this.blockMenuOpen = 0;
        this.doNotRotate = 0;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    // Wired 2.0 getters
    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.blockMenuOpen, this.doNotRotate }; }

    public boolean blocksMenuOpen() {
        return this.blockMenuOpen == 1;
    }

    public boolean suppressesRotation() {
        return this.doNotRotate == 1;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.blockMenuOpen = settings.getIntParams().length > 0 ? settings.getIntParams()[0] : 0;
        this.doNotRotate = settings.getIntParams().length > 1 ? settings.getIntParams()[1] : 0;
        return true;
    }

    static class JsonData {
        int blockMenuOpen;
        int doNotRotate;

        public JsonData(int blockMenuOpen, int doNotRotate) {
            this.blockMenuOpen = blockMenuOpen;
            this.doNotRotate = doNotRotate;
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.blockMenuOpen);
        message.appendInt(this.doNotRotate);
        message.appendInt(type.code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getEffects(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredEffect>() {
                @Override
                public boolean execute(InteractionWiredEffect object) {
                    if (object.requiresTriggeringUser()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }
}
