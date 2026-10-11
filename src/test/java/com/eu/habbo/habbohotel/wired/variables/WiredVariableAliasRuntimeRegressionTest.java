package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableLevelUp;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import org.junit.jupiter.api.Test;
import static com.eu.habbo.habbohotel.wired.variables.WiredAliasRegressionFixture.settings;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WiredVariableAliasRuntimeRegressionTest {
    @Test
    void referenceChainReopenPreservesSelectedEdgeAndUpstreamReadOnlyPolicy() throws Exception {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var origin = fixture.room(99);
            var intermediate = fixture.room(100);
            var terminal = fixture.room(101);
            fixture.user(terminal, 80, true, 11);
            var inner = new WiredVariableReference(81, 1, null, "0", 0, 0);
            inner.setRoomId(100);
            intermediate.types().addVariable(inner);
            assertTrue(inner.saveData(settings(new int[]{1}, "readonly_inner", "room:80")));
            intermediate.types().refreshVariableDefinition(inner);
            var outer = new WiredVariableReference(82, 1, null, "0", 0, 0);
            outer.setRoomId(99);
            origin.types().addVariable(outer);
            assertTrue(outer.saveData(settings(new int[]{0}, "outer", inner.variableId())));
            origin.types().refreshVariableDefinition(outer);
            var persisted = fixture.entries.get(82);
            assertEquals(100, persisted.sourceRoomId());
            assertEquals(inner.variableId(), persisted.sourceVariableId());
            assertFalse(persisted.readOnly()); // Outer setting; inner edge remains authoritative.

            // Old registry rows flattened this selection to the terminal.
            // A v1 item payload still records the real chosen Reference edge.
            fixture.entries.put(82, new WiredCrossRoomAliasRepository.Entry(82, 99, "room:82",
                    WiredVariableType.REFERENCE, 1, "outer", 11, true, 1, 0,
                    101, "room:80", false, 0));
            var itemData = mock(java.sql.ResultSet.class);
            when(itemData.getString("wired_data")).thenReturn(outer.getWiredData().replace("\"version\":2", "\"version\":1"));

            var reopened = new WiredVariableReference(82, 1, null, "0", 0, 0);
            reopened.setRoomId(99);
            reopened.loadWiredData(itemData, origin.room());
            origin.types().addVariable(reopened);
            assertEquals(inner.variableId(), reopened.sourceId());
            assertTrue(WiredVariableAliasResolver.resolve(origin.room(), reopened.variableId()).readOnly());
            var holder = WiredVariableHolder.user(77);
            terminal.manager().set("room:80", holder, 12);
            assertEquals(WiredVariableManager.MutationResult.INVALID,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reopened.variableId(), 99, 0, true, null));
            assertEquals(12, terminal.manager().get("room:80", holder).value());
            assertNull(origin.manager().get(reopened.variableId(), holder));
            inner.onPickUp();
            intermediate.types().removeVariable(inner);
            assertNull(WiredVariableAliasResolver.resolve(origin.room(), reopened.variableId()));
            assertNull(WiredVariableAliasOperations.read(origin.room(), reopened.variableId(), holder));
        }
    }

    @Test
    void generatedAliasChildrenDisappearWhenAuthoritativeSourceBecomesValueless() {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var origin = fixture.room(99);
            var sourceRoom = fixture.room(100);
            var source = fixture.user(sourceRoom, 80, true, 11);
            var reference = new WiredVariableReference(81, 1, null, "0", 0, 0);
            reference.setRoomId(99);
            origin.types().addVariable(reference);
            assertTrue(reference.saveData(settings(new int[]{0}, "xp_reference", "room:80")));
            origin.types().refreshVariableDefinition(reference);
            var addon = new WiredAddonVariableLevelUp(90, 1, null, "0", 0, 0);
            assertTrue(addon.saveData(settings(new int[]{1, 1, 100, 50}, "")));
            origin.types().addAddon(addon);
            var holder = WiredVariableHolder.user(77);
            sourceRoom.manager().set("room:80", holder, 500);
            String child = reference.variableId() + ".value.current_level";
            var before = WiredVariableCatalog.forRoom(origin.room());
            assertTrue(before.entries().containsKey(child));
            assertEquals(6, WiredGeneratedVariableRuntime.read(origin.room(), child, holder).value());

            assertTrue(source.saveData(settings(new int[]{11, 0}, "source_80")));
            sourceRoom.types().refreshVariableDefinition(source);
            // Intentionally retain the alias's old cloned numeric descriptor.
            assertTrue(origin.manager().definition(reference.variableId()).hasValue());
            var after = WiredVariableCatalog.forRoom(origin.room());
            assertFalse(after.entries().get(reference.variableId()).metadata().hasValue());
            assertFalse(after.entries().containsKey(child));
            assertNotEquals(before.aggregateHash(), after.aggregateHash());
            assertNull(WiredGeneratedVariableRuntime.read(origin.room(), child, holder));
            assertNull(WiredVariableAliasResolver.resolve(origin.room(), child));
        }
    }

    @Test
    void echoPersistsConfiguredReadOnlyReferenceEdgeAndKeepsPolicyAfterReopen() throws Exception {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var origin = fixture.room(99);
            var sourceRoom = fixture.room(100);
            var source = fixture.user(sourceRoom, 80, true, 11);
            String sourceId = "room:" + source.getId();
            var reference = new WiredVariableReference(81, 1, null, "0", 0, 0);
            reference.setRoomId(99);
            origin.types().addVariable(reference);
            assertTrue(reference.saveData(settings(new int[]{1}, "readonly_source", sourceId)));
            origin.types().refreshVariableDefinition(reference);
            var echo = echo(origin, 82);
            assertTrue(echo.saveData(settings(new int[0], "readonly_echo", reference.variableId())));
            origin.types().refreshVariableDefinition(echo);
            var stored = fixture.entries.get(82);
            assertEquals(99, stored.sourceRoomId());
            assertEquals(reference.variableId(), stored.sourceVariableId());
            assertTrue(stored.readOnly());
            var holder = WiredVariableHolder.user(77);
            sourceRoom.manager().set(sourceId, holder, 12);
            assertEquals(12, WiredVariableAliasOperations.read(origin.room(), echo.variableId(), holder));

            var reopened = new WiredVariableEcho(82, 1, null, "0", 0, 0);
            reopened.setRoomId(99);
            reopened.loadWiredData(null, origin.room());
            origin.types().addVariable(reopened);
            assertEquals(reference.variableId(), reopened.sourceId());
            assertTrue(WiredVariableAliasResolver.resolve(origin.room(), reopened.variableId()).readOnly());
            var metadata = WiredVariableMetadata.fromDefinition(origin.room(), origin.manager().definition(reopened.variableId()));
            assertFalse(metadata.canWriteValue());
            assertFalse(metadata.canCreateAndDelete());
            assertEquals(WiredVariableManager.MutationResult.INVALID,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reopened.variableId(), 99, 0, true, null));
            assertEquals(12, sourceRoom.manager().get(sourceId, holder).value());
            assertNull(origin.manager().get(reopened.variableId(), holder));

            reference.onPickUp();
            origin.types().removeVariable(reference);
            assertNull(WiredVariableAliasResolver.resolve(origin.room(), reopened.variableId()));
            assertNull(WiredVariableAliasOperations.read(origin.room(), reopened.variableId(), holder));
            assertEquals(WiredVariableManager.MutationResult.INVALID,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reopened.variableId(), 99, 0, true, null));
            assertEquals(12, sourceRoom.manager().get(sourceId, holder).value());
        }
    }

    @Test
    void internalEchoSaveRegistryReopenAndReadsUseLiveAuthoritativeState() throws Exception {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var state = fixture.room(99);
            var echo = echo(state, 10);
            assertTrue(echo.saveData(settings(new int[0], "state_echo", "internal:0:@state")));
            state.types().refreshVariableDefinition(echo);
            var persisted = fixture.entries.get(10);
            assertEquals(WiredVariableType.ECHO, persisted.type());
            assertEquals(99, persisted.sourceRoomId());
            assertEquals("internal:0:@state", persisted.sourceVariableId());
            verify(fixture.registry, never()).syncSourceDefinition(any());
            assertEquals("internal:0:@state", WiredVariableAliasResolver.resolve(state.room(), echo.variableId()).variableId());
            var metadata = WiredVariableMetadata.fromDefinition(state.room(), state.manager().definition(echo.variableId()));
            assertTrue(metadata.canWriteValue());
            assertFalse(metadata.canCreateAndDelete());
            assertTrue(metadata.canInterceptChanges());

            HabboItem item = mock(HabboItem.class);
            when(item.getId()).thenReturn(200);
            when(item.getBaseItem()).thenReturn(mock(Item.class));
            when(item.getExtradata()).thenReturn("7");
            when(state.room().getHabboItemByDatabaseId(200)).thenReturn(item);
            var holder = WiredVariableHolder.furni(200);
            assertEquals(7, WiredVariableAliasOperations.read(state.room(), echo.variableId(), holder));
            when(item.getExtradata()).thenReturn("9");
            assertEquals(9, WiredVariableAliasOperations.read(state.room(), echo.variableId(), holder));

            var reopened = new WiredVariableEcho(10, 1, null, "0", 0, 0);
            reopened.setRoomId(99);
            reopened.loadWiredData(null, state.room());
            state.types().addVariable(reopened);
            assertEquals(echo.sourceId(), reopened.sourceId());
            assertTrue(reopened.getWiredData().contains("state_echo"));
            assertEquals(9, WiredVariableAliasOperations.read(state.room(), reopened.variableId(), holder));
        }
    }

    @Test
    void generatedEchoReadsRealLevelUpOutputsAndRejectsNumericWrites() {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var state = fixture.room(99);
            var parent = fixture.user(state, 20, true, 0);
            String parentId = "room:" + parent.getId();
            var addon = new WiredAddonVariableLevelUp(30, 1, null, "0", 0, 0);
            assertTrue(addon.saveData(settings(new int[]{1, 1, 100, 50}, "")));
            state.types().addAddon(addon);
            var holder = WiredVariableHolder.user(77);
            state.manager().set(parentId, holder, 500);
            var echo = echo(state, 21);
            String generatedId = parentId + ".value.current_level";
            assertTrue(echo.saveData(settings(new int[0], "level_echo", generatedId)));
            state.types().refreshVariableDefinition(echo);
            assertEquals(6, WiredVariableAliasOperations.read(state.room(), echo.variableId(), holder));
            assertTrue(WiredVariableAliasResolver.resolve(state.room(), echo.variableId()).readOnly());
            var metadata = WiredVariableMetadata.fromDefinition(state.room(), state.manager().definition(echo.variableId()));
            assertFalse(metadata.canWriteValue());
            assertFalse(metadata.canCreateAndDelete());
            assertFalse(metadata.alwaysAvailable());
            assertNull(WiredVariableAliasOperations.writable(state.room(), echo.variableId(), holder));
            state.manager().set(parentId, holder, 700);
            assertEquals(8, WiredVariableAliasOperations.read(state.room(), echo.variableId(), holder));
            state.manager().removeValue(parentId, holder);
            assertNull(WiredVariableAliasOperations.read(state.room(), echo.variableId(), holder));
        }
    }

    @Test
    void cyclicOrDisallowedSourcesAndFailedPersistenceRestorePriorEchoConfiguration() {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var state = fixture.room(99);
            var first = echo(state, 40);
            var second = echo(state, 41);
            assertTrue(first.saveData(settings(new int[0], "original", "internal:0:@state")));
            state.types().refreshVariableDefinition(first);
            assertTrue(second.saveData(settings(new int[0], "second", first.variableId())));
            state.types().refreshVariableDefinition(second);
            assertFalse(first.saveData(settings(new int[0], "cycle", second.variableId())));
            assertEquals("internal:0:@state", first.sourceId());
            assertFalse(first.saveData(settings(new int[0], "self", first.variableId())));
            var ordinary = fixture.user(state, 42, true, 0);
            assertFalse(first.saveData(settings(new int[0], "ordinary", "room:" + ordinary.getId())));
            assertFalse(first.saveData(settings(new int[0], "missing", "room:999.value.current_level")));
            doReturn(false).when(fixture.registry).upsert(any());
            assertFalse(first.saveData(settings(new int[0], "failed", "internal:0:@rotation")));
            assertEquals("internal:0:@state", first.sourceId());
            assertTrue(first.getWiredData().contains("original"));
            assertEquals("internal:0:@state", fixture.entries.get(40).sourceVariableId());
        }
    }

    private static WiredVariableEcho echo(WiredAliasRegressionFixture.State state, int id) {
        var echo = new WiredVariableEcho(id, 1, null, "0", 0, 0);
        echo.setRoomId(state.room().getId());
        state.types().addVariable(echo);
        return echo;
    }
}
