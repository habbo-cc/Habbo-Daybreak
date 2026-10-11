package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonAchievementEnabler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.*;
import java.util.*;

/** July AIR action 51. Achievement names are client identifiers without ACH_WF_. */
public final class WiredEffectProgressAchievement extends WiredEffectConfigBase {
  public static final WiredEffectType type = WiredEffectType.PROGRESS_ACHIEVEMENT;
  private int mode = 1, option = 0, amount = 0, target = 0;
  private String achievement = "", variable = "";

  public WiredEffectProgressAchievement(ResultSet s, Item i) throws SQLException {
    super(s, i);
  }

  public WiredEffectProgressAchievement(int id, int u, Item i, String e, int l, int n) {
    super(id, u, i, e, l, n);
  }

  @Override
  public WiredEffectType getType() {
    return type;
  }

  @Override
  public boolean requiresTriggeringUser() {
    return false;
  }

  @Override
  public boolean saveData(WiredSettings s, GameClient c) throws WiredSaveException {
    Room r = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
    int[] p = s == null ? null : s.getIntParams();
    if (r == null
        || p == null
        || p.length != 4
        || (p[0] != 0 && p[0] != 1)
        || (p[1] != 0 && p[1] != 1)
        || p[2] < 0
        || s.getVariableIds().length != 1
        || s.getFurniIds().length != 0
        || s.getFurniIds2().length != 0
        || s.getFurniSourceTypes().length != 1
        || s.getUserSourceTypes().length != 2
        || s.getDelay() < 0
        || s.getDelay() > 20) throw new WiredSaveException("Invalid Progress Achievement data");
    String a = s.getStringParam();
    if (a == null || a.isBlank() || a.length() > 64 || !enabled(r, a))
      throw new WiredSaveException("Achievement is not enabled on this stack");
    if (p[1] == 1 && !validVariable(r, s.getVariableIds()[0], p[3]))
      throw new WiredSaveException("Invalid progression variable");
    mode = p[0];
    option = p[1];
    amount = p[2];
    target = p[3];
    achievement = a;
    variable = p[1] == 1 ? s.getVariableIds()[0] : "";
    setDelay(s.getDelay());
    return true;
  }

  @Override
  public void execute(WiredContext ctx) {
    Achievement a =
        Emulator.getGameEnvironment().getAchievementManager().getAchievement("WF_" + achievement);
    if (a == null || ctx == null || !enabled(ctx.room(), achievement)) return;
    List<Habbo> users = new ArrayList<>();
    if (option == 1 && target == 1) {
      for (RoomUnit u : resolveUserSource(ctx, getWiredUserSourceTypes(), 1)) {
        Habbo h = ctx.room().getHabbo(u);
        if (h != null) users.add(h);
      }
    } else ctx.actor().map(ctx.room()::getHabbo).ifPresent(users::add);
    for (Habbo h : users) {
      int value = resolveAmount(ctx, h);
      if (value < 0) continue;
      int delta =
          mode == 1 ? value : value - Math.max(0, h.getHabboStats().getAchievementProgress(a));
      if (delta > 0) AchievementManager.progressAchievement(h, a, delta);
    }
  }

  private int resolveAmount(WiredContext c, Habbo h) {
    if (option == 0) return amount;
    if (target == -20) {
      Integer v = c.contextVariables().get(variable);
      return v == null ? -1 : v;
    }
    WiredVariableManager m = c.room().getRoomSpecialTypes().getWiredVariableManager();
    if (m == null) return -1;
    WiredVariableHolder holder;
    if (target == -10) holder = WiredVariableHolder.room();
    else if (target == 1) holder = WiredVariableHolder.user(h.getHabboInfo().getId());
    else {
      var item =
          resolveFurniSource(c, getWiredFurniSourceTypes(), 0, List.of(), List.of()).stream()
              .min(Comparator.comparingInt(x -> x.getId()))
              .orElse(null);
      if (item == null) return -1;
      holder = WiredVariableHolder.furni(item.getId());
    }
    WiredVariableValue v = m.get(variable, holder);
    return v == null || v.value() < 0 ? -1 : v.value();
  }

  private boolean enabled(Room r, String id) {
    return r != null
        && r.getRoomSpecialTypes().getAddons(getX(), getY()).stream()
            .anyMatch(x -> x instanceof WiredAddonAchievementEnabler e && e.enables(id));
  }

  private boolean validVariable(Room r, String id, int t) {
    if (id == null || id.isBlank() || !(t == 0 || t == 1 || t == -10 || t == -20)) return false;
    if (t == -20) {
      try {
        return r.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5))) != null;
      } catch (Exception e) {
        return false;
      }
    }
    WiredVariableManager m = r.getRoomSpecialTypes().getWiredVariableManager();
    if (m == null) return false;
    var definition = m.runtimeDefinition(id);
    return definition != null
        && definition.holderScope() != null
        && definition.holderScope().code == (t == 0 ? 2 : t == 1 ? 1 : 0);
  }

  @Override
  public String getWiredData() {
    return WiredManager.getGson()
        .toJson(new Data(1, mode, option, amount, target, achievement, variable));
  }

  @Override
  public void loadWiredData(ResultSet s, Room r) throws SQLException {
    onPickUp();
    try {
      Data d = WiredManager.getGson().fromJson(s.getString("wired_data"), Data.class);
      if (d != null && d.v == 1) {
        mode = d.mode;
        option = d.option;
        amount = d.amount;
        target = d.target;
        achievement = d.achievement;
        variable = d.variable;
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onPickUp() {
    mode = 1;
    option = amount = target = 0;
    achievement = variable = "";
    setDelay(0);
  }

  @Override
  protected int[] getWiredIntParams() {
    return new int[] {mode, option, amount, target};
  }

  @Override
  protected String[] getWiredVariableIds() {
    return new String[] {variable};
  }

  @Override
  protected String getWiredStringParam() {
    return achievement;
  }

  @Override
  protected int getFurniSourceSlotCount() {
    return 1;
  }

  @Override
  protected int getUserSourceSlotCount() {
    return 2;
  }

  @Override
  protected int[] getAllowedFurniSourcesForSlot(int i) {
    return new int[] {FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
  }

  @Override
  protected int[] getAllowedUserSourcesForSlot(int i) {
    return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
  }

  @Override
  protected boolean isWiredAdvancedMode() {
    return true;
  }

  private static final class Data {
    int v, mode, option, amount, target;
    String achievement, variable;

    Data(int v, int m, int o, int a, int t, String n, String x) {
      this.v = v;
      mode = m;
      option = o;
      amount = a;
      target = t;
      achievement = n;
      variable = x;
    }
  }
}
