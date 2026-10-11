package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** July AIR add-on 16 ({@code wf_xtra_text_input_variable}). */
public final class WiredAddonVariableCapturer extends InteractionWiredAddon {
    private static final int VERSION = 1;
    private String name = "";
    private String variableId = "";
    private boolean textMode;

    public WiredAddonVariableCapturer(ResultSet set, Item item) throws SQLException { super(set, item); }
    public WiredAddonVariableCapturer(int id, int userId, Item item, String extraData,
                                      int limitedStack, int limitedSells) {
        super(id, userId, item, extraData, limitedStack, limitedSells);
    }
    @Override public WiredAddonType getType() { return WiredAddonType.VARIABLE_CAPTURER; }

    @Override
    public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 1
                || (settings.getIntParams()[0] != 0 && settings.getIntParams()[0] != 1)
                || settings.getVariableIds().length != 1
                || settings.getFurniIds().length != 0 || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 0 || settings.getUserSourceTypes().length != 0
                || settings.getDelay() != 0 || !WiredAddonVariablePlaceholder.validName(settings.getStringParam())) return false;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        String requestedVariable = settings.getVariableIds()[0];
        if (!isWritableCaptureTarget(room, requestedVariable)
                || (settings.getIntParams()[0] == 1
                && WiredAddonVariablePlaceholder.converter(room, requestedVariable) == null)) return false;
        this.name = settings.getStringParam(); this.variableId = requestedVariable;
        this.textMode = settings.getIntParams()[0] == 1;
        return true;
    }

    public boolean appliesTo(String pattern) { return pattern != null && pattern.contains("#(" + this.name + ")"); }

    /** Captures the named numeric token and writes it to this execution's Context Variable. */
    public boolean capture(WiredContext context, String pattern, String input) {
        return capture(context, pattern, input, 0);
    }

    /** Match type is the July User Says setting: 0 contains, 1 exact, 2 any text. */
    public boolean capture(WiredContext context, String pattern, String input, int matchType) {
        if (context == null || pattern == null || input == null || !appliesTo(pattern)
                || !isWritableCaptureTarget(context.room(), this.variableId)) return false;
        String token = "#(" + this.name + ")";
        int at = pattern.indexOf(token);
        String prefix = pattern.substring(0, at);
        String suffix = pattern.substring(at + token.length());
        WiredAddonVariableTextConverter converter = this.textMode
                ? WiredAddonVariablePlaceholder.converter(context.room(), this.variableId) : null;
        if (this.textMode && (converter == null || converter.values().isEmpty())) return false;
        String captureExpression = this.textMode
                ? "(" + converter.values().values().stream().sorted(java.util.Comparator.comparingInt(String::length).reversed())
                    .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|")) + ")"
                : "(-?\\d+)";
        String body = Pattern.quote(prefix) + captureExpression + Pattern.quote(suffix);
        Pattern expression = Pattern.compile(matchType == 1 ? "^(?:" + body + ")$" : body,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = expression.matcher(input);
        if (!matcher.find()) return false;
        try {
            Long converted = converter == null ? null : converter.valueFor(matcher.group(1));
            long value = converted != null ? converted : Long.parseLong(matcher.group(1));
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    && context.contextVariables().set(this.variableId, (int)value);
        } catch (NumberFormatException ignored) { return false; }
    }

    @Override public String getWiredData() { return WiredManager.getGson().toJson(new Data(VERSION, this.name, this.variableId, this.textMode)); }
    @Override public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        try {
            Data data = WiredManager.getGson().fromJson(set == null ? null : set.getString("wired_data"), Data.class);
            if (data != null && data.version == VERSION
                    && WiredAddonVariablePlaceholder.validName(data.name)
                    && isWritableCaptureTarget(room, data.variableId)
                    && (!data.textMode || WiredAddonVariablePlaceholder.converter(room, data.variableId) != null)) {
                this.name=data.name; this.variableId=data.variableId; this.textMode=data.textMode;
            }
        } catch (RuntimeException ignored) { onPickUp(); }
    }
    @Override public void onPickUp() { this.name=""; this.variableId=""; this.textMode=false; }
    @Override protected int[] getWiredIntParams() { return new int[] {this.textMode ? 1 : 0}; }
    @Override protected String getWiredStringParam() { return this.name; }
    @Override protected String[] getWiredVariableIds() { return this.variableId.isEmpty() ? new String[0] : new String[] {this.variableId}; }
    @Override protected int getMaxFurniSelection() { return 0; }

    /** Capturer needs numeric, creatable, writable execution-local storage. */
    static boolean isWritableCaptureTarget(Room room, String id) {
        if (room == null || room.getRoomSpecialTypes() == null || id == null || !id.startsWith("room:")) return false;
        try {
            // Context internals and Echo views are read-only. Never create a
            // shadow value under their exposed ID, including from old saves.
            return room.getRoomSpecialTypes().getVariable(Integer.parseInt(id.substring(5)))
                    instanceof WiredVariableContext context && context.hasValue()
                    && com.eu.habbo.habbohotel.wired.variables.WiredVariableName.isValid(context.variableName());
        } catch (NumberFormatException ignored) { return false; }
    }

    private static final class Data {
        int version; String name, variableId; boolean textMode;
        Data(int version, String name, String variableId, boolean textMode) {
            this.version=version; this.name=name; this.variableId=variableId; this.textMode=textMode;
        }
    }
}
