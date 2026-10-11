package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredLimitException;
import com.eu.habbo.habbohotel.wired.core.WiredSafetyBudget;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only Remote Selector evaluator. It intentionally never invokes triggers,
 * conditions, effects, box animation or plugin callbacks.
 */
final class WiredRemoteSelectorResolver {
    private WiredRemoteSelectorResolver() {
    }

    static WiredTargets resolve(
            WiredSelectorRemote owner,
            Room room,
            WiredContext context,
            Collection<HabboItem> referenceItems,
            int aggregation,
            int randomAmount) {
        WiredTargets result = new WiredTargets();
        if (owner == null || room == null || context == null
                || room.getId() != owner.getRoomId()
                || room.getRoomSpecialTypes() == null) {
            return result;
        }

        long pathKey = WiredRemoteSelectorSupport.pathKey(room.getId(), owner.getId());
        try (WiredSafetyBudget.PathLease ignored = context.state().enter(
                WiredSafetyBudget.PathKind.REMOTE_SELECTOR, pathKey)) {
            List<StackReference> references = currentStackReferences(room, referenceItems);
            List<Long> stackKeys = references.stream().map(StackReference::stackKey).toList();
            long seed = WiredRemoteSelectorSupport.samplingSeed(
                    context.state().runId(), room.getId(), owner.getId(), stackKeys);
            List<StackReference> sampled = WiredRemoteSelectorSupport.sample(references, randomAmount, seed);
            context.budget().consumeFanOut(sampled.size());

            List<WiredRemoteSelectorSupport.AxisContribution<RoomUnit>> userContributions = new ArrayList<>();
            List<WiredRemoteSelectorSupport.AxisContribution<HabboItem>> itemContributions = new ArrayList<>();
            for (StackReference reference : sampled) {
                StackOutput output = evaluateSelectorStack(room, context, reference.x(), reference.y());
                userContributions.add(new WiredRemoteSelectorSupport.AxisContribution<>(
                        output.usersTouched(), output.users()));
                itemContributions.add(new WiredRemoteSelectorSupport.AxisContribution<>(
                        output.itemsTouched(), output.items()));
            }

            Set<RoomUnit> currentUsers = identitySet(snapshotCurrentUsers(room));
            for (RoomUnit unit : WiredRemoteSelectorSupport.aggregate(userContributions, aggregation)) {
                if (currentUsers.contains(unit)) {
                    result.addUser(unit);
                }
            }

            Set<HabboItem> currentItems = identitySet(snapshotCurrentItems(room));
            for (HabboItem item : WiredRemoteSelectorSupport.aggregate(itemContributions, aggregation)) {
                if (currentItems.contains(item)
                        && item.getRoomId() == room.getId()
                        && room.getHabboItemByDatabaseId(item.getId()) == item) {
                    result.addItem(item);
                }
            }
            return result;
        }
    }

    private static List<StackReference> currentStackReferences(
            Room room,
            Collection<HabboItem> referenceItems) {
        if (referenceItems == null || referenceItems.isEmpty()) {
            return List.of();
        }

        List<HabboItem> orderedItems = referenceItems.stream()
                .filter(item -> item != null && item.getId() > 0)
                .sorted(Comparator.comparingInt(HabboItem::getId))
                .toList();
        Map<Long, StackReference> byStack = new LinkedHashMap<>();
        for (HabboItem item : orderedItems) {
            if (item.getRoomId() != room.getId()
                    || room.getHabboItemByDatabaseId(item.getId()) != item
                    || !room.getFloorItems().contains(item)) {
                continue;
            }

            long stackKey = WiredRemoteSelectorSupport.stackKey(item.getX(), item.getY());
            byStack.putIfAbsent(stackKey,
                    new StackReference(item.getId(), item.getX(), item.getY(), stackKey));
            if (byStack.size() >= WiredSelectorRemote.MAX_REFERENCE_STACKS) {
                break;
            }
        }

        List<StackReference> result = new ArrayList<>(byStack.values());
        result.sort(Comparator.comparingLong(StackReference::stackKey)
                .thenComparingInt(StackReference::referenceItemId));
        return List.copyOf(result);
    }

