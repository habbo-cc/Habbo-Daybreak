package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import java.sql.*;

public final class WiredAddonUserVariableFilter extends WiredAddonVariableSelectorFilter {
  public WiredAddonUserVariableFilter(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredAddonUserVariableFilter(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.USER_VARIABLE_FILTER;
  }
}
