package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * July AIR selector 19. The AIR custom payload is exactly
 * {@code [aggregation, randomAmount]}; Nitro's code 20/six-int layout is not
 * accepted here.
 */
public final class WiredSelectorRemote extends InteractionWiredSelector {
    static final int MAX_REFERENCE_STACKS = 64;
    static final int MAX_RANDOM_STACKS = 64;

    private int aggregation = WiredRemoteSelectorSupport.UNION;
    private int randomAmount;
    private final List<Integer> referenceItemIds = new ArrayList<>();

    public WiredSelectorRemote(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorRemote(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return WiredSelectorType.REMOTE_SELECTOR;
    }

    /** Fail-closed editor gate specific to selector code 19. */
    public boolean isAvailableToClient(GameClient client, Room room) {
        return client != null
                && room != null
                && WiredRemoteSelectorSupport.isReady(
                        WiredCapabilityService.isRoomCapabilityReady(
                                WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR),
                        client.getWiredCapabilityState().supportsRoom(
                                WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR, room.getId()));
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (isAvailableToClient(client, room)) {
            super.onClick(client, room, objects);
        }
    }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (!WiredCapabilityService.isRoomCapabilityReady(
                WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR)
                || settings == null) {
            return false;
        }

        int[] intParams = settings.getIntParams();
        if (!WiredRemoteSelectorSupport.hasExactAirParameters(intParams, MAX_RANDOM_STACKS)
                || !hasSupportedCommonFields(settings)) {
            return false;
        }

        Room room = currentRoom();
        List<Integer> validatedReferences = validateReferences(room, settings.getFurniIds());
        if (validatedReferences == null) {
            return false;
        }

        this.aggregation = intParams[0];
        this.randomAmount = intParams[1];
        this.referenceItemIds.clear();
        this.referenceItemIds.addAll(validatedReferences);
        this.setSelectorFlags(settings.isFilter(), settings.isInvert());
        return true;
    }

    @Override
    public WiredTargets resolve(Room room, WiredContext context) {
        if (!WiredCapabilityService.isRoomCapabilityReady(
                WiredCapabilityService.CAPABILITY_REMOTE_SELECTOR)) {
            return new WiredTargets();
        }
        return WiredRemoteSelectorResolver.resolve(
                this,
                room,
                context,
                resolveFurniSource(
                        context,
                        this.getWiredFurniSourceTypes(),
                        0,
                        this.getSelectedItems(),
                        List.of()),
                this.aggregation,
                this.randomAmount);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.aggregation,
                this.randomAmount,
                this.isFilter(),
                this.isInvert(),
                List.copyOf(this.referenceItemIds)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        resetData();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null) {
                return;
            }

            this.aggregation = data.aggregation == WiredRemoteSelectorSupport.INTERSECTION
                    ? WiredRemoteSelectorSupport.INTERSECTION
                    : WiredRemoteSelectorSupport.UNION;
            this.randomAmount = Math.max(0, Math.min(MAX_RANDOM_STACKS, data.randomAmount));
            this.setSelectorFlags(data.filter, data.invert);

