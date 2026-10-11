package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestContractPlan;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;
import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** July AIR action 47 ({@code wf_act_init_transaction}). */
public final class WiredEffectInitiateTransaction extends WiredEffectConfigBase {
    private static final int MODE_NONE = ChestContractPlan.MULTIPLIER_NONE;
    private static final int MODE_FIXED = ChestContractPlan.MULTIPLIER_FIXED;
    private static final int MODE_AUTO = ChestContractPlan.MULTIPLIER_AUTO;
    private static final int VALUE_LITERAL = 0;
    private static final int VALUE_VARIABLE = 1;
    private static final int MIN_MULTIPLIER = 1;
    private static final int MAX_MULTIPLIER = 500;
    private static final int MIN_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 3600;

    public WiredEffectInitiateTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectInitiateTransaction(
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
        return WiredEffectType.INITIATE_TRANSACTION;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        int[] params = settings == null ? null : settings.getIntParams();
        String[] variables = settings == null ? null : settings.getVariableIds();
        if (params == null
                || params.length != 6
                || variables == null
                || variables.length != 1
                || settings.getFurniSourceTypes().length != 3
                || settings.getUserSourceTypes().length != 2
                || settings.getStringParam() == null
                || !settings.getStringParam().isEmpty()
                || settings.getDelay() < 0
                || settings.getDelay() > 20
                || params[0] < MODE_NONE
                || params[0] > MODE_AUTO
                || params[1] < MIN_MULTIPLIER
                || params[1] > MAX_MULTIPLIER
                || (params[2] != VALUE_LITERAL && params[2] != VALUE_VARIABLE)
                || !ChestWiredSupport.validTarget(params[3])
                || (params[4] != 0 && params[4] != 1)
                || params[5] < MIN_TIMEOUT_SECONDS
                || params[5] > MAX_TIMEOUT_SECONDS) {
            throw new WiredSaveException("Invalid July initiate transaction data");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null) {
            throw new WiredSaveException("Transaction room is unavailable");
        }
        if (params[0] != MODE_NONE
                && params[2] == VALUE_VARIABLE
                && !ChestWiredSupport.definitionMatches(room, variables[0], params[3])) {
            throw new WiredSaveException("Invalid transaction multiplier variable");
        }
        validateSelections(room, settings.getFurniIds(), settings.getFurniIds2());
        return super.saveData(settings, client);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null
                || context.room() == null
                || !WiredFeatureCapabilityGuard.isRuntimeReady(this)) {
            return;
        }
        int multiplier = resolvedMultiplier(context);
        ChestManager manager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> chests =
                sourceItems(context, 0).stream()
                        .filter(item -> manager.isWiredUsable(item, ChestType.COINS)
                                || manager.isWiredUsable(item, ChestType.FURNI))
                        .sorted(Comparator.comparingInt(HabboItem::getId))
                        .toList();
        List<Habbo> users =
                ChestWiredSupport.habbos(
                        context.room(),
                        resolveUserSource(context, this.userSourceTypes, 0));
        List<HabboItem> contracts = resolvedContracts(context);

        if (multiplier == 0 || chests.isEmpty() || users.isEmpty() || contracts.isEmpty()) {
            failKnownTransactions(
                    context,
                    users,
                    contracts,
                    ChestTransactionFailure.WIRED_MISCONFIGURATION);
            return;
        }

