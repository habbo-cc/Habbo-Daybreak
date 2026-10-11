package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.WiredVariableAvailability;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredVariableManagerTest {
    @Test
    void exactCoreAvailabilityAndStableHolderIdentityAreEnforced() {
        assertEquals("furni:42", WiredVariableHolder.furni(42).canonicalId());
        assertEquals("user:9", WiredVariableHolder.user(9).canonicalId());
        assertEquals("room:0", WiredVariableHolder.room().canonicalId());
        assertEquals(WiredVariableHolder.furni(-1),
                WiredVariableHolder.of(WiredVariableHolder.Scope.FURNI, -1));
        assertThrows(IllegalArgumentException.class, () -> WiredVariableHolder.user(0));

        assertEquals(0, definition(1, WiredVariableType.USER, 0, false).availabilityCode());
        assertEquals(10, definition(2, WiredVariableType.FURNI, 10, false).availabilityCode());
        assertEquals(11, definition(3, WiredVariableType.ROOM, 11, false).availabilityCode());
        assertEquals(0, definition(4, WiredVariableType.CONTEXT, 0, false).availabilityCode());
        assertThrows(IllegalArgumentException.class,
                () -> definition(5, WiredVariableType.FURNI, 11, false));
        assertThrows(IllegalArgumentException.class,
                () -> definition(6, WiredVariableType.USER, 1, false));
    }

    @Test
    void gateIsFailClosedAndNoOpDoesNotAdvanceRevision() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, false);
        WiredVariableDefinition definition = definition(10, WiredVariableType.ROOM, 10, false);

        assertTrue(manager.registerDefinition(definition));
        long registeredRevision = manager.revision();
        assertEquals(WiredVariableManager.MutationResult.UNAVAILABLE,
                manager.set(definition.variableId(), WiredVariableHolder.room(), 1));
        assertEquals(registeredRevision, manager.revision());
        assertFalse(manager.publicSnapshot().available());
        assertNull(manager.get(definition.variableId(), WiredVariableHolder.room()));
    }

    @Test
    void signedUpdatesAreAtomicAndRejectOverflow() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(11, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));

        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(definition.variableId(), WiredVariableHolder.room(), Integer.MAX_VALUE));
        long revision = manager.revision();
        assertEquals(WiredVariableManager.MutationResult.OVERFLOW,
                manager.add(definition.variableId(), WiredVariableHolder.room(), 1));
        assertEquals(Integer.MAX_VALUE,
                manager.get(definition.variableId(), WiredVariableHolder.room()).value());
        assertEquals(revision, manager.revision());
        assertEquals(WiredVariableManager.MutationResult.UNCHANGED,
                manager.set(definition.variableId(), WiredVariableHolder.room(), Integer.MAX_VALUE));
        assertEquals(revision, manager.revision());
    }

    @Test
    void dispatchReceiptExistsOnlyAfterCommitAndRunsOutsideManagerMonitor() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(111, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));

        AtomicReference<WiredVariableMutation> dispatched = new AtomicReference<>();
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.setAndDispatch(definition.variableId(), WiredVariableHolder.room(), 9, mutation -> {
                    assertFalse(Thread.holdsLock(manager));
                    dispatched.set(mutation);
                }));
        assertEquals(WiredVariableMutation.Kind.CREATED, dispatched.get().kind());
        assertEquals(9, dispatched.get().afterValue());

        dispatched.set(null);
        repository.failNextCommit = true;
        assertEquals(WiredVariableManager.MutationResult.PERSISTENCE_FAILED,
                manager.setAndDispatch(definition.variableId(), WiredVariableHolder.room(), 10,
                        dispatched::set));
        assertNull(dispatched.get());
    }

    @Test
    void giveWithoutOverrideCreatesAtomicallyAndNeverDispatchesForExistingValue() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(112, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));

        AtomicReference<WiredVariableMutation> dispatched = new AtomicReference<>();
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.setIfAbsentAndDispatch(definition.variableId(), WiredVariableHolder.room(), 4,
                        mutation -> {
                            assertFalse(Thread.holdsLock(manager));
                            dispatched.set(mutation);
                        }));
        assertEquals(4, dispatched.get().afterValue());

        dispatched.set(null);
        assertEquals(WiredVariableManager.MutationResult.UNCHANGED,
                manager.setIfAbsentAndDispatch(definition.variableId(), WiredVariableHolder.room(), 9,
                        dispatched::set));
        assertNull(dispatched.get());
        assertEquals(4, manager.get(definition.variableId(), WiredVariableHolder.room()).value());
    }

    @Test
    void concurrentAddsCannotLoseUpdates() throws Exception {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(12, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));

        int workers = 8;
        int writesPerWorker = 100;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        for (int worker = 0; worker < workers; worker++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < writesPerWorker; i++) {
                        assertTrue(manager.add(definition.variableId(), WiredVariableHolder.room(), 1)
                                == WiredVariableManager.MutationResult.CREATED
                                || manager.get(definition.variableId(), WiredVariableHolder.room()).value() > 0);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(workers * writesPerWorker,
                manager.get(definition.variableId(), WiredVariableHolder.room()).value());
    }

    @Test
    void hashesAreDeterministicAndAuthorizationFiltered() {
        MemoryRepository firstRepository = new MemoryRepository();
        MemoryRepository secondRepository = new MemoryRepository();
        WiredVariableManager first = manager(firstRepository, true);
        WiredVariableManager second = manager(secondRepository, true);
        WiredVariableDefinition visible = definition(20, WiredVariableType.FURNI, 10, false);
        WiredVariableDefinition secret = definition(21, WiredVariableType.USER, 10, true);

        assertTrue(first.registerDefinition(visible));
        assertTrue(first.registerDefinition(secret));
        assertTrue(second.registerDefinition(secret));
        assertTrue(second.registerDefinition(visible));
        first.set(visible.variableId(), WiredVariableHolder.furni(9), -4);
        first.set(visible.variableId(), WiredVariableHolder.furni(2), 7);
        second.set(visible.variableId(), WiredVariableHolder.furni(2), 7);
        second.set(visible.variableId(), WiredVariableHolder.furni(9), -4);

        WiredVariableManager.Snapshot firstPublic = first.publicSnapshot();
        WiredVariableManager.Snapshot secondPublic = second.publicSnapshot();
        assertEquals(firstPublic.aggregateHash(), secondPublic.aggregateHash());
        assertEquals(List.of(2, 9), firstPublic.variables().get(visible.variableId()).values().stream()
                .map(value -> value.holder().stableId()).toList());
        assertFalse(firstPublic.variables().containsKey(secret.variableId()));

        WiredVariableManager.Snapshot privileged = first.snapshot(definition -> true);
        assertTrue(privileged.variables().containsKey(secret.variableId()));
        assertNotEquals(firstPublic.aggregateHash(), privileged.aggregateHash());
    }

    @Test
    void indexedSingleVariableViewsStayIsolatedAndLifecycleSafe() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition first =
                definition(22, WiredVariableType.FURNI, 10, false);
        WiredVariableDefinition second =
                definition(23, WiredVariableType.FURNI, 10, false);
        assertTrue(manager.registerDefinition(first));
        assertTrue(manager.registerDefinition(second));
        manager.set(first.variableId(), WiredVariableHolder.furni(9), 90);
        manager.set(first.variableId(), WiredVariableHolder.furni(2), 20);
        manager.set(second.variableId(), WiredVariableHolder.furni(7), 70);

        WiredVariableManager.VariableSnapshot firstView =
                manager.variableSnapshot(first.variableId());
        assertEquals(first, firstView.definition());
        assertEquals(List.of(2, 9), firstView.values().stream()
                .map(value -> value.holder().stableId()).toList());
        assertEquals(List.of(second), manager.runtimeDefinitions(
                definition -> definition.variableId().equals(second.variableId())));

        assertEquals(1, manager.removeFurniHolder(7));
        assertTrue(manager.variableSnapshot(second.variableId()).values().isEmpty());
        assertTrue(manager.removeDefinition(first.variableId()));
        assertNull(manager.variableSnapshot(first.variableId()));

        manager.close();
        assertNull(manager.runtimeDefinition(second.variableId()));
        assertTrue(manager.runtimeDefinitions(definition -> true).isEmpty());
    }

    @Test
    void equalHashProducesEmptyDiffAndBoundsAreFailClosed() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(30, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));
        manager.set(definition.variableId(), WiredVariableHolder.room(), 5);
        WiredVariableManager.Snapshot snapshot = manager.publicSnapshot();
        int variableHash = snapshot.variables().get(definition.variableId()).hash();

        WiredVariableManager.Diff equal = manager.diff(
                Map.of(definition.variableId(), variableHash), value -> !value.invisible());
        assertTrue(equal.accepted());
        assertTrue(equal.removed().isEmpty());
        assertTrue(equal.changed().isEmpty());

        Map<String, Integer> oversized = new HashMap<>();
        for (int index = 0; index <= WiredVariableManager.MAX_DIFF_HASHES; index++) {
            oversized.put("room:" + (10_000 + index), index);
        }
        assertFalse(manager.diff(oversized, value -> true).accepted());
        assertFalse(manager.diff(Map.of(), null).accepted());

        // A stale, now-unauthorized cache entry is explicitly removed rather
        // than leaked back as metadata in the next diff response.
        WiredVariableManager.Diff noRights = manager.diff(
                Map.of(definition.variableId(), variableHash), value -> false);
        assertTrue(noRights.accepted());
        assertEquals(List.of(definition.variableId()), noRights.removed());
        assertTrue(noRights.changed().isEmpty());
    }

    @Test
    void valueOnlyMutationAdvancesRuntimeRevisionButNotCatalogHash() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(31, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));
        WiredVariableManager.Snapshot before = manager.publicSnapshot();
        long beforeRevision = manager.revision();

        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(definition.variableId(), WiredVariableHolder.room(), 99));
        WiredVariableManager.Snapshot after = manager.publicSnapshot();

        assertTrue(manager.revision() > beforeRevision);
        assertEquals(before.aggregateHash(), after.aggregateHash());
        assertEquals(before.variables().get(definition.variableId()).hash(),
                after.variables().get(definition.variableId()).hash());
        assertEquals(99, after.variables().get(definition.variableId()).values().get(0).value());
    }

    @Test
    void definitionAndHolderLifecycleAreDistinct() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition furni = definition(40, WiredVariableType.FURNI, 10, false);
        WiredVariableDefinition user = definition(41, WiredVariableType.USER, 10, false);
        assertTrue(manager.registerDefinition(furni));
        assertTrue(manager.registerDefinition(user));
        manager.set(furni.variableId(), WiredVariableHolder.furni(77), 3);
        manager.set(user.variableId(), WiredVariableHolder.user(88), 4);

        manager.onUserLeaves(88);
        assertEquals(4, manager.get(user.variableId(), WiredVariableHolder.user(88)).value());
        assertEquals(1, manager.removeFurniHolder(77));
        assertNull(manager.get(furni.variableId(), WiredVariableHolder.furni(77)));
        assertTrue(manager.publicSnapshot().variables().containsKey(furni.variableId()));

        assertTrue(manager.removeDefinition(furni.variableId()));
        assertFalse(manager.publicSnapshot().variables().containsKey(furni.variableId()));
    }

    @Test
    void leaveClearsRoomPresenceValuesAndPreservesPermanentValues() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableDefinition persistent = definition(42, WiredVariableType.USER, 10, false);
        WiredVariableDefinition roomActive = definition(43, WiredVariableType.USER, 0, false);
        WiredVariableManager firstLoad = manager(repository, true);
        assertTrue(firstLoad.registerDefinition(persistent));
        assertTrue(firstLoad.registerDefinition(roomActive));
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                firstLoad.set(persistent.variableId(), WiredVariableHolder.user(88), 4));
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                firstLoad.set(roomActive.variableId(), WiredVariableHolder.user(88), 9));

        firstLoad.onUserLeaves(88);
        assertEquals(4, firstLoad.get(persistent.variableId(), WiredVariableHolder.user(88)).value());
        assertNull(firstLoad.get(roomActive.variableId(), WiredVariableHolder.user(88)));

        // Room disposal closes only in-memory state. A new room load reads the
        // committed persistent holder value under the same stable Habbo ID.
        firstLoad.close();
        WiredVariableManager reloaded = manager(repository, true);
        assertTrue(reloaded.registerDefinition(persistent));
        assertTrue(reloaded.registerDefinition(roomActive));
        assertEquals(4, reloaded.get(persistent.variableId(), WiredVariableHolder.user(88)).value());
        assertNull(reloaded.get(roomActive.variableId(), WiredVariableHolder.user(88)));
        reloaded.onUserLeaves(88);
        assertEquals(4, reloaded.get(persistent.variableId(), WiredVariableHolder.user(88)).value());
    }

    @Test
    void furniHolderPersistenceFailureRollsBackAndLeavesRuntimeDark() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition furni = definition(44, WiredVariableType.FURNI, 10, false);
        assertTrue(manager.registerDefinition(furni));
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(furni.variableId(), WiredVariableHolder.furni(77), 3));

        repository.failNextCommit = true;
        assertEquals(0, manager.removeFurniHolder(77));
        assertFalse(manager.isOperational());
        assertTrue(repository.definitionHashes.containsKey(furni.variableId()));
        assertEquals(1, repository.values.size());
        assertEquals(WiredVariableHolder.furni(77), repository.values.get(0).holder());
    }

    @Test
    void departureDispatchCannotRecreateTemporaryValueAndDoesNotHoldMonitor() {
        var manager = manager(new MemoryRepository(), true);
        var temporary = definition(47, WiredVariableType.USER, 0, false);
        var permanent = definition(48, WiredVariableType.USER, 11, false);
        var holder = WiredVariableHolder.user(88);
        assertTrue(manager.registerDefinition(temporary));
        assertTrue(manager.registerDefinition(permanent));
        manager.set(temporary.variableId(), holder, 5);
        manager.set(permanent.variableId(), holder, 6);
        var seen = new ArrayList<WiredVariableMutation>();
        manager.onUserLeaves(88, mutation -> {
            assertFalse(Thread.holdsLock(manager));
            seen.add(mutation);
            assertEquals(WiredVariableManager.MutationResult.UNAVAILABLE,
                    manager.set(temporary.variableId(), holder, 7));
            manager.onUserLeaves(88); // Re-entry must not clear the outer guard.
            assertEquals(WiredVariableManager.MutationResult.UNAVAILABLE,
                    manager.set(temporary.variableId(), holder, 8));
        });
        assertEquals(1, seen.size());
        assertEquals(WiredVariableMutation.Kind.DELETED, seen.get(0).kind());
        assertNull(manager.get(temporary.variableId(), holder));
        assertEquals(6, manager.get(permanent.variableId(), holder).value());
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(temporary.variableId(), holder, 9));
    }

    @Test
    void hydrationReconciliationPurgesOrphansAndWrongScopesOnce() {
        WiredVariableDefinition valid = definition(50, WiredVariableType.FURNI, 10, false);
        String orphanId = "room:999";
        Map<String, Integer> hashes = new HashMap<>();
        hashes.put(valid.variableId(), WiredVariableManager.hashDefinition(valid));
        hashes.put(orphanId, 123);
        List<WiredVariableValue> loadedValues = List.of(
                value(valid.variableId(), WiredVariableHolder.furni(7), 1),
                value(valid.variableId(), WiredVariableHolder.user(8), 2),
                value(orphanId, WiredVariableHolder.furni(9), 3));
        MemoryRepository repository = new MemoryRepository(
                new WiredVariableManager.LoadedRoom(true, 8L, hashes, loadedValues));
        WiredVariableManager manager = manager(repository, true);

        assertTrue(manager.registerDefinition(valid));
        assertTrue(manager.finishHydration());
        assertEquals(9L, manager.revision());
        assertEquals(Map.of(valid.variableId(), WiredVariableManager.hashDefinition(valid)),
                repository.definitionHashes);
        assertEquals(1, repository.values.size());
        assertEquals(WiredVariableHolder.furni(7), repository.values.get(0).holder());
        assertTrue(manager.finishHydration());
        assertEquals(9L, manager.revision());
    }

    @Test
    void definitionShapeChangePurgesPersistentRowsInSameRevisionTransaction() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition furni = definition(55, WiredVariableType.FURNI, 10, false);
        WiredVariableDefinition changedToUser = definition(55, WiredVariableType.USER, 10, false);
        assertTrue(manager.registerDefinition(furni));
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(furni.variableId(), WiredVariableHolder.furni(33), 8));
        long beforeChange = manager.revision();

        assertTrue(manager.registerDefinition(changedToUser));
        assertEquals(beforeChange + 1L, manager.revision());
        assertTrue(repository.values.isEmpty());
        assertNull(manager.get(furni.variableId(), WiredVariableHolder.furni(33)));
    }

    @Test
    void definitionRefreshNeverPersistsTransientFurniValues() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition furni = definition(56, WiredVariableType.FURNI, 10, false);
        WiredVariableHolder transientHolder = WiredVariableHolder.furni(-7);
        assertTrue(manager.registerDefinition(furni));
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(furni.variableId(), transientHolder, 12));
        assertTrue(repository.values.isEmpty());

        assertTrue(manager.registerDefinition(furni.renamed("renamed")));

        assertTrue(repository.values.isEmpty());
        assertEquals(12, manager.get(furni.variableId(), transientHolder).value());
    }

    @Test
    void closeDisposesRoomStateAndDeleteCleansPersistence() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(60, WiredVariableType.ROOM, 10, false);
        manager.registerDefinition(definition);
        manager.set(definition.variableId(), WiredVariableHolder.room(), 2);

        assertTrue(manager.deleteRoomState());
        assertTrue(manager.isClosed());
        assertTrue(repository.deleted);
        assertEquals(WiredVariableManager.MutationResult.UNAVAILABLE,
                manager.add(definition.variableId(), WiredVariableHolder.room(), 1));
    }

    @Test
    void permanentDeleteFailureDoesNotClearRuntimeAndSuccessfulCleanupIsIdempotent() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(61, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));
        assertEquals(WiredVariableManager.MutationResult.CREATED,
                manager.set(definition.variableId(), WiredVariableHolder.room(), 7));

        repository.failDeleteRoom = true;
        assertFalse(manager.deleteRoomState());
        assertFalse(manager.isClosed());
        assertFalse(manager.isOperational());
        assertEquals(1, repository.values.size());
        assertEquals(7, repository.values.get(0).value());
        assertEquals(1, repository.deleteCalls);

        repository.failDeleteRoom = false;
        assertTrue(manager.deleteRoomState());
        assertTrue(manager.deleteRoomState());
        assertTrue(manager.isClosed());
        assertEquals(2, repository.deleteCalls);
    }

    @Test
    void committedMutationCannotDispatchAfterRoomDispose() {
        MemoryRepository repository = new MemoryRepository();
        WiredVariableManager manager = manager(repository, true);
        WiredVariableDefinition definition = definition(62, WiredVariableType.ROOM, 10, false);
        assertTrue(manager.registerDefinition(definition));

        WiredVariableManager.MutationReceipt receipt = manager.setWithReceipt(
                definition.variableId(), WiredVariableHolder.room(), 5);
        AtomicReference<WiredVariableMutation> dispatched = new AtomicReference<>();
        manager.close();
        receipt.dispatchTo(dispatched::set);
        assertNull(dispatched.get());
    }

    @Test
    void roomSpecialTypesOwnsAndDisposesManagerBeforeIndexes() {
        WiredVariableManager manager = manager(new MemoryRepository(), true);
        RoomSpecialTypes specialTypes = new RoomSpecialTypes(manager);

        assertSame(manager, specialTypes.getWiredVariableManager());
        specialTypes.dispose();
        assertTrue(manager.isClosed());
    }

    @Test
    void failedPersistenceLoadKeepsEveryViewAndMutationDark() {
        WiredVariableManager.Repository failed = new WiredVariableManager.Repository() {
            @Override
            public WiredVariableManager.LoadedRoom load(int roomId) {
                return WiredVariableManager.LoadedRoom.failed();
            }

            @Override
            public boolean commit(int roomId, long expectedRevision, long nextRevision,
                                  WiredVariableManager.PersistenceMutation mutation) {
                return true;
            }

            @Override
            public boolean deleteRoom(int roomId) {
                return false;
            }
        };
        WiredVariableManager manager = WiredVariableManager.forTests(
                5, failed, () -> true, () -> 1_000L);
        WiredVariableDefinition definition = definition(70, WiredVariableType.ROOM, 10, false);

        assertFalse(manager.registerDefinition(definition));
        assertEquals(WiredVariableManager.MutationResult.UNAVAILABLE,
                manager.set(definition.variableId(), WiredVariableHolder.room(), 1));
        assertFalse(manager.publicSnapshot().available());
        assertFalse(manager.finishHydration());
    }

    private static WiredVariableManager manager(MemoryRepository repository, boolean ready) {
        AtomicLong clock = new AtomicLong(1_000L);
        return WiredVariableManager.forTests(5, repository, () -> ready, clock::incrementAndGet);
    }

    private static WiredVariableDefinition definition(int itemId, WiredVariableType type,
                                                      int availability, boolean invisible) {
        return WiredVariableDefinition.create(5, itemId, type,
                "Variable " + itemId, availability, invisible);
    }

    private static WiredVariableValue value(String variableId, WiredVariableHolder holder, int value) {
        return new WiredVariableValue(variableId, holder, value, 100L, 100L, 1L);
    }

    private static final class MemoryRepository implements WiredVariableManager.Repository {
        private long revision;
        private final Map<String, Integer> definitionHashes = new HashMap<>();
        private final List<WiredVariableValue> values = new ArrayList<>();
        private boolean deleted;
        private boolean failDeleteRoom;
        private int deleteCalls;
        private boolean failNextCommit;

        private MemoryRepository() {
        }

        private MemoryRepository(WiredVariableManager.LoadedRoom loaded) {
            this.revision = loaded.revision;
            this.definitionHashes.putAll(loaded.definitionHashes);
            this.values.addAll(loaded.values);
        }

        @Override
        public synchronized WiredVariableManager.LoadedRoom load(int roomId) {
            return new WiredVariableManager.LoadedRoom(
                    true, this.revision, this.definitionHashes, this.values);
        }

        @Override
        public synchronized boolean commit(int roomId, long expectedRevision, long nextRevision,
                                           WiredVariableManager.PersistenceMutation mutation) {
            if (this.failNextCommit) {
                this.failNextCommit = false;
                return false;
            }
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
                        this.values.removeIf(value -> value.variableId().equals(retained.variableId())
                                && value.holder().equals(retained.holder()));
                        this.values.add(retained);
                    }
                }
                case DELETE_DEFINITION -> {
                    this.definitionHashes.remove(mutation.variableId);
                    this.values.removeIf(value -> value.variableId().equals(mutation.variableId));
                }
                case UPSERT_VALUE -> {
                    this.values.removeIf(value -> value.variableId().equals(mutation.value.variableId())
                            && value.holder().equals(mutation.value.holder()));
                    this.values.add(mutation.value);
                }
                case DELETE_VALUE -> this.values.removeIf(value ->
                        value.variableId().equals(mutation.variableId)
                                && value.holder().equals(mutation.holder));
                case DELETE_HOLDER -> this.values.removeIf(value ->
                        value.holder().equals(mutation.holder));
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
        public synchronized boolean deleteRoom(int roomId) {
            this.deleteCalls++;
            if (this.failDeleteRoom) {
                return false;
            }
            this.revision = 0L;
            this.definitionHashes.clear();
            this.values.clear();
            this.deleted = true;
            return true;
        }
    }
}
