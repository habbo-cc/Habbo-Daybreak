package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectsDataUpdateMessageComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base abstract class for all wired furniture items (triggers, effects, conditions, extras).
 * <p>
 * The wired system allows room owners to create automated behaviors in their rooms.
 * It consists of:
 * <ul>
 *   <li><b>Triggers</b> ({@link InteractionWiredTrigger}) - Events that start the wired chain</li>
 *   <li><b>Conditions</b> ({@link InteractionWiredCondition}) - Requirements that must be met</li>
 *   <li><b>Effects</b> ({@link InteractionWiredEffect}) - Actions that are executed</li>
 *   <li><b>Extras</b> ({@link InteractionWiredExtra}) - Modifiers like random selection</li>
 * </ul>
 * </p>
 * <p>
 * Wired items at the same tile coordinates form a "stack" and execute together.
 * The {@link com.eu.habbo.habbohotel.wired.core.WiredManager} orchestrates execution.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Cooldown system to prevent spam triggering</li>
 *   <li>Per-user execution caching with automatic cleanup</li>
 *   <li>JSON-based data persistence via {@link #getWiredData()}</li>
 * </ul>
 * </p>
 * 
 * @see com.eu.habbo.habbohotel.wired.core.WiredManager
 * @see com.eu.habbo.habbohotel.rooms.RoomSpecialTypes
 */
public abstract class InteractionWired extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionWired.class);
    protected static final int FURNI_SOURCE_TRIGGERING_ITEM = 0;
    protected static final int USER_SOURCE_TRIGGERING_USER = 0;
    protected static final int FURNI_SOURCE_PICKED_1 = 100;
    protected static final int FURNI_SOURCE_PICKED_2 = 101;
    protected static final int FURNI_SOURCE_DUAL_MODE = 110;
    protected static final int FURNI_SOURCE_SELECTOR = 200;
    protected static final int USER_SOURCE_SELECTOR = 200;
    protected static final int FURNI_SOURCE_SIGNAL = 201;
    protected static final int USER_SOURCE_SIGNAL = 201;
    protected static final int FURNI_SOURCE_ROOM_FURNI = 900;
    protected static final int USER_SOURCE_ROOM_USERS = 900;
    
    /**
     * Maximum number of entries in the user execution cache to prevent memory leaks.
     */
    private static final int MAX_USER_CACHE_SIZE = 500;
    
    /**
     * Cache entries older than this (in milliseconds) will be cleaned up.
     * Default: 5 minutes
     */
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;

    /** Defensive limits for counted Wired 2.0 fields. */
    private static final int MAX_WIRED_INT_PARAMS = 64;
    private static final int MAX_WIRED_SOURCE_TYPES = 32;
    private static final int MAX_WIRED_VARIABLE_IDS = 32;
    private static final int MAX_WIRED_STRING_LENGTH = 32000;
    
    private long cooldown;
    private long lastBoxAnimationMs;
    private final ConcurrentHashMap<Long, Long> userExecutionCache = new ConcurrentHashMap<>();

    /**
     * Minimum interval between wired-box "light up" animations. A fast trigger (e.g. a 50ms
     * short repeater) fires every tick, but the cosmetic box blink must not strobe at that rate;
     * Habbo blinks the box much slower. Tune this single value to taste.
     */
    private static final long BOX_ANIMATION_MIN_INTERVAL_MS = 500L;

    // Wired 2.0 advanced source settings. Generic storage so every wired type
    // round-trips furniSourceTypes/userSourceTypes; persisted in items.wired_sources.
    // Subclasses with type-specific persistence override the getWired*SourceTypes
    // getters and keep their JSON data as the authority.
    protected int[] wiredFurniSourceTypes = new int[0];
    protected int[] wiredUserSourceTypes = new int[0];

    InteractionWired(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    InteractionWired(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    /**
     * Executes this wired item's logic.
     * 
     * @param roomUnit the room unit that triggered this (may be null for non-user triggers)
     * @param room the room where this is happening
     * @param stuff additional context data passed from the trigger
     * @return true if execution was successful, false otherwise
     */
    public abstract boolean execute(RoomUnit roomUnit, Room room, Object[] stuff);

    public abstract String getWiredData();

    public abstract void serializeWiredData(ServerMessage message, Room room);

    public abstract void loadWiredData(ResultSet set, Room room) throws SQLException;

    @Override
    public void run() {
        if (this.needsUpdate()) {
            String wiredData = this.getWiredData();

            if (wiredData == null) {
                wiredData = "";
            }

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE items SET wired_data = ?, wired_sources = ? WHERE id = ?")) {
                if (this.getRoomId() != 0) {
                    statement.setString(1, wiredData);
                    statement.setString(2, this.getWiredSourcesData());
                } else {
                    statement.setString(1, "");
                    statement.setString(2, "");
                }
                statement.setInt(3, this.getId());
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
        super.run();
    }

    @Override
    public void onPickUp(Room room) {
        this.wiredFurniSourceTypes = new int[0];
        this.wiredUserSourceTypes = new int[0];
        this.onPickUp();
    }

    public abstract void onPickUp();

    public void activateBox(Room room) {
        this.activateBox(room, (RoomUnit)null, 0L);
    }

    public void activateBox(Room room, RoomUnit roomUnit, long millis) {
        this.activateBox(room, roomUnit, millis, null);
    }

    /**
     * Toggle this wired box's highlight state.
     * <p>
     * habbo.com lights its boxes with a batched {@link ObjectsDataUpdateMessageComposer}: every box that
     * lights up during one wired tick ships in a single packet (count + roomVisibleId + serialized
     * extradata per item). The old {@link com.eu.habbo.messages.outgoing.rooms.items.OneWayDoorStatusMessageComposer}
     * is the wrong packet for this — it carries a door open/closed int, not the box extradata, so the
     * client never animated the highlight the way .com does.
     *
     * @param boxUpdates when non-null, the toggled box is added to this set instead of being sent
     *                   immediately, letting the caller ({@link com.eu.habbo.habbohotel.wired.core.WiredEngine})
     *                   flush every box lit during one event in one packet — matching .com's per-tick batch.
     *                   When null (direct furniture interaction), the toggle is sent right away as a
     *                   single-item update.
     */
    public void activateBox(Room room, RoomUnit roomUnit, long millis, Set<HabboItem> boxUpdates) {
        if(!room.isHideWired()) {
            long now = millis > 0 ? millis : System.currentTimeMillis();
            if (now - this.lastBoxAnimationMs >= BOX_ANIMATION_MIN_INTERVAL_MS) {
                this.lastBoxAnimationMs = now;
                this.setExtradata(this.getExtradata().equals("1") ? "0" : "1");
                if (boxUpdates != null) {
                    boxUpdates.add(this);
                } else {
                    room.sendComposer(new ObjectsDataUpdateMessageComposer(Collections.singleton((HabboItem) this)).compose());
                }
            }
        }
        if (roomUnit != null) {
            this.addUserExecutionCache(roomUnit.getId(), millis);
        }
    }

    protected long requiredCooldown() {
        return 50L;
    }


    public boolean canExecute(long newMillis) {
        return newMillis - this.cooldown >= this.requiredCooldown();
    }

    /**
     * Movement effects override this {@code true} so a fast trigger (e.g. a 50ms short repeater)
     * can re-run them every tick and stream smooth {@link com.eu.habbo.messages.outgoing.rooms.items.WiredMovementsMessageComposer}
     * slides. All other effects stay gated by their cooldown per triggering room unit.
     */
    public boolean bypassExecutionCooldown() {
        return false;
    }

    public void setCooldown(long newMillis) {
        this.cooldown = newMillis;
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    public boolean userCanExecute(int roomUnitId, long timestamp) {
        if (roomUnitId == -1) {
            return true;
        } else {
            if (this.userExecutionCache.containsKey((long)roomUnitId)) {
                long lastTimestamp = this.userExecutionCache.get((long)roomUnitId);
                return timestamp - lastTimestamp >= Math.max(100L, this.requiredCooldown());
            }

            return true;
        }
    }

    public void clearUserExecutionCache() {
        this.userExecutionCache.clear();
    }

    public void addUserExecutionCache(int roomUnitId, long timestamp) {
        // Enforce max size limit to prevent memory leaks
        if (this.userExecutionCache.size() >= MAX_USER_CACHE_SIZE) {
            cleanExpiredCacheEntries(timestamp);
            
            // If still too large after cleanup, remove oldest entries
            if (this.userExecutionCache.size() >= MAX_USER_CACHE_SIZE) {
                // Remove approximately 10% of entries
                int toRemove = MAX_USER_CACHE_SIZE / 10;
                Iterator<Map.Entry<Long, Long>> iterator = this.userExecutionCache.entrySet().iterator();
                while (iterator.hasNext() && toRemove > 0) {
                    iterator.next();
                    iterator.remove();
                    toRemove--;
                }
            }
        }
        this.userExecutionCache.put((long) roomUnitId, timestamp);
    }
    
    /**
     * Removes cache entries older than CACHE_EXPIRY_MS.
     * @param currentTimestamp the current timestamp to compare against
     */
    public void cleanExpiredCacheEntries(long currentTimestamp) {
        this.userExecutionCache.entrySet().removeIf(
            entry -> currentTimestamp - entry.getValue() > CACHE_EXPIRY_MS
        );
    }
    
    /**
     * Gets the current size of the user execution cache.
     * @return the number of cached entries
     */
    public int getUserExecutionCacheSize() {
        return this.userExecutionCache.size();
    }

    // ===== July Wired 2.0 editor serialization =====
    // Used by every trigger/effect/condition/selector/add-on/variable data
    // composer. Subclasses override only the fields and context blocks they own.

    /** Category drives the type-specific blocks. Mid-level bases override. */
    protected WiredCategoryType getWiredCategory() { return WiredCategoryType.TRIGGER; }

    /** Type code sent in the new packet. Mid-level bases return getType().code. */
    protected int getWiredTypeCode() { return 0; }

    protected int getMaxFurniSelection() { return WiredManager.MAXIMUM_FURNI_SELECTION; }
    protected Collection<HabboItem> getSelectedItems() { return Collections.emptyList(); }
    protected Collection<HabboItem> getSelectedItems2() { return Collections.emptyList(); }

    /**
     * Read-only inspection hook used by July's Wired Menu reference panel.
     * It intentionally consults the typed selections already exposed by each
     * editor implementation and never attempts to parse heterogeneous JSON.
     */
    public final boolean referencesConfiguredFurni(int databaseItemId) {
        if (databaseItemId == 0) {
            return false;
        }
        return containsDatabaseItem(getSelectedItems(), databaseItemId)
                || containsDatabaseItem(getSelectedItems2(), databaseItemId);
    }

    private static boolean containsDatabaseItem(
            Collection<HabboItem> items, int databaseItemId) {
        if (items == null) {
            return false;
        }
        for (HabboItem item : items) {
            if (item != null && item.getId() == databaseItemId) {
                return true;
            }
        }
        return false;
    }
    protected String getWiredStringParam() { return ""; }
    protected int[] getWiredIntParams() { return new int[0]; }
    protected String[] getWiredVariableIds() { return new String[0]; }
    protected int[] getWiredFurniSourceTypes() {
        int slots = getFurniSourceSlotCount();
        int[] out = new int[slots];
        for (int i = 0; i < slots; i++) {
            out[i] = (i < this.wiredFurniSourceTypes.length) ? this.wiredFurniSourceTypes[i] : getDefaultFurniSourceForSlot(i);
        }
        return out;
    }

    protected int[] getWiredUserSourceTypes() {
        int slots = getUserSourceSlotCount();
        int[] out = new int[slots];
        for (int i = 0; i < slots; i++) {
            out[i] = (i < this.wiredUserSourceTypes.length) ? this.wiredUserSourceTypes[i] : getDefaultUserSourceForSlot(i);
        }
        return out;
    }
    protected int getWiredDelay() { return 0; }
    protected int getWiredQuantifierCode() { return 0; }
    protected byte getWiredQuantifierType() { return 0; }
    protected boolean isWiredInvert() { return false; }
    protected boolean isWiredFilter() { return false; }
    protected boolean isWiredAdvancedMode() { return false; }
    protected boolean isWiredAllowWallFurni() { return false; }
    protected boolean supportsFurniPicking() { return false; }
    protected boolean supportsUserPicking() { return false; }
    protected int getFurniSourceSlotCount() { return supportsFurniPicking() ? 1 : 0; }
    protected int getUserSourceSlotCount() { return supportsUserPicking() ? 1 : 0; }
    protected int[] getAllowedFurniSourcesForSlot(int slot) { return new int[] { FURNI_SOURCE_PICKED_1, FURNI_SOURCE_SELECTOR }; }
    protected int[] getAllowedUserSourcesForSlot(int slot) { return new int[] { USER_SOURCE_TRIGGERING_USER, USER_SOURCE_SELECTOR }; }
    protected int getDefaultFurniSourceForSlot(int slot) { return FURNI_SOURCE_PICKED_1; }
    protected int getDefaultUserSourceForSlot(int slot) { return USER_SOURCE_TRIGGERING_USER; }

    /**
     * Stores the saved advanced source settings, clamping each slot value to the
     * allowed list for that slot (unknown values fall back to the slot default).
     */
    public void setWiredSourceTypes(int[] furniSourceTypes, int[] userSourceTypes) {
        int furniSlots = getFurniSourceSlotCount();
        int[] furni = new int[furniSlots];
        for (int i = 0; i < furniSlots; i++) {
            int requested = (furniSourceTypes != null && i < furniSourceTypes.length) ? furniSourceTypes[i] : getDefaultFurniSourceForSlot(i);
            furni[i] = clampToAllowed(requested, getAllowedFurniSourcesForSlot(i), getDefaultFurniSourceForSlot(i));
        }
        int userSlots = getUserSourceSlotCount();
        int[] users = new int[userSlots];
        for (int i = 0; i < userSlots; i++) {
            int requested = (userSourceTypes != null && i < userSourceTypes.length) ? userSourceTypes[i] : getDefaultUserSourceForSlot(i);
            users[i] = clampToAllowed(requested, getAllowedUserSourcesForSlot(i), getDefaultUserSourceForSlot(i));
        }
        this.wiredFurniSourceTypes = furni;
        this.wiredUserSourceTypes = users;
    }

    private static int clampToAllowed(int value, int[] allowed, int fallback) {
        if (allowed != null) {
            for (int a : allowed) {
                if (a == value) {
                    return value;
                }
            }
        }
        return fallback;
    }

    /** True when every stored slot equals its default (nothing worth persisting). */
    private boolean wiredSourcesAreDefault() {
        for (int i = 0; i < this.wiredFurniSourceTypes.length; i++) {
            if (this.wiredFurniSourceTypes[i] != getDefaultFurniSourceForSlot(i)) {
                return false;
            }
        }
        for (int i = 0; i < this.wiredUserSourceTypes.length; i++) {
            if (this.wiredUserSourceTypes[i] != getDefaultUserSourceForSlot(i)) {
                return false;
            }
        }
        return true;
    }

    /** Serialized form for the items.wired_sources column; empty when defaults. */
    public String getWiredSourcesData() {
        if (wiredSourcesAreDefault()) {
            return "";
        }
        return WiredManager.getGson().toJson(new WiredSourcesData(this.wiredFurniSourceTypes, this.wiredUserSourceTypes));
    }

    /** Restores from the items.wired_sources column. Null/empty/corrupt-safe. */
    public void loadWiredSourcesData(String data) {
        this.wiredFurniSourceTypes = new int[0];
        this.wiredUserSourceTypes = new int[0];
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            WiredSourcesData parsed = WiredManager.getGson().fromJson(data, WiredSourcesData.class);
            if (parsed != null) {
                setWiredSourceTypes(parsed.f, parsed.u);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse wired_sources for item {}: {}", this.getId(), data, e);
        }
    }

    private static class WiredSourcesData {
        int[] f;
        int[] u;

        WiredSourcesData(int[] f, int[] u) {
            this.f = f;
            this.u = u;
        }
    }

    /**
     * Writes the Wired 2.0 data payload (see wired-port-plan.md "New Server
     * Serialization Order"). Subclasses may append the July context blocks they
     * actually own; the safe default remains an empty context.
     */
    public final void serializeWiredDataV2(ServerMessage message, Room room) {
        if (sanitizeSelectedItems(room)) {
            this.needsUpdate(true);
        }

        WiredCategoryType category = getWiredCategory();

        message.appendInt(getMaxFurniSelection());            // furniLimit
        appendVisibleIds(message, getSelectedItemVisibleIds(room));  // stuffIds
        appendVisibleIds(message, getSelectedItem2VisibleIds(room)); // stuffIds2
        message.appendInt(this.getBaseItem().getSpriteId());  // stuffTypeId
        message.appendInt(this.getRoomVisibleId());           // id
        message.appendString(getWiredStringParam());          // stringParam
        appendInts(message, getWiredIntParams());             // intParams
        appendStrings(message, getWiredVariableIds());        // variableIds
        appendInts(message, getWiredFurniSourceTypes());      // furniSourceTypes
        appendInts(message, getWiredUserSourceTypes());       // userSourceTypes
        message.appendInt(getWiredTypeCode());                // code

        // readDefinitionSpecifics
        switch (category) {
            case EFFECT:    message.appendInt(getWiredDelay()); break;
            case CONDITION: message.appendInt(getWiredQuantifierCode()); break;
            case SELECTOR:  message.appendBoolean(isWiredFilter()); message.appendBoolean(isWiredInvert()); break;
            default: break; // TRIGGER, ADDON, VARIABLE
        }

        message.appendBoolean(isWiredAdvancedMode());         // advancedMode
        serializeInputSourcesConf(message);                   // InputSourcesConf
        message.appendBoolean(isWiredAllowWallFurni());       // allowWallFurni

        // readTypeSpecifics
        if (category == WiredCategoryType.CONDITION) {
            message.appendByte((int) getWiredQuantifierType());
            message.appendBoolean(isWiredInvert());
        }

        serializeWiredContext(message, room);                 // WiredContext block count + blocks
        message.appendInt(0);                                 // defaultIntParams count
    }

    /** Writes the complete unframed July WiredContext section, including its block count. */
    protected void serializeWiredContext(ServerMessage message, Room room) {
        message.appendInt(1);
        serializeVariableCatalogContext(message, room);
    }

    /** Block 0 is shared by all variable-using editors and their extra context blocks. */
    protected final void serializeVariableCatalogContext(ServerMessage message, Room room) {
        message.appendInt(0); // ALL_VARIABLES_IN_ROOM
        message.appendInt(WiredVariableCatalog.forRoom(room).aggregateHash());
    }

    /**
     * Removes selected furni that are no longer the current room-owned instances.
     * Settings/id-backed implementations override this to prune their backing data.
     */
    protected boolean sanitizeSelectedItems(Room room) {
        if (room == null) {
            return false;
        }
        boolean changed = pruneInvalidSelectedItems(room, getSelectedItems());
        changed |= pruneInvalidSelectedItems(room, getSelectedItems2());
        return changed;
    }

    protected final boolean pruneInvalidSelectedItems(Room room, Collection<HabboItem> items) {
        return items != null && !items.isEmpty()
                && items.removeIf(item -> !isCurrentSelectedItem(room, item));
    }

    protected final boolean isCurrentSelectedItem(Room room, HabboItem item) {
        return room != null
                && item != null
                && item.getRoomId() == room.getId()
                && room.getHabboItemByDatabaseId(item.getId()) == item;
    }

    /** Room-visible ids of the primary furni selection. */
    protected int[] getSelectedItemVisibleIds(Room room) {
        return toVisibleIds(room, getSelectedItems());
    }

    /** Room-visible ids of the secondary furni selection (stuffIds2). */
    protected int[] getSelectedItem2VisibleIds(Room room) {
        return toVisibleIds(room, getSelectedItems2());
    }

    private int[] toVisibleIds(Room room, Collection<HabboItem> items) {
        if (room == null || items == null || items.isEmpty()) {
            return new int[0];
        }
        ArrayList<Integer> ids = new ArrayList<>(items.size());
        for (HabboItem item : items) {
            if (isCurrentSelectedItem(room, item)) {
                ids.add(item.getRoomVisibleId());
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    private void appendVisibleIds(ServerMessage message, int[] ids) {
        message.appendInt(ids.length);
        for (int id : ids) {
            message.appendInt(id);
        }
    }

    private void appendInts(ServerMessage message, int[] values) {
        message.appendInt(values.length);
        for (int v : values) {
            message.appendInt(v);
        }
    }

    private void appendStrings(ServerMessage message, String[] values) {
        message.appendInt(values.length);
        for (String s : values) {
            message.appendString(s);
        }
    }

    /** Default InputSourcesConf: exposes picked furni plus selector/current targets for source-aware wired. */
    private void serializeInputSourcesConf(ServerMessage message) {
        int furniSlots = getFurniSourceSlotCount();
        message.appendInt(furniSlots);
        for (int i = 0; i < furniSlots; i++) {
            appendInts(message, getAllowedFurniSourcesForSlot(i));
        }

        int userSlots = getUserSourceSlotCount();
        message.appendInt(userSlots);
        for (int i = 0; i < userSlots; i++) {
            appendInts(message, getAllowedUserSourcesForSlot(i));
        }

        message.appendInt(furniSlots);
        for (int i = 0; i < furniSlots; i++) {
            message.appendInt(getDefaultFurniSourceForSlot(i));
        }

        message.appendInt(userSlots);
        for (int i = 0; i < userSlots; i++) {
            message.appendInt(getDefaultUserSourceForSlot(i));
        }
    }

    protected Collection<HabboItem> resolveFurniSource(WiredContext ctx, int[] sourceTypes, int slot,
                                                       Collection<HabboItem> pickedItems,
                                                       Collection<HabboItem> pickedItems2) {
        int source = getSourceType(sourceTypes, slot, getDefaultFurniSourceForSlot(slot));
        switch (source) {
            case FURNI_SOURCE_TRIGGERING_ITEM:
                return ctx.sourceItem().map(Collections::singletonList).orElse(Collections.emptyList());
            case FURNI_SOURCE_SELECTOR:
                // Selector source is never the context's backwards-compatible
                // trigger-item seed. With no selector on this stack the result is
                // deliberately empty, so the effect activates but has no target.
                if (ctx.stack() == null || !ctx.stack().hasSelectors()) {
                    return Collections.emptyList();
                }
                return new ArrayList<>(ctx.targets().items());
            case FURNI_SOURCE_SIGNAL:
                return new ArrayList<>(ctx.event().getSignalPayload().items(ctx.room()));
            case FURNI_SOURCE_ROOM_FURNI:
                return new ArrayList<>(ctx.room().getFloorItems());
            case FURNI_SOURCE_PICKED_2:
                return pickedItems2 != null ? pickedItems2 : Collections.emptyList();
            case FURNI_SOURCE_PICKED_1:
            case FURNI_SOURCE_DUAL_MODE:
            default:
                return pickedItems != null ? pickedItems : Collections.emptyList();
        }
    }

    protected Collection<RoomUnit> resolveUserSource(WiredContext ctx, int[] sourceTypes, int slot) {
        int source = getSourceType(sourceTypes, slot, getDefaultUserSourceForSlot(slot));
        switch (source) {
            case USER_SOURCE_SELECTOR:
                if (ctx.stack() == null || !ctx.stack().hasSelectors()) {
                    return Collections.emptyList();
                }
                return new ArrayList<>(ctx.targets().users());
            case USER_SOURCE_SIGNAL:
                return new ArrayList<>(ctx.event().getSignalPayload().users(ctx.room()));
            case USER_SOURCE_ROOM_USERS:
                return new ArrayList<>(ctx.room().getRoomUnits());
            case USER_SOURCE_TRIGGERING_USER:
            default:
                return ctx.actor().map(Collections::singletonList).orElse(Collections.emptyList());
        }
    }

    private int getSourceType(int[] sourceTypes, int slot, int defaultValue) {
        if (sourceTypes == null || slot < 0 || slot >= sourceTypes.length) {
            return defaultValue;
        }
        return sourceTypes[slot];
    }

    public static WiredSettings readSettings(ClientMessage packet, boolean isEffect)
    {
        int[] intParams = readCountedInts(packet, MAX_WIRED_INT_PARAMS, "legacy int parameters");
        String stringParam = packet.readBoundedString(MAX_WIRED_STRING_LENGTH);
        int[] itemIds = readCountedInts(packet, getFurniSelectionLimit(), "legacy furni selections");

        WiredSettings settings = new WiredSettings(intParams, stringParam, itemIds, -1);

        if(isEffect)
        {
            settings.setDelay(packet.readRequiredInt());
        }

        settings.setStuffTypeSelectionCode(packet.readRequiredInt());
        return settings;
    }

    /**
     * Wired 2.0 save reader — matches the new client composer write order
     * (see wired-port-plan.md "New Server Read Order"). Does NOT read the legacy
     * stuffTypeSelectionCode (removed from 2.0). Type-specific block is driven by
     * {@code category}: EFFECT->delay, CONDITION->quantifierCode,
     * SELECTOR->isFilter+isInvert; others have no type-specific field.
     */
    public static WiredSettingsV2 readSettingsV2(ClientMessage packet, WiredCategoryType category, Room room)
    {
        // Common prefix
        int[] intParams = readCountedInts(packet, MAX_WIRED_INT_PARAMS, "int parameters");
        String stringParam = packet.readBoundedString(MAX_WIRED_STRING_LENGTH);
        // Furni selections arrive as room-visible ids (BC furni use virtual ids); resolve
        // them back to real db ids so downstream getHabboItem(dbId) lookups succeed.
        int[] furniIds = resolveVisibleIds(room,
                readCountedInts(packet, getFurniSelectionLimit(), "furni selections"));

        // Type-specific block
        int delay = 0;
        int quantifierCode = 0;
        boolean isFilter = false;
        boolean isInvert = false;
        switch (category) {
            case EFFECT:
                delay = packet.readRequiredInt();
                break;
            case CONDITION:
                quantifierCode = packet.readRequiredInt();
                break;
            case SELECTOR:
                isFilter = packet.readRequiredBoolean();
                isInvert = packet.readRequiredBoolean();
                break;
            default:
                break; // TRIGGER, ADDON, VARIABLE: no type-specific field
        }

        // Common suffix
        int[] furniSourceTypes = readCountedInts(packet, MAX_WIRED_SOURCE_TYPES, "furni source types");
        int[] userSourceTypes = readCountedInts(packet, MAX_WIRED_SOURCE_TYPES, "user source types");
        String[] variableIds = readCountedStrings(packet, MAX_WIRED_VARIABLE_IDS, "variable ids");
        int[] furniIds2 = resolveVisibleIds(room,
                readCountedInts(packet, getFurniSelectionLimit(), "second furni selections"));

        return new WiredSettingsV2(intParams, stringParam, furniIds, furniIds2, variableIds,
                furniSourceTypes, userSourceTypes, delay, quantifierCode, isFilter, isInvert);
    }

    /** Maps room-visible furni ids (BC virtual ids) back to real db ids. Null/normal-safe:
     *  non-BC ids and a null room pass through unchanged. */
    private static int[] resolveVisibleIds(Room room, int[] ids) {
        if (room == null || ids == null) {
            return ids;
        }
        for (int i = 0; i < ids.length; i++) {
            ids[i] = room.getItemManager().resolveVisibleId(ids[i]);
        }
        return ids;
    }

    private static int[] readCountedInts(ClientMessage packet, int maximum, String fieldName) {
        int count = packet.readBoundedCount(maximum, Integer.BYTES);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = packet.readRequiredInt();
        }
        return out;
    }

    private static String[] readCountedStrings(ClientMessage packet, int maximum, String fieldName) {
        int count = packet.readBoundedCount(maximum, Short.BYTES);
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            out[i] = packet.readBoundedString(MAX_WIRED_STRING_LENGTH);
        }
        return out;
    }

    private static int getFurniSelectionLimit() {
        return Math.max(0, WiredManager.MAXIMUM_FURNI_SELECTION);
    }

}
