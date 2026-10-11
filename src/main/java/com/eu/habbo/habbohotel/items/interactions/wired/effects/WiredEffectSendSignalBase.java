package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSafetyBudget;
import com.eu.habbo.habbohotel.wired.core.WiredSignalAntenna;
import com.eu.habbo.habbohotel.wired.core.WiredSignalPayload;
import com.eu.habbo.habbohotel.wired.api.IWiredSignalEffect;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AIR-compatible implementation shared by actions 30 and 37.
 *
 * <p>Signal routing behavior was adapted in part from Seth/iSetht's GPL-3.0
 * Arcturus-Community-Wired implementation:
 * https://github.com/Harmonicrain/Arcturus-Community-Wired. The saved payload
 * and source arrays were replaced with the July AIR contract.</p>
 */
abstract class WiredEffectSendSignalBase extends InteractionWiredEffect implements IWiredSignalEffect {
    private static final int MAX_DELAY = 20;

    private final Set<HabboItem> antennas = new LinkedHashSet<>();
    private final Set<HabboItem> pickedPayloadItems = new LinkedHashSet<>();
    private boolean splitFurni;
    private boolean splitUsers;

    protected WiredEffectSendSignalBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredEffectSendSignalBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public final void execute(WiredContext context) {
        if (context == null || !WiredFeatureCapabilityGuard.isRuntimeReady(this)) {
            return;
        }

        Room room = context.room();
        pruneSelections(room);
        Collection<HabboItem> antennaSource = sourceUsesFurniSelectors(context, 0)
                ? resolveFurniSource(
                        context,
                        getWiredFurniSourceTypes(),
                        0,
                        this.antennas,
                        this.pickedPayloadItems)
                : List.of();
        List<HabboItem> resolvedAntennas = currentItems(room, antennaSource);
        resolvedAntennas.removeIf(item -> !WiredSignalAntenna.isAntenna(item));
        if (resolvedAntennas.isEmpty()) {
            return;
        }

        Collection<HabboItem> itemSource = sourceUsesFurniSelectors(context, 1)
                ? resolveFurniSource(context, getWiredFurniSourceTypes(), 1,
                this.antennas, this.pickedPayloadItems)
                : List.of();
        Collection<RoomUnit> userSource = sourceUsesUserSelectors(context, 0)
                ? resolveUserSource(context, getWiredUserSourceTypes(), 0)
                : List.of();

        WiredSignalPayload completePayload = WiredSignalPayload.capture(room, itemSource, userSource);
        List<List<HabboItem>> itemPayloads = splitItems(
                completePayload.items(room), this.splitFurni);
        List<List<RoomUnit>> userPayloads = splitUsers(
                completePayload.users(room), this.splitUsers);
        long dispatchCount = (long) resolvedAntennas.size()
                * itemPayloads.size()
                * userPayloads.size();
        context.budget().consumeFanOut(dispatchCount);

        long pathId = ((long) room.getId() << 32) ^ (this.getId() & 0xffffffffL);
        for (HabboItem antenna : resolvedAntennas) {
            for (List<HabboItem> items : itemPayloads) {
                for (List<RoomUnit> users : userPayloads) {
                    context.budget().consumeTargets(items.size() + users.size());
                    WiredSignalPayload payload = WiredSignalPayload.capture(room, items, users);
                    try (WiredSafetyBudget.PathLease ignored = context.state().enter(
                            WiredSafetyBudget.PathKind.SIGNAL, pathId)) {
                        WiredManager.triggerReceiveSignal(
                                room, context.actor().orElse(null), antenna, payload);
                    }
                }
            }
        }
    }

    private boolean sourceUsesFurniSelectors(WiredContext context, int slot) {
        int[] sources = getWiredFurniSourceTypes();
        if (slot >= sources.length || sources[slot] != FURNI_SOURCE_SELECTOR) {
            return true;
        }
        return context.hasStack() && context.stack().selectors().stream()
                .map(selector -> (InteractionWiredSelector) selector)
                .anyMatch(selector -> selector.getType().isFurni);
    }

    private boolean sourceUsesUserSelectors(WiredContext context, int slot) {
        int[] sources = getWiredUserSourceTypes();
        if (slot >= sources.length || sources[slot] != USER_SOURCE_SELECTOR) {
            return true;
        }
        return context.hasStack() && context.stack().selectors().stream()
                .map(selector -> (InteractionWiredSelector) selector)
                .anyMatch(selector -> selector.getType().isUser);
    }

