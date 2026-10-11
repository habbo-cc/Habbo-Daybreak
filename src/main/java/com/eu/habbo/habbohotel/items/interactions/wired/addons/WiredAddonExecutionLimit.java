package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

/** July code 5. Window is measured in 500ms pulses. */
public final class WiredAddonExecutionLimit extends InteractionWiredAddon {
  private int executions = 1, pulses = 4;
  private final Deque<Long> times = new ArrayDeque<>();

  public WiredAddonExecutionLimit(ResultSet set, Item base) throws SQLException {
    super(set, base);
  }

  public WiredAddonExecutionLimit(
      int id, int userId, Item item, String extra, int stack, int sells) {
    super(id, userId, item, extra, stack, sells);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.EXECUTION_LIMIT;
  }

  @Override
  public synchronized boolean saveData(WiredSettingsV2 s) {
    if (!ownsOnly(s)
        || s.getIntParams()[0] < 1
        || s.getIntParams()[0] > 100
        || s.getIntParams()[1] < 1
        || s.getIntParams()[1] > 20) return false;
    executions = s.getIntParams()[0];
    pulses = s.getIntParams()[1];
    times.clear();
    return true;
  }

  @Override
  public synchronized String getWiredData() {
    return WiredManager.getGson().toJson(new Data(1, executions, pulses));
  }

  @Override
  public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
    onPickUp();
    if (set == null) return;
    String raw = set.getString("wired_data");
    if (raw == null || raw.isBlank() || !raw.startsWith("{")) return;
    try {
      Data d = WiredManager.getGson().fromJson(raw, Data.class);
      if (d != null
          && d.v == 1
          && d.executions >= 1
          && d.executions <= 100
          && d.pulses >= 1
          && d.pulses <= 20) {
        executions = d.executions;
        pulses = d.pulses;
      }
    } catch (RuntimeException ignored) {
    }
  }

  @Override
  public synchronized void onPickUp() {
    executions = 1;
    pulses = 4;
    times.clear();
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {executions, pulses};
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  public synchronized boolean allowExecution(long now) {
    long cutoff = now - pulses * 500L;
    while (!times.isEmpty() && times.peekFirst() <= cutoff) times.removeFirst();
    if (times.size() >= executions) return false;
    times.addLast(now);
    return true;
  }

  private static boolean ownsOnly(WiredSettingsV2 s) {
    return s != null
        && s.getIntParams().length == 2
        && s.getStringParam().isEmpty()
        && s.getFurniIds().length == 0
        && s.getFurniIds2().length == 0
        && s.getVariableIds().length == 0
        && s.getFurniSourceTypes().length == 0
        && s.getUserSourceTypes().length == 0;
  }

  static final class Data {
    int v, executions, pulses;

    Data() {}

    Data(int v, int e, int p) {
      this.v = v;
      executions = e;
      pulses = p;
    }
  }
}
