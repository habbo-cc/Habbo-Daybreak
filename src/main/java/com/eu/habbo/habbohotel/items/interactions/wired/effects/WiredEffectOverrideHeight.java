package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectOverrideHeight extends WiredEffectSetFurniAltitude {
    public static final WiredEffectType type = WiredEffectType.OVERRIDE_HEIGHT;

    public WiredEffectOverrideHeight(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectOverrideHeight(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
}
