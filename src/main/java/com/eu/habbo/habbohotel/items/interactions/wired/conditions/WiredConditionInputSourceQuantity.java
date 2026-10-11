package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

/** July AIR condition 38 ({@code wf_cnd_slc_quantity}). */
public class WiredConditionInputSourceQuantity extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.INPUT_SOURCE_QUANTITY;

    private static final int SOURCE_FURNI = 0;
    private static final int SOURCE_USER = 1;
    private static final int LESS_THAN = 0;
    private static final int EQUALS = 1;
    private static final int GREATER_THAN = 2;
    private static final int MAX_AMOUNT = 100;

    private int sourceKind = SOURCE_FURNI;
    private int amount;
    private int comparison = EQUALS;

    public WiredConditionInputSourceQuantity(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionInputSourceQuantity(int id, int userId, Item item, String extradata,
                                             int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override public WiredConditionType getType() { return type; }
    @Override public WiredConditionOperator operator() { return WiredConditionOperator.AND; }

    @Override
    public boolean evaluate(WiredContext context) {
        int count;
        if (this.sourceKind == SOURCE_USER) {
            Collection<RoomUnit> users = resolveUserSource(context, this.wiredUserSourceTypes, 0);
            count = users.size();
        } else {
            Collection<HabboItem> items = resolveFurniSource(context, this.wiredFurniSourceTypes, 0, null, null);
            count = items.size();
        }
        switch (this.comparison) {
            case LESS_THAN: return count < this.amount;
            case GREATER_THAN: return count > this.amount;
            default: return count == this.amount;
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] values = settings.getIntParams();
        if (values == null || values.length != 3 || !validSourceKind(values[0])
                || values[1] < 0 || values[1] > MAX_AMOUNT || !validComparison(values[2])) {
            return false;
        }
        this.sourceKind = values[0];
        this.amount = values[1];
        this.comparison = values[2];
        return true;
    }

    @Override public String getWiredData() { return this.sourceKind + ";" + this.amount + ";" + this.comparison; }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        String data = set.getString("wired_data");
        if (data == null) return;
        String[] values = data.split(";", -1);
        if (values.length != 3) return;
        try {
            int loadedKind = Integer.parseInt(values[0]);
            int loadedAmount = Integer.parseInt(values[1]);
            int loadedComparison = Integer.parseInt(values[2]);
            if (!validSourceKind(loadedKind) || loadedAmount < 0 || loadedAmount > MAX_AMOUNT
                    || !validComparison(loadedComparison)) return;
            this.sourceKind = loadedKind;
            this.amount = loadedAmount;
            this.comparison = loadedComparison;
        } catch (NumberFormatException ignored) { reset(); }
    }

    @Override public void serializeWiredData(ServerMessage message, Room room) { serializeWiredDataV2(message, room); }
    @Override protected int[] getWiredIntParams() { return new int[] {this.sourceKind, this.amount, this.comparison}; }
    @Override protected int getFurniSourceSlotCount() { return 1; }
    @Override protected int getUserSourceSlotCount() { return 1; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
    }
    @Override protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }
    @Override protected int getDefaultFurniSourceForSlot(int slot) { return FURNI_SOURCE_SELECTOR; }
    @Override protected int getDefaultUserSourceForSlot(int slot) { return USER_SOURCE_SELECTOR; }

    @Override public void onPickUp() { reset(); }
    private void reset() {
        this.sourceKind = SOURCE_FURNI;
        this.amount = 0;
        this.comparison = EQUALS;
        this.wiredFurniSourceTypes = new int[0];
        this.wiredUserSourceTypes = new int[0];
    }
    private static boolean validSourceKind(int value) { return value == SOURCE_FURNI || value == SOURCE_USER; }
    private static boolean validComparison(int value) { return value >= LESS_THAN && value <= GREATER_THAN; }

    @Deprecated @Override public boolean execute(RoomUnit unit, Room room, Object[] stuff) { return false; }
}
