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
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomClock;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wired 2.0 trigger 15 (wf_trg_clock_counter): fires when the room wired clock
 * reaches the configured [minutes, seconds]. Polls the lazily-advancing room
 * clock from the 50ms tick service and self-fires once per crossing.
 */
public class WiredTriggerClockReachTime extends InteractionWiredTrigger implements WiredTickable {
    private static final WiredTriggerType type = WiredTriggerType.CLOCK_REACH_TIME;

    // May payload: intParams = [seconds, minutes, halfSecondPulse(0|1)]
    private int minutes = 0;
    private int seconds = 0;
    private int halfPulse = 0;
    private int lastFiredHalfSeconds = -1;

    public WiredTriggerClockReachTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerClockReachTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    private int targetTotalHalfSeconds() {
        return (((this.minutes * 60) + this.seconds) * 2) + this.halfPulse;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event.getType() != WiredEvent.Type.CLOCK_REACHED) {
            return false;
        }
        // Only match the event this trigger fired itself (multiple clock
        // triggers each poll independently).
        return event.getSourceItem().map(item -> item.getId() == this.getId()).orElse(false);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.minutes, this.seconds, this.halfPulse));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.minutes = 0;
        this.seconds = 0;
        this.halfPulse = 0;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.minutes = data.minutes;
            this.seconds = data.seconds;
            this.halfPulse = data.halfPulse;
        }
    }

    @Override
    public void onPickUp() {
        this.minutes = 0;
        this.seconds = 0;
        this.halfPulse = 0;
        this.lastFiredHalfSeconds = -1;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    // Wired 2.0 getters — May order [seconds, minutes, halfPulse]
    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.seconds, this.minutes, this.halfPulse }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.seconds);
        message.appendInt(this.minutes);
        message.appendInt(this.halfPulse);
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

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 2) return false;

        int newSeconds = settings.getIntParams()[0];
        int newMinutes = settings.getIntParams()[1];
        int newHalfPulse = settings.getIntParams().length > 2 ? settings.getIntParams()[2] : 0;

        if (newMinutes < 0 || newSeconds < 0 || newSeconds > 59 || newHalfPulse < 0 || newHalfPulse > 1) {
            return false;
        }

        this.minutes = newMinutes;
        this.seconds = newSeconds;
        this.halfPulse = newHalfPulse;
        this.lastFiredHalfSeconds = -1;

        return true;
    }

    // ========== WiredTickable Implementation ==========

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        if (this.getRoomId() == 0 || !room.isLoaded()) {
            return;
        }

        WiredRoomClock clock = WiredManager.getRoomClock(room);
        if (!clock.isRunning()) {
            return;
        }

        int current = clock.getTotalHalfSeconds();
        if (current == this.targetTotalHalfSeconds() && this.lastFiredHalfSeconds != current) {
            this.lastFiredHalfSeconds = current;
            WiredManager.triggerClockReached(room, this, current);
        }
        if (current != this.targetTotalHalfSeconds() && this.lastFiredHalfSeconds != -1 && current < this.lastFiredHalfSeconds) {
            // Clock was reset below the last fired point — allow firing again.
            this.lastFiredHalfSeconds = -1;
        }
    }

    @Override
    public void resetTimer() {
        this.lastFiredHalfSeconds = -1;
    }

    @Override
    public void onRegistered(Room room, long currentTimeMillis) {
    }

    @Override
    public void onUnregistered(Room room) {
    }

    @Override
    public boolean isOneShot() {
        return false;
    }

    static class JsonData {
        int minutes;
        int seconds;
        int halfPulse;

        public JsonData(int minutes, int seconds, int halfPulse) {
            this.minutes = minutes;
            this.seconds = seconds;
            this.halfPulse = halfPulse;
        }
    }
}
