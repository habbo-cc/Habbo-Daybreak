package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredTransactionOutcome;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared parameterless contract for July transaction triggers 25 and 26. */
abstract class WiredTriggerTransactionBase extends InteractionWiredTrigger {
    WiredTriggerTransactionBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    WiredTriggerTransactionBase(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    abstract WiredEvent.Type eventType();

    abstract boolean accepts(WiredTransactionOutcome outcome);

    @Override
    public final boolean matches(HabboItem triggerItem, WiredEvent event) {
        return WiredFeatureCapabilityGuard.isRuntimeReady(this)
                && event != null
                && event.getType() == eventType()
                && event.getTransactionOutcome().filter(this::accepts).isPresent();
    }

    @Override
    public final boolean saveData(WiredSettings settings) {
        return settings != null
                && settings.getIntParams().length == 0
                && "".equals(settings.getStringParam())
                && settings.getFurniIds().length == 0
                && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0
                && settings.getDelay() == 0
                && settings.getQuantifierCode() == 0;
    }

    @Override
    public final void serializeWiredData(ServerMessage message, Room room) {
        serializeWiredDataV2(message, room);
    }

    @Override
    public final String getWiredData() {
        return "";
    }

    @Override
    public final void loadWiredData(ResultSet set, Room room) throws SQLException {
        // July exposes no configurable fields.
    }

    @Override
    public final void onPickUp() {
        // July exposes no configurable fields.
    }

    @Override
    public final boolean isTriggeredByRoomUnit() {
        return true;
    }

    @Deprecated
    @Override
    public final boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }
}
