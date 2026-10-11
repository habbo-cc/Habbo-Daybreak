package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR Core Variables condition 43 ({@code wf_cnd_var_age_match}). */
public final class WiredConditionVariableAge extends WiredConditionVariableBase {
    public static final WiredConditionType type = WiredConditionType.VARIABLE_AGE;

    private static final int CREATED_AT = 0;
    private static final int UPDATED_AT = 1;
    private static final int LESS_THAN = 0;
    private static final int GREATER_THAN = 2;

    private String variableId = "";
    private int targetScope = TARGET_FURNI;
    private int comparison = LESS_THAN;
    private int timestampKind = CREATED_AT;
    private int duration;
    private int unit;

    public WiredConditionVariableAge(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableAge(int id, int userId, Item item, String extradata,
                                     int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = validateCommon(settings, 6, 1);
        if (room == null) {
            return false;
        }
        int[] params = settings.getIntParams();
        var capabilities = metadata(room, settings.getVariableIds()[0]);
        if ((params[1] != LESS_THAN && params[1] != GREATER_THAN)
                || (params[2] != CREATED_AT && params[2] != UPDATED_AT)
                || !validSignedIntParts(params[3], params[4])
                || params[5] < 0 || params[5] > 7
                || !validateDefinition(room, settings.getVariableIds()[0], params[0])
                || capabilities == null || (params[2] == CREATED_AT
                ? !capabilities.canReadCreationTime() : !capabilities.canReadLastUpdateTime())) {
            return false;
        }
        this.variableId = settings.getVariableIds()[0];
        this.targetScope = params[0];
        this.comparison = params[1];
        this.timestampKind = params[2];
        this.duration = params[4];
        this.unit = params[5];
        return true;
    }

    @Override
    public boolean evaluate(WiredContext context) {
        if (!WiredFeatureCapabilityGuard.isRuntimeReady(this)) {
            return false;
        }
        // July exposes calendar months/years. Fixed-duration approximations
        // would be incorrect at month boundaries, so stay dark pending capture.
        if (this.unit == 6 || this.unit == 7) {
            return false;
        }
        WiredVariableValue value = resolveSingleValue(context, this.variableId, this.targetScope, 0);
        if (value == null) {
            return false;
        }
        long timestamp = this.timestampKind == CREATED_AT ? value.createdAtMs() : value.updatedAtMs();
        long threshold = toMilliseconds(this.duration, this.unit);
        if (timestamp <= 0L || threshold < 0L) {
            return false;
        }
        long age = Math.max(0L, System.currentTimeMillis() - timestamp);
        return this.comparison == LESS_THAN ? age < threshold : age > threshold;
    }

    @Override
    public String getWiredData() {
        return encodeRecord(this.variableId, String.valueOf(this.targetScope), String.valueOf(this.comparison),
                String.valueOf(this.timestampKind), String.valueOf(this.duration), String.valueOf(this.unit),
                String.valueOf(sourceAt(this.furniSourceTypes, FURNI_SOURCE_TRIGGERING_ITEM)),
                String.valueOf(sourceAt(this.userSourceTypes, USER_SOURCE_TRIGGERING_USER)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        String[] fields = decodeRecord(set.getString("wired_data"), 8);
        if (fields == null || !isValidVariableId(fields[0])) {
            return;
        }
        try {
            int loadedTargetScope = Integer.parseInt(fields[1]);
            int loadedComparison = Integer.parseInt(fields[2]);
            int loadedTimestampKind = Integer.parseInt(fields[3]);
            int loadedDuration = Integer.parseInt(fields[4]);
            int loadedUnit = Integer.parseInt(fields[5]);
            int furniSource = Integer.parseInt(fields[6]);
            int userSource = Integer.parseInt(fields[7]);
            if (!isSupportedTarget(loadedTargetScope)
                    || (loadedComparison != LESS_THAN && loadedComparison != GREATER_THAN)
                    || (loadedTimestampKind != CREATED_AT && loadedTimestampKind != UPDATED_AT)
                    || loadedUnit < 0 || loadedUnit > 7
                    || !isAllowedFurniSource(furniSource) || !isAllowedUserSource(userSource)) {
                return;
            }
            this.variableId = fields[0];
            this.targetScope = loadedTargetScope;
            this.comparison = loadedComparison;
            this.timestampKind = loadedTimestampKind;
            this.duration = loadedDuration;
            this.unit = loadedUnit;
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
        if (this.variableId.isEmpty()) {
            return new int[0];
        }
        return new int[] {
                this.targetScope, this.comparison, this.timestampKind,
                this.duration < 0 ? -1 : 0, this.duration, this.unit
        };
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId};
    }

    private static long toMilliseconds(int duration, int unit) {
        long multiplier = switch (unit) {
            case 0 -> 1L;
            case 1 -> 1_000L;
            case 2 -> 60_000L;
            case 3 -> 3_600_000L;
            case 4 -> 86_400_000L;
            case 5 -> 604_800_000L;
            default -> -1L;
        };
        if (multiplier <= 0L) {
            return -1L;
        }
        if (duration > 0 && duration > Long.MAX_VALUE / multiplier) {
            return -1L;
        }
        if (duration < 0 && duration < Long.MIN_VALUE / multiplier) {
            return -1L;
        }
        return duration * multiplier;
    }

    private void reset() {
        this.variableId = "";
        this.targetScope = TARGET_FURNI;
        this.comparison = LESS_THAN;
        this.timestampKind = CREATED_AT;
        this.duration = 0;
        this.unit = 0;
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.items.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.variableIds = new String[0];
    }

    private static int sourceAt(int[] values, int fallback) {
        return values != null && values.length == 1 ? values[0] : fallback;
    }

    private boolean isAllowedFurniSource(int source) {
        return source == FURNI_SOURCE_TRIGGERING_ITEM
                || source == FURNI_SOURCE_SELECTOR || source == FURNI_SOURCE_SIGNAL;
    }

    private boolean isAllowedUserSource(int source) {
        return source == USER_SOURCE_TRIGGERING_USER
                || source == USER_SOURCE_SELECTOR || source == USER_SOURCE_SIGNAL;
    }
}
