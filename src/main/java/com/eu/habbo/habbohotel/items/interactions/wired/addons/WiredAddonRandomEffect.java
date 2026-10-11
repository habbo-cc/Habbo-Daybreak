package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** July code 1. Recent selections are runtime-only and reset on save/reload/pickup. */
public final class WiredAddonRandomEffect extends InteractionWiredAddon {
  private int skips;
  private int picks = 1;
  private final Deque<Set<Integer>> history = new ArrayDeque<>();

  public WiredAddonRandomEffect(ResultSet set, Item baseItem) throws SQLException {
    super(set, baseItem);
  }

  public WiredAddonRandomEffect(
      int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
    super(id, userId, item, extradata, limitedStack, limitedSells);
  }

  @Override
  public WiredAddonType getType() {
    return WiredAddonType.EFFECT_PICK_AND_SKIP;
  }

  @Override
  public synchronized boolean saveData(WiredSettingsV2 s) {
    if (!ownsOnly(s, 2)
        || s.getIntParams()[0] < 0
        || s.getIntParams()[0] > 100
        || s.getIntParams()[1] < 1
        || s.getIntParams()[1] > 100) return false;
    skips = s.getIntParams()[0];
    picks = s.getIntParams()[1];
    history.clear();
    return true;
  }

  @Override
  public synchronized String getWiredData() {
    return WiredManager.getGson().toJson(new Data(1, skips, picks));
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
          && d.skips >= 0
          && d.skips <= 100
          && d.picks >= 1
          && d.picks <= 100) {
        skips = d.skips;
        picks = d.picks;
      }
    } catch (RuntimeException ignored) {
    }
  }

  @Override
  public synchronized void onPickUp() {
    skips = 0;
    picks = 1;
    history.clear();
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {skips, picks};
  }

  @Override
  protected int getMaxFurniSelection() {
    return 0;
  }

  public synchronized List<IWiredEffect> selectEffects(List<IWiredEffect> effects, Random random) {
    if (effects == null || effects.isEmpty()) return List.of();
    while (history.size() > skips) history.removeLast();
    Set<Integer> unavailable = new HashSet<>();
    history.forEach(unavailable::addAll);
    List<IWiredEffect> pool = available(effects, unavailable);
    if (pool.isEmpty()) pool = new ArrayList<>(effects);
    int amount = Math.min(picks, pool.size());
    Collections.shuffle(pool, Objects.requireNonNull(random));
    List<IWiredEffect> selected = new ArrayList<>(pool.subList(0, amount));
    if (skips > 0) {
      Set<Integer> ids = new HashSet<>();
      selected.forEach(e -> ids.add(id(e)));
      history.addFirst(ids);
      while (history.size() > skips) history.removeLast();
    } else history.clear();
    return selected;
  }

  private static List<IWiredEffect> available(List<IWiredEffect> effects, Set<Integer> excluded) {
    List<IWiredEffect> out = new ArrayList<>();
    for (IWiredEffect e : effects) if (!excluded.contains(id(e))) out.add(e);
    return out;
  }

  private static int id(IWiredEffect e) {
    return e instanceof InteractionWiredEffect x ? x.getId() : System.identityHashCode(e);
  }

  private static boolean ownsOnly(WiredSettingsV2 s, int n) {
    return s != null
        && s.getIntParams().length == n
        && s.getStringParam().isEmpty()
        && s.getFurniIds().length == 0
        && s.getFurniIds2().length == 0
        && s.getVariableIds().length == 0
        && s.getFurniSourceTypes().length == 0
        && s.getUserSourceTypes().length == 0;
  }

  static final class Data {
    int v, skips, picks;

    Data() {}

    Data(int v, int skips, int picks) {
      this.v = v;
      this.skips = skips;
      this.picks = picks;
    }
  }
}
