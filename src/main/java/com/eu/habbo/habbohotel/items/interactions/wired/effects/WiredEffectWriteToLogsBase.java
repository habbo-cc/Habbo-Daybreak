package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTextTransform;
import com.eu.habbo.habbohotel.wired.menu.WiredRoomMonitor;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared July AIR payload and runtime for actions 49 and 50. */
abstract class WiredEffectWriteToLogsBase extends WiredEffectConfigBase {
    private static final int MAX_MESSAGE_LENGTH = 400;

    protected WiredEffectWriteToLogsBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredEffectWriteToLogsBase(
            int id, int userId, Item item, String extradata,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client)
            throws WiredSaveException {
        if (settings == null
                || settings.getIntParams() == null
                || settings.getIntParams().length != 1
                || settings.getIntParams()[0] < WiredRoomMonitor.LEVEL_INFO
                || settings.getIntParams()[0] > WiredRoomMonitor.LEVEL_DEBUG
                || settings.getStringParam() == null
                || settings.getStringParam().length() > MAX_MESSAGE_LENGTH
                || settings.getDelay() < 0
                || settings.getDelay() > Emulator.getConfig().getInt(
                        "hotel.wired.max_delay", 20)
                || settings.getFurniIds().length != 0
                || settings.getFurniIds2().length != 0
                || settings.getVariableIds().length != 0
                || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 0) {
            throw new WiredSaveException("invalid write-to-logs settings");
        }
        return super.saveData(settings, client);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) {
            return;
        }
        int level = this.intParams.length == 1
                ? this.intParams[0] : WiredRoomMonitor.LEVEL_INFO;
        WiredRoomMonitor.wiredLog(
                context.room(),
                level,
                WiredTextTransform.apply(context, this.stringParam));
    }
}
