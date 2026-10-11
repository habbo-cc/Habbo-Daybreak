package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR selector 17 ({@code wf_slc_furni_byvariable}). */
public final class WiredSelectorFurniWithVariable extends WiredSelectorVariableBase {
    public WiredSelectorFurniWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorFurniWithVariable(int id, int userId, Item item, String extradata,
                                           int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return WiredSelectorType.FURNI_WITH_VARIABLE;
    }

    @Override
    WiredVariableHolder.Scope selectedScope() {
        return WiredVariableHolder.Scope.FURNI;
    }
}
