package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR Core Variables condition 40 ({@code wf_cnd_has_var}). */
public class WiredConditionHasVariable extends WiredConditionVariableBase {
    public static final WiredConditionType type = WiredConditionType.HAS_VARIABLE;

    private String variableId = "";
    private int targetScope = TARGET_FURNI;

    public WiredConditionHasVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHasVariable(int id, int userId, Item item, String extradata,
                                     int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = validateCommon(settings, 1, 1);
        if (room == null) {
            return false;
        }
        int requestedScope = settings.getIntParams()[0];
        String requestedId = settings.getVariableIds()[0];
        var capabilities = metadata(room, requestedId);
        if (!validateDefinition(room, requestedId, requestedScope)
                || capabilities == null || capabilities.alwaysAvailable()) {
            return false;
        }
        this.variableId = requestedId;
        this.targetScope = requestedScope;
        return true;
    }

    @Override
    public boolean evaluate(WiredContext context) {
        return WiredFeatureCapabilityGuard.isRuntimeReady(this)
                && resolveSingleValue(context, this.variableId, this.targetScope, 0) != null;
    }

    @Override
    public String getWiredData() {
        return encodeRecord(this.variableId, String.valueOf(this.targetScope),
                String.valueOf(sourceAt(this.furniSourceTypes, FURNI_SOURCE_TRIGGERING_ITEM)),
                String.valueOf(sourceAt(this.userSourceTypes, USER_SOURCE_TRIGGERING_USER)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        String[] fields = decodeRecord(set.getString("wired_data"), 4);
        if (fields == null || !isValidVariableId(fields[0])) {
            return;
        }
        try {
            int loadedScope = Integer.parseInt(fields[1]);
            int furniSource = Integer.parseInt(fields[2]);
            int userSource = Integer.parseInt(fields[3]);
            if (!isSupportedTarget(loadedScope)
                    || !isAllowedFurniSource(furniSource)
                    || !isAllowedUserSource(userSource)) {
                return;
            }
            this.variableId = fields[0];
            this.targetScope = loadedScope;
            this.furniSourceTypes = new int[] {furniSource};
            this.userSourceTypes = new int[] {userSource};
        } catch (NumberFormatException ignored) {
            reset();
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        serializeWiredDataV2(message, room);
    }

    @Override
    public void onPickUp() {
        reset();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    protected int[] getWiredIntParams() {
        return this.variableId.isEmpty() ? new int[0] : new int[] {this.targetScope};
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId};
    }

    protected void reset() {
        this.variableId = "";
        this.targetScope = TARGET_FURNI;
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.items.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.variableIds = new String[0];
    }

    private boolean isAllowedFurniSource(int source) {
        return source == FURNI_SOURCE_TRIGGERING_ITEM
                || source == FURNI_SOURCE_SELECTOR || source == FURNI_SOURCE_SIGNAL;
    }

    private boolean isAllowedUserSource(int source) {
        return source == USER_SOURCE_TRIGGERING_USER
                || source == USER_SOURCE_SELECTOR || source == USER_SOURCE_SIGNAL;
    }

    private static int sourceAt(int[] values, int fallback) {
        return values != null && values.length == 1 ? values[0] : fallback;
    }
}
