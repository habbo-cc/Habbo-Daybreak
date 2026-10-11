package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** July AIR action 58: add to or set a Reward Track task's progress. */
public final class WiredEffectProgressRewardTrack extends WiredEffectConfigBase {
    private static final int LITERAL = 0;
    private static final int VARIABLE = 1;

    public WiredEffectProgressRewardTrack(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectProgressRewardTrack(int id, int userId, Item item, String extradata,
                                          int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.PROGRESS_REWARD_TRACK;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 2;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        RewardTrackManager manager = Emulator.getGameEnvironment().getRewardTrackManager();
        int[] params = settings == null ? null : settings.getIntParams();
        String rawIds = settings == null ? null : settings.getStringParam();
        String[] ids = rawIds == null ? new String[0] : rawIds.split("\\t", -1);
        if (room == null || manager == null || params == null || params.length != 4
                || (params[0] != 0 && params[0] != 1)
                || (params[1] != LITERAL && params[1] != VARIABLE)
                || params[2] < 0 || ids.length != 2
                || !validId(ids[0]) || !validId(ids[1]) || !manager.hasTask(ids[0], ids[1])
                || settings.getVariableIds().length != 1
                || (params[1] == VARIABLE && (settings.getVariableIds()[0] == null
                    || settings.getVariableIds()[0].isBlank()
                    || !validTarget(params[3])))
                || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes().length != 2
                || settings.getDelay() < 0 || settings.getDelay() > 20) {
            throw new WiredSaveException("Invalid Progress Reward Track data");
        }
        return super.saveData(settings, client);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null || this.intParams.length != 4) {
            return;
        }
        String[] ids = this.stringParam.split("\\t", -1);
        if (ids.length != 2) {
            return;
        }
        RewardTrackManager manager = Emulator.getGameEnvironment().getRewardTrackManager();
        if (manager == null || !manager.hasTask(ids[0], ids[1])) {
            return;
        }
        Collection<RoomUnit> users;
        if (this.intParams[1] == VARIABLE && this.intParams[3] == 1) {
            users = resolveUserSource(context, this.userSourceTypes, 1);
        } else {
            RoomUnit actor = context.actor().orElse(null);
            users = actor == null ? List.of() : List.of(actor);
        }
        for (RoomUnit unit : users) {
            Habbo habbo = context.room().getHabbo(unit);
            if (habbo == null) {
                continue;
            }
            int value = resolveValue(context, habbo);
            if (value < 0) {
                continue;
            }
            context.budget().consumeTargets(1);
            manager.progressWired(habbo, ids[0], ids[1], value, this.intParams[0] == 1);
        }
    }

    private int resolveValue(WiredContext context, Habbo habbo) {
        if (this.intParams[1] == LITERAL) {
            return this.intParams[2];
        }
        if (this.variableIds.length != 1 || this.variableIds[0].isBlank()) {
            return -1;
        }
        String variableId = this.variableIds[0];
        int target = this.intParams[3];
        if (target == -20) {
            Integer value = context.contextVariables().get(variableId);
            return value == null ? -1 : value;
        }
        WiredVariableManager variables =
                context.room().getRoomSpecialTypes().getWiredVariableManager();
        if (variables == null) {
            return -1;
        }
        WiredVariableHolder holder;
        if (target == -10) {
            holder = WiredVariableHolder.room();
        } else if (target == 1) {
            holder = WiredVariableHolder.user(habbo.getHabboInfo().getId());
        } else {
            var item = sourceItems(context).stream()
                    .min(Comparator.comparingInt(value -> value.getId())).orElse(null);
            if (item == null) {
                return -1;
            }
            holder = WiredVariableHolder.furni(item.getId());
        }
        WiredVariableValue value = variables.get(variableId, holder);
        if (value == null) {
            value = WiredGeneratedVariableRuntime.read(context.room(), variableId, holder);
        }
        Integer resolved = value == null
                ? WiredInternalVariableRuntime.read(context.room(), variableId, holder)
                : value.value();
        return resolved == null ? -1 : resolved;
    }

    private boolean validId(String value) {
        return value != null && !value.isBlank() && value.length() <= 100
                && value.matches("[A-Za-z0-9_]+");
    }

    private boolean validTarget(int target) {
        return target == 0 || target == 1 || target == -10 || target == -20;
    }
}
