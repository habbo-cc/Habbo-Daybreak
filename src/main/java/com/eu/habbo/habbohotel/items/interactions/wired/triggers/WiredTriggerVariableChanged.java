package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.messages.ServerMessage;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/** July AIR Core Variables trigger 22 ({@code wf_trg_var_changed}). */
public final class WiredTriggerVariableChanged extends InteractionWiredTrigger {
    static final int MASK_INCREASED = 1;
    static final int MASK_DECREASED = 2;
    static final int MASK_UNCHANGED = 4;

    private static final String PERSISTENCE_VERSION = "v1";
    private String variableId = "";
    private boolean created;
    private boolean valueChanged;
    private boolean deleted;
    private int valueChangeMask;

    public WiredTriggerVariableChanged(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerVariableChanged(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return WiredTriggerType.VARIABLE_CHANGED;
    }

    public String selectedVariableId() { return this.variableId; }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (!WiredFeatureCapabilityGuard.isRuntimeReady(this)
                || event == null
                || event.getType() != WiredEvent.Type.VARIABLE_CHANGED) {
            return false;
        }
        return event.getVariableMutation().map(this::matchesMutation).orElse(false);
    }

    boolean matchesMutation(WiredVariableMutation mutation) {
        return matchesSelection(this.variableId, this.created, this.valueChanged,
                this.deleted, this.valueChangeMask, mutation);
    }

    static boolean matchesSelection(String variableId, boolean created, boolean valueChanged,
                                    boolean deleted, int valueChangeMask,
                                    WiredVariableMutation mutation) {
        if (mutation == null || !variableId.equals(mutation.variableId())) {
            return false;
        }
        return switch (mutation.kind()) {
            case CREATED -> created;
            case DELETED -> deleted;
            case VALUE_CHANGED -> valueChanged
                    && (valueChangeMask & mutation.valueChangeMaskBit()) != 0;
        };
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings == null
                || settings.getIntParams() == null
                || settings.getIntParams().length != 4
                || settings.getVariableIds() == null
                || settings.getVariableIds().length != 1
                || settings.getStringParam() == null
                || !settings.getStringParam().isEmpty()
                || settings.getFurniIds().length != 0
                || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 0
                || settings.getUserSourceTypes().length != 0
                || settings.getDelay() != 0) {
            return false;
        }
        String requestedId = settings.getVariableIds()[0];
        if (!isValidVariableId(requestedId)) {
            return false;
        }
        int[] params = settings.getIntParams();
        if (!isBoolean(params[0]) || !isBoolean(params[1]) || !isBoolean(params[2])
                || (params[3] & ~(MASK_INCREASED | MASK_DECREASED | MASK_UNCHANGED)) != 0) {
            return false;
        }
        // July retains checked disabled direction boxes. They are irrelevant
        // when value changed is off, rather than an invalid configuration.
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        WiredInternalVariableRuntime.Definition internal =
                room == null ? null
                        : WiredInternalVariableRuntime.definitions().stream()
                        .filter(definition -> definition.variableId().equals(requestedId))
                        .findFirst().orElse(null);
        if (internal != null) {
            if (!internal.writable() && !internal.runtimeObservable()) {
                return false;
            }
        } else {
            WiredVariableManager manager = room == null
                    ? null : room.getRoomSpecialTypes().getWiredVariableManager();
            if (manager == null || !manager.isOperational()
                    || manager.runtimeDefinition(requestedId) == null
                    || !com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata.fromDefinition(
                            room, manager.runtimeDefinition(requestedId)).canInterceptChanges()) {
                return false;
            }
        }
        this.variableId = requestedId;
        this.created = params[0] == 1;
        this.valueChanged = params[1] == 1;
        this.deleted = params[2] == 1;
        this.valueChangeMask = this.valueChanged ? params[3] : 0;
        return this.created || this.valueChanged || this.deleted;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public String getWiredData() {
        // The item table has one string column.  Keep its opaque compact record
        // typed and versioned; no donor JSON/name/type payload crosses this boundary.
        return PERSISTENCE_VERSION + '.'
                + Base64.getUrlEncoder().withoutPadding().encodeToString(
                        this.variableId.getBytes(StandardCharsets.UTF_8))
                + '.' + (this.created ? '1' : '0')
                + (this.valueChanged ? '1' : '0')
                + (this.deleted ? '1' : '0')
                + this.valueChangeMask;
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String data = set.getString("wired_data");
        if (data == null) {
            return;
        }
        String[] fields = data.split("\\.", -1);
        if (fields.length != 3 || !PERSISTENCE_VERSION.equals(fields[0])
                || fields[2].length() != 4) {
            return;
        }
        try {
            String loadedId = new String(Base64.getUrlDecoder().decode(fields[1]), StandardCharsets.UTF_8);
            int loadedMask = fields[2].charAt(3) - '0';
            if (!isValidVariableId(loadedId)
                    || !isBooleanChar(fields[2].charAt(0))
                    || !isBooleanChar(fields[2].charAt(1))
                    || !isBooleanChar(fields[2].charAt(2))
                    || (loadedMask & ~(MASK_INCREASED | MASK_DECREASED | MASK_UNCHANGED)) != 0) {
                return;
            }
            this.variableId = loadedId;
            this.created = fields[2].charAt(0) == '1';
            this.valueChanged = fields[2].charAt(1) == '1';
            this.deleted = fields[2].charAt(2) == '1';
            this.valueChangeMask = this.valueChanged ? loadedMask : 0;
        } catch (IllegalArgumentException ignored) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.variableId = "";
        this.created = false;
        this.valueChanged = false;
        this.deleted = false;
        this.valueChangeMask = 0;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    protected int[] getWiredIntParams() {
        return new int[] {
                this.created ? 1 : 0,
                this.valueChanged ? 1 : 0,
                this.deleted ? 1 : 0,
                this.valueChangeMask
        };
    }

    @Override
    protected String[] getWiredVariableIds() {
        return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId};
    }

    private static boolean isBoolean(int value) {
        return value == 0 || value == 1;
    }

    private static boolean isBooleanChar(char value) {
        return value == '0' || value == '1';
    }

    private static boolean isValidVariableId(String id) {
        return id != null && !id.isBlank() && id.length() <= 128;
    }
}
