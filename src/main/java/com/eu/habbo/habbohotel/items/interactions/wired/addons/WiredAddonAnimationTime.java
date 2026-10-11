package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.ResultSet;
import java.sql.SQLException;

/** July code 9, 50..2000ms in 50ms increments. */
public final class WiredAddonAnimationTime extends WiredMovementAddon {
  private int milliseconds = 500;

  public WiredAddonAnimationTime(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredAddonAnimationTime(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.ANIMATION_TIME;
  }

  @Override
  public boolean saveData(WiredSettingsV2 s) {
    if (!ownsOnly(s, 1)) return false;
    int v = s.getIntParams()[0];
    if (v < 50 || v > 2000 || v % 50 != 0) return false;
    milliseconds = v;
    return true;
  }

  @Override
  public String getWiredData() {
    return json(new Data(1, milliseconds));
  }

  @Override
  public void loadWiredData(ResultSet s, Room r) throws SQLException {
    onPickUp();
    if (s == null) return;
    String raw = s.getString("wired_data");
    if (raw == null || !raw.startsWith("{")) return;
    try {
      Data d = WiredManager.getGson().fromJson(raw, Data.class);
      if (d != null
          && d.v == 1
          && d.milliseconds >= 50
          && d.milliseconds <= 2000
          && d.milliseconds % 50 == 0) milliseconds = d.milliseconds;
    } catch (RuntimeException ignored) {
    }
  }

  @Override
  public void onPickUp() {
    milliseconds = 500;
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {milliseconds};
  }

  public int milliseconds() {
    return milliseconds;
  }

  static final class Data {
    int v, milliseconds;

    Data() {}

    Data(int v, int milliseconds) {
      this.v = v;
      this.milliseconds = milliseconds;
    }
  }
}
