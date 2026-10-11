package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredCrossRoomAliasRepository;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July code 7 ({@code wf_var_echo}): a configured local alias edge. */
public final class WiredVariableEcho extends InteractionWiredVariable {
    private static final int VERSION = 2;

    private String name = "";
    private String sourceId = "";

    public WiredVariableEcho(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredVariableEcho(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return WiredVariableType.ECHO;
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
        this.name = requestedName;
        this.sourceId = requestedSourceId;
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(getRoomId());
        if (room == null
                || WiredVariableAliasResolver.resolve(room, variableId()) == null
                || !isPermittedSource(room, requestedSourceId)
                || !persist()) {
            this.name = previousName;
            this.sourceId = previousSourceId;
            return false;
        }
        return true;
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        clear();

        WiredCrossRoomAliasRepository.Entry stored =
                WiredCrossRoomAliasRepository.instance().loadByItem(getId());
        if (stored != null && stored.type() == WiredVariableType.ECHO && stored.version() >= VERSION) {
            this.name = stored.name();
            this.sourceId = stored.sourceVariableId();
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
                && data.type == WiredVariableType.ECHO.code
                && WiredVariableName.isValid(normalizedName)
                && isValidVariableId(data.sourceId)
                && !data.sourceId.equals(variableId())) {
            this.name = normalizedName;
            this.sourceId = data.sourceId;
            // Legacy registries flattened the edge and lost Reference policy.
            // Retain the original item payload whenever it is available.
            return;
        }
        if (stored != null && stored.type() == WiredVariableType.ECHO) {
            this.name = stored.name();
            this.sourceId = stored.sourceVariableId();
        }
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(
                new Data(VERSION, WiredVariableType.ECHO.code,
                        this.name, this.sourceId));
    }

    @Override
    public synchronized boolean bindManager(WiredVariableManager manager) {
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(getRoomId());
        WiredVariableAliasResolver.Resolved source =
                WiredVariableAliasResolver.resolve(room, this.sourceId);
        if (manager == null || source == null) {
            return false;
        }
        try {
            return manager.registerDefinition(WiredVariableDefinition.create(
                    getRoomId(), getId(), source.definition().type(), this.name,
                    source.definition().availabilityCode(),
                    source.definition().hasValue(), false));
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
    protected synchronized String[] getWiredVariableIds() {
        return this.sourceId.isEmpty()
                ? new String[0] : new String[] {this.sourceId};
    }

    public synchronized String variableId() {
        return "room:" + getId();
    }

    public synchronized String sourceId() {
        return this.sourceId;
    }

    @Override
    public synchronized boolean syncDefinitionRegistry(WiredVariableDefinition definition) {
        WiredCrossRoomAliasRepository.Entry stored =
                WiredCrossRoomAliasRepository.instance().loadByItem(getId());
        return persist()
                || stored != null
                && stored.type() == WiredVariableType.ECHO
                && stored.sourceRoomId() != null;
    }

    private boolean persist() {
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null
                ? null : environment.getRoomManager().getRoom(getRoomId());
        WiredVariableAliasResolver.Resolved source =
                WiredVariableAliasResolver.resolve(room, this.sourceId);
        if (source == null
                || (!isVirtualSource(source.variableId()) && !WiredCrossRoomAliasRepository.instance()
                .syncSourceDefinition(source.definition()))) {
            return false;
        }
        return WiredCrossRoomAliasRepository.instance().upsert(
                new WiredCrossRoomAliasRepository.Entry(
                        getId(),
                        getRoomId(),
                        variableId(),
                        getType(),
                        source.definition().holderScope() == null ? 2 : source.definition().holderScope().code,
                        this.name,
                        source.definition().availabilityCode(),
                        source.definition().hasValue(),
                        VERSION,
                        0,
                        getRoomId(),
                        this.sourceId,
                        source.readOnly(),
                        System.currentTimeMillis()));
    }

    private static boolean isVirtualSource(String id) {
        return id != null && (id.startsWith("internal:") || id.contains(".value."));
    }

    private static boolean isPermittedSource(Room room, String id) {
        // July Echo excludes user-created category 0. Reference/Echo boxes
        // (category 2), internals (1), and smart views (3) are valid sources.
        if (isVirtualSource(id)) return true;
        if (id == null || !id.startsWith("room:")) return false;
        try {
            var item = room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5)));
            return item instanceof WiredVariableEcho || item instanceof WiredVariableReference;
        } catch (NumberFormatException exception) { return false; }
    }

    private static boolean hasValidShape(WiredSettingsV2 settings) {
        return settings != null
                && settings.getIntParams().length == 0
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

    private void clear() {
        this.name = "";
        this.sourceId = "";
    }

    private static final class Data {
        private int version;
        private int type;
        private String name;
        private String sourceId;

        private Data(int version, int type, String name, String sourceId) {
            this.version = version;
            this.type = type;
            this.name = name;
            this.sourceId = sourceId;
        }
    }
}
