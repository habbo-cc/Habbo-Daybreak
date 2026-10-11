package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR positive Write to Logs action (type 49, furni class wf_act_log). */
public final class WiredEffectWriteToLogs extends WiredEffectWriteToLogsBase {
    public WiredEffectWriteToLogs(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectWriteToLogs(
            int id, int userId, Item item, String extradata,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.WRITE_TO_LOGS;
    }
}