    @Override
    public final boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || !WiredFeatureCapabilityGuard.isEditorReady(gameClient, room, this)) {
            return false;
        }
        validateSettingsShape(settings);

        int delay = settings.getDelay();
        if (delay < 0 || delay > MAX_DELAY) {
            throw new WiredSaveException("Delay must be between 0 and 20");
        }

        List<HabboItem> newAntennas = resolveSavedItems(room, settings.getFurniIds(), true);
        List<HabboItem> newPayloadItems = resolveSavedItems(room, settings.getFurniIds2(), false);
        this.antennas.clear();
        this.antennas.addAll(newAntennas);
        this.pickedPayloadItems.clear();
        this.pickedPayloadItems.addAll(newPayloadItems);
        this.splitFurni = settings.getIntParams()[0] != 0;
        this.splitUsers = settings.getIntParams()[1] != 0;
        this.setDelay(delay);
        return true;
    }

    static void validateSettingsShape(WiredSettings settings) throws WiredSaveException {
        if (settings == null
                || settings.getIntParams().length != 2
                || !settings.getStringParam().isEmpty()
                || settings.getVariableIds().length != 0
                || settings.getFurniSourceTypes().length != 2
                || settings.getUserSourceTypes().length != 1) {
            throw new WiredSaveException("Invalid Send Signal data");
        }
        for (int value : settings.getIntParams()) {
            if (value != 0 && value != 1) {
                throw new WiredSaveException("Signal split options must be boolean");
            }
        }
    }

    private static List<HabboItem> resolveSavedItems(Room room, int[] ids, boolean antennasOnly)
            throws WiredSaveException {
        if (ids.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Too many furni selected");
        }
        LinkedHashSet<HabboItem> items = new LinkedHashSet<>();
        for (int id : ids) {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (item == null || item.getRoomId() != room.getId()) {
                throw new WiredSaveException("Selected furni is not in this room");
            }
            if (antennasOnly && !WiredSignalAntenna.isAntenna(item)) {
                throw new WiredSaveException("Only signal antennas can be selected as destinations");
            }
            if (!items.add(item)) {
                throw new WiredSaveException("Duplicate furni selection");
            }
        }
        return new ArrayList<>(items);
    }

    @Override
    public final String getWiredData() {
        Room room = this.getRoom();
        pruneSelections(room);
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.splitFurni,
                this.splitUsers,
                ids(this.antennas),
                ids(this.pickedPayloadItems)));
    }

    @Override
    public final void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null || data.delay < 0 || data.delay > MAX_DELAY) {
                return;
            }
            this.setDelay(data.delay);
            this.splitFurni = data.splitFurni;
            this.splitUsers = data.splitUsers;
            loadIds(room, data.antennaIds, this.antennas, true);
            loadIds(room, data.payloadItemIds, this.pickedPayloadItems, false);
        } catch (RuntimeException ignored) {
            reset();
        }
    }

    @Override
    public final void onPickUp() {
        reset();
    }

    private void reset() {
        this.antennas.clear();
        this.pickedPayloadItems.clear();
        this.splitFurni = false;
        this.splitUsers = false;
        this.setDelay(0);
    }

    private void pruneSelections(Room room) {
        this.antennas.removeIf(item -> !isCurrent(room, item) || !WiredSignalAntenna.isAntenna(item));
        this.pickedPayloadItems.removeIf(item -> !isCurrent(room, item));
    }

    private static boolean isCurrent(Room room, HabboItem item) {
        return room != null
                && item != null
                && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item;
    }

    private static List<HabboItem> currentItems(Room room, Collection<HabboItem> source) {
        return source.stream()
                .filter(item -> isCurrent(room, item))
                .distinct()
                .sorted(Comparator.comparingInt(HabboItem::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static void loadIds(
            Room room, List<Integer> ids, Set<HabboItem> destination, boolean antennasOnly) {
        if (room == null || ids == null) {
            return;
        }
        ids.stream().distinct().limit(WiredManager.MAXIMUM_FURNI_SELECTION).forEach(id -> {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (isCurrent(room, item) && (!antennasOnly || WiredSignalAntenna.isAntenna(item))) {
                destination.add(item);
            }
        });
    }

    private static List<Integer> ids(Collection<HabboItem> items) {
        return items.stream().map(HabboItem::getId).sorted().toList();
    }

    static <T> List<List<T>> splitPayload(List<T> values, boolean split) {
        List<T> immutable = values == null ? List.of() : List.copyOf(values);
        if (!split || immutable.isEmpty()) {
            return List.of(immutable);
        }
        return immutable.stream().map(List::of).toList();
    }

    static List<List<HabboItem>> splitItems(List<HabboItem> values, boolean split) {
        return splitPayload(values, split);
    }

    static List<List<RoomUnit>> splitUsers(List<RoomUnit> values, boolean split) {
        return splitPayload(values, split);
    }

    @Override
    public final void serializeWiredData(ServerMessage message, Room room) {
        pruneSelections(room);
        this.serializeWiredDataV2(message, room);
    }

    @Override
    protected final Collection<HabboItem> getSelectedItems() {
        return this.antennas;
    }

    @Override
    protected final Collection<HabboItem> getSelectedItems2() {
        return this.pickedPayloadItems;
    }

    @Override
    protected final int[] getWiredIntParams() {
        return new int[] {this.splitFurni ? 1 : 0, this.splitUsers ? 1 : 0};
    }

    @Override
    protected final int getFurniSourceSlotCount() {
        return 2;
    }

    @Override
    protected final int getUserSourceSlotCount() {
        return 1;
    }

    @Override
    protected final int[] getAllowedFurniSourcesForSlot(int slot) {
        // The precise July server InputSourcesConf option ordering needs one
        // capture. These modes preserve donor-proven runtime semantics while
        // AIR remains authoritative for the generic source-array wire fields.
        return new int[] {
                FURNI_SOURCE_PICKED_1,
                FURNI_SOURCE_PICKED_2,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_SIGNAL,
                FURNI_SOURCE_TRIGGERING_ITEM
        };
    }

    @Override
    protected final int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {
                USER_SOURCE_SELECTOR,
                USER_SOURCE_SIGNAL,
                USER_SOURCE_TRIGGERING_USER
        };
    }

    @Override
    protected final int getDefaultFurniSourceForSlot(int slot) {
        return slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_SELECTOR;
    }

    @Override
    protected final int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_SELECTOR;
    }

    static final class JsonData {
        int delay;
        boolean splitFurni;
        boolean splitUsers;
        List<Integer> antennaIds;
        List<Integer> payloadItemIds;

        JsonData(
                int delay,
                boolean splitFurni,
                boolean splitUsers,
                List<Integer> antennaIds,
                List<Integer> payloadItemIds) {
            this.delay = delay;
            this.splitFurni = splitFurni;
            this.splitUsers = splitUsers;
            this.antennaIds = antennaIds;
            this.payloadItemIds = payloadItemIds;
        }
    }
}
