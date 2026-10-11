package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableRoomView;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Shared concrete definition furniture for July Furni and User variables. */
abstract class WiredVariableScoped extends InteractionWiredVariable {
    private static final int STORAGE_VERSION = 1;
    private String variableName = "";
    private WiredVariableAvailability availability;
    private boolean hasValue;
    private transient WiredVariableManager manager;

    WiredVariableScoped(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    WiredVariableScoped(int id, int userId, Item item, String extra, int stack, int sells) {
        super(id, userId, item, extra, stack, sells);
    }

    protected abstract boolean isFurni();
    protected abstract int contextBlockType();
    protected abstract int[] encodeParams(boolean hasValue, int availability);
    protected abstract ParsedParams decodeParams(int[] params);

    @Override public synchronized boolean saveData(WiredSettingsV2 settings) {
        if (!strict(settings)) return false;
        String name = WiredVariableName.normalize(settings.getStringParam());
        ParsedParams parsed = decodeParams(settings.getIntParams());
        if (!WiredVariableName.isValid(name) || parsed == null) return false;
        WiredVariableAvailability requested = WiredVariableAvailability.fromCode(parsed.availability());
        if (requested == null || this.manager == null) return false;
        WiredVariableDefinition definition;
        try {
            definition = WiredVariableDefinition.create(getRoomId(), getId(), getType(), name,
                    requested.code, parsed.hasValue(), false);
        } catch (IllegalArgumentException ignored) { return false; }
        if (!this.manager.registerDefinition(definition)) return false;
        this.variableName = name;
        this.availability = requested;
        this.hasValue = parsed.hasValue();
        return true;
    }

    @Override public synchronized boolean bindManager(WiredVariableManager manager) {
        this.manager = manager;
        if (manager == null || this.variableName.isEmpty() || this.availability == null) return false;
        try {
            return manager.registerDefinition(WiredVariableDefinition.create(getRoomId(), getId(), getType(),
                    this.variableName, this.availability.code, this.hasValue, false));
        } catch (IllegalArgumentException ignored) { return false; }
    }

    @Override public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.manager = room == null ? null : room.getRoomSpecialTypes().getWiredVariableManager();
        this.variableName = ""; this.availability = null; this.hasValue = false;
        DefinitionData data;
        try { data = WiredManager.getGson().fromJson(set == null ? null : set.getString("wired_data"), DefinitionData.class); }
        catch (RuntimeException ignored) { return; }
        if (data == null || data.version != STORAGE_VERSION || data.type != getType().code) return;
        String name = WiredVariableName.normalize(data.name);
        WiredVariableAvailability a = WiredVariableAvailability.fromCode(data.availability);
        if (!WiredVariableName.isValid(name) || a == null) return;
        this.variableName = name; this.availability = a; this.hasValue = data.hasValue;
        bindManager(this.manager);
    }

    @Override public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(new DefinitionData(STORAGE_VERSION, getType().code,
                this.variableName, this.availability == null ? 0 : this.availability.code, this.hasValue));
    }

    @Override public synchronized void onPickUp() {
        if (this.manager != null) this.manager.removeDefinition(variableId());
        this.manager = null; this.variableName = ""; this.availability = null; this.hasValue = false;
    }

    @Override protected int getMaxFurniSelection() { return 0; }
    @Override protected synchronized String getWiredStringParam() { return this.variableName; }
    @Override protected synchronized int[] getWiredIntParams() {
        return encodeParams(this.hasValue, this.availability == null ? 0 : this.availability.code);
    }
    @Override protected synchronized void serializeWiredContext(ServerMessage message, Room room) {
        WiredVariableManager.VariableSnapshot variable = this.manager == null ? null
                : this.manager.variableSnapshot(variableId());
        message.appendInt(variable == null ? 1 : 2);
        serializeVariableCatalogContext(message, room);
        if (variable == null) return;
        message.appendInt(contextBlockType());
        WiredVariableMetadata.fromDefinition(room, variable.definition()).serialize(message);
        List<WiredVariableRoomView.VisibleValue> holders = isFurni()
                ? WiredVariableRoomView.furniValues(room, variable)
                : WiredVariableRoomView.userValues(room, variable);
        message.appendInt(holders.size());
        for (WiredVariableRoomView.VisibleValue holder : holders) {
            message.appendInt(holder.roomVisibleId()); message.appendInt(holder.value());
        }
    }
    String variableId() { return "room:" + getId(); }
    private static boolean strict(WiredSettingsV2 s) {
        return s != null && s.getStringParam() != null && s.getFurniIds().length == 0
                && s.getFurniIds2().length == 0 && s.getVariableIds().length == 0
                && s.getFurniSourceTypes().length == 0 && s.getUserSourceTypes().length == 0;
    }
    protected record ParsedParams(boolean hasValue, int availability) { }
    private static final class DefinitionData {
        int version; int type; String name; int availability; boolean hasValue;
        DefinitionData(int v, int t, String n, int a, boolean h) { version=v; type=t; name=n; availability=a; hasValue=h; }
    }
}
