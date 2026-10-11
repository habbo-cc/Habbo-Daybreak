package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import java.sql.ResultSet;
import java.sql.SQLException;

/** July code 3 ({@code wf_var_context}); definition persists on its furniture, values do not. */
public final class WiredVariableContext extends InteractionWiredVariable {
  private static final int VERSION = 1;
  private String name = "";
  private boolean hasValue;

  public WiredVariableContext(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredVariableContext(int id, int u, Item i, String e, int l, int n) {
    super(id, u, i, e, l, n);
  }

  @Override
  public WiredVariableType getType() {
    return WiredVariableType.CONTEXT;
  }

  @Override
  public synchronized boolean saveData(WiredSettingsV2 s) {
    if (s == null
        || s.getIntParams().length != 1
        || s.getStringParam() == null
        || s.getFurniIds().length != 0
        || s.getFurniIds2().length != 0
        || s.getVariableIds().length != 0
        || s.getFurniSourceTypes().length != 0
        || s.getUserSourceTypes().length != 0
        || (s.getIntParams()[0] != 0 && s.getIntParams()[0] != 1)) return false;
    String n = WiredVariableName.normalize(s.getStringParam());
    if (!WiredVariableName.isValid(n)) return false;
    name = n;
    hasValue = s.getIntParams()[0] != 0;
    return true;
  }

  @Override
  public synchronized void loadWiredData(ResultSet s, Room r) throws SQLException {
    name = "";
    hasValue = false;
    Data d;
    try {
      d = WiredManager.getGson().fromJson(s == null ? null : s.getString("wired_data"), Data.class);
    } catch (RuntimeException e) {
      return;
    }
    if (d != null
        && d.version == VERSION
        && d.type == 3
        && WiredVariableName.isValid(WiredVariableName.normalize(d.name))) {
      name = WiredVariableName.normalize(d.name);
      hasValue = d.hasValue;
    }
  }

  @Override
  public synchronized String getWiredData() {
    return WiredManager.getGson().toJson(new Data(VERSION, 3, name, hasValue));
  }

  @Override
  public synchronized void onPickUp() {
    name = "";
    hasValue = false;
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  @Override
  protected synchronized String getWiredStringParam() {
    return name;
  }

  @Override
  protected synchronized int[] getWiredIntParams() {
    return new int[] {hasValue ? 1 : 0};
  }

  public synchronized String variableId() {
    return "room:" + getId();
  }

  public synchronized boolean hasValue() {
    return hasValue;
  }

  public synchronized String variableName() {
    return name;
  }

  private static final class Data {
    int version, type;
    String name;
    boolean hasValue;

    Data(int v, int t, String n, boolean h) {
      version = v;
      type = t;
      name = n;
      hasValue = h;
    }
  }
}
