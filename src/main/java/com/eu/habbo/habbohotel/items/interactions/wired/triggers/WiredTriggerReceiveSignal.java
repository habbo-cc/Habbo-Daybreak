package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSignalAntenna;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** July AIR trigger 17. Its editor payload is completely parameterless. */
public final class WiredTriggerReceiveSignal extends InteractionWiredTrigger {
    private final Set<HabboItem> antennas = new LinkedHashSet<>();

    public WiredTriggerReceiveSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerReceiveSignal(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return WiredTriggerType.RECEIVE_SIGNAL;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (!WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || triggerItem == null
                || event == null
                || event.getType() != WiredEvent.Type.RECEIVE_SIGNAL) {
            return false;
        }

        HabboItem antenna = event.getSourceItem().orElse(null);
        if (!WiredSignalAntenna.isAntenna(antenna)) {
            return false;
        }

        Room room = event.getRoom();
        prune(room);
        int source = this.getWiredFurniSourceTypes()[0];
        Collection<HabboItem> candidates;
        switch (source) {
            case FURNI_SOURCE_TRIGGERING_ITEM:
                candidates = List.of(antenna);
                break;
            case FURNI_SOURCE_SIGNAL:
                candidates = event.getSignalPayload().items(room);
                break;
            case FURNI_SOURCE_SELECTOR:
                // Donor selector-source resolution is restricted to the current
                // signal payload on RECEIVE_SIGNAL. Until the exact July server
                // selector pre-pass is captured, retain that safe boundary and
                // use the carried candidates rather than room-wide selection.
                candidates = event.getSignalPayload().items(room);
                break;
            case FURNI_SOURCE_PICKED_1:
            default:
                candidates = this.antennas;
                break;
        }
        return candidates.stream().anyMatch(item -> item == antenna);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings == null
                || settings.getIntParams().length != 0
                || !settings.getStringParam().isEmpty()
                || settings.getFurniIds2().length != 0
                || settings.getVariableIds().length != 0
                || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes().length != 0
                || settings.getDelay() != 0) {
            return false;
        }
        return saveAntennas(settings.getFurniIds());
    }

    private boolean saveAntennas(int[] ids) {
        Room room = com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || ids.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            return false;
        }
        LinkedHashSet<HabboItem> resolved = new LinkedHashSet<>();
        for (int id : ids) {
            HabboItem item = room.getHabboItemByDatabaseId(id);
            if (!WiredSignalAntenna.isAntenna(item)
                    || item.getRoomId() != room.getId()
                    || !resolved.add(item)) {
                return false;
            }
        }
        this.antennas.clear();
        this.antennas.addAll(resolved);
        return true;
    }

    @Override
    public String getWiredData() {
        Room room = com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        prune(room);
        return WiredManager.getGson().toJson(new JsonData(
                this.antennas.stream().map(HabboItem::getId).sorted().toList()));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.antennas.clear();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null && data.antennaIds != null) {
                data.antennaIds.stream().distinct().limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                        .map(room::getHabboItemByDatabaseId)
                        .filter(WiredSignalAntenna::isAntenna)
                        .forEach(this.antennas::add);
            }
        } catch (RuntimeException ignored) {
            this.antennas.clear();
        }
    }

    @Override
    public void onPickUp() {
        this.antennas.clear();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    protected Collection<HabboItem> getSelectedItems() {
        return this.antennas;
    }

    @Override
    protected int getFurniSourceSlotCount() {
        return 1;
    }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        // The exact July server InputSourcesConf list still needs one capture;
        // these are the donor-proven functional modes expressed with AIR codes.
        return new int[] {
                FURNI_SOURCE_PICKED_1,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_SIGNAL,
                FURNI_SOURCE_TRIGGERING_ITEM
        };
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return FURNI_SOURCE_PICKED_1;
    }

    private void prune(Room room) {
        this.antennas.removeIf(item -> room == null
                || !WiredSignalAntenna.isAntenna(item)
                || item.getRoomId() != room.getId()
                || room.getHabboItemByDatabaseId(item.getId()) != item);
    }

    static final class JsonData {
        List<Integer> antennaIds;

        JsonData(List<Integer> antennaIds) {
            this.antennaIds = antennaIds;
        }
    }
}
