package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.ResultSet;
import java.sql.SQLException;

abstract class WiredAddonVariableSelectorFilter extends InteractionWiredAddon {
  int amount = 10, sort, mode, target;
  String variable = "", reference = "";

  WiredAddonVariableSelectorFilter(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  WiredAddonVariableSelectorFilter(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public boolean saveData(WiredSettingsV2 s) {
    if (s == null
        || s.getIntParams().length != 4
        || s.getVariableIds().length != 2
        || !s.getStringParam().isEmpty()
        || s.getFurniIds().length != 0
        || s.getFurniIds2().length != 0
        || s.getFurniSourceTypes().length != 0
        || s.getUserSourceTypes().length != 0) return false;
    int[] p = s.getIntParams();
    if (p[0] < 1
        || p[0] > 1000
        || p[1] < 0
        || p[1] > 5
        || p[2] < 0
        || p[2] > 1
        || p[3] < 0
        || p[3] > 3
        || s.getVariableIds()[0].isBlank()
        || (p[2] == 1 && s.getVariableIds()[1].isBlank())) return false;
    amount = p[0];
    sort = p[1];
    mode = p[2];
    target = p[3];
    variable = s.getVariableIds()[0];
    reference = s.getVariableIds()[1];
    return true;
  }

  @Override
  public String getWiredData() {
    return WiredManager.getGson()
        .toJson(new Data(1, amount, sort, mode, target, variable, reference));
  }

  @Override
  public void loadWiredData(ResultSet s, Room r) throws SQLException {
    onPickUp();
    if (s == null) return;
    try {
      Data d = WiredManager.getGson().fromJson(s.getString("wired_data"), Data.class);
      if (d != null
          && d.v == 1
          && d.amount >= 1
          && d.amount <= 1000
          && d.sort >= 0
          && d.sort <= 5
          && d.mode >= 0
          && d.mode <= 1
          && d.target >= 0
          && d.target <= 3
          && !d.variable.isBlank()
          && (d.mode == 0 || !d.reference.isBlank())) {
        amount = d.amount;
        sort = d.sort;
        mode = d.mode;
        target = d.target;
        variable = d.variable;
        reference = d.reference;
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onPickUp() {
    amount = 10;
    sort = mode = target = 0;
    variable = reference = "";
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {amount, sort, mode, target};
  }

  @Override
  protected String[] getWiredVariableIds() {
    return new String[] {variable, reference};
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  public int amount() {
    return amount;
  }

  public int sort() {
    return sort;
  }

  public boolean usesReference() {
    return mode == 1;
  }

  public int target() {
    return target;
  }

  public String variable() {
    return variable;
  }

  public String reference() {
    return reference;
  }

  static final class Data {
    int v, amount, sort, mode, target;
    String variable = "", reference = "";

    Data() {}

    Data(int v, int a, int s, int m, int t, String x, String r) {
      this.v = v;
      amount = a;
      sort = s;
      mode = m;
      target = t;
      variable = x;
      reference = r;
    }
  }
}
