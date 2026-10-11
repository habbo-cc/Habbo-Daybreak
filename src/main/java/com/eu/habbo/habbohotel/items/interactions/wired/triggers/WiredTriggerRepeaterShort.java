package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wired 2.0 trigger 19 (wf_trg_period_short): repeats every 50..500 ms
 * (intParams[0] = interval in 50ms units, 1..10 — May SliderValueMilliseconds50).
 * Inherits the 50ms tick self-firing behavior from {@link WiredTriggerRepeater}.
 */
public class WiredTriggerRepeaterShort extends WiredTriggerRepeater {
    public static final WiredTriggerType shortType = WiredTriggerType.PERIOD_SHORT;
    public static final int DEFAULT_SHORT_DELAY = 500;

    public WiredTriggerRepeaterShort(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.repeatTime = DEFAULT_SHORT_DELAY;
    }

    public WiredTriggerRepeaterShort(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.repeatTime = DEFAULT_SHORT_DELAY;
    }

    @Override
    public WiredTriggerType getType() {
        return shortType;
    }

    @Override
    public void onPickUp() {
        this.repeatTime = DEFAULT_SHORT_DELAY;
    }

    // Wired 2.0 getters — interval travels as 50ms units, not 500ms pulses.
    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.repeatTime / 50 }; }

    // Fire the short-timer event so PERIOD_SHORT stacks match (not the base PERIODICALLY event).
    @Override
    protected void fireTimerEvent(Room room) {
        WiredManager.triggerTimerRepeatShort(room, this);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.repeatTime = DEFAULT_SHORT_DELAY;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.repeatTime = data.repeatTime;
        }

        if (this.repeatTime < 50) {
            this.repeatTime = 50;
        }
        if (this.repeatTime > 500) {
            this.repeatTime = 500;
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 1) return false;
        int units = settings.getIntParams()[0];

        if (units < 1) {
            units = 1;
        }
        if (units > 10) {
            units = 10;
        }

        this.repeatTime = units * 50;

        return true;
    }
}
