package com.eu.habbo.habbohotel.wired.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, execution-owned Context Variable values. Never persisted or shared between runs. */
public final class WiredContextVariableStore {
    public static final int MAX_VALUES = 256;
    private final Map<String, Integer> values = new LinkedHashMap<>();
    public Integer get(String id) { return id == null ? null : values.get(runtimeId(id)); }
    public boolean contains(String id) { return id != null && values.containsKey(runtimeId(id)); }
    public boolean set(String id, int value) {
        String key = runtimeId(id);
        if (key == null || (!values.containsKey(key) && values.size() >= MAX_VALUES)) return false;
        values.put(key, value);
        return true;
    }
    public boolean remove(String id) { return id != null && values.remove(runtimeId(id)) != null; }
    public WiredContextVariableStore snapshot() { WiredContextVariableStore copy = new WiredContextVariableStore(); copy.values.putAll(values); return copy; }
    public void clear() { values.clear(); }

    private static String runtimeId(String id) {
        String prefix = "internal:-20:";
        return id != null && id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }
}
