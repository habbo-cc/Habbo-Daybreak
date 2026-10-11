package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * July AIR add-on 20 ({@code wf_xtra_custom_contract}).
 *
 * <p>The wire shape is exactly ten integers and two variable IDs. Seth's older fourteen-integer
 * JSON contract is deliberately not accepted.</p>
 */
public final class WiredAddonCustomContract extends InteractionWiredAddon {
    public static final int TYPE_COINS = 0;
    public static final int TYPE_FURNITURE = 1;
    public static final int AMOUNT_LITERAL = 0;
    public static final int AMOUNT_VARIABLE = 1;
    public static final String NO_VARIABLE = "n";

    private static final int VERSION = 1;
    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 100_000;
    private static final int PAYMENT_OFFSET = 0;
    private static final int REWARD_OFFSET = 5;

    private int[] params = defaults();
    private String[] variableIds = new String[] {NO_VARIABLE, NO_VARIABLE};
    private final List<HabboItem> paymentFurniture = new ArrayList<>();
    private final List<HabboItem> rewardFurniture = new ArrayList<>();

    public WiredAddonCustomContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredAddonCustomContract(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.CUSTOM_CONTRACT;
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null
                || !validParams(settings.getIntParams())
                || settings.getVariableIds().length != 2
                || !"".equals(settings.getStringParam())
                || settings.getFurniSourceTypes().length != 4
                || settings.getUserSourceTypes().length != 2
                || settings.getDelay() != 0
                || settings.getQuantifierCode() != 0
                || settings.isFilter()
                || settings.isInvert()
                || !validSources(
                        settings.getFurniSourceTypes(), settings.getUserSourceTypes())) {
            return false;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        List<HabboItem> payment = loadDatabaseItems(room, settings.getFurniIds());
        List<HabboItem> reward = loadDatabaseItems(room, settings.getFurniIds2());
        if (room == null
                || payment.size() != settings.getFurniIds().length
                || reward.size() != settings.getFurniIds2().length
                || !validVariable(
                        room,
                        settings.getIntParams(),
                        settings.getVariableIds(),
                        PAYMENT_OFFSET,
                        0)
                || !validVariable(
                        room,
                        settings.getIntParams(),
                        settings.getVariableIds(),
                        REWARD_OFFSET,
                        1)) {
            return false;
        }

        this.params = settings.getIntParams().clone();
        this.variableIds =
                new String[] {
                    normalizedVariable(this.params, settings.getVariableIds(), PAYMENT_OFFSET, 0),
                    normalizedVariable(this.params, settings.getVariableIds(), REWARD_OFFSET, 1)
                };
        this.paymentFurniture.clear();
        this.paymentFurniture.addAll(payment);
        this.rewardFurniture.clear();
        this.rewardFurniture.addAll(reward);
        setWiredSourceTypes(
                settings.getFurniSourceTypes(), settings.getUserSourceTypes());
        return true;
    }

    public ContractSide payment() {
        return side(PAYMENT_OFFSET, 0);
    }

    public ContractSide reward() {
        return side(REWARD_OFFSET, 1);
    }

    public Integer resolvePaymentAmount(WiredContext context) {
        return resolveAmount(context, PAYMENT_OFFSET, 0);
    }

    public Integer resolveRewardAmount(WiredContext context) {
        return resolveAmount(context, REWARD_OFFSET, 1);
    }

    public Collection<HabboItem> resolvePaymentFurniture(WiredContext context) {
        return resolveFurniSource(
                context,
                getWiredFurniSourceTypes(),
                0,
                this.paymentFurniture,
                this.rewardFurniture);
    }

    public Collection<HabboItem> resolveRewardFurniture(WiredContext context) {
        return resolveFurniSource(
                context,
                getWiredFurniSourceTypes(),
                1,
                this.paymentFurniture,
                this.rewardFurniture);
    }

