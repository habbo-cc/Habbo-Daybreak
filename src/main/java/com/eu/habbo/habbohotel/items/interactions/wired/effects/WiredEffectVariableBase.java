package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import com.eu.habbo.habbohotel.wired.variables.WiredGeneratedVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Shared AIR-shaped plumbing for Core Variables actions 39--41. */
abstract class WiredEffectVariableBase extends InteractionWiredEffect {
    static final int TARGET_FURNI = 0;
    static final int TARGET_USER = 1;
    static final int TARGET_GLOBAL = -10;
    static final int TARGET_CONTEXT = -20;
    static final int MAX_DELAY = 20;

    private static final String PERSISTENCE_VERSION = "v1";
    final List<HabboItem> primaryItems = new ArrayList<>();
    final List<HabboItem> secondaryItems = new ArrayList<>();

    WiredEffectVariableBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    WiredEffectVariableBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public final void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public final void onPickUp() {
        this.primaryItems.clear();
        this.secondaryItems.clear();
        this.setDelay(0);
        resetVariableConfiguration();
    }

    abstract void resetVariableConfiguration();

    @Override
    protected final Collection<HabboItem> getSelectedItems() {
        return this.primaryItems;
    }

    @Override
    protected final Collection<HabboItem> getSelectedItems2() {
        return this.secondaryItems;
    }

    @Override
    protected final boolean supportsFurniPicking() {
        return true;
    }

    @Override
    protected final boolean isWiredAdvancedMode() {
        return true;
    }

    @Override
    protected final int[] getAllowedFurniSourcesForSlot(int slot) {
        return new int[] {
                slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_PICKED_2,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_TRIGGERING_ITEM,
                FURNI_SOURCE_SIGNAL
        };
    }

