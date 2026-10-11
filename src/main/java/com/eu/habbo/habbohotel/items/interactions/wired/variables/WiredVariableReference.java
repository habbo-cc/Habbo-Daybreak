package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredCrossRoomAliasRepository;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** July code 4 ({@code wf_var_reference}): a configured cross-room alias. */
public final class WiredVariableReference extends InteractionWiredVariable {
    private static final int VERSION = 2;
    private static final int MAX_SHARED = 16_384;

    private String name = "";
    private String sourceId = "";
    private boolean readOnly;

    public WiredVariableReference(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredVariableReference(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return WiredVariableType.REFERENCE;
    }

    @Override
    public synchronized boolean saveData(WiredSettingsV2 settings) {
        if (!hasValidShape(settings)) {
            return false;
        }

        String requestedName = WiredVariableName.normalize(settings.getStringParam());
        String requestedSourceId = settings.getVariableIds()[0];
        if (!WiredVariableName.isValid(requestedName)
                || !isValidVariableId(requestedSourceId)
                || requestedSourceId.equals(variableId())) {
            return false;
        }

        String previousName = this.name;
        String previousSourceId = this.sourceId;
        boolean previousReadOnly = this.readOnly;
        this.name = requestedName;
        this.sourceId = requestedSourceId;
        this.readOnly = settings.getIntParams()[0] != 0;
        if (!persist()) {
            this.name = previousName;
            this.sourceId = previousSourceId;
            this.readOnly = previousReadOnly;
            return false;
        }
        return true;
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        clear();

        WiredCrossRoomAliasRepository.Entry stored =
                WiredCrossRoomAliasRepository.instance().loadByItem(getId());
        if (stored != null && stored.type() == WiredVariableType.REFERENCE && stored.version() >= VERSION) {
            this.name = stored.name();
            this.sourceId = stored.sourceVariableId();
            this.readOnly = stored.readOnly();
            return;
        }

        Data data;
        try {
            data = WiredManager.getGson().fromJson(
                    set == null ? null : set.getString("wired_data"), Data.class);
        } catch (RuntimeException exception) {
            return;
        }
        String normalizedName = data == null ? null : WiredVariableName.normalize(data.name);
        if (data != null
                && (data.version == VERSION || data.version == 1)
                && data.type == WiredVariableType.REFERENCE.code
                && WiredVariableName.isValid(normalizedName)
                && isValidVariableId(data.sourceId)
                && !data.sourceId.equals(variableId())) {
            this.name = normalizedName;
            this.sourceId = data.sourceId;
            this.readOnly = data.readOnly;
            return;
        }
        if (stored != null && stored.type() == WiredVariableType.REFERENCE) {
            // A flattened legacy edge cannot prove its original permission
            // chain if the item payload has also been lost. Keep reads only.
            this.name = stored.name();
            this.sourceId = stored.sourceVariableId();
            this.readOnly = true;
        }
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(
                new Data(VERSION, WiredVariableType.REFERENCE.code,
                        this.name, this.sourceId, this.readOnly));
    }

    @Override
    public synchronized boolean bindManager(WiredVariableManager manager) {
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(getRoomId());
        if (manager == null || room == null) {
            return false;
        }

        WiredVariableAliasResolver.Resolved source =
                WiredVariableAliasResolver.resolveShared(this.sourceId);
        WiredVariableType sourceType;
        int availability;
        boolean hasValue;
        if (source != null) {
            if (source.room() == room
                    || source.definition().availabilityCode()
                    != WiredVariableAvailability.SHARED_PERMANENT.code
                    || !isReferenceScope(source.definition().holderScope())) {
                return false;
            }
            sourceType = source.definition().type();
            availability = source.definition().availabilityCode();
            hasValue = source.definition().hasValue();
        } else {
            WiredCrossRoomAliasRepository.Entry stored =
                    WiredCrossRoomAliasRepository.instance().loadByItem(getId());
            if (stored == null
                    || stored.type() != WiredVariableType.REFERENCE
                    || stored.sourceRoomId() == null
                    || stored.sourceRoomId() == getRoomId()
                    || stored.availability()
                    != WiredVariableAvailability.SHARED_PERMANENT.code) {
                return false;
            }
            sourceType = typeForScope(stored.scope());
            if (sourceType == WiredVariableType.UNKNOWN) {
                return false;
            }
            availability = stored.availability();
            hasValue = stored.hasValue();
        }

        try {
            return manager.registerDefinition(WiredVariableDefinition.create(
                    getRoomId(), getId(), sourceType, this.name,
                    availability, hasValue, false));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public synchronized void onPickUp() {
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(getRoomId());
        if (room != null
                && room.getRoomSpecialTypes().getWiredVariableManager() != null) {
            room.getRoomSpecialTypes().getWiredVariableManager()
                    .removeDefinition(variableId());
        }
        WiredCrossRoomAliasRepository.instance().delete(getId());
        clear();
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }

    @Override
    protected synchronized String getWiredStringParam() {
        return this.name;
    }

    @Override
    protected synchronized int[] getWiredIntParams() {
        return new int[] {this.readOnly ? 1 : 0};
    }

    @Override
    protected synchronized String[] getWiredVariableIds() {
        return this.sourceId.isEmpty()
                ? new String[0] : new String[] {this.sourceId};
    }

    @Override
    protected void serializeWiredContext(ServerMessage message, Room room) {
        List<Shared> shared = new ArrayList<>();
        var environment = Emulator.getGameEnvironment();
        if (environment != null) {
            for (Room candidate : environment.getRoomManager().getActiveRooms()) {
                if (candidate == room) {
                    continue;
                }
                WiredVariableManager manager =
                        candidate.getRoomSpecialTypes().getWiredVariableManager();
                if (manager == null || !manager.isOperational()) {
                    continue;
                }
                for (WiredVariableDefinition definition :
                        manager.runtimeDefinitions(value -> value.availabilityCode()
                                == WiredVariableAvailability.SHARED_PERMANENT.code)) {
                    if (shared.size() >= MAX_SHARED) {
                        break;
                    }
                    shared.add(new Shared(candidate, definition));
                }
                if (shared.size() >= MAX_SHARED) {
                    break;
                }
            }
        }

        message.appendInt(2);
        serializeVariableCatalogContext(message, room);
        message.appendInt(WiredVariableType.REFERENCE.code);
        message.appendInt(shared.size());
        for (Shared entry : shared) {
            message.appendInt(entry.room().getId());
            message.appendString(entry.room().getName());
            WiredVariableMetadata.fromDefinition(
                    entry.room(), entry.definition()).serialize(message);
        }
    }

    public synchronized String variableId() {
        return "room:" + getId();
    }

    public synchronized String sourceId() {
        return this.sourceId;
    }

    public synchronized boolean readOnly() {
        return this.readOnly;
    }

    @Override
    public synchronized boolean syncDefinitionRegistry(WiredVariableDefinition definition) {
        WiredCrossRoomAliasRepository.Entry stored =
                WiredCrossRoomAliasRepository.instance().loadByItem(getId());
        return persist()
                || stored != null
                && stored.type() == WiredVariableType.REFERENCE
                && stored.sourceRoomId() != null;
    }

    private boolean persist() {
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(getRoomId());
        WiredVariableAliasResolver.Resolved source =
                WiredVariableAliasResolver.resolveShared(this.sourceId);
        if (room == null
                || source == null
                || source.room() == room
                || source.definition().availabilityCode()
                != WiredVariableAvailability.SHARED_PERMANENT.code
                || !isReferenceScope(source.definition().holderScope())
                || !WiredCrossRoomAliasRepository.instance()
                .syncSourceDefinition(source.definition())) {
            return false;
        }

        // Preserve the selected edge, not just its resolved terminal. Flattening
        // a Reference chain would discard upstream read-only policy on reload.
        Room selectedRoom = null;
        for (Room candidate : environment.getRoomManager().getActiveRooms()) {
            var manager = candidate.getRoomSpecialTypes().getWiredVariableManager();
            var definition = manager == null || !manager.isOperational()
                    ? null : manager.runtimeDefinition(this.sourceId);
            if (definition == null || definition.availabilityCode()
                    != WiredVariableAvailability.SHARED_PERMANENT.code) continue;
            if (selectedRoom != null) return false;
            selectedRoom = candidate;
        }
        if (selectedRoom == null || selectedRoom == room) return false;

        return WiredCrossRoomAliasRepository.instance().upsert(
                new WiredCrossRoomAliasRepository.Entry(
                        getId(),
                        getRoomId(),
                        variableId(),
                        getType(),
                        source.definition().holderScope().code,
                        this.name,
                        source.definition().availabilityCode(),
                        source.definition().hasValue(),
                        VERSION,
                        0,
                        selectedRoom.getId(),
                        this.sourceId,
                        this.readOnly,
                        System.currentTimeMillis()));
    }

    private static boolean hasValidShape(WiredSettingsV2 settings) {
        return settings != null
                && settings.getIntParams().length == 1
                && (settings.getIntParams()[0] == 0
                || settings.getIntParams()[0] == 1)
                && settings.getVariableIds().length == 1
                && settings.getStringParam() != null
                && settings.getFurniIds().length == 0
                && settings.getFurniIds2().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0;
    }

    private static boolean isValidVariableId(String value) {
        return value != null && !value.isBlank() && value.length() <= 128;
    }

    private static boolean isReferenceScope(WiredVariableHolder.Scope scope) {
        return scope == WiredVariableHolder.Scope.ROOM
                || scope == WiredVariableHolder.Scope.USER;
    }

    private static WiredVariableType typeForScope(int scope) {
        return switch (scope) {
            case 0 -> WiredVariableType.ROOM;
            case 1 -> WiredVariableType.USER;
            default -> WiredVariableType.UNKNOWN;
        };
    }

    private void clear() {
        this.name = "";
        this.sourceId = "";
        this.readOnly = false;
    }

    private record Shared(
            Room room, WiredVariableDefinition definition) {
    }

    private static final class Data {
        private int version;
        private int type;
        private String name;
        private String sourceId;
        private boolean readOnly;

        private Data(
                int version, int type, String name, String sourceId, boolean readOnly) {
            this.version = version;
            this.type = type;
            this.name = name;
            this.sourceId = sourceId;
            this.readOnly = readOnly;
        }
    }
}
