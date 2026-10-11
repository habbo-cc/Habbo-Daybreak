package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.menu.WiredRoomMonitor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Room-owned Core Variables state.
 *
 * <p>All writes are serialized per room and persisted before publication. This
 * makes value/revision changes atomic without relying on concurrent-map compound
 * operations. Wire composers consume immutable snapshots and perform visible-ID
 * translation outside this class.</p>
 */
public final class WiredVariableManager implements AutoCloseable {
    /** Replaceable emulation hash revision; July's canonical algorithm needs a live capture. */
    public static final int EMULATED_HASH_REVISION = 1;
    /**
     * Room capacity limits, loaded from emulator_settings in
     * PluginManager.globalOnConfigurationUpdated using the wired.variables.* keys, so they apply at
     * boot and on every config reload. The initialisers are the shipped defaults and are what unit
     * tests and any pre-boot code observe.
     *
     * <p>These are the single source of truth: read them through these fields rather than calling
     * Emulator.getConfig() again at the point of use, so a configured value cannot drift between
     * call sites.</p>
     */
    public static int MAX_DEFINITIONS = 512;

    public static int MAX_VALUES_PER_DEFINITION = 10_000;

    public static int MAX_VALUES_PER_ROOM = 50_000;

    /**
     * Bounds the client-supplied hash list accepted by a variables-diff request. This is an abuse
     * limit rather than a capacity one; lowering it breaks diff synchronisation and raising it
     * weakens the bound, so operators should leave it at the default.
     */
    public static int MAX_DIFF_HASHES = 2_048;

    private final int roomId;
    private final Repository repository;
    private final BooleanSupplier runtimeReady;
    private final LongSupplier clock;
    private final Map<String, WiredVariableDefinition> definitions = new HashMap<>();
    /**
     * Values are partitioned by variable with an invalidation-based ordered
     * view. Runtime reads stay hash-map O(1), while snapshot construction visits
     * each visible value once instead of filtering the complete room value set
     * once per definition.
     */
    private final Map<String, VariableValues>
            valuesByVariable = new HashMap<>();
    private final Map<String, Integer> persistedDefinitionHashes = new HashMap<>();
    private int valueCount;
    private long revision;
    private int persistedValueCount;
    private boolean operational;
    private boolean closed;
    private boolean roomStateDeleted;
    private final java.util.Set<Integer> departingUsers = new java.util.HashSet<>();

    private WiredVariableManager(int roomId, Repository repository,
                                 BooleanSupplier runtimeReady, LongSupplier clock) {
        if (roomId <= 0) {
            throw new IllegalArgumentException("room ID must be positive");
        }
        this.roomId = roomId;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.runtimeReady = Objects.requireNonNull(runtimeReady, "runtimeReady");
        this.clock = Objects.requireNonNull(clock, "clock");

        LoadedRoom loaded = repository.load(roomId);
        this.operational = loaded != null && loaded.successful;
        if (loaded != null && loaded.successful) {
            this.revision = Math.max(0L, loaded.revision);
            this.persistedDefinitionHashes.putAll(loaded.definitionHashes);
            for (WiredVariableValue value : loaded.values) {
                if (value != null && value.variableId() != null) {
                    putValue(value);
                }
            }
        }
        this.persistedValueCount = this.valueCount;
    }

    public static WiredVariableManager forRoom(int roomId) {
        boolean readyAtRoomLoad = WiredCapabilityService.isRoomCapabilityReady(
                WiredCapabilityService.CAPABILITY_VARIABLES);
        // Do not touch new tables on legacy/capability-dark installations. A
        // room loaded dark stays dark until reloaded after capability enablement.
        Repository repository = readyAtRoomLoad
                ? WiredVariableRepository.instance()
                : new CapabilityDarkRepository();
        return new WiredVariableManager(roomId, repository,
                () -> readyAtRoomLoad, System::currentTimeMillis);
    }

    static WiredVariableManager forTests(int roomId, Repository repository,
                                         BooleanSupplier runtimeReady, LongSupplier clock) {
        return new WiredVariableManager(roomId, repository, runtimeReady, clock);
    }

    public synchronized int roomId() {
        return this.roomId;
    }

    public synchronized long revision() {
        return this.revision;
    }

    public synchronized boolean isClosed() {
        return this.closed;
    }

    public synchronized boolean isOperational() {
        return this.operational && !this.closed;
    }

