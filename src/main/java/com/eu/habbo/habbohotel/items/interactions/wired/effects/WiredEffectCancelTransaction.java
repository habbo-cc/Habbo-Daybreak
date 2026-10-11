package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

/** July AIR action 48 ({@code wf_act_cancel_transaction}). */
public final class WiredEffectCancelTransaction extends WiredEffectConfigBase {
    private static final int MATCH_CONTRACT = 0;
    private static final int MATCH_ANY = 1;

    public WiredEffectCancelTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectCancelTransaction(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.CANCEL_TRANSACTION;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        int[] params = settings == null ? null : settings.getIntParams();
        if (params == null
                || params.length != 1
                || (params[0] != MATCH_CONTRACT && params[0] != MATCH_ANY)
                || settings.getVariableIds().length != 0
                || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes().length != 1
                || settings.getStringParam() == null
                || !settings.getStringParam().isEmpty()
                || settings.getDelay() < 0
                || settings.getDelay() > 20) {
            throw new WiredSaveException("Invalid July cancel transaction data");
        }
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null) {
            throw new WiredSaveException("Transaction room is unavailable");
        }
        for (int id : settings.getFurniIds()) {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (!isContract(item)) {
                throw new WiredSaveException("A cancellation contract selection is invalid");
            }
        }
        return super.saveData(settings, client);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null
                || context.room() == null
                || !WiredFeatureCapabilityGuard.isRuntimeReady(this)) {
            return;
        }
        boolean any = this.intParams.length == 1 && this.intParams[0] == MATCH_ANY;
        Set<Integer> contractIds =
                any
                        ? Set.of()
                        : sourceItems(context, 0).stream()
                                .filter(WiredEffectCancelTransaction::isContract)
                                .map(HabboItem::getId)
                                .collect(Collectors.toUnmodifiableSet());
        if (!any && contractIds.isEmpty()) {
            return;
        }
        for (Habbo user :
                ChestWiredSupport.habbos(
                        context.room(),
                        resolveUserSource(context, this.userSourceTypes, 0))) {
            Emulator.getGameEnvironment()
                    .getChestManager()
                    .cancelContractTransaction(user, context.room(), contractIds, any);
        }
    }

    private static boolean isContract(HabboItem item) {
        return item instanceof InteractionChestContract
                || item instanceof WiredAddonCustomContract;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 1;
    }

    @Override
    protected boolean supportsFurniPickingWhenEmpty() {
        return true;
    }

    @Override
    protected boolean supportsUserPicking() {
        return true;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {
            FURNI_SOURCE_PICKED_1,
            FURNI_SOURCE_TRIGGERING_ITEM,
            FURNI_SOURCE_SELECTOR,
            FURNI_SOURCE_SIGNAL
        };
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {
            USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL
        };
    }
}
