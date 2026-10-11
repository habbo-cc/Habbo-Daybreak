package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableActionPersistenceTest {
    @Test
    void giveAndRemoveReloadExactAirIntAndVariableShapesWithoutJson() throws Exception {
        WiredEffectGiveVariable give = new WiredEffectGiveVariable(1, 1, null, "0", 0, 0);
        set(give, "variableId", "room:41");
        set(give, "target", 1);
        set(give, "initialValue", -21);
        set(give, "overrideExisting", true);
        String giveData = give.getWiredData();
        assertFalse(giveData.startsWith("{"));
        assertFalse(giveData.contains("room:41"));
        WiredEffectGiveVariable reloadedGive = new WiredEffectGiveVariable(2, 1, null, "0", 0, 0);
        reloadedGive.loadWiredData(row(giveData), null);
        assertArrayEquals(new String[] {"room:41"}, variableIds(reloadedGive));
        assertArrayEquals(new int[] {1, -1, -21, 1}, intParams(reloadedGive));

        WiredEffectRemoveVariable remove = new WiredEffectRemoveVariable(3, 1, null, "0", 0, 0);
        set(remove, "variableId", "room:40");
        set(remove, "target", 0);
        String removeData = remove.getWiredData();
        WiredEffectRemoveVariable reloadedRemove = new WiredEffectRemoveVariable(4, 1, null, "0", 0, 0);
        reloadedRemove.loadWiredData(row(removeData), null);
        assertArrayEquals(new String[] {"room:40"}, variableIds(reloadedRemove));
        assertArrayEquals(new int[] {0}, intParams(reloadedRemove));
    }

    @Test
    void changeReloadsLiteralAndRejectsUnsupportedPersistedOperation() throws Exception {
        WiredEffectChangeVariableValue change = new WiredEffectChangeVariableValue(1, 1, null, "0", 0, 0);
        set(change, "variableId", "room:41");
        set(change, "target", -10);
        set(change, "operation", 1);
        set(change, "operandMode", 0);
        set(change, "literalValue", -7);
        set(change, "operandTarget", 0);
        String persisted = change.getWiredData();
        assertFalse(persisted.startsWith("{"));
        WiredEffectChangeVariableValue loaded = new WiredEffectChangeVariableValue(2, 1, null, "0", 0, 0);
        loaded.loadWiredData(row(persisted), null);
        assertArrayEquals(new String[] {"room:41", ""}, variableIds(loaded));
        assertArrayEquals(new int[] {-10, 1, 0, -1, -7, 0}, intParams(loaded));

        String unsupported = persisted.replace(".1.0.-7.", ".999.0.-7.");
        WiredEffectChangeVariableValue rejected = new WiredEffectChangeVariableValue(3, 1, null, "0", 0, 0);
        rejected.loadWiredData(row(unsupported), null);
        assertTrue(variableIds(rejected).length == 0);
    }

    private static ResultSet row(String value) {
        return (ResultSet) Proxy.newProxyInstance(WiredVariableActionPersistenceTest.class.getClassLoader(),
                new Class[] {ResultSet.class}, (proxy, method, arguments) ->
                        method.getName().equals("getString") ? value : null);
    }

    private static int[] intParams(Object action) throws Exception {
        Method method = action.getClass().getDeclaredMethod("getWiredIntParams");
        method.setAccessible(true);
        return (int[]) method.invoke(action);
    }

    private static String[] variableIds(Object action) throws Exception {
        Method method = action.getClass().getDeclaredMethod("getWiredVariableIds");
        method.setAccessible(true);
        return (String[]) method.invoke(action);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
