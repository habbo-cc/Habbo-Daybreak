package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** July AIR condition 46 ({@code wf_cnd_chest_has_item_type}). */
public final class WiredConditionChestHasItemTypes extends WiredConditionChestBase {
    public WiredConditionChestHasItemTypes(ResultSet set, Item item) throws SQLException { super(set,item); }
    public WiredConditionChestHasItemTypes(int id,int userId,Item item,String data,int stack,int sells) {
        super(id,userId,item,data,stack,sells);
    }
    @Override public WiredConditionType getType() { return WiredConditionType.CHEST_HAS_ITEM_TYPES; }
    @Override protected int furniSlots() { return 3; }
    @Override protected boolean validSelections(ChestManager manager) {
        return this.selected.stream().noneMatch(manager::isChest)
                && this.selected2.stream().allMatch(item -> ChestType.fromItem(item) == ChestType.FURNI);
    }
    private List<HabboItem> types(WiredContext context, ChestManager manager) {
        return ChestWiredSupport.nonChests(manager,
                resolveFurniSource(context, getWiredFurniSourceTypes(), 0,
                        this.selected, this.selected2));
    }
    @Override protected List<HabboItem> resolvedChests(WiredContext context, ChestManager manager) {
        return ChestWiredSupport.chests(manager,
                resolveFurniSource(context, getWiredFurniSourceTypes(), 1,
                        this.selected2, this.selected), ChestType.FURNI);
    }
    @Override protected long valueFor(ChestManager manager, HabboItem chest, WiredContext context) {
        return manager.wiredFurnitureCount(chest, types(context, manager));
    }
}
