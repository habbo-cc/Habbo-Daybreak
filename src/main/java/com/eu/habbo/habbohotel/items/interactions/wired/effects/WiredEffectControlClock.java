package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectControlClock extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.CONTROL_CLOCK;

    public WiredEffectControlClock(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectControlClock(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }

    @Override
    public void execute(WiredContext ctx) {
        int mode = this.intParams.length > 0 ? this.intParams[0] : 0;
        switch (mode) {
            case 1 -> WiredManager.getRoomClock(ctx.room()).stop();
            case 2 -> WiredManager.getRoomClock(ctx.room()).reset();
            case 3 -> { WiredManager.getRoomClock(ctx.room()).reset(); WiredManager.getRoomClock(ctx.room()).stop(); }
            case 4 -> { WiredManager.getRoomClock(ctx.room()).reset(); WiredManager.getRoomClock(ctx.room()).start(); }
            default -> WiredManager.getRoomClock(ctx.room()).start();
        }
    }
}
