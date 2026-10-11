package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.*;

public final class WiredAddonUsernamePlaceholder extends InteractionWiredAddon {
  private String placeholder = "$";
  private String delimiter = "";
  private boolean multiple;

  public WiredAddonUsernamePlaceholder(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredAddonUsernamePlaceholder(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.USERNAME_PLACEHOLDER;
  }

  @Override
  public boolean saveData(WiredSettingsV2 s) {
    if (s == null
        || s.getIntParams().length != 1
        || s.getIntParams()[0] < 0
        || s.getIntParams()[0] > 1
        || s.getFurniIds().length != 0
        || s.getFurniIds2().length != 0
        || s.getVariableIds().length != 0
        || s.getFurniSourceTypes().length != 0
        || s.getUserSourceTypes().length != 0) return false;
    String[] p = s.getStringParam().split("\\t", 2);
    if (p[0].isBlank()
        || p[0].length() > 64
        || (s.getIntParams()[0] == 0 && p.length > 1)
        || (p.length > 1 && p[1].length() > 64)) return false;
    placeholder = p[0];
    multiple = s.getIntParams()[0] == 1;
    delimiter = p.length > 1 ? p[1] : "";
    return true;
  }

  @Override
  public String getWiredData() {
    return WiredManager.getGson().toJson(new Data(1, placeholder, delimiter, multiple));
  }

  @Override
  public void loadWiredData(ResultSet s, Room r) throws SQLException {
    onPickUp();
    if (s == null) return;
    try {
      Data d = WiredManager.getGson().fromJson(s.getString("wired_data"), Data.class);
      if (d != null
          && d.v == 1
          && !d.placeholder.isBlank()
          && d.placeholder.length() <= 64
          && d.delimiter.length() <= 64) {
        placeholder = d.placeholder;
        delimiter = d.delimiter;
        multiple = d.multiple;
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onPickUp() {
    placeholder = "$";
    delimiter = "";
    multiple = false;
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {multiple ? 1 : 0};
  }

  @Override
  protected String getWiredStringParam() {
    return multiple ? placeholder + "\t" + delimiter : placeholder;
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  public String placeholder() {
    return placeholder;
  }

  public String delimiter() {
    return delimiter;
  }

  public boolean multiple() {
    return multiple;
  }

  static final class Data {
    int v;
    String placeholder = "$", delimiter = "";
    boolean multiple;

    Data() {}

    Data(int v, String p, String d, boolean m) {
      this.v = v;
      placeholder = p;
      delimiter = d;
      multiple = m;
    }
  }
}