        int timeoutSeconds = this.intParams[4] == 1 ? this.intParams[5] : 0;
        for (HabboItem contract : contracts) {
            ChestContractPlan plan = contractPlan(contract, context, multiplier);
            if (plan == null) {
                failKnownTransactions(
                        context,
                        users,
                        List.of(contract),
                        ChestTransactionFailure.WIRED_MISCONFIGURATION);
                continue;
            }
            if (plan.getRule() != null
                    && users.size() > 1
                    && !manager.canFulfillContractRewards(plan, chests, users.size())) {
                failKnownTransactions(
                        context,
                        users,
                        List.of(contract),
                        ChestTransactionFailure.CANNOT_GIVE_ALL_TO_MULTIPLE_USERS);
                continue;
            }
            for (Habbo user : users) {
                ChestTransactionFailure failure =
                        manager.startContractTransaction(
                                context.room(), user, chests, plan, timeoutSeconds);
                if (failure != null) {
                    WiredManager.triggerTransactionFailed(
                            context.room(), user.getRoomUnit(), contract, failure);
                }
            }
        }
    }

    private int resolvedMultiplier(WiredContext context) {
        if (this.intParams.length != 6 || this.intParams[0] == MODE_NONE) {
            return this.intParams.length == 6 ? 1 : 0;
        }
        if (this.intParams[2] == VALUE_LITERAL) {
            return validMultiplier(this.intParams[1]) ? this.intParams[1] : 0;
        }
        Integer value =
                ChestWiredSupport.resolveValue(
                        context,
                        this.variableIds.length == 1 ? this.variableIds[0] : "",
                        this.intParams[3],
                        sourceItems(context, 2),
                        resolveUserSource(context, this.userSourceTypes, 1));
        return value != null && validMultiplier(value) ? value : 0;
    }

    private ChestContractPlan contractPlan(
            HabboItem contract, WiredContext context, int multiplier) {
        int mode = this.intParams[0];
        if (contract instanceof InteractionChestContract chestContract) {
            return ChestContractPlan.fromContract(chestContract, mode, multiplier);
        }
        if (contract instanceof WiredAddonCustomContract customContract) {
            return ChestContractPlan.fromCustomAddon(customContract, context, mode, multiplier);
        }
        return null;
    }

    private List<HabboItem> resolvedContracts(WiredContext context) {
        Map<Integer, HabboItem> result = new LinkedHashMap<>();
        sourceItems(context, 1).stream()
                .filter(WiredEffectInitiateTransaction::isContract)
                .sorted(Comparator.comparingInt(HabboItem::getId))
                .forEach(item -> result.put(item.getId(), item));
        if (result.isEmpty() && context.stack() != null) {
            var addon = context.stack().addon(WiredAddonType.CUSTOM_CONTRACT);
            if (addon instanceof WiredAddonCustomContract customContract) {
                result.put(customContract.getId(), customContract);
            }
        }
        return List.copyOf(result.values());
    }

    private void validateSelections(Room room, int[] chestIds, int[] contractIds)
            throws WiredSaveException {
        if (chestIds == null || contractIds == null) {
            throw new WiredSaveException("Missing transaction selections");
        }
        ChestManager manager = Emulator.getGameEnvironment().getChestManager();
        for (int id : chestIds) {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (item == null || !manager.isChest(item)) {
                throw new WiredSaveException("A transaction chest selection is invalid");
            }
        }
        for (int id : contractIds) {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (!isContract(item)) {
                throw new WiredSaveException("A transaction contract selection is invalid");
            }
        }
    }

    private static boolean isContract(HabboItem item) {
        return item instanceof InteractionChestContract
                || item instanceof WiredAddonCustomContract;
    }

    private static boolean validMultiplier(int value) {
        return value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER;
    }

    private static void failKnownTransactions(
            WiredContext context,
            Collection<Habbo> users,
            Collection<HabboItem> contracts,
            ChestTransactionFailure failure) {
        if (context == null || users == null || contracts == null) {
            return;
        }
        for (HabboItem contract : contracts) {
            for (Habbo user : users) {
                if (user != null) {
                    WiredManager.triggerTransactionFailed(
                            context.room(), user.getRoomUnit(), contract, failure);
                }
            }
        }
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 3;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 2;
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
        if (slot == 0) {
            return new int[] {
                FURNI_SOURCE_PICKED_1,
                FURNI_SOURCE_TRIGGERING_ITEM,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_SIGNAL
            };
        }
        if (slot == 1) {
            return new int[] {
                FURNI_SOURCE_PICKED_2,
                FURNI_SOURCE_TRIGGERING_ITEM,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_SIGNAL
            };
        }
        return new int[] {
            FURNI_SOURCE_PICKED_1,
            FURNI_SOURCE_PICKED_2,
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

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return switch (slot) {
            case 0 -> FURNI_SOURCE_PICKED_1;
            case 1 -> FURNI_SOURCE_PICKED_2;
            default -> FURNI_SOURCE_TRIGGERING_ITEM;
        };
    }
}