    /** Hydrates or refreshes an item-backed definition; safe while runtime is dark. */
    public synchronized boolean registerDefinition(WiredVariableDefinition definition) {
        if (this.closed || !this.operational || definition == null
                || definition.roomId() != this.roomId) {
            return false;
        }
        WiredVariableDefinition current = this.definitions.get(definition.variableId());
        int definitionHash = hashDefinition(definition);
        if (sameDefinition(current, definition)) {
            return true;
        }
        if (current == null && this.definitions.size() >= MAX_DEFINITIONS) {
            reportTooManyVariables();
            return false;
        }

        Integer persistedHash = this.persistedDefinitionHashes.get(definition.variableId());
        if (persistedHash != null && persistedHash == definitionHash) {
            this.definitions.put(definition.variableId(), definition);
            discardInvalidValues(definition);
            return true;
        }

        long nextRevision = nextRevision();
        List<WiredVariableValue> retainedValues = definition.persistsValues()
                ? valuesFor(definition.variableId()).stream()
                        .filter(value -> definition.accepts(value.holder()))
                        .filter(value -> !value.holder().isTransientFurni())
                        .toList()
                : List.of();
        PersistenceMutation mutation = PersistenceMutation.upsertDefinition(
                definition.variableId(), definitionHash, definition, retainedValues);
        if (!commit(nextRevision, mutation)) {
            return false;
        }
        this.revision = nextRevision;
        this.persistedDefinitionHashes.put(definition.variableId(), definitionHash);
        this.definitions.put(definition.variableId(), definition);
        discardInvalidValues(definition);
        return true;
    }

    /**
     * Bounded post-item-load reconciliation. Removes orphan/malformed rows and
     * makes the persisted definition signatures match the loaded room graph.
     */
    public synchronized boolean finishHydration() {
        if (this.closed || !this.operational) {
            return false;
        }
        Map<String, Integer> currentHashes = new LinkedHashMap<>();
        this.definitions.values().stream()
                .sorted(Comparator.comparing(WiredVariableDefinition::variableId))
                .forEach(definition -> currentHashes.put(
                        definition.variableId(), hashDefinition(definition)));
        discardUnpersistableValues();
        boolean clean = currentHashes.equals(this.persistedDefinitionHashes)
                && this.persistedValueCount == this.valueCount;
        if (clean) {
            return true;
        }
        long nextRevision = nextRevision();
        List<WiredVariableValue> persistentValues = allValues();
        if (!commit(nextRevision, PersistenceMutation.reconcile(currentHashes, persistentValues))) {
            return false;
        }
        this.revision = nextRevision;
        this.persistedDefinitionHashes.clear();
        this.persistedDefinitionHashes.putAll(currentHashes);
        this.persistedValueCount = this.valueCount;
        return true;
    }

    /** Permanent definition-furniture removal, not an ordinary holder removal. */
    public synchronized boolean removeDefinition(String variableId) {
        if (this.closed || !this.operational || variableId == null
                || !this.definitions.containsKey(variableId)) {
            return false;
        }
        long nextRevision = nextRevision();
        if (!commit(nextRevision, PersistenceMutation.deleteDefinition(variableId))) {
            return false;
        }
        this.revision = nextRevision;
        this.definitions.remove(variableId);
        this.persistedDefinitionHashes.remove(variableId);
        removeVariableValues(variableId);
        return true;
    }

    public MutationResult set(String variableId, WiredVariableHolder holder, long value) {
        return setWithReceipt(variableId, holder, value).result();
    }

    public MutationResult add(String variableId, WiredVariableHolder holder, long delta) {
        return addWithReceipt(variableId, holder, delta).result();
    }

    /**
     * Mutates a value and returns an immutable committed mutation when one was
     * actually persisted.  Callers must dispatch the returned mutation only
     * after this method returns; the manager monitor is no longer held then.
     */
    public MutationReceipt setWithReceipt(String variableId, WiredVariableHolder holder, long value) {
        synchronized (this) {
            return update(variableId, holder, value, false);
        }
    }

    public MutationReceipt addWithReceipt(String variableId, WiredVariableHolder holder, long delta) {
        synchronized (this) {
            return update(variableId, holder, delta, true);
        }
    }

    /**
     * Convenience for action code: dispatch is invoked after leaving the
     * synchronized manager section, and only for a committed mutation.
     */
    public MutationResult setAndDispatch(String variableId, WiredVariableHolder holder, long value,
                                         Consumer<WiredVariableMutation> dispatcher) {
        MutationReceipt receipt = setWithReceipt(variableId, holder, value);
        receipt.dispatchTo(dispatcher);
        return receipt.result();
    }

