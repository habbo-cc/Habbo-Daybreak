package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.outgoing.rooms.users.AvatarEffectMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectGiveEffect extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.GIVE_EFFECT;

    public WiredEffectGiveEffect(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectGiveEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override public boolean requiresTriggeringUser() { return true; }
    @Override protected boolean supportsUserPicking() { return true; }

    @Override
    public void execute(WiredContext ctx) {
        int effect = this.intParams.length > 0 ? this.intParams[0] : 0;
        for (RoomUnit unit : sourceUsers(ctx)) {
            ctx.room().sendComposer(new AvatarEffectMessageComposer(unit, effect).compose());
        }
    }
}
