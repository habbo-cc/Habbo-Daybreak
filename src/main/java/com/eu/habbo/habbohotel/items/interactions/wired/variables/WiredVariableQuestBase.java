package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared exact AIR save shape for July Quest and Quest Chain variables. */
public abstract class WiredVariableQuestBase extends InteractionWiredVariable {
    private static final int STORAGE_VERSION = 1;
    private static final int MAX_CODE_LENGTH = 500;

    private String variableName = "";
    private String configuredCode = "";
    private transient WiredVariableManager manager;

    protected WiredVariableQuestBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredVariableQuestBase(int id, int userId, Item item, String extra,
                                     int limitedStack, int limitedSells) {
        super(id, userId, item, extra, limitedStack, limitedSells);
    }

    @Override
    public synchronized boolean saveData(WiredSettingsV2 settings) {
        Parsed parsed = parse(settings);
        if (parsed == null || this.manager == null) {
            return false;
        }
        WiredVariableDefinition definition;
        try {
            definition = WiredVariableDefinition.create(getRoomId(), getId(), getType(),
                    parsed.variableName, 0, true, false);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (!this.manager.registerDefinition(definition)) {
            return false;
        }
        this.variableName = parsed.variableName;
        this.configuredCode = parsed.configuredCode;
        return true;
    }

    @Override
    public synchronized boolean bindManager(WiredVariableManager manager) {
        this.manager = manager;
        if (manager == null || this.variableName.isEmpty() || this.configuredCode.isEmpty()) {
            return false;
        }
        try {
            return manager.registerDefinition(WiredVariableDefinition.create(
                    getRoomId(), getId(), getType(), this.variableName, 0, true, false));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.manager = room == null ? null
                : room.getRoomSpecialTypes().getWiredVariableManager();
        this.variableName = "";
        this.configuredCode = "";
        Data data;
        try {
            data = WiredManager.getGson().fromJson(
                    set == null ? null : set.getString("wired_data"), Data.class);
        } catch (RuntimeException ignored) {
            return;
        }
        if (data == null || data.version != STORAGE_VERSION
                || data.type != getType().code) {
            return;
        }
        String name = WiredVariableName.normalize(data.variableName);
        String code = normalizeCode(data.configuredCode);
        if (!WiredVariableName.isValid(name) || code == null) {
            return;
        }
        this.variableName = name;
        this.configuredCode = code;
        bindManager(this.manager);
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(new Data(STORAGE_VERSION,
                getType().code, this.variableName, this.configuredCode));
    }

    @Override
    public synchronized void onPickUp() {
        if (this.manager != null) {
            this.manager.removeDefinition(variableId());
        }
        this.manager = null;
        this.variableName = "";
        this.configuredCode = "";
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }

    @Override
    protected synchronized String getWiredStringParam() {
        return this.variableName + "\t" + this.configuredCode;
    }

    public synchronized String configuredCode() {
        return this.configuredCode;
    }

    public String variableId() {
        return "room:" + getId();
    }

    private static Parsed parse(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 0
                || settings.getVariableIds().length != 0
                || settings.getFurniIds().length != 0
                || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 0
                || settings.getStringParam() == null) {
            return null;
        }
        String[] fields = settings.getStringParam().split("\t", -1);
        if (fields.length != 2) {
            return null;
        }
        String name = WiredVariableName.normalize(fields[0]);
        String code = normalizeCode(fields[1]);
        return WiredVariableName.isValid(name) && code != null
                ? new Parsed(name, code) : null;
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String code = value.trim();
        return code.isEmpty() || code.length() > MAX_CODE_LENGTH || code.indexOf('\t') >= 0
                ? null : code;
    }

    private record Parsed(String variableName, String configuredCode) {
    }

    private static final class Data {
        int version;
        int type;
        String variableName;
        String configuredCode;

        Data(int version, int type, String variableName, String configuredCode) {
            this.version = version;
            this.type = type;
            this.variableName = variableName;
            this.configuredCode = configuredCode;
        }
    }
}
