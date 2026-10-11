package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR selector 18 ({@code wf_slc_users_byvariable}). */
public final class WiredSelectorUsersWithVariable extends WiredSelectorVariableBase {
    public WiredSelectorUsersWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorUsersWithVariable(int id, int userId, Item item, String extradata,
                                           int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return WiredSelectorType.USERS_WITH_VARIABLE;
    }

    @Override
    WiredVariableHolder.Scope selectedScope() {
        return WiredVariableHolder.Scope.USER;
    }
}
