package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.ResultSet;
import java.sql.SQLException;

abstract class WiredAddonSelectorFilter extends InteractionWiredAddon {
  int amount = 10, mode, target;
  String reference = "";

  WiredAddonSelectorFilter(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  WiredAddonSelectorFilter(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  final boolean save(WiredSettingsV2 s) {
    if (s == null
        || s.getIntParams().length != 3
        || s.getVariableIds().length != 1
        || !s.getStringParam().isEmpty()
        || s.getFurniIds().length != 0
        || s.getFurniIds2().length != 0
        || s.getFurniSourceTypes().length != 0
        || s.getUserSourceTypes().length != 0) return false;
    int[] p = s.getIntParams();
    if (p[0] < 1
        || p[0] > 1000
        || p[1] < 0
        || p[1] > 1
        || p[2] < 0
        || p[2] > 3
        || (p[1] == 1 && s.getVariableIds()[0].isBlank())) return false;
    amount = p[0];
    mode = p[1];
    target = p[2];
    reference = s.getVariableIds()[0];
    return true;
  }

  @Override
  public String getWiredData() {
    return WiredManager.getGson().toJson(new Data(1, amount, mode, target, reference));
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
          && d.mode >= 0
          && d.mode <= 1
          && d.target >= 0
          && d.target <= 3
          && (d.mode == 0 || !d.reference.isBlank())) {
        amount = d.amount;
        mode = d.mode;
        target = d.target;
        reference = d.reference;
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onPickUp() {
    amount = 10;
    mode = 0;
    target = 0;
    reference = "";
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {amount, mode, target};
  }

  @Override
  protected String[] getWiredVariableIds() {
    return new String[] {reference};
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  static final class Data {
    int v, amount, mode, target;
    String reference = "";

    Data() {}

    Data(int v, int a, int m, int t, String r) {
      this.v = v;
      amount = a;
      mode = m;
      target = t;
      reference = r;
    }
  }
}
