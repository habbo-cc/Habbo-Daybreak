package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class WiredAddonFurniSelectorFilter extends WiredAddonSelectorFilter {
  public WiredAddonFurniSelectorFilter(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredAddonFurniSelectorFilter(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.FURNI_SELECTOR_FILTER;
  }

  @Override
  public boolean saveData(WiredSettingsV2 s) {
    return save(s);
  }

  public int filterAmount() {
    return amount;
  }

  public boolean usesReference() {
    return mode == 1;
  }

  public int referenceTarget() {
    return target;
  }

  public String referenceId() {
    return reference;
  }
}
