package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real variable managers and room indexes; only emulator/database boundaries are replaced. */
final class WiredAliasRegressionFixture implements AutoCloseable {
    final GameEnvironment environment = mock(GameEnvironment.class);
    final RoomManager rooms = mock(RoomManager.class);
    final WiredCrossRoomAliasRepository registry = mock(WiredCrossRoomAliasRepository.class);
    final Map<Integer, WiredCrossRoomAliasRepository.Entry> entries = new HashMap<>();
    final List<Room> active = new ArrayList<>();
    private final MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
    private final MockedStatic<WiredCrossRoomAliasRepository> aliases = mockStatic(WiredCrossRoomAliasRepository.class);

    WiredAliasRegressionFixture() {
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        aliases.when(WiredCrossRoomAliasRepository::instance).thenReturn(registry);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getActiveRooms()).thenAnswer(call -> new ArrayList<>(active));
        when(registry.syncSourceDefinition(any())).thenReturn(true);
        when(registry.upsert(any())).thenAnswer(call -> {
            WiredCrossRoomAliasRepository.Entry entry = call.getArgument(0);
            entries.put(entry.itemId(), entry);
            return true;
        });
        when(registry.loadByItem(anyInt())).thenAnswer(call -> entries.get(call.getArgument(0)));
        when(registry.loadByAlias(anyInt(), anyString())).thenAnswer(call -> entries.values().stream()
                .filter(entry -> entry.roomId() == (int) call.getArgument(0)
                        && entry.variableId().equals(call.getArgument(1)))
                .findFirst().orElse(null));
        when(registry.delete(anyInt())).thenAnswer(call -> entries.remove(call.getArgument(0)) != null);
    }

    State room(int id) {
        Room room = mock(Room.class);
        WiredVariableManager manager = WiredVariableManager.forTests(id, new MemoryRepository(), () -> true, () -> 1000L);
        RoomSpecialTypes types = new RoomSpecialTypes(manager);
        when(room.getId()).thenReturn(id);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        when(rooms.getRoom(id)).thenReturn(room);
        active.add(room);
        return new State(room, manager, types);
    }

    WiredVariableUser user(State state, int id, boolean value, int availability) {
        WiredVariableUser user = new WiredVariableUser(id, 1, null, "0", 0, 0);
        user.setRoomId(state.room().getId());
        state.types().addVariable(user);
        assertTrue(user.saveData(settings(new int[]{availability, value ? 1 : 0}, "source_" + id)));
        state.types().refreshVariableDefinition(user);
        return user;
    }

    static WiredSettingsV2 settings(int[] ints, String name, String... ids) {
        return new WiredSettingsV2(ints, name, new int[0], new int[0], ids,
                new int[0], new int[0], 0, 0, false, false);
    }

    @Override public void close() { aliases.close(); emulator.close(); }
    record State(Room room, WiredVariableManager manager, RoomSpecialTypes types) { }

    private static final class MemoryRepository implements WiredVariableManager.Repository {
        private long revision;
        private final Map<String, Integer> hashes = new HashMap<>();
        private final Map<String, WiredVariableValue> values = new HashMap<>();
        @Override public WiredVariableManager.LoadedRoom load(int id) {
            return new WiredVariableManager.LoadedRoom(true, revision, hashes, new ArrayList<>(values.values()));
        }
        @Override public boolean commit(int id, long expected, long next, WiredVariableManager.PersistenceMutation mutation) {
            if (revision != expected || next != expected + 1) return false;
            switch (mutation.kind) {
                case UPSERT_DEFINITION -> hashes.put(mutation.variableId, mutation.definitionHash);
                case DELETE_DEFINITION -> {
                    hashes.remove(mutation.variableId);
                    values.values().removeIf(v -> v.variableId().equals(mutation.variableId));
                }
                case UPSERT_VALUE -> values.put(key(mutation.value.variableId(), mutation.value.holder()), mutation.value);
                case DELETE_VALUE -> values.remove(key(mutation.variableId, mutation.holder));
                case DELETE_HOLDER -> values.values().removeIf(v -> v.holder().equals(mutation.holder));
                case RECONCILE -> {
                    hashes.clear(); hashes.putAll(mutation.definitionHashes); values.clear();
                    mutation.values.forEach(v -> values.put(key(v.variableId(), v.holder()), v));
                }
                case REVISION_ONLY -> { }
            }
            revision = next;
            return true;
        }
        @Override public boolean deleteRoom(int id) { hashes.clear(); values.clear(); revision = 0; return true; }
        private String key(String id, WiredVariableHolder holder) { return id + ":" + holder.canonicalId(); }
    }
}
