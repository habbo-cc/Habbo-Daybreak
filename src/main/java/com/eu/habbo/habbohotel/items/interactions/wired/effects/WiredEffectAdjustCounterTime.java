package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * July AIR action 38: increase, decrease, or set a game counter with
 * 0.5-second precision.
 *
 * <p>The counter-adjustment runtime was adapted in part from Seth/iSetht's
 * GPL-3.0 implementation:
 * https://github.com/Harmonicrain/Arcturus-Community-Wired. Parameter ordering
 * was corrected to July AIR's four-int payload.</p>
 */
public final class WiredEffectAdjustCounterTime extends WiredEffectConfigBase {
    private static final int INCREASE = 0;
    private static final int DECREASE = 1;
    private static final int SET = 2;

    public WiredEffectAdjustCounterTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectAdjustCounterTime(int id, int userId, Item item, String extradata,
                                        int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.ADJUST_COUNTER;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected boolean supportsFurniPickingWhenEmpty() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        int[] params = settings == null ? null : settings.getIntParams();
        if (room == null || params == null || params.length != 4
                || params[0] < 0 || params[0] > 59
                || params[1] < 0 || params[1] > 99
                || params[2] < 0 || params[2] > 1
                || params[3] < INCREASE || params[3] > SET
                || settings.getFurniIds().length > WiredManager.MAXIMUM_FURNI_SELECTION
                || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes().length != 0
                || settings.getVariableIds().length != 0
                || settings.getStringParam() == null || !settings.getStringParam().isEmpty()
                || settings.getDelay() < 0 || settings.getDelay() > 20) {
            throw new WiredSaveException("Invalid Adjust Counter Time data");
        }
        for (int visibleId : settings.getFurniIds()) {
            if (!(room.getHabboItem(visibleId) instanceof InteractionGameTimer)) {
                throw new WiredSaveException("Only game timers can be selected");
            }
        }
        return super.saveData(settings, client);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null || this.intParams.length != 4) {
            return;
        }
        int adjustment = this.intParams[1] * 120 + this.intParams[0] * 2 + this.intParams[2];
        int operation = this.intParams[3];
        for (HabboItem item : sourceItems(context)) {
            if (!(item instanceof InteractionGameTimer timer)) {
                continue;
            }
            context.budget().consumeTargets(1);
            int current = timer.getEffectiveHalfSeconds();
            int updated = switch (operation) {
                case DECREASE -> Math.max(0, current - adjustment);
                case SET -> adjustment;
                default -> (int) Math.min(Integer.MAX_VALUE - 1L,
                        (long) current + adjustment);
            };
            timer.setHalfTick((updated & 1) != 0);
            timer.setTimeNow((int) (((long) updated + 1L) / 2L));
            timer.setExtradata(timer.getTimeNow() + "\t" + baseTime(timer));
            context.room().updateItem(timer);
            timer.needsUpdate(true);
        }
    }

    private int baseTime(InteractionGameTimer timer) {
        String[] data = timer.getExtradata().split("\t");
        if (data.length > 1) {
            try {
                return Math.max(0, Integer.parseInt(data[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(0, timer.getTimeNow());
    }
}
