package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class WiredSelectorFurniByType extends WiredSelectorConfigBase {
    public WiredSelectorFurniByType(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorFurniByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.FURNI_BY_TYPE; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] { FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL };
    }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        Collection<HabboItem> references = resolveFurniSource(
                ctx, this.furniSourceTypes, 0, this.items, java.util.List.of());
        if (references.isEmpty()) {
            return targets;
        }

        boolean matchState = this.intParams.length > 0 && this.intParams[0] != 0;
        Set<Integer> baseItemTypes = new HashSet<>();
        for (HabboItem reference : references) {
            if (reference != null && reference != this) {
                baseItemTypes.add(reference.getBaseItem().getId());
            }
        }
        for (HabboItem item : allFloorItems(room)) {
            if (item == this || !baseItemTypes.contains(item.getBaseItem().getId())) {
                continue;
            }
            if (!matchState || references.stream().anyMatch(reference ->
                    reference != null
                            && reference != this
                            && reference.getBaseItem().getId() == item.getBaseItem().getId()
                            && normalizeExtraData(reference.getExtradata())
                            .equals(normalizeExtraData(item.getExtradata())))) {
                targets.addItem(item);
            }
        }
        return targets;
    }
}
