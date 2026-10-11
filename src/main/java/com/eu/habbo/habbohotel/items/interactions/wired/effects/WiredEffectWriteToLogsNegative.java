package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.api.IWiredNegativeEffect;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR negative Write to Logs action (type 50, furni class wf_act_neg_log). */
public final class WiredEffectWriteToLogsNegative extends WiredEffectWriteToLogsBase
        implements IWiredNegativeEffect {
    public WiredEffectWriteToLogsNegative(ResultSet set, Item baseItem)
            throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectWriteToLogsNegative(
            int id, int userId, Item item, String extradata,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.NEGATIVE_WRITE_TO_LOGS;
    }
}