            if (data.referenceItemIds != null) {
                data.referenceItemIds.stream()
                        .filter(id -> id != null && id > 0 && id != this.getId())
                        .distinct()
                        .limit(MAX_REFERENCE_STACKS)
                        .forEach(this.referenceItemIds::add);
            }
        } catch (RuntimeException ignored) {
            resetData();
        }
    }

    @Override
    public void onPickUp() {
        resetData();
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        Room room = currentRoom();
        if (room == null) {
            return List.of();
        }

        Map<Long, HabboItem> currentByStack = new LinkedHashMap<>();
        this.referenceItemIds.stream().sorted().forEach(referenceItemId -> {
            HabboItem item = room.getHabboItemByDatabaseId(referenceItemId);
            if (isLegalCurrentReference(room, item)
                    && item.getId() != this.getId()
                    && (item.getX() != this.getX() || item.getY() != this.getY())) {
                currentByStack.putIfAbsent(
                        WiredRemoteSelectorSupport.stackKey(item.getX(), item.getY()), item);
            }
        });
        return List.copyOf(currentByStack.values());
    }

    @Override
    protected boolean sanitizeSelectedItems(Room room) {
        if (room == null) {
            return false;
        }
        return this.referenceItemIds.removeIf(referenceItemId -> {
            HabboItem item = room.getHabboItemByDatabaseId(referenceItemId);
            return !isLegalCurrentReference(room, item)
                    || item.getId() == this.getId()
                    || (item.getX() == this.getX() && item.getY() == this.getY());
        });
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {this.aggregation, this.randomAmount};
    }

    @Override
    protected boolean supportsFurniPicking() {
        return true;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        // Donor-proven runtime mapping expressed through AIR's generic source
        // array. The exact official allow-list still needs one production capture.
        return new int[] {FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL};
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_PICKED_1;
    }

    List<Integer> referenceItemIdsForTest() {
        return List.copyOf(this.referenceItemIds);
    }

    static boolean hasSupportedCommonFields(WiredSettingsV2 settings) {
        return settings != null
                && settings.getStringParam().isEmpty()
                && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 1
                && (settings.getFurniSourceTypes()[0] == FURNI_SOURCE_PICKED_1
                || settings.getFurniSourceTypes()[0] == FURNI_SOURCE_SELECTOR
                || settings.getFurniSourceTypes()[0] == FURNI_SOURCE_SIGNAL)
                && settings.getUserSourceTypes().length == 0;
    }

    private List<Integer> validateReferences(Room room, int[] ids) {
        if (room == null || ids == null) {
            return null;
        }

        int configuredLimit = Math.max(0, WiredManager.MAXIMUM_FURNI_SELECTION);
        int limit = Math.min(MAX_REFERENCE_STACKS, configuredLimit);
        if (ids.length > limit) {
            return null;
        }

        List<Integer> orderedIds = java.util.Arrays.stream(ids).distinct().sorted().boxed().toList();
        Map<Long, Integer> byStack = new LinkedHashMap<>();
        for (Integer id : orderedIds) {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (!isLegalCurrentReference(room, item)
                    || item.getId() == this.getId()
                    || (item.getX() == this.getX() && item.getY() == this.getY())) {
                return null;
            }
            byStack.putIfAbsent(WiredRemoteSelectorSupport.stackKey(item.getX(), item.getY()), item.getId());
        }
        return List.copyOf(byStack.values());
    }

    private boolean isLegalCurrentReference(Room room, HabboItem item) {
        // Donor runtime treats a current floor furni as a pointer to the Wired
        // stack tile beneath/around it. Exact official selectable types need a
        // production capture; wall/cross-room/stale references are rejected.
        return item != null
                && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item
                && room.getFloorItems().contains(item);
    }

    private Room currentRoom() {
        if (this.getRoomId() <= 0 || Emulator.getGameEnvironment() == null
                || Emulator.getGameEnvironment().getRoomManager() == null) {
            return null;
        }
        return Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
    }

    private void resetData() {
        this.aggregation = WiredRemoteSelectorSupport.UNION;
        this.randomAmount = 0;
        this.referenceItemIds.clear();
        this.setSelectorFlags(false, false);
    }

    private static final class JsonData {
        int aggregation;
        int randomAmount;
        boolean filter;
        boolean invert;
        List<Integer> referenceItemIds;

        JsonData(
                int aggregation,
                int randomAmount,
                boolean filter,
                boolean invert,
                List<Integer> referenceItemIds) {
            this.aggregation = aggregation;
            this.randomAmount = randomAmount;
            this.filter = filter;
            this.invert = invert;
            this.referenceItemIds = referenceItemIds;
        }
    }
}
