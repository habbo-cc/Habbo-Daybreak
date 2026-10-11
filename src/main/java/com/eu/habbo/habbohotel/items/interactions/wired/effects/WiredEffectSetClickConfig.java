package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.WiredClickSettingsMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July action 54 (wf_act_click_conf), applying [userMode 0..2, furniMode 0..1] to trigger users. */
public class WiredEffectSetClickConfig extends WiredEffectConfigBase {
    private static final WiredEffectType TYPE = WiredEffectType.SET_CLICK_CONFIG;

    public WiredEffectSetClickConfig(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectSetClickConfig(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return TYPE; }
    @Override public boolean requiresTriggeringUser() { return true; }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        if (settings.getIntParams() == null || settings.getIntParams().length != 2) {
            throw new WiredSaveException("click settings require user and furni modes");
        }
        if (!super.saveData(settings, client)) {
            return false;
        }
        this.intParams[0] = normalizeUserMode(this.intParams[0]);
        this.intParams[1] = normalizeFurniMode(this.intParams[1]);
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        int userMode = this.intParams.length > 0 ? normalizeUserMode(this.intParams[0]) : 0;
        int furniMode = this.intParams.length > 1 ? normalizeFurniMode(this.intParams[1]) : 0;
        for (RoomUnit unit : sourceUsers(ctx)) {
            Habbo habbo = ctx.room().getHabbo(unit);
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(new WiredClickSettingsMessageComposer(userMode, furniMode));
            }
        }
    }

    private static int normalizeUserMode(int mode) { return mode >= 0 && mode <= 2 ? mode : 0; }
    private static int normalizeFurniMode(int mode) { return mode >= 0 && mode <= 1 ? mode : 0; }
}