    private static StackOutput evaluateSelectorStack(Room room, WiredContext context, int x, int y) {
        List<InteractionWiredSelector> selectors = new ArrayList<>(room.getRoomSpecialTypes().getSelectors(x, y));
        selectors.removeIf(selector -> selector == null
                || selector.getRoomId() != room.getId()
                || selector.getX() != x
                || selector.getY() != y
                || room.getHabboItemByDatabaseId(selector.getId()) != selector);
        selectors.sort(Comparator.comparingDouble(InteractionWiredSelector::getZ)
                .thenComparingInt(InteractionWiredSelector::getId));

        LinkedHashSet<RoomUnit> users = new LinkedHashSet<>();
        Set<RoomUnit> currentUsers = identitySet(snapshotCurrentUsers(room));
        for (RoomUnit unit : context.targets().users()) {
            if (currentUsers.contains(unit)) {
                users.add(unit);
            }
        }

        LinkedHashSet<HabboItem> items = new LinkedHashSet<>();
        Set<HabboItem> currentItems = identitySet(snapshotCurrentItems(room));
        for (HabboItem item : context.targets().items()) {
            if (currentItems.contains(item)
                    && item.getRoomId() == room.getId()
                    && room.getHabboItemByDatabaseId(item.getId()) == item) {
                items.add(item);
            }
        }

        // Each referenced stack gets an isolated target view, but retains the
        // root safety budget and branch ancestry through WiredState.fork().
        WiredContext nestedContext = context.forkForDelayedExecution();
        nestedContext.targets().setUsers(users);
        nestedContext.targets().setItems(items);

        boolean usersTouched = false;
        boolean itemsTouched = false;
        boolean usersNonFilterSeen = false;
        boolean itemsNonFilterSeen = false;

        for (InteractionWiredSelector selector : selectors) {
            if (!WiredFeatureCapabilityGuard.isRuntimeReady(selector)) {
                // Do not let a disabled nested selector become an empty result
                // that a containing invert flag could expand to the whole room.
                throw new WiredLimitException("Remote selector referenced an unavailable selector type: "
                        + selector.getType());
            }
            nestedContext.state().step();
            WiredTargets raw = selector.resolve(room, nestedContext);
            if (raw == null) {
                raw = new WiredTargets();
            }

            LinkedHashSet<RoomUnit> resolvedUsers = currentUsers(raw.users(), currentUsers);
            LinkedHashSet<HabboItem> resolvedItems = currentItems(room, raw.items(), currentItems);
            nestedContext.budget().consumeTargets(resolvedUsers.size() + resolvedItems.size());

            if (selector.getType().isUser) {
                usersTouched = true;
                if (selector.isInvert()) {
                    int rawCount = resolvedUsers.size();
                    resolvedUsers = new LinkedHashSet<>(snapshotCurrentUsers(room));
                    resolvedUsers.removeAll(currentUsers(raw.users(), currentUsers));
                    nestedContext.budget().consumeTargets(Math.max(0, resolvedUsers.size() - rawCount));
                }
                if (selector.isFilter()) {
                    users.retainAll(resolvedUsers);
                } else {
                    if (!usersNonFilterSeen) {
                        users.clear();
                    }
                    users.addAll(resolvedUsers);
                    usersNonFilterSeen = true;
                }
            }

            if (selector.getType().isFurni) {
                itemsTouched = true;
                if (selector.isInvert()) {
                    int rawCount = resolvedItems.size();
                    resolvedItems = new LinkedHashSet<>(snapshotCurrentItems(room));
                    resolvedItems.removeAll(currentItems(room, raw.items(), currentItems));
                    nestedContext.budget().consumeTargets(Math.max(0, resolvedItems.size() - rawCount));
                }
                if (selector.isFilter()) {
                    items.retainAll(resolvedItems);
                } else {
                    if (!itemsNonFilterSeen) {
                        items.clear();
                    }
                    items.addAll(resolvedItems);
                    itemsNonFilterSeen = true;
                }
            }

            // The next selector sees exactly the target state produced by the
            // previous selector, matching WiredEngine's ordinary pipeline.
            nestedContext.targets().setUsers(users);
            nestedContext.targets().setItems(items);
        }

        return new StackOutput(usersTouched, itemsTouched,
                Collections.unmodifiableSet(users), Collections.unmodifiableSet(items));
    }

    private static List<RoomUnit> snapshotCurrentUsers(Room room) {
        List<RoomUnit> users = new ArrayList<>(room.getRoomUnits());
        users.removeIf(unit -> unit == null);
        users.sort(Comparator.comparingInt(RoomUnit::getId));
        return List.copyOf(users);
    }

    private static List<HabboItem> snapshotCurrentItems(Room room) {
        List<HabboItem> items = new ArrayList<>(room.getFloorItems());
        items.removeIf(item -> item == null
                || item.getRoomId() != room.getId()
                || room.getHabboItemByDatabaseId(item.getId()) != item);
        items.sort(Comparator.comparingInt(HabboItem::getId));
        return List.copyOf(items);
    }

    private static LinkedHashSet<RoomUnit> currentUsers(
            Collection<RoomUnit> candidates,
            Set<RoomUnit> currentUsers) {
        LinkedHashSet<RoomUnit> result = new LinkedHashSet<>();
        for (RoomUnit unit : candidates) {
            if (unit != null && currentUsers.contains(unit)) {
                result.add(unit);
            }
        }
        return result;
    }

    private static LinkedHashSet<HabboItem> currentItems(
            Room room,
            Collection<HabboItem> candidates,
            Set<HabboItem> currentItems) {
        LinkedHashSet<HabboItem> result = new LinkedHashSet<>();
        for (HabboItem item : candidates) {
            if (item != null
                    && currentItems.contains(item)
                    && item.getRoomId() == room.getId()
                    && room.getHabboItemByDatabaseId(item.getId()) == item) {
                result.add(item);
            }
        }
        return result;
    }

    private static <T> Set<T> identitySet(Collection<T> values) {
        Set<T> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(values);
        return result;
    }

    private record StackReference(int referenceItemId, int x, int y, long stackKey) {
    }

    private record StackOutput(
            boolean usersTouched,
            boolean itemsTouched,
            Set<RoomUnit> users,
            Set<HabboItem> items) {
    }
}
