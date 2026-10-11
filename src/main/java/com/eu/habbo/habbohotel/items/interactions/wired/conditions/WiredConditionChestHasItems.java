package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** July AIR condition 45 ({@code wf_cnd_chest_has_items}). */
public final class WiredConditionChestHasItems extends WiredConditionChestBase {
    public WiredConditionChestHasItems(ResultSet set, Item item) throws SQLException { super(set,item); }
    public WiredConditionChestHasItems(int id,int userId,Item item,String data,int stack,int sells) {
        super(id,userId,item,data,stack,sells);
    }
    @Override public WiredConditionType getType() { return WiredConditionType.CHEST_HAS_ITEMS; }
    @Override protected int furniSlots() { return 2; }
    @Override protected boolean validSelections(ChestManager manager) {
        return this.selected2.isEmpty() && this.selected.stream().allMatch(manager::isChest);
    }
    @Override protected List<HabboItem> resolvedChests(WiredContext context, ChestManager manager) {
        List<HabboItem> all = new java.util.ArrayList<>();
        all.addAll(com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport.chests(
                manager, resolveFurniSource(context, getWiredFurniSourceTypes(), 0,
                        this.selected, this.selected2), ChestType.FURNI));
        all.addAll(com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport.chests(
                manager, resolveFurniSource(context, getWiredFurniSourceTypes(), 0,
                        this.selected, this.selected2), ChestType.COINS));
        return all;
    }
    @Override protected long valueFor(ChestManager manager, HabboItem chest, WiredContext context) {
        return manager.contentsCount(chest);
    }
}