    /**
     * Creates a value only when it is still absent at commit time.  Give
     * Variable's "override existing" option must not be implemented as an
     * unlocked get-then-set pair: more than one execution lane can otherwise
     * observe the same missing value and both report a creation.
     */
    public MutationResult setIfAbsentAndDispatch(String variableId, WiredVariableHolder holder, long value,
                                                 Consumer<WiredVariableMutation> dispatcher) {
        MutationReceipt receipt;
        synchronized (this) {
            if (variableId != null && holder != null
                    && value(variableId, holder) != null) {
                receipt = MutationReceipt.of(MutationResult.UNCHANGED);
            } else {
                receipt = update(variableId, holder, value, false);
            }
        }
        receipt.dispatchTo(dispatcher);
        return receipt.result();
    }

    public MutationResult addAndDispatch(String variableId, WiredVariableHolder holder, long delta,
                                         Consumer<WiredVariableMutation> dispatcher) {
        MutationReceipt receipt = addWithReceipt(variableId, holder, delta);
        receipt.dispatchTo(dispatcher);
        return receipt.result();
    }

    private MutationReceipt update(String variableId, WiredVariableHolder holder,
                                   long operand, boolean additive) {
        if (this.closed || !this.operational || !this.runtimeReady.getAsBoolean()) {
            return MutationReceipt.of(MutationResult.UNAVAILABLE);
        }
        WiredVariableDefinition definition = this.definitions.get(variableId);
        if (definition == null || !definition.accepts(holder)
                || WiredQuestVariableRuntime.isQuestBacked(definition)
                || additive && !definition.hasValue()) {
            return MutationReceipt.of(MutationResult.INVALID);
        }
        if (holder.scope() == WiredVariableHolder.Scope.USER
                && definition.availabilityCode() == 0
                && this.departingUsers.contains(holder.stableId())) {
            return MutationReceipt.of(MutationResult.UNAVAILABLE);
        }
        WiredVariableValue current = value(variableId, holder);
        long currentValue = current == null ? 0L : current.value();
        long candidate;
        try {
            candidate = additive ? Math.addExact(currentValue, operand) : operand;
        } catch (ArithmeticException exception) {
            return MutationReceipt.of(MutationResult.OVERFLOW);
        }
        if (!definition.hasValue()) candidate = 0L;
        if (candidate < Integer.MIN_VALUE || candidate > Integer.MAX_VALUE) {
            return MutationReceipt.of(MutationResult.OVERFLOW);
        }
        if (current != null && current.value() == (int) candidate) {
            return MutationReceipt.of(MutationResult.UNCHANGED);
        }
        if (current == null && !canAddValue(variableId)) {
            reportTooManyVariables();
            return MutationReceipt.of(MutationResult.LIMIT_REACHED);
        }

        long now = Math.max(0L, this.clock.getAsLong());
        long nextRevision = nextRevision();
        WiredVariableValue replacement = new WiredVariableValue(
                variableId, holder, (int) candidate,
                current == null ? now : current.createdAtMs(), now, nextRevision);
        PersistenceMutation mutation = definition.persistsValues() && !holder.isTransientFurni()
                ? PersistenceMutation.upsertValue(replacement)
                : PersistenceMutation.revisionOnly();
        if (!commit(nextRevision, mutation)) {
            return MutationReceipt.of(MutationResult.PERSISTENCE_FAILED);
        }
        putValue(replacement);
        this.revision = nextRevision;
        return new MutationReceipt(current == null ? MutationResult.CREATED : MutationResult.CHANGED,
                new WiredVariableMutation(variableId, holder,
                        current == null ? null : current.value(), replacement.value(),
                        current == null ? WiredVariableMutation.Kind.CREATED
                                : WiredVariableMutation.Kind.VALUE_CHANGED,
                        nextRevision,
                        definition.definitionItemId(),
                        WiredVariableMutation.CHANGE_ORIGIN_IN_ROOM),
                this::isDispatchable);
    }

    public MutationResult removeValue(String variableId, WiredVariableHolder holder) {
        return removeValueWithReceipt(variableId, holder).result();
    }

    public MutationReceipt removeValueWithReceipt(String variableId, WiredVariableHolder holder) {
        synchronized (this) {
            return removeValueLocked(variableId, holder);
        }
    }

    public MutationResult removeValueAndDispatch(String variableId, WiredVariableHolder holder,
                                                  Consumer<WiredVariableMutation> dispatcher) {
        MutationReceipt receipt = removeValueWithReceipt(variableId, holder);
        receipt.dispatchTo(dispatcher);
        return receipt.result();
    }

