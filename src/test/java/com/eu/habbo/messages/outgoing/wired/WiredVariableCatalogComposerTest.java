package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMetadata;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableCatalogComposerTest {
    @Test
    void writesExactHashAndDiffBodies() {
        ByteBuf hash = new WiredAllVariablesHashMessageComposer(0x12345678).compose().get();
        try {
            int declaredLength = hash.readInt();
            assertEquals(hash.readableBytes(), declaredLength);
            assertEquals(Outgoing.WiredAllVariablesHashMessageComposer, hash.readUnsignedShort());
            assertEquals(0x12345678, hash.readInt());
            assertEquals(0, hash.readableBytes());
        } finally {
            hash.release();
        }

        WiredVariableDefinition definition = WiredVariableDefinition.create(
                99, 52, WiredVariableType.ROOM, "room_count",
                WiredVariableAvailability.ROOM_ACTIVE, false);
        WiredVariableManager.VariableSnapshot changed =
                new WiredVariableManager.VariableSnapshot(definition, List.of(), 321);
        List<WiredAllVariablesDiffMessageComposer> chunks =
                WiredAllVariablesDiffMessageComposer.chunks(
                        catalog(654, List.of(changed)), Map.of("removed", -1));
        assertEquals(1, chunks.size());
        ByteBuf packet = chunks.get(0).compose().get();
        try {
            int declaredLength = packet.readInt();
            assertEquals(packet.readableBytes(), declaredLength);
            assertEquals(Outgoing.WiredAllVariablesDiffMessageComposer, packet.readUnsignedShort());
            assertEquals(654, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(1, packet.readInt());
            assertEquals("removed", readString(packet));
            assertEquals(1, packet.readInt());
            assertEquals(321, packet.readInt());
            assertEquals("room:52", readString(packet));
            assertEquals(0, packet.readInt());
            assertEquals("room_count", readString(packet));
            assertEquals(1, packet.readInt());
            assertEquals(-10, packet.readInt());
            assertTrue(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    @Test
    void roomVisibleDefinitionsConvergeAcrossBoundedChunks() {
        WiredVariableDefinition furni = definition(71, WiredVariableType.FURNI, 10);
        WiredVariableDefinition user = definition(72, WiredVariableType.USER, 11);
        WiredVariableDefinition room = definition(73, WiredVariableType.ROOM, 1);
        List<WiredVariableManager.VariableSnapshot> changed = List.of(
                new WiredVariableManager.VariableSnapshot(furni, List.of(), 701),
                new WiredVariableManager.VariableSnapshot(user, List.of(), 702),
                new WiredVariableManager.VariableSnapshot(room, List.of(), 703));
        List<String> removed = new ArrayList<>();
        for (int index = 0; index < WiredAllVariablesDiffMessageComposer.MAX_ENTRIES_PER_CHUNK; index++) {
            removed.add("stale:" + index);
        }
        removed.add("obsolete");

        Map<String, Integer> clientCache = new LinkedHashMap<>();
        for (String id : removed) {
            clientCache.put(id, -1);
        }
        List<WiredAllVariablesDiffMessageComposer> chunks =
                WiredAllVariablesDiffMessageComposer.chunks(catalog(9001, changed), clientCache);
        assertEquals(2, chunks.size());
        List<DecodedChunk> decoded = new ArrayList<>();
        for (WiredAllVariablesDiffMessageComposer composer : chunks) {
            decoded.add(decode(composer));
        }
        assertFalse(decoded.get(0).last());
        assertTrue(decoded.get(1).last());
        assertEquals(9001, decoded.get(0).aggregateHash());
        assertEquals(9001, decoded.get(1).aggregateHash());

        for (DecodedChunk chunk : decoded) {
            chunk.removed().forEach(clientCache::remove);
            chunk.updated().forEach(clientCache::put);
        }
        assertEquals(Map.of(furni.variableId(), 701, user.variableId(), 702, room.variableId(), 703),
                clientCache);
        assertEquals(List.of(0, 1, -10), decoded.get(1).targets());
    }

    private static WiredVariableDefinition definition(int itemId, WiredVariableType type,
                                                       int availability) {
        return WiredVariableDefinition.create(99, itemId, type,
                "variable_" + itemId, availability, false);
    }

    private static WiredVariableCatalog.Catalog catalog(
            int aggregateHash, List<WiredVariableManager.VariableSnapshot> variables) {
        Map<String, WiredVariableCatalog.Entry> entries = new LinkedHashMap<>();
        for (WiredVariableManager.VariableSnapshot variable : variables) {
            entries.put(variable.definition().variableId(), new WiredVariableCatalog.Entry(
                    variable.definition().variableId(), variable.hash(),
                    WiredVariableMetadata.fromDefinition(variable.definition())));
        }
        return new WiredVariableCatalog.Catalog(aggregateHash, entries);
    }

    private static DecodedChunk decode(WiredAllVariablesDiffMessageComposer composer) {
        ByteBuf packet = composer.compose().get();
        try {
            int declaredLength = packet.readInt();
            assertEquals(packet.readableBytes(), declaredLength);
            assertEquals(Outgoing.WiredAllVariablesDiffMessageComposer, packet.readUnsignedShort());
            int aggregateHash = packet.readInt();
            boolean last = packet.readBoolean();
            int removedCount = packet.readInt();
            List<String> removed = new ArrayList<>();
            for (int index = 0; index < removedCount; index++) {
                removed.add(readString(packet));
            }
            int updatedCount = packet.readInt();
            Map<String, Integer> updated = new LinkedHashMap<>();
            List<Integer> targets = new ArrayList<>();
            for (int index = 0; index < updatedCount; index++) {
                int hash = packet.readInt();
                String id = readString(packet);
                packet.readInt(); // variableType
                readString(packet); // variableName
                packet.readInt(); // availability
                targets.add(packet.readInt());
                for (int field = 0; field < 9; field++) {
                    packet.readBoolean();
                }
                updated.put(id, hash);
            }
            assertEquals(0, packet.readableBytes());
            return new DecodedChunk(aggregateHash, last, removed, updated, targets);
        } finally {
            packet.release();
        }
    }

    private record DecodedChunk(int aggregateHash, boolean last, List<String> removed,
                                Map<String, Integer> updated, List<Integer> targets) {
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
