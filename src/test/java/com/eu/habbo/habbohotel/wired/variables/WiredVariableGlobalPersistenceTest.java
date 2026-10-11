package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableGlobal;
import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableGlobalPersistenceTest {
    @Test
    void saveNormalizesNameAndRejectsUnsupportedOrExpandedPayloads() throws Exception {
        WiredVariableGlobal variable = managedVariable(41, 7, new ManagerRepository());

        assertTrue(variable.saveData(settings("  My   Counter  ", 1)));
        assertEquals("__my___counter__", variable.getVariableName());
        assertEquals(WiredVariableAvailability.ROOM_ACTIVE, variable.getAvailability());
        assertEquals("{\"name\":\"__my___counter__\",\"persistence\":1}", variable.getWiredData());

        assertFalse(variable.saveData(settings("counter", 9)));
        assertFalse(variable.saveData(settings("this_name_is_deliberately_more_than_forty_characters", 1)));
        assertTrue(variable.saveData(settings("Bad-Name", 1)));
        assertEquals("bad-name", variable.getVariableName());
        assertFalse(variable.saveData(settings("bad\nname", 1)));

        WiredSettingsV2 expanded = new WiredSettingsV2(
                new int[] {1}, "counter", new int[] {99}, new int[0],
                new String[0], new int[0], new int[0], 0, 0, false, false);
        assertFalse(variable.saveData(expanded));
        assertEquals("bad-name", variable.getVariableName());
    }

    @Test
    void persistentValueAndTimestampsSurviveReloadThenPickupRemovesManagedState() throws Exception {
        ManagerRepository repository = new ManagerRepository();
        WiredVariableGlobal variable = managedVariable(42, 81, repository);

        assertTrue(variable.saveData(settings("score", 10)));
        assertTrue(variable.setValue(17));
        assertEquals(1, repository.values.size());
        long createdAt = repository.values.get(0).createdAtMs();
        assertTrue(createdAt > 0L);
        assertEquals(17, repository.values.get(0).value());

        WiredVariableGlobal reloaded = variable(42, 81);
        reloaded.loadWiredData(resultSet(variable.getWiredData()), null);
        reloaded.bindManager(manager(81, repository));
        assertEquals("score", reloaded.getVariableName());
        assertEquals(WiredVariableAvailability.PERMANENT, reloaded.getAvailability());
        assertEquals(17, reloaded.getValue());
        assertEquals(repository.values.get(0).createdAtMs(), reloaded.getCreatedAtMs());
        assertEquals(repository.values.get(0).updatedAtMs(), reloaded.getUpdatedAtMs());

        reloaded.onPickUp();
        assertTrue(repository.values.isEmpty());
        assertTrue(repository.definitionHashes.isEmpty());
        assertEquals("", reloaded.getVariableName());
        assertEquals(0, reloaded.getValue());
    }

    @Test
    void roomActiveValueResetsOnRoomReloadAndPersistenceSwitchRemovesStoredRow() throws Exception {
        ManagerRepository repository = new ManagerRepository();
        WiredVariableGlobal variable = managedVariable(43, 82, repository);

        assertTrue(variable.saveData(settings("round_score", 11)));
        assertTrue(variable.setValue(25));
        assertTrue(variable.saveData(settings("round_score", 1)));
        assertTrue(repository.values.isEmpty());
        assertEquals(25, variable.getValue());

        WiredVariableGlobal reloaded = variable(43, 82);
        reloaded.loadWiredData(resultSet(variable.getWiredData()), null);
        reloaded.bindManager(manager(82, repository));
        assertEquals(WiredVariableAvailability.ROOM_ACTIVE, reloaded.getAvailability());
        assertEquals(0, reloaded.getValue());
        assertEquals(0L, reloaded.getCreatedAtMs());
    }

    @Test
    void missingPermanentRowKeepsImplicitZeroUntilFirstAtomicWrite() throws Exception {
        ManagerRepository repository = new ManagerRepository();
        WiredVariableGlobal variable = variable(44, 83);

        variable.loadWiredData(resultSet("{\"name\":\"visits\",\"persistence\":10}"), null);
        variable.bindManager(manager(83, repository));

        assertTrue(repository.values.isEmpty());
        assertEquals(0, variable.getValue());
        assertTrue(variable.setValue(0));
        assertEquals(1, repository.values.size());
        assertEquals(0, repository.values.get(0).value());
    }

    @Test
    void switchingRoomActiveValueToPermanentPersistsSameManagerValue() throws Exception {
        ManagerRepository repository = new ManagerRepository();
        WiredVariableGlobal variable = managedVariable(47, 86, repository);
        assertTrue(variable.saveData(settings("round_total", 1)));
        assertTrue(variable.setValue(13));
        assertTrue(repository.values.isEmpty());

        assertTrue(variable.saveData(settings("round_total", 10)));
        assertEquals(1, repository.values.size());
        assertEquals(13, repository.values.get(0).value());

        WiredVariableGlobal reloaded = variable(47, 86);
        reloaded.loadWiredData(resultSet(variable.getWiredData()), null);
        reloaded.bindManager(manager(86, repository));
        assertEquals(13, reloaded.getValue());
    }

    @Test
    void earlyAvailabilityJsonKeyRemainsReadable() throws Exception {
        WiredVariableGlobal variable = variable(45, 84);

        variable.loadWiredData(resultSet("{\"name\":\"legacy-name\",\"availability\":1}"), null);

        assertEquals("legacy-name", variable.getVariableName());
        assertEquals(WiredVariableAvailability.ROOM_ACTIVE, variable.getAvailability());
    }

    @Test
    void globalReadsTheManagerSourceOfTruthAfterExternalAtomicMutation() throws Exception {
        ManagerRepository repository = new ManagerRepository();
        WiredVariableManager manager = manager(85, repository);
        WiredVariableGlobal variable = variable(46, 85);
        variable.bindManager(manager);
        assertTrue(variable.saveData(settings("shared_counter", 10)));

        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set("room:46", WiredVariableHolder.room(), 44));
        assertEquals(44, variable.getValue());
        assertEquals(repository.values.get(0).updatedAtMs(), variable.getUpdatedAtMs());
    }

    private static WiredVariableGlobal variable(int id, int roomId) throws Exception {
        WiredVariableGlobal variable = new WiredVariableGlobal(id, 1, baseItem(), "0", 0, 0);
        variable.setRoomId(roomId);
        return variable;
    }

    private static WiredVariableGlobal managedVariable(int id, int roomId,
                                                       ManagerRepository repository) throws Exception {
        WiredVariableGlobal variable = variable(id, roomId);
        variable.bindManager(manager(roomId, repository));
        return variable;
    }

    private static WiredVariableManager manager(int roomId, ManagerRepository repository) {
        AtomicLong clock = new AtomicLong(1_000L);
        return WiredVariableManager.forTests(roomId, repository, () -> true, clock::incrementAndGet);
    }

    private static Item baseItem() throws Exception {
        Constructor<Item> constructor = Item.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Item item = constructor.newInstance();
        Field sprite = Item.class.getDeclaredField("spriteId");
        sprite.setAccessible(true);
        sprite.setInt(item, 700);
        return item;
    }

    private static WiredSettingsV2 settings(String name, int availability) {
        return new WiredSettingsV2(
                new int[] {availability}, name, new int[0], new int[0],
                new String[0], new int[0], new int[0], 0, 0, false, false);
    }

    private static ResultSet resultSet(String wiredData) {
        return (ResultSet) Proxy.newProxyInstance(
                WiredVariableGlobalPersistenceTest.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, method, arguments) -> {
                    if ("getString".equals(method.getName())) {
                        return wiredData;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == float.class) return 0F;
                    if (returnType == double.class) return 0D;
                    return null;
                });
    }

    private static final class ManagerRepository implements WiredVariableManager.Repository {
        private long revision;
        private final Map<String, Integer> definitionHashes = new HashMap<>();
        private final List<WiredVariableValue> values = new ArrayList<>();

        @Override
        public WiredVariableManager.LoadedRoom load(int roomId) {
            return new WiredVariableManager.LoadedRoom(
                    true, this.revision, this.definitionHashes, this.values);
        }

        @Override
        public boolean commit(int roomId, long expectedRevision, long nextRevision,
                              WiredVariableManager.PersistenceMutation mutation) {
            if (this.revision != expectedRevision || nextRevision != expectedRevision + 1L) {
                return false;
            }
            switch (mutation.kind) {
                case REVISION_ONLY -> { }
                case UPSERT_DEFINITION -> {
                    this.definitionHashes.put(mutation.variableId, mutation.definitionHash);
                    this.values.removeIf(value -> value.variableId().equals(mutation.variableId)
                            && (!mutation.retainValues
                            || mutation.allowedScopeCode == null
                            || value.holder().scope().code != mutation.allowedScopeCode));
                    for (WiredVariableValue retained : mutation.values) {
                        put(retained);
                    }
                }
                case DELETE_DEFINITION -> {
                    this.definitionHashes.remove(mutation.variableId);
                    this.values.removeIf(value -> value.variableId().equals(mutation.variableId));
                }
                case UPSERT_VALUE -> put(mutation.value);
                case DELETE_VALUE -> this.values.removeIf(value ->
                        value.variableId().equals(mutation.variableId)
                                && value.holder().equals(mutation.holder));
                case DELETE_HOLDER -> this.values.removeIf(value -> value.holder().equals(mutation.holder));
                case RECONCILE -> {
                    this.definitionHashes.clear();
                    this.definitionHashes.putAll(mutation.definitionHashes);
                    this.values.clear();
                    this.values.addAll(mutation.values);
                }
            }
            this.revision = nextRevision;
            return true;
        }

        @Override
        public boolean deleteRoom(int roomId) {
            this.definitionHashes.clear();
            this.values.clear();
            this.revision = 0L;
            return true;
        }

        private void put(WiredVariableValue replacement) {
            this.values.removeIf(value -> value.variableId().equals(replacement.variableId())
                    && value.holder().equals(replacement.holder()));
            this.values.add(replacement);
        }
    }
}
