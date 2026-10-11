package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** July AIR add-on 15 ({@code wf_xtra_text_output_variable}). */
public final class WiredAddonVariablePlaceholder extends InteractionWiredAddon {
    private static final int VERSION = 1;
    private static final int TARGET_FURNI = 0;
    private static final int TARGET_USER = 1;
    private static final int TARGET_GLOBAL = -10;
    private static final int TARGET_CONTEXT = -20;
    private static final int MAX_NAME = 32;
    private static final int MAX_DELIMITER = 32;
    private static final String NULL_VALUE = "null";

    private String name = "";
    private String delimiter = "";
    private String variableId = "";
    private int target = TARGET_FURNI;
    private boolean multiple;
    private boolean textMode;

    public WiredAddonVariablePlaceholder(ResultSet set, Item item) throws SQLException { super(set, item); }
    public WiredAddonVariablePlaceholder(int id, int userId, Item item, String extraData,
                                         int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }

    @Override public WiredAddonType getType() { return WiredAddonType.VARIABLE_PLACEHOLDER; }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 3
                || settings.getVariableIds().length != 1 || settings.getFurniIds().length != 0
                || settings.getFurniIds2().length != 0 || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes().length != 1 || settings.getDelay() != 0) return false;
        int[] params = settings.getIntParams();
        if ((params[0] != 0 && params[0] != 1) || !validTarget(params[1])
                || (params[2] != 0 && params[2] != 1)) return false;
        String[] text = settings.getStringParam().split("\\t", -1);
        boolean wantsMultiple = params[0] == 1;
        if (text.length != (wantsMultiple ? 2 : 1) || !validName(text[0])
                || (wantsMultiple && (text[1].isEmpty() || text[1].length() > MAX_DELIMITER))
                || (wantsMultiple && (params[1] == TARGET_GLOBAL || params[1] == TARGET_CONTEXT))) return false;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        String requestedVariable = settings.getVariableIds()[0];
        if (room == null || !definitionMatches(room, requestedVariable, params[1])
                || (params[2] == 1 && converter(room, requestedVariable) == null)) return false;
        this.name = text[0];
        this.delimiter = wantsMultiple ? text[1] : "";
        this.variableId = requestedVariable;
        this.target = params[1];
        this.multiple = wantsMultiple;
        this.textMode = params[2] == 1;
        return true;
    }

    /** Replaces this add-on's July {@code $(name)} token with its current numeric value(s). */
    public String apply(WiredContext context, String input) {
        if (context == null || input == null || this.variableId.isEmpty()) return input;
        String token = "$(" + this.name + ")";
        if (!input.contains(token)) return input;
        List<Integer> values = resolveValues(context);
        WiredAddonVariableTextConverter converter = this.textMode ? converter(context.room(), this.variableId) : null;
        String replacement = values.isEmpty() ? NULL_VALUE
                : values.stream().limit(this.multiple ? 16 : 1)
                .map(value -> converter == null ? String.valueOf(value) : converter.textFor(value))
                .map(value -> value == null ? NULL_VALUE : value)
                .collect(java.util.stream.Collectors.joining(this.delimiter));
        return input.replace(token, replacement);
    }

    private List<Integer> resolveValues(WiredContext context) {
        if (this.target == TARGET_CONTEXT) {
            Integer value = com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations.readContext(context, this.variableId);
            return value == null ? List.of() : List.of(value);
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) return List.of();
        if (this.target == TARGET_GLOBAL) {
            WiredVariableValue value = com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations.readValue(
                    context.room(), this.variableId, WiredVariableHolder.room());
            if (value == null) value = WiredGeneratedVariableRuntime.read(context.room(), this.variableId,
                    WiredVariableHolder.room());
            Integer internal = value == null
                    ? WiredInternalVariableRuntime.read(context.room(), this.variableId,
                            WiredVariableHolder.room()) : null;
            return value != null ? List.of(value.value())
                    : internal == null ? List.of() : List.of(internal);
        }
        WiredInternalVariableRuntime.Definition internal =
                WiredInternalVariableRuntime.definition(this.variableId, this.target);
        if (this.target == TARGET_USER && internal != null) {
            List<Integer> values = new ArrayList<>();
            resolveUserSource(context, this.wiredUserSourceTypes, 0).stream()
                    .filter(unit -> unit != null && unit.isInRoom()
                            && unit.getRoom() == context.room())
                    .sorted(Comparator.comparingInt(RoomUnit::getId))
                    .limit(16)
                    .forEach(unit -> {
                        Integer value = WiredInternalVariableRuntime
                                .unitValues(context.room(), unit)
                                .get(internal.name());
                        if (value != null) {
                            values.add(value);
                        }
                    });
            return List.copyOf(values);
        }
        List<WiredVariableHolder> holders = new ArrayList<>();
        if (this.target == TARGET_FURNI) {
            resolveFurniSource(context, this.wiredFurniSourceTypes, 0, List.of(), List.of()).stream()
                    .sorted(Comparator.comparingInt(HabboItem::getId))
                    .forEach(item -> holders.add(WiredVariableHolder.furni(item.getId())));
        } else {
            resolveUserSource(context, this.wiredUserSourceTypes, 0).stream()
                    .sorted(Comparator.comparingInt(RoomUnit::getId))
                    .map(context.room()::getHabbo).filter(java.util.Objects::nonNull)
                    .forEach(habbo -> holders.add(WiredVariableHolder.user(habbo.getHabboInfo().getId())));
        }
        List<Integer> values = new ArrayList<>();
        for (WiredVariableHolder holder : holders) {
            WiredVariableValue value = com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations.readValue(
                    context.room(), this.variableId, holder);
            if (value == null) value = WiredGeneratedVariableRuntime.read(context.room(), this.variableId, holder);
            Integer internalValue = value == null
                    ? WiredInternalVariableRuntime.read(
                            context.room(), this.variableId, holder) : null;
            if (value != null) values.add(value.value());
            else if (internalValue != null) values.add(internalValue);
        }
        return values;
    }

    @Override public String getWiredData() {
        return WiredManager.getGson().toJson(new Data(VERSION, this.name, this.delimiter,
                this.variableId, this.target, this.multiple, this.textMode));
    }
    @Override public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            Data data = WiredManager.getGson().fromJson(set == null ? null : set.getString("wired_data"), Data.class);
            if (data != null && data.version == VERSION && validName(data.name)
                    && data.delimiter != null && data.delimiter.length() <= MAX_DELIMITER
                    && validTarget(data.target)
                    && (!data.multiple || (data.target != TARGET_GLOBAL && data.target != TARGET_CONTEXT))
                    && definitionMatches(room, data.variableId, data.target)
                    && (!data.textMode || converter(room, data.variableId) != null)) {
                this.name = data.name; this.delimiter = data.multiple ? data.delimiter : "";
                this.variableId = data.variableId; this.target = data.target;
                this.multiple = data.multiple; this.textMode = data.textMode;
            }
        } catch (RuntimeException ignored) { onPickUp(); }
    }
    @Override public void onPickUp() {
        this.name = ""; this.delimiter = ""; this.variableId = "";
        this.target = TARGET_FURNI; this.multiple = false; this.textMode = false;
        this.wiredFurniSourceTypes = new int[0]; this.wiredUserSourceTypes = new int[0];
    }
    @Override protected int[] getWiredIntParams() { return new int[] {this.multiple ? 1 : 0, this.target, this.textMode ? 1 : 0}; }
    @Override protected String getWiredStringParam() { return this.multiple ? this.name + "\t" + this.delimiter : this.name; }
    @Override protected String[] getWiredVariableIds() { return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId}; }
    @Override protected int getMaxFurniSelection() { return 0; }
    @Override protected int getFurniSourceSlotCount() { return 1; }
    @Override protected int getUserSourceSlotCount() { return 1; }
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot) { return new int[] {FURNI_SOURCE_TRIGGERING_ITEM, FURNI_SOURCE_SELECTOR, FURNI_SOURCE_SIGNAL}; }
    @Override protected int[] getAllowedUserSourcesForSlot(int slot) { return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL}; }

    static boolean validName(String value) { return value != null && value.length() >= 1 && value.length() <= MAX_NAME && value.matches("[a-z0-9_]+"); }
    static boolean definitionMatches(Room room, String id, int target) {
        if (room == null || id == null) return false;
        if (WiredInternalVariableRuntime.matches(id, target)) return true;
        if (!id.startsWith("room:")) return false;
        if (target == TARGET_CONTEXT) {
            var alias = com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations.resolve(room, id);
            if (alias != null && alias.definition().type() == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT)
                return alias.definition().hasValue();
            try {
                return room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5))) instanceof WiredVariableContext value && value.hasValue();
            } catch (NumberFormatException ignored) { return false; }
        }
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) return false;
        WiredVariableDefinition definition = manager.runtimeDefinition(id);
        if (definition == null) {
            definition = WiredGeneratedVariableRuntime.parentDefinition(room, id);
        }
        if (definition == null || !definition.hasValue()) return false;
        return (target == TARGET_FURNI && definition.holderScope() == WiredVariableHolder.Scope.FURNI)
                || (target == TARGET_USER && definition.holderScope() == WiredVariableHolder.Scope.USER)
                || (target == TARGET_GLOBAL && definition.holderScope() == WiredVariableHolder.Scope.ROOM);
    }
    public static boolean hasTextConverter(Room room, String variableId) {
        return converter(room, variableId) != null;
    }

    public static Map<Long, String> textConverterValues(Room room, String variableId) {
        WiredAddonVariableTextConverter converter = converter(room, variableId);
        return converter == null ? Map.of() : converter.values();
    }

    static WiredAddonVariableTextConverter converter(Room room, String variableId) {
        if (room == null || variableId == null || !variableId.startsWith("room:")) return null;
        try {
            InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(
                    Integer.parseInt(variableId.substring(5)));
            if (variable == null) return null;
            return room.getRoomSpecialTypes().getAddons(variable.getX(), variable.getY()).stream()
                    .filter(WiredAddonVariableTextConverter.class::isInstance)
                    .map(WiredAddonVariableTextConverter.class::cast)
                    .min(Comparator.comparingInt(
                            WiredAddonVariableTextConverter::getId))
                    .orElse(null);
        } catch (NumberFormatException ignored) { }
        return null;
    }
    private static boolean validTarget(int value) { return value == TARGET_FURNI || value == TARGET_USER || value == TARGET_GLOBAL || value == TARGET_CONTEXT; }

    private static final class Data {
        int version; String name, delimiter, variableId; int target; boolean multiple, textMode;
        Data(int version, String name, String delimiter, String variableId, int target, boolean multiple, boolean textMode) {
            this.version=version; this.name=name; this.delimiter=delimiter; this.variableId=variableId;
            this.target=target; this.multiple=multiple; this.textMode=textMode;
        }
    }
}
