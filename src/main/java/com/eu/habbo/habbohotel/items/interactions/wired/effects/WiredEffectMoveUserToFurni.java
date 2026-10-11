package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectMoveUserToFurni extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.MOVE_USER_TO_FURNI;

    public WiredEffectMoveUserToFurni(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectMoveUserToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override public boolean requiresTriggeringUser() { return false; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected boolean supportsUserPicking() { return true; }

    @Override
    public void execute(WiredContext ctx) {
        HabboItem target = sourceItems(ctx).stream()
                .filter(item -> item != null)
                .sorted(java.util.Comparator.comparingInt(HabboItem::getId))
                .findFirst().orElse(null);
        if (target == null) return;
        RoomTile tile = ctx.room().getLayout().getTile(target.getX(), target.getY());
        if (tile == null) return;
        for (RoomUnit unit : sourceUsers(ctx)) {
            int walkMode = this.intParams.length == 0 ? 0 : this.intParams[0];
            WiredMovementAddonRuntime.moveUser(ctx, unit, tile, walkMode);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams() == null || settings.getIntParams().length != 1
                || settings.getIntParams()[0] < 0 || settings.getIntParams()[0] > 2) {
            throw new WiredSaveException("Invalid walk mode");
        }
        return super.saveData(settings, gameClient);
    }
}
