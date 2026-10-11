package com.eu.habbo.habbohotel.wired.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WiredContextVariableStoreTest {
    @Test void delayedSnapshotIsIndependentAndBounded() {
        WiredContextVariableStore store = new WiredContextVariableStore();
        assertTrue(store.set("room:23824", 7));
        WiredContextVariableStore snapshot = store.snapshot();
        assertTrue(store.set("room:23824", 9));
        assertEquals(7, snapshot.get("room:23824"));
        for (int i = 0; i < WiredContextVariableStore.MAX_VALUES - 1; i++) assertTrue(store.set("v" + i, i));
        assertFalse(store.set("overflow", 1));
        store.clear();
        assertFalse(store.contains("room:23824"));
    }
}
