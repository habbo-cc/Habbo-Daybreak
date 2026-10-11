package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WiredVariableCatalogRegressionTest {
    @Test
    void storedPresenceAndCreationDoNotDependOnNumericValues() {
        for (WiredVariableType type : List.of(WiredVariableType.FURNI, WiredVariableType.USER)) {
            for (boolean hasValue : List.of(false, true)) {
                Metadata decoded = decode(WiredVariableMetadata.fromDefinition(
                        definition(51, type, hasValue)));
                assertFalse(decoded.alwaysAvailable());
                assertTrue(decoded.canCreateAndDelete());
                assertEquals(hasValue, decoded.hasValue());
                assertEquals(hasValue, decoded.canWriteValue());
                assertTrue(decoded.intercept());
            }
        }
        Metadata global = decode(WiredVariableMetadata.fromDefinition(definition(52, WiredVariableType.ROOM, true)));
        assertTrue(global.alwaysAvailable());
        assertFalse(global.canCreateAndDelete());
        assertTrue(global.canWriteValue());
    }

    @Test
    void generatedChildrenUseDynamicCategoryAndParentPresence() {
        Metadata child = decode(WiredVariableMetadata.generated("room:51.value.seconds", "seconds",
                definition(51, WiredVariableType.USER, true)));
        assertEquals(3, child.type());
        assertFalse(child.alwaysAvailable());
        assertFalse(child.canCreateAndDelete());
        assertFalse(child.canWriteValue());
        Metadata globalChild = decode(WiredVariableMetadata.generated("room:52.value.seconds", "seconds",
                definition(52, WiredVariableType.ROOM, true)));
        assertTrue(globalChild.alwaysAvailable());
    }

    @Test
    void referenceCapabilitiesAndHashFollowResolvedReadOnlyEdges() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = new RoomSpecialTypes();
        when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredVariableReference item = new WiredVariableReference(60, 1, null, "0", 0, 0);
        types.addVariable(item);
        WiredVariableDefinition alias = definition(60, WiredVariableType.USER, true);
        WiredVariableDefinition source = definition(61, WiredVariableType.USER, true);
        WiredVariableManager manager = mock(WiredVariableManager.class);
        when(manager.roomId()).thenReturn(99);
        when(room.getId()).thenReturn(99);
        try (var operations = mockStatic(WiredVariableAliasOperations.class)) {
            operations.when(() -> WiredVariableAliasOperations.resolve(room, alias.variableId()))
                    .thenReturn(new WiredVariableAliasOperations.Resolved(manager, source.variableId(), source, false));
            WiredVariableMetadata writable = WiredVariableMetadata.fromDefinition(room, alias);
            Metadata writableDecoded = decode(writable);
            assertEquals(2, writableDecoded.type());
            assertTrue(writableDecoded.canCreateAndDelete());
            assertTrue(writableDecoded.canWriteValue());
            operations.when(() -> WiredVariableAliasOperations.resolve(room, alias.variableId()))
                    .thenReturn(new WiredVariableAliasOperations.Resolved(manager, source.variableId(), source, true));
            WiredVariableMetadata readOnly = WiredVariableMetadata.fromDefinition(room, alias);
            Metadata readOnlyDecoded = decode(readOnly);
            assertFalse(readOnlyDecoded.canCreateAndDelete());
            assertFalse(readOnlyDecoded.canWriteValue());
            assertFalse(readOnlyDecoded.alwaysAvailable());
            assertEquals(alias.variableId(), readOnlyDecoded.id());
            assertNotEquals(writable.catalogHash(), readOnly.catalogHash());
            operations.when(() -> WiredVariableAliasOperations.resolve(room, alias.variableId())).thenReturn(null);
            Metadata unavailable = decode(WiredVariableMetadata.fromDefinition(room, alias));
            assertFalse(unavailable.canCreateAndDelete());
            assertFalse(unavailable.canWriteValue());
            assertFalse(unavailable.intercept());
            WiredVariableDefinition hidden = WiredVariableDefinition.create(99, 61,
                    WiredVariableType.USER, "hidden_source", 0, true, true);
            operations.when(() -> WiredVariableAliasOperations.resolve(room, alias.variableId()))
                    .thenReturn(new WiredVariableAliasOperations.Resolved(manager, hidden.variableId(), hidden, true));
            var snapshot = new WiredVariableManager.Snapshot(true, 0, 0, Map.of(alias.variableId(),
                    new WiredVariableManager.VariableSnapshot(alias, List.of(), 1)));
            assertFalse(WiredVariableCatalog.build(room, snapshot).entries().containsKey(alias.variableId()));
        }
    }

    @Test
    void contextAndQuestDefinitionsReachCatalogAndHashChangesOnSaveAndPickup() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = new RoomSpecialTypes();
        when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredVariableContext context = new WiredVariableContext(63, 1, null, "0", 0, 0);
        types.addVariable(context);
        WiredVariableDefinition quest = definition(64, WiredVariableType.QUEST, true);
        WiredVariableDefinition chain = definition(65, WiredVariableType.QUEST_CHAIN, true);
        Map<String, WiredVariableManager.VariableSnapshot> definitions = Map.of(
                quest.variableId(), new WiredVariableManager.VariableSnapshot(quest, List.of(), 1),
                chain.variableId(), new WiredVariableManager.VariableSnapshot(chain, List.of(), 2));
        var snapshot = new WiredVariableManager.Snapshot(true, 1, 1, definitions);
        var emptyContext = WiredVariableCatalog.build(room, snapshot);
        assertFalse(emptyContext.entries().containsKey(context.variableId()));
        assertTrue(emptyContext.entries().containsKey(quest.variableId()));
        assertTrue(emptyContext.entries().containsKey(chain.variableId()));
        assertTrue(context.saveData(settings("context_count", true)));
        var populated = WiredVariableCatalog.build(room, snapshot);
        Metadata metadata = decode(populated.entries().get(context.variableId()).metadata());
        assertEquals(-20, metadata.target());
        assertFalse(metadata.alwaysAvailable());
        assertTrue(metadata.canCreateAndDelete());
        assertTrue(metadata.canWriteValue());
        assertFalse(metadata.intercept());
        assertNotEquals(emptyContext.aggregateHash(), populated.aggregateHash());
        assertTrue(context.saveData(settings("renamed_context", false)));
        var renamed = WiredVariableCatalog.build(room, snapshot);
        Metadata valueless = decode(renamed.entries().get(context.variableId()).metadata());
        assertTrue(valueless.canCreateAndDelete());
        assertFalse(valueless.canWriteValue());
        assertNotEquals(populated.aggregateHash(), renamed.aggregateHash());
        types.removeVariable(context);
        assertEquals(emptyContext.aggregateHash(), WiredVariableCatalog.build(room, snapshot).aggregateHash());
        assertEquals(0, WiredVariableCatalog.build(room, null).aggregateHash());
    }

    @Test
    void internalInterceptionMatchesObservableOrWritableContract() {
        for (var definition : WiredInternalVariableRuntime.definitions()) {
            assertEquals(definition.writable() || definition.runtimeObservable(),
                    decode(WiredVariableMetadata.internal(definition)).intercept());
        }
    }

    private static WiredVariableDefinition definition(int id, WiredVariableType type, boolean value) {
        int availability = switch (type) { case USER, QUEST, QUEST_CHAIN -> 0; default -> 1; };
        return WiredVariableDefinition.create(99, id, type, "value_" + id, availability, value, false);
    }

    private static WiredSettingsV2 settings(String name, boolean value) {
        return new WiredSettingsV2(new int[]{value ? 1 : 0}, name, new int[0], new int[0],
                new String[0], new int[0], new int[0], 0, 0, false, false);
    }

    private static Metadata decode(WiredVariableMetadata metadata) {
        ServerMessage message = new ServerMessage(1);
        metadata.serialize(message);
        ByteBuf packet = message.get();
        try {
            packet.skipBytes(6);
            String id = string(packet);
            int type = packet.readInt();
            string(packet);
            packet.readInt();
            int target = packet.readInt();
            boolean always = packet.readBoolean(), create = packet.readBoolean(), value = packet.readBoolean();
            boolean write = packet.readBoolean(), intercept = packet.readBoolean();
            packet.skipBytes(3); // visibility and timestamp capabilities
            assertFalse(packet.readBoolean()); // connector values absent in these fixtures
            assertEquals(0, packet.readableBytes());
            return new Metadata(id, type, target, always, create, value, write, intercept);
        } finally { packet.release(); }
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8).toString();
    }
    private record Metadata(String id, int type, int target, boolean alwaysAvailable,
                            boolean canCreateAndDelete, boolean hasValue, boolean canWriteValue,
                            boolean intercept) { }
}
