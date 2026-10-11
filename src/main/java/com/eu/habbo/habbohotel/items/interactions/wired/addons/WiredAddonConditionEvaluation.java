package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July code 0.  Modes 0..3 are all/any/none/not-all; -1 compares passed conditions. */
public final class WiredAddonConditionEvaluation extends InteractionWiredAddon {
    private int mode = 1; // Existing wf_xtra_or_eval rooms retain their historical OR behaviour.
    private int comparison;
    private int threshold;

    public WiredAddonConditionEvaluation(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredAddonConditionEvaluation(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredAddonType getType() { return WiredAddonType.CONDITION_EVALUATION; }

    @Override public boolean saveData(WiredSettingsV2 settings) {
        if (!ownsOnly(settings, 3)) return false;
        int[] p = settings.getIntParams();
        if ((p[0] < -1 || p[0] > 3) || (p[0] == -1 && (p[1] < 0 || p[1] > 2 || p[2] < 0 || p[2] > 1000))) return false;
        mode = p[0]; comparison = p[1]; threshold = p[2]; return true;
    }
    @Override public String getWiredData() { return WiredManager.getGson().toJson(new Data(1, mode, comparison, threshold)); }
    @Override public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset(); if (set == null) return; String raw = set.getString("wired_data");
        if (raw == null || raw.isBlank() || !raw.startsWith("{")) return;
        try { Data data = WiredManager.getGson().fromJson(raw, Data.class); if (data != null && data.v == 1 && data.mode >= -1 && data.mode <= 3 && (data.mode != -1 || (data.comparison >= 0 && data.comparison <= 2 && data.threshold >= 0 && data.threshold <= 1000))) { mode = data.mode; comparison = data.comparison; threshold = data.threshold; } } catch (RuntimeException ignored) { }
    }
    @Override public void onPickUp() { reset(); }
    @Override protected int[] getWiredIntParams() { return new int[] {mode, comparison, threshold}; }
    @Override protected int getMaxFurniSelection() { return 0; }
    public boolean evaluate(int passed, int total) {
        return switch (mode) { case 0 -> total > 0 && passed == total; case 1 -> passed > 0; case 2 -> passed == 0; case 3 -> total > 0 && passed < total; case -1 -> switch (comparison) { case 0 -> passed == threshold; case 1 -> passed >= threshold; default -> passed <= threshold; }; default -> false; };
    }
    private void reset() { mode = 1; comparison = 0; threshold = 0; }
    private static boolean ownsOnly(WiredSettingsV2 s, int ints) { return s != null && s.getIntParams().length == ints && s.getStringParam().isEmpty() && s.getFurniIds().length == 0 && s.getFurniIds2().length == 0 && s.getVariableIds().length == 0 && s.getFurniSourceTypes().length == 0 && s.getUserSourceTypes().length == 0; }
    static final class Data { int v; int mode; int comparison; int threshold; Data() {} Data(int v, int mode, int comparison, int threshold) { this.v=v; this.mode=mode; this.comparison=comparison; this.threshold=threshold; } }
}