    @Override
    protected final int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR, USER_SOURCE_SIGNAL};
    }

    @Override
    protected final int getDefaultFurniSourceForSlot(int slot) {
        return slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_PICKED_2;
    }

    @Override
    protected final int getDefaultUserSourceForSlot(int slot) {
        return USER_SOURCE_TRIGGERING_USER;
    }

    final Room requireEditorRoom(WiredSettings settings, com.eu.habbo.habbohotel.gameclients.GameClient client)
            throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || !WiredFeatureCapabilityGuard.isEditorReady(client, room, this)) {
            return null;
        }
        if (settings == null || settings.getStringParam() == null || !settings.getStringParam().isEmpty()
                || settings.getDelay() < 0 || settings.getDelay() > MAX_DELAY) {
            throw new WiredSaveException("Invalid Variable action data");
        }
        return room;
    }

    final WiredVariableDefinition validateDefinition(Room room, String variableId, int target,
                                                     boolean allowGlobal) throws WiredSaveException {
        if (!isValidVariableId(variableId) || !isTargetCode(target, allowGlobal)) {
            throw new WiredSaveException("Invalid Variable action selection");
        }
        if (target == TARGET_CONTEXT) {
            WiredVariableContext context = contextDefinition(room, variableId);
            if (context == null) {
                var resolved = WiredVariableAliasOperations.resolve(room, variableId);
                if (resolved != null && resolved.definition().type()
                        == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT) return resolved.definition();
                throw new WiredSaveException("Variable target does not match its definition");
            }
            return WiredVariableDefinition.create(room.getId(), context.getId(), context.getType(),
                    context.variableName(), 0, context.hasValue(), false);
        }
        WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
        if (manager == null || !manager.isOperational()) {
            throw new WiredSaveException("Variables are unavailable");
        }
        WiredVariableDefinition definition = manager.runtimeDefinition(variableId);
        if (definition == null || !matchesTarget(definition, target)) {
            throw new WiredSaveException("Variable target does not match its definition");
        }
        return definition;
    }

    final WiredVariableDefinition validateOperation(Room room, String id, int target,
            boolean allowGlobal, boolean creationDeletion) throws WiredSaveException {
        WiredVariableDefinition definition = validateDefinition(room, id, target, allowGlobal);
        boolean allowed = canOperate(room, id, target, creationDeletion);
        if (!allowed) throw new WiredSaveException("Variable does not support this operation");
        return definition;
    }

    final boolean canOperate(Room room, String id, int target, boolean creationDeletion) {
        var context = target == TARGET_CONTEXT ? contextDefinition(room, id) : null;
        return context != null ? creationDeletion || context.hasValue()
                : creationDeletion ? WiredVariableAliasOperations.canCreateDelete(room, id)
                : WiredVariableAliasOperations.canWriteValue(room, id);
    }

    final WiredVariableDefinition validateReadableDefinition(Room room, String variableId, int target,
                                                             boolean allowGlobal) throws WiredSaveException {
        try {
            var definition = validateDefinition(room, variableId, target, allowGlobal);
            if (!definition.hasValue()) throw new WiredSaveException("Variable has no numeric value");
            return definition;
        } catch (WiredSaveException exception) {
            WiredVariableDefinition parent = WiredGeneratedVariableRuntime.parentDefinition(room, variableId);
            if (parent != null && matchesTarget(parent, target)) return parent;
            throw exception;
        }
    }

    final void saveSelections(Room room, int[] primaryIds, int[] secondaryIds,
                              boolean secondaryAllowed) throws WiredSaveException {
        loadSelection(room, primaryIds, this.primaryItems);
        if (secondaryAllowed) {
            loadSelection(room, secondaryIds, this.secondaryItems);
        } else if (secondaryIds != null && secondaryIds.length != 0) {
            throw new WiredSaveException("Unexpected secondary furni selection");
        } else {
            this.secondaryItems.clear();
        }
    }

    final List<WiredVariableHolder> resolveHolders(WiredContext context, int target, int slot) {
        if (context == null || context.room() == null) {
            return List.of();
        }
        Room room = context.room();
        Set<WiredVariableHolder> holders = new LinkedHashSet<>();
        switch (target) {
            case TARGET_FURNI -> resolveFurniSource(context, getWiredFurniSourceTypes(), slot,
                    this.primaryItems, this.secondaryItems).stream()
                    .filter(item -> isCurrent(room, item))
                    .sorted(Comparator.comparingInt(HabboItem::getId))
                    .forEach(item -> holders.add(WiredVariableHolder.furni(item.getId())));
            case TARGET_USER -> resolveUserSource(context, getWiredUserSourceTypes(), slot).stream()
                    .filter(unit -> isCurrent(room, unit))
                    .map(room::getHabbo)
                    .filter(habbo -> habbo != null && habbo.getHabboInfo() != null)
                    .sorted(Comparator.comparingInt(habbo -> habbo.getHabboInfo().getId()))
                    .forEach(habbo -> holders.add(WiredVariableHolder.user(habbo.getHabboInfo().getId())));
            case TARGET_GLOBAL -> holders.add(WiredVariableHolder.room());
            default -> {
            }
        }
        return List.copyOf(holders);
    }

    final Consumer<com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation> mutationDispatcher(
            WiredContext context) {
        return mutation -> WiredManager.triggerVariableChanged(
                context.room(), context.actor().orElse(null), mutation);
    }

    final Consumer<WiredVariableMutation> mutationDispatcher(
            WiredContext context, String exposedVariableId) {
        return mutationDispatcher(context, exposedVariableId, null);
    }

    final Consumer<WiredVariableMutation> mutationDispatcher(
            WiredContext context, String exposedVariableId, Integer forcedOrigin) {
        return mutation -> {
            if (mutation == null || exposedVariableId == null
                    || exposedVariableId.isBlank()) {
                return;
            }
            var source = WiredVariableAliasOperations.resolve(context.room(), exposedVariableId);
            boolean anotherRoom = source != null && source.manager().roomId() != context.room().getId();
            WiredVariableMutation sourced = forcedOrigin == null ? mutation
                    : mutation.withProvenance(mutation.boxId(), anotherRoom ? forcedOrigin
                    : WiredVariableMutation.CHANGE_ORIGIN_IN_ROOM);
            WiredVariableMutation exposed = exposedVariableId.equals(sourced.variableId())
                    ? sourced
                    : new WiredVariableMutation(
                    exposedVariableId,
                    sourced.holder(),
                    sourced.beforeValue(),
                    sourced.afterValue(),
                    sourced.kind(),
                    sourced.revision(),
                    sourced.boxId(),
                    sourced.changeOrigin());
            WiredManager.triggerVariableChanged(
                    context.room(), context.actor().orElse(null), exposed);
        };
    }

    final void consumeTargets(WiredContext context, Collection<WiredVariableHolder> holders) {
        if (context != null && holders != null && !holders.isEmpty()) {
            context.budget().consumeTargets(holders.size());
        }
    }

    final String encode(String... fields) {
        return PERSISTENCE_VERSION + '.' + String.join(".", fields);
    }

    final String[] decode(String data, int fieldCount) {
        if (data == null || !data.startsWith(PERSISTENCE_VERSION + '.')) {
            return null;
        }
        String[] fields = data.split("\\.", -1);
        return fields.length == fieldCount && PERSISTENCE_VERSION.equals(fields[0]) ? fields : null;
    }

    static String encodeVariableId(String id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    static String decodeVariableId(String encoded) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            return isValidVariableId(decoded) ? decoded : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    final String encodeItemIds(Collection<HabboItem> items) {
        return items.stream().filter(item -> item != null).map(HabboItem::getId).distinct()
                .sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    final void decodeItemIds(Room room, String encoded, List<HabboItem> target) {
        target.clear();
        if (encoded == null || encoded.isEmpty() || room == null) {
            return;
        }
        String[] ids = encoded.split(",", -1);
        if (ids.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            return;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        try {
            for (String id : ids) {
                int databaseId = Integer.parseInt(id);
                if (databaseId <= 0 || !unique.add(databaseId)) {
                    target.clear();
                    return;
                }
                HabboItem item = room.getHabboItemByDatabaseId(databaseId);
                if (!isCurrent(room, item)) {
                    target.clear();
                    return;
                }
                target.add(item);
            }
        } catch (NumberFormatException ignored) {
            target.clear();
        }
    }

    static boolean validSignedIntParts(int high, int low) {
        return (high == 0 && low >= 0) || (high == -1 && low < 0);
    }

    private static void loadSelection(Room room, int[] visibleIds, List<HabboItem> target)
            throws WiredSaveException {
        target.clear();
        if (visibleIds == null || visibleIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Invalid furni selection");
        }
        Set<Integer> unique = new LinkedHashSet<>();
        for (int visibleId : visibleIds) {
            HabboItem item = room.getHabboItem(visibleId);
            if (!isCurrent(room, item) || !unique.add(item.getId())) {
                target.clear();
                throw new WiredSaveException("Selected furni is not in this room");
            }
            target.add(item);
        }
    }

    private static boolean isCurrent(Room room, HabboItem item) {
        return room != null && item != null && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item;
    }

    private static boolean isCurrent(Room room, RoomUnit unit) {
        return unit != null && unit.getRoom() == room && unit.isInRoom()
                && room.getRoomUnits().contains(unit);
    }

    private static boolean isValidVariableId(String id) {
        return id != null && !id.isBlank() && id.length() <= 128;
    }

    private static boolean isTargetCode(int target, boolean allowGlobal) {
        return target == TARGET_FURNI || target == TARGET_USER || target == TARGET_CONTEXT
                || (allowGlobal && target == TARGET_GLOBAL);
    }

    private static boolean matchesTarget(WiredVariableDefinition definition, int target) {
        return definition != null && switch (target) {
            case TARGET_FURNI -> definition.holderScope() == WiredVariableHolder.Scope.FURNI;
            case TARGET_USER -> definition.holderScope() == WiredVariableHolder.Scope.USER;
            case TARGET_GLOBAL -> definition.holderScope() == WiredVariableHolder.Scope.ROOM;
            case TARGET_CONTEXT -> definition.type() == com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT;
            default -> false;
        };
    }

    final WiredVariableContext contextDefinition(Room room, String variableId) {
        if (room == null || variableId == null || !variableId.startsWith("room:")) return null;
        try {
            int id = Integer.parseInt(variableId.substring(5));
            return room.getRoomSpecialTypes().getVariable(id) instanceof WiredVariableContext context ? context : null;
        } catch (NumberFormatException ignored) { return null; }
    }
}
