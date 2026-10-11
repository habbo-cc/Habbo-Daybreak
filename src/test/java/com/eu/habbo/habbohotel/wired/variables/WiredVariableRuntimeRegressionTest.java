package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import gnu.trove.set.hash.THashSet;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WiredVariableRuntimeRegressionTest {
    @Test
    void menuCreatesAndRemovesValuelessPresenceButRefusesNumericWrites() {
        Fixture f = new Fixture();
        var definition = f.definition(1, WiredVariableType.FURNI, 1, false);
        var holder = WiredVariableHolder.furni(42);
        var seen = new ArrayList<WiredVariableMutation>();
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                WiredVariableMenuMutation.apply(f.room, holder, definition.variableId(), 999, 1, false, seen::add));
        assertEquals(0, f.manager.get(definition.variableId(), holder).value());
        assertEquals(WiredVariableManager.MutationResult.INVALID,
                WiredVariableMenuMutation.apply(f.room, holder, definition.variableId(), 2, 0, false, seen::add));
        assertEquals(WiredVariableManager.MutationResult.REMOVED,
                WiredVariableMenuMutation.apply(f.room, holder, definition.variableId(), 0, 2, false, seen::add));
        assertEquals(List.of(WiredVariableMutation.Kind.CREATED, WiredVariableMutation.Kind.DELETED),
                seen.stream().map(WiredVariableMutation::kind).toList());
    }

    @Test
    void menuMutatesWritableReferenceSourceAndReadOnlyEdgesNeverCreateShadowValues() {
        Fixture f = new Fixture();
        var source = f.definition(1, WiredVariableType.USER, 11, true);
        f.definition(2, WiredVariableType.USER, 11, true);
        var reference = mock(WiredVariableReference.class);
        when(reference.getId()).thenReturn(2);
        when(reference.getType()).thenReturn(WiredVariableType.REFERENCE);
        when(reference.sourceId()).thenReturn(source.variableId());
        f.types.addVariable(reference);
        var holder = WiredVariableHolder.user(77);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(f.environment);
            assertEquals(WiredVariableManager.MutationResult.CREATED,
                    WiredVariableMenuMutation.apply(f.room, holder, "room:2", 10, 1, false, null));
            assertEquals(10, f.manager.get("room:1", holder).value());
            assertNull(f.manager.get("room:2", holder));
            assertEquals(WiredVariableManager.MutationResult.CHANGED,
                    WiredVariableMenuMutation.apply(f.room, holder, "room:2", 20, 0, false, null));
            when(reference.readOnly()).thenReturn(true);
            assertEquals(WiredVariableManager.MutationResult.INVALID,
                    WiredVariableMenuMutation.apply(f.room, holder, "room:2", 0, 2, false, null));
            assertEquals(20, f.manager.get("room:1", holder).value());
            assertNull(f.manager.get("room:2", holder));
            when(reference.readOnly()).thenReturn(false);
            assertEquals(WiredVariableManager.MutationResult.REMOVED,
                    WiredVariableMenuMutation.apply(f.room, holder, "room:2", 0, 2, false, null));
            assertNull(f.manager.get("room:1", holder));
        }
    }

    @Test
    void echoReadsInternalSourceAndPreservesReadOnlyAndContextPolicy() {
        Fixture f = new Fixture();
        f.echo(2, "internal:1:@handitem");
        var holder = WiredVariableHolder.user(77);
        try (var emulator = mockStatic(Emulator.class);
             var internal = mockStatic(WiredInternalVariableRuntime.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(f.environment);
            internal.when(() -> WiredInternalVariableRuntime.read(f.room, "internal:1:@handitem", holder)).thenReturn(12);
            assertEquals("internal:1:@handitem", WiredVariableAliasResolver.resolve(f.room, "room:2").variableId());
            assertEquals(12, WiredVariableAliasOperations.read(f.room, "room:2", holder));
            assertTrue(WiredVariableAliasOperations.canWriteValue(f.room, "room:2"));
            assertFalse(WiredVariableAliasOperations.canCreateDelete(f.room, "room:2"));
            f.echo(3, "internal:1:@user_id");
            assertFalse(WiredVariableAliasOperations.canWriteValue(f.room, "room:3"));
            f.echo(4, "internal:-20:@selector_furni_count");
            var resolved = WiredVariableAliasResolver.resolve(f.room, "room:4");
            assertEquals(WiredVariableType.CONTEXT, resolved.definition().type());
            assertTrue(resolved.readOnly());
        }
    }

    @Test
    void echoGeneratedSourceIsReadableButGeneratedParentCyclesFailClosed() {
        Fixture f = new Fixture();
        var parent = f.definition(1, WiredVariableType.USER, 0, true);
        f.echo(2, "room:1.value.seconds");
        var holder = WiredVariableHolder.user(77);
        try (var emulator = mockStatic(Emulator.class);
             var generated = mockStatic(WiredGeneratedVariableRuntime.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(f.environment);
            generated.when(() -> WiredGeneratedVariableRuntime.parentDefinition(f.room, "room:1.value.seconds")).thenReturn(parent);
            generated.when(() -> WiredGeneratedVariableRuntime.read(f.room, "room:1.value.seconds", holder))
                    .thenReturn(new WiredVariableValue("room:1.value.seconds", holder, 42, 0, 0, 1));
            assertEquals(42, WiredVariableAliasOperations.read(f.room, "room:2", holder));
            assertFalse(WiredVariableAliasOperations.canWriteValue(f.room, "room:2"));
            f.echo(3, "room:3.value.seconds");
            var cycleParent = f.definition(3, WiredVariableType.USER, 0, true);
            generated.when(() -> WiredGeneratedVariableRuntime.parentDefinition(f.room, "room:3.value.seconds")).thenReturn(cycleParent);
            assertNull(WiredVariableAliasResolver.resolve(f.room, "room:3"));
        }
    }

    @Test
    void ordinaryInternalChangesPreserveLifecycleAndExplicitWritesDoNotDuplicate() {
        Fixture f = new Fixture();
        var trigger = mock(WiredTriggerVariableChanged.class);
        when(trigger.getId()).thenReturn(10);
        when(trigger.getType()).thenReturn(WiredTriggerType.VARIABLE_CHANGED);
        when(trigger.selectedVariableId()).thenReturn("internal:1:@handitem");
        f.types.addTrigger(trigger);
        var unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(77);
        when(unit.getRoom()).thenReturn(f.room);
        when(unit.isInRoom()).thenReturn(true);
        when(f.room.getRoomUnits()).thenReturn(new THashSet<>(List.of(unit)));
        var values = new AtomicReference<Map<String, Integer>>(Map.of());
        var seen = new ArrayList<WiredVariableMutation>();
        try (var internal = mockStatic(WiredInternalVariableRuntime.class, CALLS_REAL_METHODS);
             var wired = mockStatic(WiredManager.class)) {
            internal.when(() -> WiredInternalVariableRuntime.unitValues(f.room, unit)).thenAnswer(call -> values.get());
            wired.when(() -> WiredManager.triggerVariableChanged(eq(f.room), eq(unit), any())).thenAnswer(call -> {
                seen.add(call.getArgument(2)); return true;
            });
            WiredInternalVariableObserver.observe(f.room);
            values.set(Map.of("@handitem", 12));
            WiredInternalVariableObserver.observe(f.room);
            assertEquals(WiredVariableMutation.Kind.CREATED, seen.get(0).kind());
            values.set(Map.of("@handitem", 13));
            WiredInternalVariableObserver.observe(f.room);
            assertEquals(WiredVariableMutation.Kind.VALUE_CHANGED, seen.get(1).kind());
            values.set(Map.of());
            WiredInternalVariableObserver.record(f.room, WiredInternalVariableObserver.transition(
                    "internal:1:@handitem", WiredVariableHolder.user(77), 13, null, 1, 0));
            WiredInternalVariableObserver.observe(f.room);
            assertEquals(2, seen.size());
            values.set(Map.of("@handitem", 14));
            WiredInternalVariableObserver.observe(f.room);
            values.set(Map.of());
            WiredInternalVariableObserver.observe(f.room);
            assertEquals(WiredVariableMutation.Kind.DELETED, seen.get(3).kind());
        }
    }

    @Test
    void roomsWithoutInternalSubscriptionsDoNotScanFurnitureOrUnits() {
        Fixture f = new Fixture();
        WiredInternalVariableObserver.observe(f.room);
        verify(f.room, never()).getFloorItems();
        verify(f.room, never()).getWallItems();
        verify(f.room, never()).getRoomUnits();
    }

    @Test
    void sharedSourceNotifiesSubscribedRemoteAliasesOnceFromEitherMutationDirection() {
        Fixture source = new Fixture(5);
        Fixture consumer = new Fixture(6);
        source.definition(1, WiredVariableType.USER, 11, true);
        consumer.definition(2, WiredVariableType.USER, 11, true);
        var reference = mock(WiredVariableReference.class);
        when(reference.getId()).thenReturn(2);
        when(reference.getType()).thenReturn(WiredVariableType.REFERENCE);
        when(reference.sourceId()).thenReturn("room:1");
        consumer.types.addVariable(reference);
        for (int id : new int[] {20, 21}) {
            var trigger = mock(WiredTriggerVariableChanged.class);
            when(trigger.getId()).thenReturn(id);
            when(trigger.getType()).thenReturn(WiredTriggerType.VARIABLE_CHANGED);
            when(trigger.selectedVariableId()).thenReturn("room:2");
            consumer.types.addTrigger(trigger);
        }
        var rooms = source.environment.getRoomManager();
        when(rooms.getRoom(6)).thenReturn(consumer.room);
        when(rooms.getActiveRooms()).thenReturn(new ArrayList<>(List.of(source.room, consumer.room)));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(source.environment);
            var change = new WiredVariableMutation("room:1", WiredVariableHolder.user(77), 10, 20,
                    WiredVariableMutation.Kind.VALUE_CHANGED, 1);
            var outward = WiredVariableAliasOperations.notificationsAcrossRooms(source.room, change);
            assertEquals(List.of("5:room:1", "6:room:2"), outward.stream()
                    .map(event -> event.room().getId() + ":" + event.mutation().variableId()).toList());
            var aliasChange = new WiredVariableMutation("room:2", change.holder(), 10, 20,
                    WiredVariableMutation.Kind.VALUE_CHANGED, 1);
            var inward = WiredVariableAliasOperations.notificationsAcrossRooms(consumer.room, aliasChange);
            assertEquals(List.of("5:room:1", "6:room:2"), inward.stream()
                    .map(event -> event.room().getId() + ":" + event.mutation().variableId()).toList());
        }
    }

    @Test
    void creatorMutationCannotWriteAnInvisibleDefinitionThroughCraftedRequests() {
        Fixture f = new Fixture();
        var hidden = WiredVariableDefinition.create(5, 1, WiredVariableType.FURNI,
                "hidden", 1, true, true);
        assertTrue(f.manager.registerDefinition(hidden));
        var holder = WiredVariableHolder.furni(42);
        assertEquals(WiredVariableManager.MutationResult.INVALID,
                WiredVariableMenuMutation.apply(f.room, holder, hidden.variableId(), 12, 1, false, null));
        assertNull(f.manager.get(hidden.variableId(), holder));
    }

    @Test
    void durableCapabilityPolicyPreservesUnloadedWritableAliasesAndRejectsReadOnlyAndValuelessWrites() {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var origin = fixture.room(99);
            var source = fixture.room(100);
            var sourceVariable = fixture.user(source, 50, true, 11);
            var reference = new WiredVariableReference(51, 1, null, "0", 0, 0);
            reference.setRoomId(99);
            origin.types().addVariable(reference);
            assertTrue(reference.saveData(WiredAliasRegressionFixture.settings(new int[] {0}, "reference", "room:50")));
            origin.types().refreshVariableDefinition(reference);
            var edge = fixture.entries.get(51);
            var terminal = new WiredCrossRoomAliasRepository.Entry(50, 100, "room:50",
                    WiredVariableType.USER, 1, "source", 11, true, 1, 0, null, null, false, 1000);
            when(fixture.registry.loadByAlias(99, "room:51")).thenReturn(edge);
            when(fixture.registry.loadByAlias(100, "room:50")).thenReturn(terminal);
            fixture.active.remove(source.room());
            when(fixture.rooms.getRoom(100)).thenReturn(null);
            assertTrue(WiredVariableAliasOperations.canCreateDelete(origin.room(), "room:51"));
            assertTrue(WiredVariableAliasOperations.canWriteValue(origin.room(), "room:51"));
            assertEquals(WiredVariableManager.MutationResult.INVALID, WiredVariableMenuMutation.apply(
                    origin.room(), WiredVariableHolder.user(77), "room:51", 12, 1, true, null));
            when(fixture.registry.loadByAlias(100, "room:50")).thenReturn(
                    new WiredCrossRoomAliasRepository.Entry(50, 100, "room:50", WiredVariableType.USER,
                            1, "source", 11, false, 1, 0, null, null, false, 1000));
            assertFalse(WiredVariableAliasOperations.canWriteValue(origin.room(), "room:51"));
            assertTrue(WiredVariableAliasOperations.canCreateDelete(origin.room(), "room:51"));
            when(fixture.registry.loadByAlias(99, "room:51")).thenReturn(
                    new WiredCrossRoomAliasRepository.Entry(edge.itemId(), edge.roomId(), edge.variableId(),
                            edge.type(), edge.scope(), edge.name(), edge.availability(), edge.hasValue(),
                            edge.version(), edge.hash(), edge.sourceRoomId(), edge.sourceVariableId(), true, edge.updatedAt()));
            assertFalse(WiredVariableAliasOperations.canCreateDelete(origin.room(), "room:51"));
            assertFalse(WiredVariableAliasOperations.canWriteValue(origin.room(), "room:51"));
        }
    }

    static final class Fixture {
        final Room room = mock(Room.class);
        final WiredVariableManager manager;
        final RoomSpecialTypes types;
        final GameEnvironment environment = mock(GameEnvironment.class);
        Fixture() { this(5); }
        Fixture(int roomId) {
            var repository = mock(WiredVariableManager.Repository.class);
            when(repository.load(roomId)).thenReturn(new WiredVariableManager.LoadedRoom(true, 0, Map.of(), List.of()));
            when(repository.commit(anyInt(), anyLong(), anyLong(), any())).thenReturn(true);
            manager = WiredVariableManager.forTests(roomId, repository, () -> true, () -> 1_000L);
            types = new RoomSpecialTypes(manager);
            when(room.getId()).thenReturn(roomId);
            when(room.getRoomSpecialTypes()).thenReturn(types);
            var rooms = mock(RoomManager.class);
            when(environment.getRoomManager()).thenReturn(rooms);
            when(rooms.getRoom(roomId)).thenReturn(room);
            when(rooms.getActiveRooms()).thenReturn(new ArrayList<>(List.of(room)));
        }
        WiredVariableDefinition definition(int id, WiredVariableType type, int availability, boolean hasValue) {
            var definition = WiredVariableDefinition.create(manager.roomId(), id, type, "variable" + id, availability, hasValue, false);
            assertTrue(manager.registerDefinition(definition));
            return definition;
        }
        void echo(int id, String source) {
            var echo = mock(WiredVariableEcho.class);
            when(echo.getId()).thenReturn(id);
            when(echo.getType()).thenReturn(WiredVariableType.ECHO);
            when(echo.sourceId()).thenReturn(source);
            types.addVariable(echo);
        }
    }
}