    private MutationReceipt removeValueLocked(String variableId, WiredVariableHolder holder) {
        if (this.closed || !this.operational || !this.runtimeReady.getAsBoolean()) {
            return MutationReceipt.of(MutationResult.UNAVAILABLE);
        }
        WiredVariableDefinition definition = this.definitions.get(variableId);
        if (definition == null || !definition.accepts(holder)
                || WiredQuestVariableRuntime.isQuestBacked(definition)) {
            return MutationReceipt.of(MutationResult.INVALID);
        }
        WiredVariableValue current = value(variableId, holder);
        if (current == null) {
            return MutationReceipt.of(MutationResult.UNCHANGED);
        }
        long nextRevision = nextRevision();
        PersistenceMutation mutation = definition.persistsValues() && !holder.isTransientFurni()
                ? PersistenceMutation.deleteValue(variableId, holder)
                : PersistenceMutation.revisionOnly();
        if (!commit(nextRevision, mutation)) {
            return MutationReceipt.of(MutationResult.PERSISTENCE_FAILED);
        }
        WiredVariableValue removed = removeValueInternal(variableId, holder);
        this.revision = nextRevision;
        return new MutationReceipt(MutationResult.REMOVED,
                new WiredVariableMutation(variableId, holder, removed.value(), null,
                        WiredVariableMutation.Kind.DELETED, nextRevision,
                        definition.definitionItemId(),
                        WiredVariableMutation.CHANGE_ORIGIN_IN_ROOM),
                this::isDispatchable);
    }

    /** Furniture permanently left this room: remove its values in one transaction. */
    public synchronized int removeFurniHolder(int databaseItemId) {
        if (this.closed || !this.operational || databaseItemId == 0) {
            return 0;
        }
        WiredVariableHolder holder = WiredVariableHolder.furni(databaseItemId);
        List<String> variableIds = this.valuesByVariable.entrySet().stream()
                .filter(entry -> entry.getValue().contains(holder))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (variableIds.isEmpty()) {
            return 0;
        }
        long nextRevision = nextRevision();
        PersistenceMutation mutation = holder.isTransientFurni()
                ? PersistenceMutation.revisionOnly()
                : PersistenceMutation.deleteHolder(holder);
        if (!commit(nextRevision, mutation)) {
            return 0;
        }
        variableIds.forEach(variableId -> removeValueInternal(variableId, holder));
        this.revision = nextRevision;
        return variableIds.size();
    }

    /** Clear room-presence user values; permanent values retain stable holder IDs. */
    public void onUserLeaves(int habboId) {
        onUserLeaves(habboId, null);
    }

    public void onUserLeaves(int habboId, Consumer<WiredVariableMutation> dispatcher) {
        List<WiredVariableMutation> mutations = new java.util.ArrayList<>();
        synchronized (this) {
            if (this.closed || !this.operational || habboId <= 0
                    || !this.departingUsers.add(habboId)) return;
            WiredVariableHolder holder = WiredVariableHolder.user(habboId);
            List<WiredVariableDefinition> temporary = this.definitions.values().stream()
                    .filter(definition -> definition.type()
                            == com.eu.habbo.habbohotel.wired.WiredVariableType.USER
                            && definition.availabilityCode() == 0
                            && value(definition.variableId(), holder) != null)
                    .sorted(Comparator.comparing(WiredVariableDefinition::variableId)).toList();
            if (!temporary.isEmpty()) {
                long next = nextRevision();
                if (!commit(next, PersistenceMutation.revisionOnly())) {
                    this.departingUsers.remove(habboId);
                    return;
                }
                for (WiredVariableDefinition definition : temporary) {
                    WiredVariableValue removed = removeValueInternal(definition.variableId(), holder);
                    mutations.add(new WiredVariableMutation(definition.variableId(), holder,
                            removed.value(), null, WiredVariableMutation.Kind.DELETED, next,
                            definition.definitionItemId(), WiredVariableMutation.CHANGE_ORIGIN_IN_ROOM));
                }
                this.revision = next;
            }
        }
        try {
            // Keep the departure guard active while handlers run, outside the
            // manager monitor, so a deletion handler cannot recreate this value.
            if (dispatcher != null) mutations.forEach(dispatcher);
        } finally {
            synchronized (this) { this.departingUsers.remove(habboId); }
        }
    }

