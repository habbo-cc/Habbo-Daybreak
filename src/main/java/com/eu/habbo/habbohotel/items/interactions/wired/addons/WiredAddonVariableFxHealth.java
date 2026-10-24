package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class WiredAddonVariableFxHealth extends WiredAddonVariableFx {
    public WiredAddonVariableFxHealth(ResultSet set,Item item) throws SQLException { super(set,item); }
    public WiredAddonVariableFxHealth(int id,int userId,Item item,String extra,int stack,int sells) { super(id,userId,item,extra,stack,sells); }
    @Override public WiredAddonType getType() { return WiredAddonType.VARIABLE_FX_HEALTH; }
}
