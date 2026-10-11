package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** July code 2. */
public final class WiredAddonUnseenEffect extends InteractionWiredAddon {
  private int next;

  public WiredAddonUnseenEffect(ResultSet set, Item baseItem) throws SQLException {
    super(set, baseItem);
  }

  public WiredAddonUnseenEffect(int id, int userId, Item item, String extra, int stack, int sells) {
    super(id, userId, item, extra, stack, sells);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.EFFECT_MARKER;
  }

  @Override
  public boolean saveData(WiredSettingsV2 s) {
    return s != null
        && s.getIntParams().length == 0
        && s.getStringParam().isEmpty()
        && s.getFurniIds().length == 0
        && s.getFurniIds2().length == 0
        && s.getVariableIds().length == 0
        && s.getFurniSourceTypes().length == 0
        && s.getUserSourceTypes().length == 0;
  }

  @Override
  public String getWiredData() {
    return "";
  }

  @Override
  public void loadWiredData(ResultSet set, Room room) throws SQLException {
    next = 0;
  }

  @Override
  public void onPickUp() {
    next = 0;
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  public synchronized IWiredEffect selectEffect(List<IWiredEffect> effects) {
    if (effects == null || effects.isEmpty()) {
      next = 0;
      return null;
    }
    if (next >= effects.size()) next = 0;
    IWiredEffect result = effects.get(next);
    next = (next + 1) % effects.size();
    return result;
  }
}
