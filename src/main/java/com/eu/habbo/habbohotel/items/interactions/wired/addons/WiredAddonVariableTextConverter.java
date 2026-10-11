package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** July add-on 1000: maps integer variable values to display text. */
public final class WiredAddonVariableTextConverter extends InteractionWiredAddon {
    private static final int VERSION = 1;
    private static final int MAX_LINES = 30;
    private static final int MAX_CHARACTERS = 1000;

    private String text = "";
    private Map<Long, String> values = Collections.emptyMap();

    public WiredAddonVariableTextConverter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredAddonVariableTextConverter(int id, int userId, Item item, String extradata,
                                           int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.VARIABLE_TEXT_CONVERTER;
    }

    @Override
    public synchronized boolean saveData(WiredSettingsV2 settings) {
        if (!ownsOnlyString(settings)) {
            return false;
        }
        Parsed parsed = parse(settings.getStringParam());
        if (parsed == null) {
            return false;
        }
        apply(parsed);
        return true;
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(new Data(VERSION, this.text));
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        if (set == null) {
            return;
        }
        try {
            String raw = set.getString("wired_data");
            Data data = raw != null && raw.startsWith("{")
                    ? WiredManager.getGson().fromJson(raw, Data.class) : null;
            Parsed parsed = data != null && data.version == VERSION ? parse(data.text) : parse(raw);
            if (parsed != null) {
                apply(parsed);
            }
        } catch (RuntimeException ignored) {
            // Corrupt data leaves the safe empty default.
        }
    }

    @Override
    public synchronized void onPickUp() {
        this.text = "";
        this.values = Collections.emptyMap();
    }

    @Override
    protected synchronized String getWiredStringParam() {
        return this.text;
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }

    public synchronized String textFor(long value) {
        return this.values.get(value);
    }

    public synchronized Long valueFor(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        for (Map.Entry<Long, String> entry : this.values.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized Map<Long, String> values() {
        return this.values;
    }

    private static boolean ownsOnlyString(WiredSettingsV2 settings) {
        return settings != null && settings.getIntParams().length == 0
                && settings.getFurniIds().length == 0 && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0;
    }

    private static Parsed parse(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() > MAX_CHARACTERS) {
            return null;
        }
        LinkedHashMap<Long, String> parsed = new LinkedHashMap<>();
        if (!normalized.isEmpty()) {
            String[] lines = normalized.split("\n", -1);
            if (lines.length > MAX_LINES) {
                return null;
            }
            for (String rawLine : lines) {
                String line = rawLine.trim();
                int separator = line.indexOf('=');
                if (separator <= 0 || separator == line.length() - 1) {
                    return null;
                }
                try {
                    long key = Long.parseLong(line.substring(0, separator).trim());
                    if (key < Integer.MIN_VALUE || key > Integer.MAX_VALUE) {
                        return null;
                    }
                    String mappedText = line.substring(separator + 1).trim();
                    if (mappedText.isEmpty()) {
                        return null;
                    }
                    parsed.put(key, mappedText);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return new Parsed(normalized, Collections.unmodifiableMap(parsed));
    }

    private void apply(Parsed parsed) {
        this.text = parsed.text;
        this.values = parsed.values;
    }

    private record Parsed(String text, Map<Long, String> values) { }

    private static final class Data {
        int version;
        String text = "";
        Data() { }
        Data(int version, String text) { this.version = version; this.text = text; }
    }
}
