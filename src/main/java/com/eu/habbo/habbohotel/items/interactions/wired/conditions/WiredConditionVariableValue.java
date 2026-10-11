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

/** July AIR Core Variables condition 42 ({@code wf_cnd_var_val_match}). */
public final class WiredConditionVariableValue extends WiredConditionVariableBase {
    public static final WiredConditionType type = WiredConditionType.VARIABLE_VALUE;

    private static final int REFERENCE_LITERAL = 0;
    private static final int REFERENCE_VARIABLE = 1;

    private String targetVariableId = "";
    private String referenceVariableId = "";
    private int targetScope = TARGET_FURNI;
    private int comparison;
    private int referenceMode = REFERENCE_LITERAL;
    private int literalValue;
    private int referenceScope = TARGET_FURNI;

    public WiredConditionVariableValue(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableValue(int id, int userId, Item item, String extradata,
                                       int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int sourceSlotCount() {
        return 2;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = validateCommon(settings, 6, 2);
        if (room == null) {
            return false;
        }
        int[] params = settings.getIntParams();
        if (!isComparison(params[1])
                || (params[2] != REFERENCE_LITERAL && params[2] != REFERENCE_VARIABLE)
                || !validSignedIntParts(params[3], params[4])) {
            return false;
        }
        String requestedTargetId = settings.getVariableIds()[0];
        String requestedReferenceId = settings.getVariableIds()[1];
        if (!validateNumericDefinition(room, requestedTargetId, params[0])) {
            return false;
        }
        if (params[2] == REFERENCE_VARIABLE
                && !validateNumericDefinition(room, requestedReferenceId, params[5])) {
            return false;
        }
        if (params[2] == REFERENCE_LITERAL) {
            requestedReferenceId = "";
        }
        this.targetVariableId = requestedTargetId;
        this.referenceVariableId = requestedReferenceId;
        this.targetScope = params[0];
        this.comparison = params[1];
        this.referenceMode = params[2];
        this.literalValue = params[4];
        this.referenceScope = params[5];
        return true;
    }

    @Override
    public boolean evaluate(WiredContext context) {
        if (context == null || !WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || !hasNumericValue(context.room(), this.targetVariableId, this.targetScope)
                || this.referenceMode == REFERENCE_VARIABLE
                && !hasNumericValue(context.room(), this.referenceVariableId, this.referenceScope)) {
            return false;
        }
        WiredVariableValue target = resolveSingleValue(context, this.targetVariableId, this.targetScope, 0);
        if (target == null) {
            return false;
        }
        Integer reference = this.referenceMode == REFERENCE_LITERAL ? this.literalValue : resolveReference(context);
        return reference != null && matchesComparison(target.value(), reference, this.comparison);
    }

    @Override
    public String getWiredData() {
        return encodeRecord(this.targetVariableId, this.referenceVariableId,
                String.valueOf(this.targetScope), String.valueOf(this.comparison),
                String.valueOf(this.referenceMode), String.valueOf(this.literalValue),
                String.valueOf(this.referenceScope),
                String.valueOf(sourceAt(this.furniSourceTypes, 0, FURNI_SOURCE_TRIGGERING_ITEM)),
                String.valueOf(sourceAt(this.userSourceTypes, 0, USER_SOURCE_TRIGGERING_USER)),
                String.valueOf(sourceAt(this.furniSourceTypes, 1, FURNI_SOURCE_TRIGGERING_ITEM)),
                String.valueOf(sourceAt(this.userSourceTypes, 1, USER_SOURCE_TRIGGERING_USER)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        String[] fields = decodeRecord(set.getString("wired_data"), 11);
        if (fields == null || !isValidVariableId(fields[0])) {
            return;
        }
        try {
            int loadedTargetScope = Integer.parseInt(fields[2]);
            int loadedComparison = Integer.parseInt(fields[3]);
            int loadedReferenceMode = Integer.parseInt(fields[4]);
            int loadedLiteral = Integer.parseInt(fields[5]);
            int loadedReferenceScope = Integer.parseInt(fields[6]);
            int firstFurniSource = Integer.parseInt(fields[7]);
            int firstUserSource = Integer.parseInt(fields[8]);
            int secondFurniSource = Integer.parseInt(fields[9]);
            int secondUserSource = Integer.parseInt(fields[10]);
            if (!isSupportedTarget(loadedTargetScope)
                    || !isComparison(loadedComparison)
                    || (loadedReferenceMode != REFERENCE_LITERAL && loadedReferenceMode != REFERENCE_VARIABLE)
                    || (loadedReferenceMode == REFERENCE_VARIABLE
                    && (!isValidVariableId(fields[1]) || !isSupportedTarget(loadedReferenceScope)))
                    || !isAllowedFurniSource(firstFurniSource) || !isAllowedUserSource(firstUserSource)
                    || !isAllowedFurniSource(secondFurniSource) || !isAllowedUserSource(secondUserSource)) {
                return;
            }
            this.targetVariableId = fields[0];
            this.referenceVariableId = loadedReferenceMode == REFERENCE_VARIABLE ? fields[1] : "";
            this.targetScope = loadedTargetScope;
            this.comparison = loadedComparison;
            this.referenceMode = loadedReferenceMode;
            this.literalValue = loadedLiteral;
            this.referenceScope = loadedReferenceScope;
            this.furniSourceTypes = new int[] {firstFurniSource, secondFurniSource};
            this.userSourceTypes = new int[] {firstUserSource, secondUserSource};
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
        if (this.targetVariableId.isEmpty()) {
            return new int[0];
        }
        return new int[] {
                this.targetScope, this.comparison, this.referenceMode,
                this.literalValue < 0 ? -1 : 0, this.literalValue, this.referenceScope
        };
    }

    @Override
    protected String[] getWiredVariableIds() {
        if (this.targetVariableId.isEmpty()) {
            return new String[0];
        }
        return new String[] {this.targetVariableId,
                this.referenceMode == REFERENCE_VARIABLE ? this.referenceVariableId : ""};
    }

    private Integer resolveReference(WiredContext context) {
        WiredVariableValue reference = resolveSingleValue(context, this.referenceVariableId,
                this.referenceScope, 1);
        return reference == null ? null : reference.value();
    }

    static boolean matchesComparison(int left, int right, int code) {
        return switch (code) {
            case 0 -> left < right;
            case 1 -> left == right;
            case 2 -> left > right;
            case 3 -> left <= right;
            case 4 -> left != right;
            case 5 -> left >= right;
            default -> false;
        };
    }

    static boolean isComparison(int code) {
        return code >= 0 && code <= 5;
    }

    private void reset() {
        this.targetVariableId = "";
        this.referenceVariableId = "";
        this.targetScope = TARGET_FURNI;
        this.comparison = 0;
        this.referenceMode = REFERENCE_LITERAL;
        this.literalValue = 0;
        this.referenceScope = TARGET_FURNI;
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.items.clear();
        this.intParams = new int[0];
        this.stringParam = "";
        this.variableIds = new String[0];
    }

    private static int sourceAt(int[] values, int index, int fallback) {
        return values != null && index >= 0 && index < values.length ? values[index] : fallback;
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
