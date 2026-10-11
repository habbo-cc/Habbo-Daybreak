package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredConditionType;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR inverse condition 41 ({@code wf_cnd_neg_has_var}). */
public final class WiredConditionNotHasVariable extends WiredConditionHasVariable {
    public static final WiredConditionType type = WiredConditionType.NOT_HAS_VARIABLE;

    public WiredConditionNotHasVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotHasVariable(int id, int userId, Item item, String extradata,
                                        int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean evaluate(com.eu.habbo.habbohotel.wired.core.WiredContext context) {
        // Inversion applies only after the target was resolved. A missing or
        // ambiguous owner or deleted definition remains a failed condition,
        // never a synthetic match.
        return com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard.isRuntimeReady(this)
                && hasSingleResolvedOwner(
                        context, variableId(), targetScope(), 0)
                && hasRegisteredDefinition(context, variableId(), targetScope())
                && resolveSingleValue(context, variableId(), targetScope(), 0) == null;
    }

    private String variableId() {
        return getWiredVariableIds().length == 1 ? getWiredVariableIds()[0] : "";
    }

    private int targetScope() {
        return getWiredIntParams().length == 1 ? getWiredIntParams()[0] : TARGET_FURNI;
    }
}
