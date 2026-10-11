package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * July Place Furni effect.
 *
 * <p>The primary picked furni are captured as state snapshots and are only
 * prototypes: one temporary copy is produced for each snapshot. The secondary
 * pick/source slots resolve the optional custom target and variable reference.</p>
 *
 * <p>The temporary-furniture runtime was adapted in part from Seth/iSetht's
 * GPL-3.0 Arcturus-Community-Wired Place Furni implementation:
 * https://github.com/Harmonicrain/Arcturus-Community-Wired. This version was
 * rewritten for July AIR's ten-int/two-variable contract and made deliberately
 * inert and non-persistent to prevent reward or inventory side effects.</p>
 */
public class WiredEffectPlaceFurni extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.PLACE_FURNI;

    private static final int PARAM_COUNT = 10;
    private static final int VARIABLE_COUNT = 2;
    private static final int MAX_SNAPSHOT_ITEMS = 20;
    private static final int DEFAULT_MAX_TEMP_ITEMS = 4500;
    private static final String CONFIG_MAX_TEMP_ITEMS = "hotel.wired.place_furni.max_temp_items";
    private static final String CONFIG_BANNED_NAMES = "hotel.wired.place_furni.banned_item_names";

    private static final int TARGET_FURNI = 0;
    private static final int TARGET_USER = 1;
    private static final int TARGET_GLOBAL = -10;
    private static final int TARGET_CONTEXT = -20;

    private final List<SnapshotItem> snapshots = new ArrayList<>();

    public WiredEffectPlaceFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        registerConfig();
    }

    public WiredEffectPlaceFurni(int id, int userId, Item item, String extradata,
                                 int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        registerConfig();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return false;
    }

    @Override
    protected boolean supportsFurniPickingWhenEmpty() {
        return true;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 2;
    }

    @Override
    protected int getUserSourceSlotCount() {
        return 2;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {
                FURNI_SOURCE_PICKED_1,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_TRIGGERING_ITEM,
                FURNI_SOURCE_SIGNAL
        };
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings == null || settings.getIntParams() == null
                || settings.getIntParams().length != PARAM_COUNT
                || settings.getVariableIds() == null
                || settings.getVariableIds().length != VARIABLE_COUNT
                || settings.getStringParam() == null
                || !settings.getStringParam().isEmpty()) {
            throw new WiredSaveException("Invalid Place Furni configuration");
        }

        int[] params = settings.getIntParams();
        if ((params[0] != TARGET_FURNI && params[0] != TARGET_USER)
                || (params[1] != 0 && params[1] != 1)
                || params[2] < 0 || params[2] > 2
                || params[3] < -64 || params[3] > 64
                || params[4] < -64 || params[4] > 64
                || params[5] < -8000 || params[5] > 8000
                || (params[6] != 0 && params[6] != 1)
                || (params[7] != 0 && params[7] != 1)
                || !validVariableTarget(params[9])) {
            throw new WiredSaveException("Invalid Place Furni parameters");
        }

        int[] selectedIds = settings.getFurniIds();
        if (selectedIds == null || selectedIds.length == 0
                || selectedIds.length > MAX_SNAPSHOT_ITEMS) {
            throw new WiredSaveException("Select between one and twenty prototype furni");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Room is unavailable");
        }

        List<SnapshotItem> captured = captureSnapshots(room, selectedIds);
        validateVariables(room, params, settings.getVariableIds());
        if (!super.saveData(settings, gameClient)) {
            return false;
        }
        this.furniSourceTypes = normalizeFurniSources(settings.getFurniSourceTypes());
        this.userSourceTypes = normalizeUserSources(settings.getUserSourceTypes());
        this.snapshots.clear();
        this.snapshots.addAll(captured);
        return true;
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null || this.intParams.length != PARAM_COUNT
                || this.snapshots.isEmpty()) {
            return;
        }

        Room room = context.room();
        int limit = Math.max(1, Emulator.getConfig().getInt(
                CONFIG_MAX_TEMP_ITEMS, DEFAULT_MAX_TEMP_ITEMS));
        SnapshotItem anchor = this.snapshots.stream()
                .min(Comparator.comparingInt((SnapshotItem value) -> value.y)
                        .thenComparingInt(value -> value.x))
                .orElse(null);
        TargetReference custom = needsCustomTarget() ? resolveCustomTarget(context) : null;
        if (anchor == null || (needsCustomTarget() && custom == null)) {
            return;
        }

        for (SnapshotItem snapshot : List.copyOf(this.snapshots)) {
            if (countTemporaryItems(room) >= limit) {
                return;
            }
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(snapshot.baseItemId);
            if (!canSpawn(baseItem)) {
                continue;
            }

            RoomTile destination = resolveDestination(room, snapshot, anchor, custom);
            if (destination == null) {
                continue;
            }
            double altitude = resolveAltitude(destination, snapshot, anchor, custom);
            HabboItem placed = Emulator.getGameEnvironment().getItemManager().createTemporaryItem(
                    snapshot.userId, baseItem, snapshot.extraData);
            if (placed == null) {
                continue;
            }

            boolean ignoreFurni = WiredMovementAddonRuntime.bypassFurniCollision(
                    WiredMovementAddonRuntime.physics(context));
            boolean checkForUnits = !WiredMovementAddonRuntime.bypassUnitCollision(
                    WiredMovementAddonRuntime.physics(context));
            FurnitureMovementError result = room.placeTemporaryFloorFurniAt(
                    placed, destination, snapshot.rotation, altitude, room.getOwnerName(),
                    checkForUnits, ignoreFurni);
            if (result != FurnitureMovementError.NONE) {
                continue;
            }

            applySpawnVariable(context, placed);
            context.targets().addItem(placed);
        }
    }

    private RoomTile resolveDestination(Room room, SnapshotItem snapshot,
                                        SnapshotItem anchor, TargetReference custom) {
        int baseX = this.intParams[1] == 1 ? custom.tile.x : snapshot.x;
        int baseY = this.intParams[1] == 1 ? custom.tile.y : snapshot.y;
        int relativeX = this.intParams[1] == 1 ? snapshot.x - anchor.x : 0;
        int relativeY = this.intParams[1] == 1 ? snapshot.y - anchor.y : 0;
        int x = baseX + relativeX + this.intParams[3];
        int y = baseY + relativeY + this.intParams[4];
        if (x < Short.MIN_VALUE || x > Short.MAX_VALUE
                || y < Short.MIN_VALUE || y > Short.MAX_VALUE) {
            return null;
        }
        return room.getLayout().getTile((short) x, (short) y);
    }

    private double resolveAltitude(RoomTile destination, SnapshotItem snapshot,
                                   SnapshotItem anchor, TargetReference custom) {
        double base = switch (this.intParams[2]) {
            case 1 -> snapshot.z;
            case 2 -> custom == null ? snapshot.z
                    : custom.z + (snapshot.z - anchor.z);
            default -> destination.getStackHeight();
        };
        return Math.max(0.0, base + this.intParams[5] / 100.0);
    }

    private boolean needsCustomTarget() {
        return this.intParams[1] == 1 || this.intParams[2] == 2;
    }

    private TargetReference resolveCustomTarget(WiredContext context) {
        if (this.intParams[0] == TARGET_USER) {
            return resolveUserSource(context, this.userSourceTypes, 0).stream()
                    .filter(unit -> unit != null && unit.getCurrentLocation() != null)
                    .sorted(Comparator.comparingInt(RoomUnit::getId))
                    .map(unit -> new TargetReference(unit.getCurrentLocation(),
                            unit.getCurrentLocation().z))
                    .findFirst().orElse(null);
        }
        return resolveSecondaryItems(context, 0).stream()
                .filter(item -> item != null)
                .sorted(Comparator.comparingInt(HabboItem::getId))
                .map(item -> {
                    RoomTile tile = context.room().getLayout().getTile(item.getX(), item.getY());
                    return tile == null ? null : new TargetReference(tile, item.getZ());
                })
                .filter(value -> value != null)
                .findFirst().orElse(null);
    }

    private Collection<HabboItem> resolveSecondaryItems(WiredContext context, int slot) {
        return resolveFurniSource(context, this.furniSourceTypes, slot, this.items2, this.items2);
    }

    private void applySpawnVariable(WiredContext context, HabboItem placed) {
        if (this.intParams[6] != 1 || isNone(this.variableIds[0])) {
            return;
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        WiredVariableDefinition spawn = spawnDefinition(context.room(), this.variableIds[0]);
        if (manager == null || spawn == null) {
            return;
        }

        Integer value = !spawn.hasValue() ? 0 : this.intParams[7] == 0
                ? this.intParams[8]
                : resolveReferenceValue(context, manager);
        if (value == null) {
            return;
        }
        manager.setAndDispatch(this.variableIds[0], WiredVariableHolder.furni(placed.getId()),
                value, mutation -> WiredManager.triggerVariableChanged(
                        context.room(), context.actor().orElse(null), mutation));
    }

    private Integer resolveReferenceValue(WiredContext context, WiredVariableManager manager) {
        String variableId = this.variableIds[1];
        if (isNone(variableId)) {
            return null;
        }
        if (this.intParams[9] == TARGET_CONTEXT) {
            return WiredVariableAliasOperations.readContext(context, variableId);
        }
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(variableId, this.intParams[9]);
        if (internal != null && this.intParams[9] == TARGET_USER) {
            return resolveUserSource(context, this.userSourceTypes, 1).stream()
                    .filter(unit -> unit != null && unit.getRoom() == context.room()
                            && unit.isInRoom()
                            && context.room().getRoomUnits().contains(unit))
                    .sorted(Comparator.comparingInt(unit -> unit.getId()))
                    .map(unit -> WiredInternalVariableRuntime
                            .unitValues(context.room(), unit).get(internal.name()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
        }

        List<WiredVariableHolder> holders = new ArrayList<>();
        if (this.intParams[9] == TARGET_GLOBAL) {
            holders.add(WiredVariableHolder.room());
        } else if (this.intParams[9] == TARGET_USER) {
            resolveUserSource(context, this.userSourceTypes, 1).stream()
                    .map(context.room()::getHabbo)
                    .filter(habbo -> habbo != null && habbo.getHabboInfo() != null)
                    .sorted(Comparator.comparingInt(habbo -> habbo.getHabboInfo().getId()))
                    .map(habbo -> WiredVariableHolder.user(habbo.getHabboInfo().getId()))
                    .findFirst().ifPresent(holders::add);
        } else {
            resolveSecondaryItems(context, 1).stream()
                    .filter(item -> item != null)
                    .sorted(Comparator.comparingInt(HabboItem::getId))
                    .map(item -> WiredVariableHolder.furni(item.getId()))
                    .findFirst().ifPresent(holders::add);
        }

        for (WiredVariableHolder holder : holders) {
            Integer aliased = WiredVariableAliasOperations.read(
                    context.room(), variableId, holder);
            if (aliased != null) {
                return aliased;
            }
            WiredVariableValue stored = manager.get(variableId, holder);
            if (stored == null) {
                stored = WiredGeneratedVariableRuntime.read(context.room(), variableId, holder);
            }
            if (stored != null) {
                return stored.value();
            }
            Integer internalValue = WiredInternalVariableRuntime.read(
                    context.room(), variableId, holder);
            if (internalValue != null) {
                return internalValue;
            }
        }
        return null;
    }

    private void validateVariables(Room room, int[] params, String[] ids)
            throws WiredSaveException {
        if (params[6] == 0) {
            return;
        }
        WiredVariableDefinition spawn = spawnDefinition(room, ids[0]);
        if (spawn == null) {
            throw new WiredSaveException("Choose a writable furniture variable");
        }
        // The July form disables seed controls for valueless presence flags.
        if (!spawn.hasValue()) return;
        if (params[7] == 1) {
            if (!readableReference(room, ids[1], params[9]))
                throw new WiredSaveException("Variable reference target does not match");
        }
    }

    private WiredVariableDefinition spawnDefinition(Room room, String id) {
        if (room == null || room.getRoomSpecialTypes() == null) return null;
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        WiredVariableDefinition spawn = definition(manager, id);
        // July explicitly limits spawn destinations to category 0, excluding
        // Reference/Echo views even when their terminal source is writable.
        return spawn != null && spawn.type() == WiredVariableType.FURNI
                && !WiredVariableAliasOperations.isAlias(room, id)
                && WiredVariableAliasOperations.canCreateDelete(room, id) ? spawn : null;
    }

    private boolean readableReference(Room room, String id, int target) {
        if (WiredInternalVariableRuntime.matches(id, target)) return true;
        if (target == TARGET_CONTEXT) {
            WiredVariableContext context = contextDefinition(room, id);
            if (context != null) return context.hasValue();
        }
        var source = WiredVariableAliasOperations.resolve(room, id);
        if (source == null || !source.definition().hasValue()) return false;
        return target == TARGET_CONTEXT ? source.definition().type() == WiredVariableType.CONTEXT
                : source.definition().holderScope() != null
                && source.definition().holderScope().code == targetScope(target);
    }

    private WiredVariableContext contextDefinition(Room room, String id) {
        if (room == null || id == null || !id.startsWith("room:")) {
            return null;
        }
        try {
            return room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5)))
                    instanceof WiredVariableContext value ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private WiredVariableDefinition definition(WiredVariableManager manager, String id) {
        if (manager == null || !manager.isOperational() || isNone(id)) {
            return null;
        }
        return manager.runtimeDefinition(id);
    }

    private List<SnapshotItem> captureSnapshots(Room room, int[] ids)
            throws WiredSaveException {
        List<SnapshotItem> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int visibleId : ids) {
            HabboItem item = room.getHabboItem(visibleId);
            if (item == null || !seen.add(item.getId()) || !canSnapshot(item.getBaseItem())) {
                throw new WiredSaveException("A selected prototype cannot be spawned");
            }
            result.add(new SnapshotItem(item));
        }
        return result;
    }

    private boolean canSpawn(Item item) {
        if (!canSnapshot(item)) {
            return false;
        }
        return !bannedNames().contains(item.getName().toLowerCase(Locale.ROOT));
    }

    private boolean canSnapshot(Item item) {
        return item != null && item.getType() == FurnitureType.FLOOR
                && item.getInteractionType() != null
                && item.getInteractionType().getType() != null
                && !InteractionWired.class.isAssignableFrom(item.getInteractionType().getType());
    }

    private Set<String> bannedNames() {
        String raw = Emulator.getConfig().getValue(CONFIG_BANNED_NAMES, "");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,;]"))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private int countTemporaryItems(Room room) {
        return (int) room.getFloorItems().stream().filter(item -> item.getId() < 0).count();
    }

    private int[] normalizeFurniSources(int[] requested) {
        int[] result = new int[2];
        for (int slot = 0; slot < result.length; slot++) {
            int value = requested != null && slot < requested.length
                    ? requested[slot] : FURNI_SOURCE_PICKED_1;
            result[slot] = value == FURNI_SOURCE_SELECTOR
                    || value == FURNI_SOURCE_TRIGGERING_ITEM
                    || value == FURNI_SOURCE_SIGNAL ? value : FURNI_SOURCE_PICKED_1;
        }
        return result;
    }

    private int[] normalizeUserSources(int[] requested) {
        int[] result = new int[2];
        for (int slot = 0; slot < result.length; slot++) {
            int value = requested != null && slot < requested.length
                    ? requested[slot] : USER_SOURCE_TRIGGERING_USER;
            result[slot] = value == USER_SOURCE_SELECTOR || value == USER_SOURCE_SIGNAL
                    ? value : USER_SOURCE_TRIGGERING_USER;
        }
        return result;
    }

    private static boolean validVariableTarget(int target) {
        return target == TARGET_FURNI || target == TARGET_USER
                || target == TARGET_GLOBAL || target == TARGET_CONTEXT;
    }

    private static int targetScope(int target) {
        return target == TARGET_FURNI ? WiredVariableHolder.Scope.FURNI.code
                : target == TARGET_USER ? WiredVariableHolder.Scope.USER.code
                : WiredVariableHolder.Scope.ROOM.code;
    }

    private static boolean isNone(String id) {
        return id == null || id.isBlank() || "n".equals(id);
    }

    private static void registerConfig() {
        Emulator.getConfig().register(CONFIG_MAX_TEMP_ITEMS, String.valueOf(DEFAULT_MAX_TEMP_ITEMS));
        Emulator.getConfig().register(CONFIG_BANNED_NAMES, "");
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(), this.intParams, this.stringParam,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.items2.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSourceTypes, this.userSourceTypes, this.variableIds,
                new ArrayList<>(this.snapshots)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        String raw = set.getString("wired_data");
        if (raw == null || !raw.startsWith("{")) {
            return;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(raw, JsonData.class);
            if (data == null || data.intParams == null
                    || data.intParams.length != PARAM_COUNT) {
                return;
            }
            this.setDelay(Math.max(0, data.delay));
            this.intParams = data.intParams;
            this.stringParam = data.stringParam == null ? "" : data.stringParam;
            this.furniSourceTypes = normalizeFurniSources(data.furniSourceTypes);
            this.userSourceTypes = normalizeUserSources(data.userSourceTypes);
            this.variableIds = data.variableIds != null && data.variableIds.length == VARIABLE_COUNT
                    ? data.variableIds : new String[] {"n", "n"};
            loadLiveItems(room, data.itemIds, this.items);
            loadLiveItems(room, data.itemIds2, this.items2);
            if (data.snapshots != null) {
                data.snapshots.stream().filter(SnapshotItem::valid).limit(MAX_SNAPSHOT_ITEMS)
                        .forEach(this.snapshots::add);
            }
            if (this.snapshots.isEmpty()) {
                this.items.stream().filter(item -> canSnapshot(item.getBaseItem()))
                        .map(SnapshotItem::new).forEach(this.snapshots::add);
            }
        } catch (RuntimeException ignored) {
            onPickUp();
        }
    }

    private void loadLiveItems(Room room, List<Integer> ids, List<HabboItem> destination) {
        if (ids == null) {
            return;
        }
        for (Integer id : ids) {
            HabboItem item = id == null ? null : room.getHabboItemByDatabaseId(id);
            if (item != null) {
                destination.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.snapshots.clear();
    }

    private record TargetReference(RoomTile tile, double z) {
    }

    static final class SnapshotItem {
        int itemId;
        int userId;
        int baseItemId;
        int x;
        int y;
        double z;
        int rotation;
        String extraData;

        SnapshotItem() {
        }

        SnapshotItem(HabboItem item) {
            this.itemId = item.getId();
            this.userId = item.getUserId();
            this.baseItemId = item.getBaseItem().getId();
            this.x = item.getX();
            this.y = item.getY();
            this.z = item.getZ();
            this.rotation = item.getRotation();
            this.extraData = item.getExtradata();
        }

        boolean valid() {
            return this.baseItemId > 0 && this.userId >= 0
                    && this.rotation >= 0 && this.rotation <= 7
                    && this.extraData != null;
        }
    }

    static final class JsonData {
        int delay;
        int[] intParams;
        String stringParam;
        List<Integer> itemIds;
        List<Integer> itemIds2;
        int[] furniSourceTypes;
        int[] userSourceTypes;
        String[] variableIds;
        List<SnapshotItem> snapshots;

        JsonData(int delay, int[] intParams, String stringParam,
                 List<Integer> itemIds, List<Integer> itemIds2,
                 int[] furniSourceTypes, int[] userSourceTypes,
                 String[] variableIds, List<SnapshotItem> snapshots) {
            this.delay = delay;
            this.intParams = intParams;
            this.stringParam = stringParam;
            this.itemIds = itemIds;
            this.itemIds2 = itemIds2;
            this.furniSourceTypes = furniSourceTypes;
            this.userSourceTypes = userSourceTypes;
            this.variableIds = variableIds;
            this.snapshots = snapshots;
        }
    }
}
