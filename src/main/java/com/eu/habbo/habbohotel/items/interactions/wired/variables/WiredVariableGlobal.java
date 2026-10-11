package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July variable code 2: one integer value belonging to the room. */
public class WiredVariableGlobal extends InteractionWiredVariable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableGlobal.class);

    private String variableName = "";
    private WiredVariableAvailability availability = WiredVariableAvailability.ROOM_ACTIVE;
    private int value;
    private long createdAtMs;
    private long updatedAtMs;
    private transient WiredVariableManager variableManager;

    public WiredVariableGlobal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableGlobal(int id, int userId, Item item, String extradata,
                               int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return WiredVariableType.ROOM;
    }

    public synchronized String getVariableName() {
        return this.variableName;
    }

    public synchronized WiredVariableAvailability getAvailability() {
        return this.availability;
    }

    public synchronized int getValue() {
        refreshValueFromManager();
        return this.value;
    }

    public synchronized long getCreatedAtMs() {
        refreshValueFromManager();
        return this.createdAtMs;
    }

    public synchronized long getUpdatedAtMs() {
        refreshValueFromManager();
        return this.updatedAtMs;
    }

    /** Future variable effects use this method so persistent changes remain atomic. */
    public synchronized boolean setValue(int newValue) {
        if (this.variableManager == null || this.variableName.isEmpty()) {
            return false;
        }
        WiredVariableManager.MutationResult result = this.variableManager.set(
                variableId(), WiredVariableHolder.room(), newValue);
        if (result != WiredVariableManager.MutationResult.CREATED
                && result != WiredVariableManager.MutationResult.CHANGED
                && result != WiredVariableManager.MutationResult.UNCHANGED) {
            return false;
        }
        refreshValueFromManager();
        return this.value == newValue;
    }

    @Override
    public synchronized boolean saveData(WiredSettingsV2 settings) {
        if (!isStrictGlobalPayload(settings)) {
            return false;
        }

        String rawName = settings.getStringParam();
        if (rawName.length() > WiredVariableName.MAX_LENGTH) {
            return false;
        }
        String normalizedName = WiredVariableName.normalize(rawName);
        if (!WiredVariableName.isValid(normalizedName)) {
            return false;
        }

        WiredVariableAvailability requested = WiredVariableAvailability.fromCode(settings.getIntParams()[0]);
        if (requested == null) {
            return false;
        }

        if (this.variableManager == null) {
            return false;
        }
        WiredVariableDefinition proposed;
        try {
            proposed = WiredVariableDefinition.create(this.getRoomId(), this.getId(), getType(),
                    normalizedName, requested, false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!this.variableManager.registerDefinition(proposed)) {
            return false;
        }

        this.variableName = normalizedName;
        this.availability = requested;
        refreshValueFromManager();
        return true;
    }

    private static boolean isStrictGlobalPayload(WiredSettingsV2 settings) {
        return settings != null
                && settings.getIntParams().length == 1
                && settings.getStringParam() != null
                && settings.getFurniIds().length == 0
                && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0;
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(new DefinitionData(this.variableName, this.availability.code));
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        loadDefinition(set == null ? null : set.getString("wired_data"));
        this.variableManager = room == null || room.getRoomSpecialTypes() == null
                ? null : room.getRoomSpecialTypes().getWiredVariableManager();
        if (this.variableManager != null && !this.variableName.isEmpty()) {
            bindManager(this.variableManager);
        }
    }

    /** Separated from JDBC row access so corrupt/legacy definitions are unit-testable. */
    synchronized void loadDefinition(String wiredData) {
        resetState();
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        DefinitionData definition;
        try {
            definition = WiredManager.getGson().fromJson(wiredData, DefinitionData.class);
        } catch (RuntimeException exception) {
            LOGGER.warn("Ignoring corrupt Global Variable definition for item {}", this.getId(), exception);
            return;
        }

        String normalizedName = definition == null ? "" : WiredVariableName.normalize(definition.name);
        WiredVariableAvailability loadedAvailability = definition == null
                ? null : WiredVariableAvailability.fromCode(definition.availabilityCode());
        if (!WiredVariableName.isValid(normalizedName) || loadedAvailability == null) {
            return;
        }

        this.variableName = normalizedName;
        this.availability = loadedAvailability;
        this.value = 0;
        this.createdAtMs = 0L;
        this.updatedAtMs = 0L;
    }

    private void resetState() {
        this.variableName = "";
        this.availability = WiredVariableAvailability.ROOM_ACTIVE;
        this.value = 0;
        this.createdAtMs = 0L;
        this.updatedAtMs = 0L;
    }

    @Override
    public synchronized void onPickUp() {
        if (this.variableManager != null) {
            this.variableManager.removeDefinition(variableId());
        }
        resetState();
        this.variableManager = null;
    }

    public synchronized boolean bindManager(WiredVariableManager manager) {
        this.variableManager = manager;
        if (manager == null || this.variableName.isEmpty()) {
            return false;
        }
        WiredVariableDefinition definition;
        try {
            definition = WiredVariableDefinition.create(this.getRoomId(), this.getId(), getType(),
                    this.variableName, this.availability, false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!manager.registerDefinition(definition)) {
            return false;
        }
        refreshValueFromManager();
        return true;
    }

    private void refreshValueFromManager() {
        if (this.variableManager == null) {
            return;
        }
        com.eu.habbo.habbohotel.wired.variables.WiredVariableValue managed =
                this.variableManager.get(variableId(), WiredVariableHolder.room());
        if (managed == null) {
            this.value = 0;
            this.createdAtMs = 0L;
            this.updatedAtMs = 0L;
            return;
        }
        this.value = managed.value();
        this.createdAtMs = managed.createdAtMs();
        this.updatedAtMs = managed.updatedAtMs();
    }

    private String variableId() {
        return "room:" + this.getId();
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }

    @Override
    protected synchronized String getWiredStringParam() {
        return this.variableName;
    }

    @Override
    protected synchronized int[] getWiredIntParams() {
        return new int[] {this.availability.code};
    }

    @Override
    protected synchronized void serializeWiredContext(ServerMessage message, Room room) {
        refreshValueFromManager();
        message.appendInt(2); // catalog plus global value
        serializeVariableCatalogContext(message, room);
        message.appendInt(3); // GLOBAL_VARIABLE_INFO_AND_VALUE
        WiredVariableMetadata.roomGlobal(room, this.getId(), this.variableName, this.availability.code)
                .serialize(message);
        message.appendInt(this.value);
    }

    static final class DefinitionData {
        String name;
        Integer persistence;
        Integer availability;

        DefinitionData(String name, int availability) {
            this.name = name;
            this.persistence = availability;
        }

        int availabilityCode() {
            // `persistence` is donor-compatible canonical storage. Accept the
            // early port's `availability` key so already-saved test/dev items
            // remain loadable without a data migration.
            return this.persistence != null
                    ? this.persistence
                    : (this.availability != null ? this.availability : Integer.MIN_VALUE);
        }
    }
}
