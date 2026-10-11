package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableConditionWireTest {
    @Test
    void variableValueUsesExactAirFieldOrderAndSixComparisons() throws Exception {
        assertTrue(WiredConditionVariableValue.matchesComparison(1, 2, 0));
        assertTrue(WiredConditionVariableValue.matchesComparison(2, 2, 1));
        assertTrue(WiredConditionVariableValue.matchesComparison(3, 2, 2));
        assertTrue(WiredConditionVariableValue.matchesComparison(2, 2, 3));
        assertTrue(WiredConditionVariableValue.matchesComparison(3, 2, 4));
        assertTrue(WiredConditionVariableValue.matchesComparison(2, 2, 5));
        assertFalse(WiredConditionVariableValue.matchesComparison(1, 2, 5));
        assertFalse(WiredConditionVariableValue.matchesComparison(1, 2, 6));

        WiredConditionVariableValue saved = new WiredConditionVariableValue(1, 1, null, "0", 0, 0);
        set(saved, "targetVariableId", "room:target");
        set(saved, "referenceVariableId", "room:reference");
        set(saved, "targetScope", -10);
        set(saved, "comparison", 5);
        set(saved, "referenceMode", 1);
        set(saved, "literalValue", -9);
        set(saved, "referenceScope", -10);
        set(saved, "furniSourceTypes", new int[] {0, 201});
        set(saved, "userSourceTypes", new int[] {0, 201});

        String persisted = saved.getWiredData();
        assertFalse(persisted.startsWith("{"));
        assertFalse(persisted.contains("room:target"));

        WiredConditionVariableValue loaded = new WiredConditionVariableValue(2, 1, null, "0", 0, 0);
        loaded.loadWiredData(row(persisted), null);
        assertArrayEquals(new int[] {-10, 5, 1, -1, -9, -10}, loaded.getWiredIntParams());
        assertArrayEquals(new String[] {"room:target", "room:reference"}, loaded.getWiredVariableIds());
        assertArrayEquals(new int[] {0, 201}, loaded.getWiredFurniSourceTypes());
        assertArrayEquals(new int[] {0, 201}, loaded.getWiredUserSourceTypes());
    }

    @Test
    void ageKeepsSignedDurationAndRejectsMalformedPersistence() throws Exception {
        assertTrue(WiredConditionVariableBase.validSignedIntParts(-1, -1));
        assertFalse(WiredConditionVariableBase.validSignedIntParts(0, -1));
        assertFalse(WiredConditionVariableBase.validSignedIntParts(-1, 0));

        WiredConditionVariableAge saved = new WiredConditionVariableAge(1, 1, null, "0", 0, 0);
        set(saved, "variableId", "room:age");
        set(saved, "targetScope", -10);
        set(saved, "comparison", 2);
        set(saved, "timestampKind", 1);
        set(saved, "duration", -3);
        set(saved, "unit", 5);
        set(saved, "furniSourceTypes", new int[] {201});
        set(saved, "userSourceTypes", new int[] {201});

        WiredConditionVariableAge loaded = new WiredConditionVariableAge(2, 1, null, "0", 0, 0);
        loaded.loadWiredData(row(saved.getWiredData()), null);
        assertArrayEquals(new int[] {-10, 2, 1, -1, -3, 5}, loaded.getWiredIntParams());
        assertArrayEquals(new String[] {"room:age"}, loaded.getWiredVariableIds());

        loaded.loadWiredData(row("v1.not-base64"), null);
        assertArrayEquals(new int[0], loaded.getWiredIntParams());
        assertArrayEquals(new String[0], loaded.getWiredVariableIds());
    }

    @Test
    void hasVariableRoundTripsOnlyAirTargetAndVariableId() throws Exception {
        WiredConditionHasVariable saved = new WiredConditionHasVariable(1, 1, null, "0", 0, 0);
        set(saved, "variableId", "room:has");
        set(saved, "targetScope", -10);
        set(saved, "furniSourceTypes", new int[] {0});
        set(saved, "userSourceTypes", new int[] {0});

        WiredConditionHasVariable loaded = new WiredConditionHasVariable(2, 1, null, "0", 0, 0);
        String persisted = saved.getWiredData();
        assertFalse(persisted.startsWith("{"));
        loaded.loadWiredData(row(persisted), null);
        assertArrayEquals(new int[] {-10}, loaded.getWiredIntParams());
        assertArrayEquals(new String[] {"room:has"}, loaded.getWiredVariableIds());
    }

    @Test
    void runtimeEntryPointsFailClosedWithoutAValidatedContext() {
        assertFalse(new WiredConditionHasVariable(1, 1, null, "0", 0, 0).evaluate(null));
        assertFalse(new WiredConditionNotHasVariable(1, 1, null, "0", 0, 0).evaluate(null));
        assertFalse(new WiredConditionVariableValue(1, 1, null, "0", 0, 0).evaluate(null));
        assertFalse(new WiredConditionVariableAge(1, 1, null, "0", 0, 0).evaluate(null));
    }

    private static ResultSet row(String wiredData) {
        return (ResultSet) Proxy.newProxyInstance(WiredVariableConditionWireTest.class.getClassLoader(),
                new Class[] {ResultSet.class}, (proxy, method, arguments) ->
                        method.getName().equals("getString") ? wiredData : null);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