    public synchronized WiredVariableValue get(String variableId, WiredVariableHolder holder) {
        if (this.closed || !this.operational || !this.runtimeReady.getAsBoolean()) {
            return null;
        }
        WiredVariableDefinition definition = this.definitions.get(variableId);
        if (definition != null && WiredQuestVariableRuntime.isQuestBacked(definition)) {
            var environment = Emulator.getGameEnvironment();
            Room room = environment == null ? null : environment.getRoomManager().getRoom(this.roomId);
            return WiredQuestVariableRuntime.read(room, definition, holder);
        }
        return value(variableId, holder);
    }

    /**
     * Creates a deterministic, authorization-filtered immutable view.
     * Callers must supply their permission predicate; null is rejected fail-closed.
     */
    public synchronized Snapshot snapshot(Predicate<WiredVariableDefinition> authorized) {
        if (this.closed || !this.operational || !this.runtimeReady.getAsBoolean()
                || authorized == null) {
            return Snapshot.unavailable(this.revision);
        }
        List<WiredVariableDefinition> visibleDefinitions = this.definitions.values().stream()
                .filter(authorized)
                .sorted(Comparator.comparing(WiredVariableDefinition::variableId))
                .toList();
        Map<String, VariableSnapshot> byId = new LinkedHashMap<>();
        Room room = null;
        if (visibleDefinitions.stream().anyMatch(WiredQuestVariableRuntime::isQuestBacked)) {
            var environment = Emulator.getGameEnvironment();
            room = environment == null ? null : environment.getRoomManager().getRoom(this.roomId);
        }
        for (WiredVariableDefinition definition : visibleDefinitions) {
            List<WiredVariableValue> visibleValues;
            if (WiredQuestVariableRuntime.isQuestBacked(definition)) {
                visibleValues = WiredQuestVariableRuntime.values(room, definition);
            } else {
                visibleValues = valuesFor(definition.variableId());
            }
            int hash = hashCatalogEntry(definition);
            byId.put(definition.variableId(), new VariableSnapshot(definition, visibleValues, hash));
        }
        return new Snapshot(true, this.revision, hashAggregate(byId), byId);
    }

    public Snapshot publicSnapshot() {
        return snapshot(definition -> !definition.invisible());
    }

    public synchronized WiredVariableDefinition definition(String variableId) {
        if (this.closed || !this.operational || variableId == null) {
            return null;
        }
        return this.definitions.get(variableId);
    }

    public synchronized WiredVariableDefinition runtimeDefinition(String variableId) {
        if (!this.runtimeReady.getAsBoolean()) {
            return null;
        }
        return definition(variableId);
    }

    public synchronized VariableSnapshot variableSnapshot(String variableId) {
        WiredVariableDefinition definition = runtimeDefinition(variableId);
        if (definition == null) {
            return null;
        }
        List<WiredVariableValue> visibleValues;
        if (WiredQuestVariableRuntime.isQuestBacked(definition)) {
            var environment = Emulator.getGameEnvironment();
            Room room = environment == null
                    ? null : environment.getRoomManager().getRoom(this.roomId);
            visibleValues = WiredQuestVariableRuntime.values(room, definition);
        } else {
            visibleValues = valuesFor(variableId);
        }
        return new VariableSnapshot(
                definition, visibleValues, hashCatalogEntry(definition));
    }

