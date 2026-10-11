package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredTriggerVariableChangedTest {
    @Test
    void airMaskIsGatedByValueChangedAndClassifiesEveryDirection() {
        WiredVariableMutation increased = change(4, 7);
        WiredVariableMutation decreased = change(7, 4);
        WiredVariableMutation unchanged = change(7, 7);

        assertTrue(WiredTriggerVariableChanged.matchesSelection(
                "room:42", false, true, false,
                WiredTriggerVariableChanged.MASK_INCREASED, increased));
        assertFalse(WiredTriggerVariableChanged.matchesSelection(
                "room:42", false, true, false,
                WiredTriggerVariableChanged.MASK_INCREASED, decreased));
        assertTrue(WiredTriggerVariableChanged.matchesSelection(
                "room:42", false, true, false,
                WiredTriggerVariableChanged.MASK_DECREASED, decreased));
        assertTrue(WiredTriggerVariableChanged.matchesSelection(
                "room:42", false, true, false,
                WiredTriggerVariableChanged.MASK_UNCHANGED, unchanged));
        assertFalse(WiredTriggerVariableChanged.matchesSelection(
                "room:42", false, false, false,
                WiredTriggerVariableChanged.MASK_INCREASED
                        | WiredTriggerVariableChanged.MASK_DECREASED
                        | WiredTriggerVariableChanged.MASK_UNCHANGED,
                increased));
    }

    @Test
    void createdAndDeletedAreIndependentOfValueMaskAndIdMustMatch() {
        WiredVariableMutation created = new WiredVariableMutation("room:42", WiredVariableHolder.room(),
                null, 0, WiredVariableMutation.Kind.CREATED, 1);
        WiredVariableMutation deleted = new WiredVariableMutation("room:42", WiredVariableHolder.room(),
                0, null, WiredVariableMutation.Kind.DELETED, 2);

        assertTrue(WiredTriggerVariableChanged.matchesSelection("room:42", true, false, false, 0, created));
        assertTrue(WiredTriggerVariableChanged.matchesSelection("room:42", false, false, true, 0, deleted));
        assertFalse(WiredTriggerVariableChanged.matchesSelection("room:other", true, true, true, 7, created));
    }

    @Test
    void persistedTriggerReopensWithExactAirIdsAndIntsWithoutJson() throws Exception {
        WiredTriggerVariableChanged saved = new WiredTriggerVariableChanged(1, 1, null, "0", 0, 0);
        set(saved, "variableId", "room:42");
        set(saved, "created", true);
        set(saved, "valueChanged", true);
        set(saved, "deleted", false);
        set(saved, "valueChangeMask", WiredTriggerVariableChanged.MASK_INCREASED
                | WiredTriggerVariableChanged.MASK_UNCHANGED);

        String persisted = saved.getWiredData();
        assertFalse(persisted.startsWith("{"));
        assertFalse(persisted.contains("room:42"));

        ResultSet row = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] {ResultSet.class},
                (proxy, method, arguments) -> method.getName().equals("getString") ? persisted : null);
        WiredTriggerVariableChanged loaded = new WiredTriggerVariableChanged(2, 1, null, "0", 0, 0);
        loaded.loadWiredData(row, null);

        assertArrayEquals(new String[] {"room:42"}, loaded.getWiredVariableIds());
        assertArrayEquals(new int[] {1, 1, 0,
                        WiredTriggerVariableChanged.MASK_INCREASED
                                | WiredTriggerVariableChanged.MASK_UNCHANGED},
                loaded.getWiredIntParams());
        assertNotNull(loaded.getWiredData());
    }

    @Test
    void retainedDisabledDirectionMaskIsNormalizedOnReload() throws Exception {
        var saved = new WiredTriggerVariableChanged(1, 1, null, "0", 0, 0);
        set(saved, "variableId", "internal:1:@handitem");
        set(saved, "created", true);
        set(saved, "deleted", true);
        set(saved, "valueChangeMask", 7);
        String persisted = saved.getWiredData();
        var row = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {ResultSet.class},
                (proxy, method, arguments) -> method.getName().equals("getString") ? persisted : null);
        var loaded = new WiredTriggerVariableChanged(2, 1, null, "0", 0, 0);
        loaded.loadWiredData(row, null);
        assertArrayEquals(new int[] {1, 0, 1, 0}, loaded.getWiredIntParams());
    }

    private static WiredVariableMutation change(int before, int after) {
        return new WiredVariableMutation("room:42", WiredVariableHolder.room(), before, after,
                WiredVariableMutation.Kind.VALUE_CHANGED, 3);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
