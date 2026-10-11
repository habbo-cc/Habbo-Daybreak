package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.api.IWiredNegativeEffect;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR negative action 37; executes only through the engine's failed-condition lane. */
public final class WiredEffectSendSignalNegative extends WiredEffectSendSignalBase
        implements IWiredNegativeEffect {
    public WiredEffectSendSignalNegative(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSendSignalNegative(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.NEGATIVE_SEND_SIGNAL;
    }
}