    public synchronized List<WiredVariableDefinition> runtimeDefinitions(
            Predicate<WiredVariableDefinition> filter) {
        if (this.closed || !this.operational || !this.runtimeReady.getAsBoolean()
                || filter == null) {
            return List.of();
        }
        return this.definitions.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(WiredVariableDefinition::variableId))
                .toList();
    }

    public synchronized Diff diff(Map<String, Integer> clientHashes,
                                  Predicate<WiredVariableDefinition> authorized) {
        if (clientHashes == null || clientHashes.size() > MAX_DIFF_HASHES) {
            return Diff.rejected();
        }
        Snapshot snapshot = snapshot(authorized);
        if (!snapshot.available()) {
            return Diff.rejected();
        }
        List<String> removed = clientHashes.keySet().stream()
                .filter(Objects::nonNull)
                .filter(id -> !snapshot.variables().containsKey(id))
                .sorted()
                .toList();
        List<VariableSnapshot> changed = snapshot.variables().values().stream()
                .filter(variable -> !Objects.equals(clientHashes.get(variable.definition().variableId()),
                        variable.hash()))
                .toList();
        return new Diff(true, snapshot.aggregateHash(), removed, changed);
    }

    /** Room deletion cleanup; no packet behavior is implied. */
    public synchronized boolean deleteRoomState() {
        if (this.closed) {
            return this.roomStateDeleted;
        }
        if (!this.repository.deleteRoom(this.roomId)) {
            // The room itself is retained by the caller, but its variable
            // state must no longer be published after an uncertain cleanup.
            this.operational = false;
            return false;
        }
        this.definitions.clear();
        clearValues();
        this.persistedDefinitionHashes.clear();
        this.revision = 0L;
        this.persistedValueCount = 0;
        this.roomStateDeleted = true;
        this.closed = true;
        return true;
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.definitions.clear();
        clearValues();
        this.persistedDefinitionHashes.clear();
    }

    private boolean canAddValue(String variableId) {
        if (this.valueCount >= MAX_VALUES_PER_ROOM) {
            return false;
        }
        VariableValues values =
                this.valuesByVariable.get(variableId);
        return values == null || values.size() < MAX_VALUES_PER_DEFINITION;
    }

    private void reportTooManyVariables() {
        if (Emulator.getGameEnvironment() == null
                || Emulator.getGameEnvironment().getRoomManager() == null) {
            return;
        }
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.roomId);
        if (room != null) {
            WiredRoomMonitor.tooManyVariables(room);
        }
    }

    /** A committed mutation cannot publish into a room that has already unloaded. */
    private synchronized boolean isDispatchable() {
        return !this.closed && this.operational && this.runtimeReady.getAsBoolean();
    }

    private boolean commit(long nextRevision, PersistenceMutation mutation) {
        if (!this.repository.commit(this.roomId, this.revision, nextRevision, mutation)) {
            this.operational = false;
            return false;
        }
        return true;
    }

    private long nextRevision() {
        if (this.revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Wired variable revision exhausted");
        }
        return this.revision + 1L;
    }

    private void discardInvalidValues(WiredVariableDefinition definition) {
        VariableValues values =
                this.valuesByVariable.get(definition.variableId());
        if (values == null) {
            return;
        }
        int removed = values.removeIf(
                holder -> !definition.accepts(holder));
        this.valueCount -= removed;
        if (values.isEmpty()) {
            this.valuesByVariable.remove(definition.variableId());
        }
    }

    private void discardUnpersistableValues() {
        for (String variableId : List.copyOf(this.valuesByVariable.keySet())) {
            WiredVariableDefinition definition = this.definitions.get(variableId);
            if (definition == null || !definition.persistsValues()) {
                removeVariableValues(variableId);
            } else {
                VariableValues values = this.valuesByVariable.get(variableId);
                if (values != null) {
                    int removed = values.removeIf(holder ->
                            !definition.accepts(holder)
                                    || holder.isTransientFurni());
                    this.valueCount -= removed;
                    if (values.isEmpty()) {
                        this.valuesByVariable.remove(variableId);
                    }
                }
            }
        }
    }

    private WiredVariableValue value(
            String variableId, WiredVariableHolder holder) {
        VariableValues values =
                this.valuesByVariable.get(variableId);
        return values == null ? null : values.get(holder);
    }

    private WiredVariableValue putValue(WiredVariableValue value) {
        VariableValues values =
                this.valuesByVariable.computeIfAbsent(
                        value.variableId(), ignored -> new VariableValues());
        WiredVariableValue previous = values.put(value);
        if (previous == null) {
            this.valueCount++;
        }
        return previous;
    }

    private WiredVariableValue removeValueInternal(
            String variableId, WiredVariableHolder holder) {
        VariableValues values =
                this.valuesByVariable.get(variableId);
        if (values == null) {
            return null;
        }
        WiredVariableValue removed = values.remove(holder);
        if (removed != null) {
            this.valueCount--;
        }
        if (values.isEmpty()) {
            this.valuesByVariable.remove(variableId);
        }
        return removed;
    }

    private void removeVariableValues(String variableId) {
        VariableValues removed =
                this.valuesByVariable.remove(variableId);
        if (removed != null) {
            this.valueCount -= removed.size();
        }
    }

    private List<WiredVariableValue> valuesFor(String variableId) {
        VariableValues values =
                this.valuesByVariable.get(variableId);
        return values == null ? List.of() : values.ordered();
    }

    private List<WiredVariableValue> allValues() {
        return this.valuesByVariable.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().ordered().stream())
                .filter(value -> !value.holder().isTransientFurni())
                .toList();
    }

    private void clearValues() {
        this.valuesByVariable.clear();
        this.valueCount = 0;
    }

    private static boolean sameDefinition(WiredVariableDefinition left, WiredVariableDefinition right) {
        return left != null && right != null
                && left.roomId() == right.roomId()
                && left.definitionItemId() == right.definitionItemId()
                && left.variableId().equals(right.variableId())
                && left.type() == right.type()
                && left.name().equals(right.name())
                && left.availabilityCode() == right.availabilityCode()
                && left.hasValue() == right.hasValue()
                && left.invisible() == right.invisible();
    }

    static int hashDefinition(WiredVariableDefinition definition) {
        return digest(output -> {
            output.writeUTF(definition.variableId());
            output.writeInt(definition.roomId());
            output.writeInt(definition.definitionItemId());
            output.writeInt(definition.type().code);
            output.writeUTF(definition.name());
            output.writeInt(definition.availabilityCode());
            output.writeBoolean(definition.hasValue());
            output.writeBoolean(definition.invisible());
        });
    }

    /** Metadata-only emulation hash for the future 7106/7107 definition catalog. */
    static int hashCatalogEntry(WiredVariableDefinition definition) {
        return hashDefinition(definition);
    }

    static int hashAggregate(Map<String, VariableSnapshot> variables) {
        return digest(output -> {
            output.writeInt(variables.size());
            for (Map.Entry<String, VariableSnapshot> entry : variables.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeInt(entry.getValue().hash());
            }
        });
    }

    private static int digest(CanonicalWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash Wired variable state", exception);
        }
    }

    @FunctionalInterface
    private interface CanonicalWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private static final class VariableValues {
        private final Map<WiredVariableHolder, WiredVariableValue> byHolder =
                new HashMap<>();
        private List<WiredVariableValue> ordered = List.of();
        private boolean orderedDirty;

        private WiredVariableValue get(WiredVariableHolder holder) {
            return this.byHolder.get(holder);
        }

        private boolean contains(WiredVariableHolder holder) {
            return this.byHolder.containsKey(holder);
        }

        private WiredVariableValue put(WiredVariableValue value) {
            WiredVariableValue previous =
                    this.byHolder.put(value.holder(), value);
            this.orderedDirty = true;
            return previous;
        }

        private WiredVariableValue remove(WiredVariableHolder holder) {
            WiredVariableValue removed = this.byHolder.remove(holder);
            if (removed != null) {
                this.orderedDirty = true;
            }
            return removed;
        }

        private int removeIf(Predicate<WiredVariableHolder> predicate) {
            int before = this.byHolder.size();
            this.byHolder.keySet().removeIf(predicate);
            int removed = before - this.byHolder.size();
            if (removed != 0) {
                this.orderedDirty = true;
            }
            return removed;
        }

        private int size() {
            return this.byHolder.size();
        }

        private boolean isEmpty() {
            return this.byHolder.isEmpty();
        }

        private List<WiredVariableValue> ordered() {
            if (this.orderedDirty) {
                this.ordered = this.byHolder.values().stream()
                        .sorted(Comparator.comparing(
                                WiredVariableValue::holder))
                        .toList();
                this.orderedDirty = false;
            }
            return this.ordered;
        }
    }

    public enum MutationResult {
        CREATED,
        CHANGED,
        REMOVED,
        UNCHANGED,
        INVALID,
        OVERFLOW,
        LIMIT_REACHED,
        UNAVAILABLE,
        PERSISTENCE_FAILED
    }

    /** Result plus an event payload that is safe to publish after return. */
    public record MutationReceipt(MutationResult result, WiredVariableMutation mutation,
                                  BooleanSupplier dispatchAllowed) {
        public MutationReceipt {
            Objects.requireNonNull(result, "result");
        }

        static MutationReceipt of(MutationResult result) {
            return new MutationReceipt(result, null, () -> false);
        }

        public boolean committed() {
            return this.mutation != null;
        }

        public void dispatchTo(Consumer<WiredVariableMutation> dispatcher) {
            if (this.mutation != null && dispatcher != null
                    && this.dispatchAllowed != null && this.dispatchAllowed.getAsBoolean()) {
                dispatcher.accept(this.mutation);
            }
        }
    }

    public record VariableSnapshot(WiredVariableDefinition definition,
                                   List<WiredVariableValue> values, int hash) {
        public VariableSnapshot {
            values = List.copyOf(values);
        }
    }

    public record Snapshot(boolean available, long revision, int aggregateHash,
                           Map<String, VariableSnapshot> variables) {
        public Snapshot {
            variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        }

        static Snapshot unavailable(long revision) {
            return new Snapshot(false, revision, 0, Map.of());
        }
    }

    public record Diff(boolean accepted, int aggregateHash, List<String> removed,
                       List<VariableSnapshot> changed) {
        public Diff {
            removed = List.copyOf(removed);
            changed = List.copyOf(changed);
        }

        static Diff rejected() {
            return new Diff(false, 0, List.of(), List.of());
        }
    }

    interface Repository {
        LoadedRoom load(int roomId);
        boolean commit(int roomId, long expectedRevision, long nextRevision,
                       PersistenceMutation mutation);
        boolean deleteRoom(int roomId);
    }

    private static final class CapabilityDarkRepository implements Repository {
        private long revision;

        @Override
        public LoadedRoom load(int roomId) {
            return new LoadedRoom(true, 0L, Map.of(), List.of());
        }

        @Override
        public boolean commit(int roomId, long expectedRevision, long nextRevision,
                              PersistenceMutation mutation) {
            if (this.revision != expectedRevision || nextRevision != expectedRevision + 1L) {
                return false;
            }
            this.revision = nextRevision;
            return true;
        }

        @Override
        public boolean deleteRoom(int roomId) {
            this.revision = 0L;
            return true;
        }
    }

    static final class LoadedRoom {
        final boolean successful;
        final long revision;
        final Map<String, Integer> definitionHashes;
        final List<WiredVariableValue> values;

        LoadedRoom(boolean successful, long revision, Map<String, Integer> definitionHashes,
                   List<WiredVariableValue> values) {
            this.successful = successful;
            this.revision = revision;
            this.definitionHashes = Map.copyOf(definitionHashes);
            this.values = List.copyOf(values);
        }

        static LoadedRoom failed() {
            return new LoadedRoom(false, 0L, Map.of(), List.of());
        }
    }

    static final class PersistenceMutation {
        enum Kind { REVISION_ONLY, UPSERT_DEFINITION, DELETE_DEFINITION,
                    UPSERT_VALUE, DELETE_VALUE, DELETE_HOLDER, RECONCILE }

        final Kind kind;
        final String variableId;
        final Integer definitionHash;
        final WiredVariableValue value;
        final WiredVariableHolder holder;
        final Map<String, Integer> definitionHashes;
        final List<WiredVariableValue> values;
        final Integer allowedScopeCode;
        final boolean retainValues;

        private PersistenceMutation(Kind kind, String variableId, Integer definitionHash,
                                    WiredVariableValue value, WiredVariableHolder holder,
                                    Map<String, Integer> definitionHashes,
                                    List<WiredVariableValue> values,
                                    Integer allowedScopeCode, boolean retainValues) {
            this.kind = kind;
            this.variableId = variableId;
            this.definitionHash = definitionHash;
            this.value = value;
            this.holder = holder;
            this.definitionHashes = definitionHashes;
            this.values = values;
            this.allowedScopeCode = allowedScopeCode;
            this.retainValues = retainValues;
        }

        static PersistenceMutation revisionOnly() {
            return new PersistenceMutation(Kind.REVISION_ONLY, null, null, null, null,
                    null, null, null, false);
        }

        static PersistenceMutation upsertDefinition(String id, int hash,
                                                    WiredVariableDefinition definition,
                                                    List<WiredVariableValue> retainedValues) {
            WiredVariableHolder.Scope scope = definition.holderScope();
            return new PersistenceMutation(Kind.UPSERT_DEFINITION, id, hash, null, null,
                    null, List.copyOf(retainedValues), scope == null ? null : scope.code,
                    definition.persistsValues());
        }

        static PersistenceMutation deleteDefinition(String id) {
            return new PersistenceMutation(Kind.DELETE_DEFINITION, id, null, null, null,
                    null, null, null, false);
        }

        static PersistenceMutation upsertValue(WiredVariableValue value) {
            return new PersistenceMutation(Kind.UPSERT_VALUE, value.variableId(), null, value,
                    value.holder(), null, null, null, false);
        }

        static PersistenceMutation deleteValue(String id, WiredVariableHolder holder) {
            return new PersistenceMutation(Kind.DELETE_VALUE, id, null, null, holder,
                    null, null, null, false);
        }

        static PersistenceMutation deleteHolder(WiredVariableHolder holder) {
            return new PersistenceMutation(Kind.DELETE_HOLDER, null, null, null, holder,
                    null, null, null, false);
        }

        static PersistenceMutation reconcile(Map<String, Integer> hashes,
                                             List<WiredVariableValue> values) {
            return new PersistenceMutation(Kind.RECONCILE, null, null, null, null,
                    Map.copyOf(hashes), List.copyOf(values), null, false);
        }
    }
}
