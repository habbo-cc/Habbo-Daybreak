package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import java.sql.*;

public final class WiredAddonFurniVariableFilter extends WiredAddonVariableSelectorFilter {
  public WiredAddonFurniVariableFilter(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredAddonFurniVariableFilter(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.FURNI_VARIABLE_FILTER;
  }
}
