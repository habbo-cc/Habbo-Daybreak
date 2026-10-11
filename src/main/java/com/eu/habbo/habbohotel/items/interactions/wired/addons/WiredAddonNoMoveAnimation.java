package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import java.sql.ResultSet;
import java.sql.SQLException;

/** July code 6: commits movement normally but suppresses the 7115 animation record. */
public final class WiredAddonNoMoveAnimation extends WiredMovementAddon {
  public WiredAddonNoMoveAnimation(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredAddonNoMoveAnimation(int id, int u, Item i, String e, int st, int se) {
    super(id, u, i, e, st, se);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.NO_MOVE_ANIMATION;
  }

  @Override
  public boolean saveData(WiredSettingsV2 s) {
    return ownsOnly(s, 0);
  }

  @Override
  public String getWiredData() {
    return "";
  }

  @Override
  public void loadWiredData(ResultSet s, Room r) throws SQLException {}

  @Override
  public void onPickUp() {}
}