    private Integer resolveAmount(WiredContext context, int offset, int variableIndex) {
        ContractSide side = side(offset, variableIndex);
        if (!side.enabled()) {
            return 0;
        }
        if (side.amountOption() == AMOUNT_LITERAL) {
            return side.literalAmount();
        }
        Collection<HabboItem> furniture =
                resolveFurniSource(
                        context,
                        getWiredFurniSourceTypes(),
                        variableIndex + 2,
                        this.paymentFurniture,
                        this.rewardFurniture);
        Collection<RoomUnit> users =
                resolveUserSource(context, getWiredUserSourceTypes(), variableIndex);
        Integer value =
                ChestWiredSupport.resolveValue(
                        context, side.variableId(), side.variableTarget(), furniture, users);
        return value != null && value >= MIN_AMOUNT && value <= MAX_AMOUNT ? value : null;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(
                        new Data(
                                VERSION,
                                this.params,
                                this.variableIds,
                                databaseIds(this.paymentFurniture),
                                databaseIds(this.rewardFurniture),
                                getWiredFurniSourceTypes(),
                                getWiredUserSourceTypes()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            Data data =
                    WiredManager.getGson()
                            .fromJson(set == null ? null : set.getString("wired_data"), Data.class);
            if (data == null
                    || data.version != VERSION
                    || !validParams(data.params)
                    || data.variableIds == null
                    || data.variableIds.length != 2
                    || data.paymentFurnitureIds == null
                    || data.rewardFurnitureIds == null
                    || data.furniSources == null
                    || data.userSources == null
                    || data.furniSources.length != 4
                    || data.userSources.length != 2
                    || !validSources(data.furniSources, data.userSources)
                    || !validVariable(room, data.params, data.variableIds, PAYMENT_OFFSET, 0)
                    || !validVariable(room, data.params, data.variableIds, REWARD_OFFSET, 1)) {
                return;
            }
            this.params = data.params.clone();
            this.variableIds =
                    new String[] {
                        normalizedVariable(this.params, data.variableIds, PAYMENT_OFFSET, 0),
                        normalizedVariable(this.params, data.variableIds, REWARD_OFFSET, 1)
                    };
            this.paymentFurniture.addAll(
                    loadDatabaseItems(room, data.paymentFurnitureIds));
            this.rewardFurniture.addAll(
                    loadDatabaseItems(room, data.rewardFurnitureIds));
            setWiredSourceTypes(data.furniSources, data.userSources);
        } catch (RuntimeException ignored) {
            onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.params = defaults();
        this.variableIds = new String[] {NO_VARIABLE, NO_VARIABLE};
        this.paymentFurniture.clear();
        this.rewardFurniture.clear();
        setWiredSourceTypes(new int[0], new int[0]);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        serializeWiredDataV2(message, room);
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.paymentFurniture;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems2() {
        return this.rewardFurniture;
    }

    @Override
    protected int[] getWiredIntParams() {
        return this.params.clone();
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableIds.clone();
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 4;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 2;
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

    @Override
    protected boolean supportsFurniPicking() {
        return true;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    private ContractSide side(int offset, int variableIndex) {
        return new ContractSide(
                this.params[offset] == 1,
                this.params[offset + 1],
                this.params[offset + 2],
                this.params[offset + 3],
                this.params[offset + 4],
                this.variableIds[variableIndex]);
    }

    private boolean validSources(int[] furniSources, int[] userSources) {
        for (int slot = 0; slot < furniSources.length; slot++) {
            if (!contains(getAllowedFurniSourcesForSlot(slot), furniSources[slot])) {
                return false;
            }
        }
        for (int slot = 0; slot < userSources.length; slot++) {
            if (!contains(getAllowedUserSourcesForSlot(slot), userSources[slot])) {
                return false;
            }
        }
        return true;
    }

    private static boolean validParams(int[] params) {
        return params != null
                && params.length == 10
                && validBoolean(params[0])
                && validType(params[1])
                && validOption(params[2])
                && validAmount(params[3])
                && ChestWiredSupport.validTarget(params[4])
                && validBoolean(params[5])
                && validType(params[6])
                && validOption(params[7])
                && validAmount(params[8])
                && ChestWiredSupport.validTarget(params[9]);
    }

    private static boolean validVariable(
            Room room, int[] params, String[] variableIds, int offset, int variableIndex) {
        if (params[offset] == 0 || params[offset + 2] == AMOUNT_LITERAL) {
            return true;
        }
        String variableId = variableIds[variableIndex];
        return variableId != null
                && !variableId.isBlank()
                && !NO_VARIABLE.equals(variableId)
                && variableId.length() <= 128
                && WiredAddonVariablePlaceholder.definitionMatches(
                        room, variableId, params[offset + 4]);
    }

    private static String normalizedVariable(
            int[] params, String[] variableIds, int offset, int variableIndex) {
        return params[offset] == 1 && params[offset + 2] == AMOUNT_VARIABLE
                ? variableIds[variableIndex]
                : NO_VARIABLE;
    }

    private static boolean validBoolean(int value) {
        return value == 0 || value == 1;
    }

    private static boolean validType(int value) {
        return value == TYPE_COINS || value == TYPE_FURNITURE;
    }

    private static boolean validOption(int value) {
        return value == AMOUNT_LITERAL || value == AMOUNT_VARIABLE;
    }

    private static boolean validAmount(int value) {
        return value >= MIN_AMOUNT && value <= MAX_AMOUNT;
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private static int[] defaults() {
        return new int[] {0, TYPE_COINS, AMOUNT_LITERAL, 1, 0,
                0, TYPE_COINS, AMOUNT_LITERAL, 1, 0};
    }

    private static List<HabboItem> loadDatabaseItems(Room room, int[] ids) {
        if (ids == null) {
            return List.of();
        }
        List<Integer> boxed = new ArrayList<>(ids.length);
        for (int id : ids) {
            boxed.add(id);
        }
        return loadDatabaseItems(room, boxed);
    }

    private static List<HabboItem> loadDatabaseItems(Room room, List<Integer> ids) {
        List<HabboItem> result = new ArrayList<>();
        if (room == null || ids == null) {
            return result;
        }
        for (Integer id : ids) {
            HabboItem item = id == null ? null : room.getHabboItemByDatabaseId(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<Integer> databaseIds(Collection<HabboItem> items) {
        return items.stream().map(HabboItem::getId).toList();
    }

    public record ContractSide(
            boolean enabled,
            int type,
            int amountOption,
            int literalAmount,
            int variableTarget,
            String variableId) {}

    private static final class Data {
        int version;
        int[] params;
        String[] variableIds;
        List<Integer> paymentFurnitureIds;
        List<Integer> rewardFurnitureIds;
        int[] furniSources;
        int[] userSources;

        Data(
                int version,
                int[] params,
                String[] variableIds,
                List<Integer> paymentFurnitureIds,
                List<Integer> rewardFurnitureIds,
                int[] furniSources,
                int[] userSources) {
            this.version = version;
            this.params = params;
            this.variableIds = variableIds;
            this.paymentFurnitureIds = paymentFurnitureIds;
            this.rewardFurnitureIds = rewardFurnitureIds;
            this.furniSources = furniSources;
            this.userSources = userSources;
        }
    }
}
