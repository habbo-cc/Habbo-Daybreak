package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR action 59: reset the triggering user's progress for one Reward Track. */
public final class WiredEffectResetRewardTrack extends WiredEffectConfigBase {
    public WiredEffectResetRewardTrack(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectResetRewardTrack(int id, int userId, Item item, String extradata,
                                       int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.RESET_REWARD_TRACK;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        RewardTrackManager manager = Emulator.getGameEnvironment().getRewardTrackManager();
        String trackId = settings == null ? null : settings.getStringParam();
        if (manager == null || trackId == null || trackId.isBlank() || trackId.length() > 100
                || !manager.hasTrack(trackId)
                || settings.getIntParams().length != 0
                || settings.getVariableIds().length != 0
                || settings.getFurniIds().length != 0 || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 0
                || settings.getDelay() < 0 || settings.getDelay() > 20) {
            throw new WiredSaveException("Invalid Reset Reward Track data");
        }
        return super.saveData(settings, client);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null || this.stringParam.isBlank()) {
            return;
        }
        RewardTrackManager manager = Emulator.getGameEnvironment().getRewardTrackManager();
        Habbo habbo = context.actor().map(context.room()::getHabbo).orElse(null);
        if (manager != null && habbo != null) {
            context.budget().consumeTargets(1);
            manager.resetWired(habbo, this.stringParam);
        }
    }
}
