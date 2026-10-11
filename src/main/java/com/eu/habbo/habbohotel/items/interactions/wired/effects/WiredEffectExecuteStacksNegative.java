package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.api.IWiredNegativeEffect;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * July AIR action 36: execute another stack when this stack's conditions fail.
 *
 * <p>Behavior was adapted from Seth/iSetht's GPL-3.0 negative Execute Stacks:
 * https://github.com/Harmonicrain/Arcturus-Community-Wired, then integrated
 * with this project's bounded negative-effect execution lane.</p>
 */
public final class WiredEffectExecuteStacksNegative extends WiredEffectTriggerStacks
        implements IWiredNegativeEffect {
    public WiredEffectExecuteStacksNegative(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectExecuteStacksNegative(int id, int userId, Item item, String extradata,
                                            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.NEGATIVE_EXECUTE_STACKS;
    }
}
