package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveReward;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTriggerStacks;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredGiveRewardItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.UnseenItemsMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListInvalidateMessageComposer;
import com.eu.habbo.messages.outgoing.users.BadgeReceivedMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredEnvironmentMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredRewardResultMessageComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.users.UserWiredRewardReceived;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Manager class for the new wired engine system.
 * <p>
 * This class serves as the integration point between the emulator and the new
 * wired engine. It provides static methods for triggering events and manages
 * the lifecycle of the engine.
 * </p>
 * 
 * <h3>Configuration Options:</h3>
 * <ul>
 *   <li>{@code wired.engine.enabled} - Enable new engine (parallel mode)</li>
 *   <li>{@code wired.engine.exclusive} - Disable legacy handler when true</li>
 *   <li>{@code wired.engine.maxStepsPerStack} - Loop protection limit</li>
 *   <li>{@code wired.engine.debug} - Verbose logging</li>
 * </ul>
 * 
 * <h3>Migration Strategy:</h3>
 * <ol>
 *   <li>Set {@code wired.engine.enabled=true} to run both engines in parallel</li>
 *   <li>Test thoroughly to ensure identical behavior</li>
 *   <li>Set {@code wired.engine.exclusive=true} to disable legacy engine</li>
 *   <li>Full migration complete - WiredManager is now the only wired engine</li>
 * </ol>
 * 
 * @see WiredEngine
 * @see WiredEvents
 */
public final class WiredManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredManager.class);

    // Configuration keys
    public static final String CONFIG_ENABLED = "wired.engine.enabled";
    public static final String CONFIG_EXCLUSIVE = "wired.engine.exclusive";
    public static final String CONFIG_MAX_STEPS = "wired.engine.maxStepsPerStack";
    public static final String CONFIG_MAX_TOTAL_STEPS = "wired.engine.maxTotalStepsPerRun";
    public static final String CONFIG_MAX_SIGNAL_DEPTH = "wired.engine.maxSignalDepth";
    public static final String CONFIG_MAX_REMOTE_DEPTH = "wired.engine.maxRemoteDepth";
    public static final String CONFIG_MAX_TRIGGER_STACK_DEPTH = "wired.engine.maxTriggerStackDepth";
    public static final String CONFIG_MAX_FAN_OUT = "wired.engine.maxFanOut";
    public static final String CONFIG_MAX_TARGETS = "wired.engine.maxTargets";
    public static final String CONFIG_DEBUG = "wired.engine.debug";

    // Defaults
    private static final boolean DEFAULT_ENABLED = false;
    private static final boolean DEFAULT_EXCLUSIVE = false;
    private static final int DEFAULT_MAX_STEPS = 100;
    private static final int DEFAULT_MAX_TOTAL_STEPS = 1_000;
    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final int DEFAULT_MAX_FAN_OUT = 1_000;
    private static final int DEFAULT_MAX_TARGETS = 1_000;
    private static final int TRANSACTION_CAPABILITIES =
            WiredCapabilityService.CAPABILITY_CHESTS
                    | WiredCapabilityService.CAPABILITY_CHEST_WIRED
                    | WiredCapabilityService.CAPABILITY_CONTRACTS;

    /** The singleton engine instance */
    private static volatile WiredEngine engine;

    /** Rolling execution meter used by July's Wired Menu monitor. */
    private static final WiredUsageTracker USAGE_TRACKER = new WiredUsageTracker();

    /** Per-room Wired clocks; cleared on room unload. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, WiredRoomClock> roomClocks = new java.util.concurrent.ConcurrentHashMap<>();
    
    /** The stack index */
    private static volatile RoomWiredStackIndex stackIndex;
    
    /** Whether the engine is initialized */
    private static volatile boolean initialized = false;

    private WiredManager() {
        // Static utility class
    }
    /**
     * Event handler called when the emulator is loaded.
     * Initializes the wired manager.
     */
    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        initialize();
    }

    /**
     * Register the engine's configuration keys with their code defaults.
     *
     * <p>{@link com.eu.habbo.core.ConfigurationManager#register} only inserts a key that is not
     * already present, so this is safe on every boot and never overwrites an operator's value.
     * Without it the engine still runs on its defaults, but each key logs "Config key not found"
     * on startup and never appears in emulator_settings for anyone to tune.</p>
     */
    private static void registerConfigDefaults() {
        Emulator.getConfig().register(CONFIG_MAX_TOTAL_STEPS, String.valueOf(DEFAULT_MAX_TOTAL_STEPS));
        Emulator.getConfig().register(CONFIG_MAX_SIGNAL_DEPTH, String.valueOf(DEFAULT_MAX_DEPTH));
        Emulator.getConfig().register(CONFIG_MAX_REMOTE_DEPTH, String.valueOf(DEFAULT_MAX_DEPTH));
        Emulator.getConfig().register(CONFIG_MAX_TRIGGER_STACK_DEPTH, String.valueOf(DEFAULT_MAX_DEPTH));
        Emulator.getConfig().register(CONFIG_MAX_FAN_OUT, String.valueOf(DEFAULT_MAX_FAN_OUT));
        Emulator.getConfig().register(CONFIG_MAX_TARGETS, String.valueOf(DEFAULT_MAX_TARGETS));
        Emulator.getConfig().register("wired.abuse.protection.enabled", "1");
        Emulator.getConfig().register("wired.chests.upgrade_cost_credits", "10");
        Emulator.getConfig().register("wired.chests.upgrade_cost_diamonds", "10");

        // Core Variables room capacity. Read back into WiredVariableManager's static fields by
        // PluginManager.globalOnConfigurationUpdated; see that class for the single-source-of-truth
        // rule on these keys.
        Emulator.getConfig().register("wired.variables.max.definitions.per.room",
                String.valueOf(WiredVariableManager.MAX_DEFINITIONS));
        Emulator.getConfig().register("wired.variables.max.values.per.room",
                String.valueOf(WiredVariableManager.MAX_VALUES_PER_ROOM));
        Emulator.getConfig().register("wired.variables.max.values.per.definition",
                String.valueOf(WiredVariableManager.MAX_VALUES_PER_DEFINITION));
        Emulator.getConfig().register("wired.variables.max.diff.hashes",
                String.valueOf(WiredVariableManager.MAX_DIFF_HASHES));
    }

    /**
     * Initialize the wired manager and engine.
     * Called during emulator startup.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        long millis = System.currentTimeMillis();

        // Seed any engine setting the database does not carry yet, so operators
        // can see and tune the limits instead of them living only as code defaults.
        registerConfigDefaults();

        // Load configuration
        boolean enabled = Emulator.getConfig().getBoolean(CONFIG_ENABLED, DEFAULT_ENABLED);
        int maxSteps = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_STEPS, DEFAULT_MAX_STEPS));
        int maxTotalSteps = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_TOTAL_STEPS, DEFAULT_MAX_TOTAL_STEPS));
        int maxSignalDepth = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_SIGNAL_DEPTH, DEFAULT_MAX_DEPTH));
        int maxRemoteDepth = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_REMOTE_DEPTH, DEFAULT_MAX_DEPTH));
        int maxTriggerStackDepth = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_TRIGGER_STACK_DEPTH, DEFAULT_MAX_DEPTH));
        int maxFanOut = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_FAN_OUT, DEFAULT_MAX_FAN_OUT));
        int maxTargets = Math.max(1, Emulator.getConfig().getInt(CONFIG_MAX_TARGETS, DEFAULT_MAX_TARGETS));
        boolean debug = Emulator.getConfig().getBoolean(CONFIG_DEBUG, false);
        
        // Load additional configuration
        MAXIMUM_FURNI_SELECTION = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5);
        TELEPORT_DELAY = Emulator.getConfig().getInt("wired.effect.teleport.delay", 500);

        // Set debug mode
        if (debug) {
            setDebugEnabled(true);
        }

        // Create components
        stackIndex = new RoomWiredStackIndex();
        WiredServices services = DefaultWiredServices.getInstance();
        engine = new WiredEngine(
                services,
                stackIndex,
                maxSteps,
                maxTotalSteps,
                maxSignalDepth,
                maxRemoteDepth,
                maxTriggerStackDepth,
                maxFanOut,
                maxTargets);
        
        // Start the centralized tick service (50ms interval)
        WiredTickService.getInstance().start();

        initialized = true;
        
        LOGGER.info("Wired Manager -> Loaded! ({} MS, enabled={}, max steps={}, tick={} MS)",
                System.currentTimeMillis() - millis, enabled, maxSteps,
                WiredTickService.getInstance().getTickIntervalMs());
        LOGGER.trace("Wired Manager -> Debug mode: {}.", debug);
    }

    /**
     * Shutdown the wired manager.
     * Called during emulator shutdown.
     */
    public static synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        LOGGER.info("Shutting down Wired Manager...");
        
        // Stop the tick service first
        WiredTickService.getInstance().stop();
        
        if (stackIndex != null) {
            stackIndex.clearAll();
        }
        
        if (engine != null) {
            engine.clearUnseenCache();
        }
        USAGE_TRACKER.clearAll();

        initialized = false;
        LOGGER.info("Wired Manager shutdown complete");
    }

    /**
     * Check if the new wired engine is enabled.
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return Emulator.getConfig().getBoolean(CONFIG_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * Check if the new engine is exclusive (legacy disabled).
     * @return true if exclusive mode
     */
    public static boolean isExclusive() {
        return Emulator.getConfig().getBoolean(CONFIG_EXCLUSIVE, DEFAULT_EXCLUSIVE);
    }

    /**
     * Get the wired engine instance.
     * @return the engine, or null if not initialized
     */
    public static WiredEngine getEngine() {
        return engine;
    }

    public static WiredUsageTracker getUsageTracker() {
        return USAGE_TRACKER;
    }

    /**
     * Get the stack index instance.
     * @return the stack index, or null if not initialized
     */
    public static RoomWiredStackIndex getStackIndex() {
        return stackIndex;
    }

    // ========== Event Triggering Methods ==========

    /**
     * Handle a wired event using the new engine.
     * @param event the event to handle
     * @return true if any stack was triggered
     */
    public static boolean handleEvent(WiredEvent event) {
        if (!isEnabled() || engine == null) {
            return false;
        }
        
        return engine.handleEvent(event);
    }

    /**
     * Publishes a Core Variables mutation after the variable manager has
     * committed it.  Call this through a manager receipt callback, never from
     * inside the manager monitor.  {@link WiredEngine} inherits its current
     * state so nested variable networks share the root safety budget.
     *
     * <p>The current provisional policy is synchronous re-entry on the
     * committing thread, not a queued event loop.  This preserves ordinary
     * effect ordering and is bounded by the shared state. The scheduling
     * policy is an explicit server emulation because AIR does not reveal the
     * official server's internal queue.</p>
     */
    public static boolean triggerVariableChanged(
            Room room, RoomUnit actor, WiredVariableMutation mutation) {
        if (!isEnabled()
                || !WiredCapabilityService.isRoomCapabilityReady(
                WiredCapabilityService.CAPABILITY_VARIABLES)
                || room == null
                || mutation == null) {
            return false;
        }

        RoomUnit currentActor = actor != null
                && actor.getRoom() == room
                && actor.isInRoom()
                && room.getRoomUnits().contains(actor)
                ? actor : null;
        boolean handled = false;
        for (var notification : com.eu.habbo.habbohotel.wired.variables.WiredVariableAliasOperations.notificationsAcrossRooms(room, mutation)) {
            com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableObserver.record(notification.room(), notification.mutation());
            handled |= handleEvent(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, notification.room())
                .actor(notification.room() == room ? currentActor : null)
                .variableMutation(notification.mutation())
                .triggeredByEffect(true)
                .build());
        }
        return handled;
    }

    public static boolean triggerTransactionCompleted(
            Room room,
            RoomUnit actor,
            HabboItem contract,
            WiredTransactionOutcome outcome) {
        if (!validTransactionEvent(room, actor, contract)
                || outcome == null
                || !outcome.completed()) {
            return false;
        }
        return handleEvent(WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETED, room)
                .actor(actor)
                .sourceItem(contract)
                .transactionOutcome(outcome)
                .build());
    }

    public static boolean triggerTransactionFailed(
            Room room,
            RoomUnit actor,
            HabboItem contract,
            ChestTransactionFailure failure) {
        if (!validTransactionEvent(room, actor, contract) || failure == null) {
            return false;
        }
        return handleEvent(WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAILED, room)
                .actor(actor)
                .sourceItem(contract)
                .transactionOutcome(WiredTransactionOutcome.failed(failure))
                .build());
    }

    private static boolean validTransactionEvent(
            Room room, RoomUnit actor, HabboItem contract) {
        return isEnabled()
                && WiredCapabilityService.isRoomCapabilityReady(TRANSACTION_CAPABILITIES)
                && room != null
                && actor != null
                && actor.getRoom() == room
                && actor.isInRoom()
                && room.getRoomUnits().contains(actor)
                && contract != null
                && room.getHabboItemByDatabaseId(contract.getId()) == contract;
    }

    /**
     * Trigger when a user walks onto furniture.
     */
    public static boolean triggerUserWalksOn(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userWalksOn(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user walks off furniture.
     */
    public static boolean triggerUserWalksOff(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userWalksOff(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user says something.
     */
    public static boolean triggerUserSays(Room room, RoomUnit user, String message) {
        return triggerUserSays(room, user, message, -1, -1);
    }

    public static boolean triggerUserSays(
            Room room, RoomUnit user, String message, int chatType, int chatStyle) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userSays(
                room, user, message, chatType, chatStyle);
        return handleEvent(event);
    }

    public static boolean shouldHideUserSays(Room room, RoomUnit user, String message) {
        return shouldHideUserSays(room, user, message, -1, -1);
    }

    public static boolean shouldHideUserSays(
            Room room, RoomUnit user, String message, int chatType, int chatStyle) {
        if (!isEnabled() || engine == null || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userSays(
                room, user, message, chatType, chatStyle);
        return engine.shouldHideUserSays(event);
    }

    /**
     * Trigger when a user enters the room.
     */
    public static boolean triggerUserEntersRoom(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userEntersRoom(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when furniture state changes.
     */
    public static boolean triggerFurniStateChanged(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || item == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.furniStateChanged(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger a timer tick.
     */
    public static boolean triggerTimerTick(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.timerTick(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger a periodic timer.
     */
    public static boolean triggerTimerRepeat(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeat(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger a short periodic timer (PERIOD_SHORT / trigger 19).
     */
    public static boolean triggerTimerRepeatShort(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeatShort(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger a long periodic timer (PERIODICALLY_LONG / trigger 12).
     */
    public static boolean triggerTimerRepeatLong(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeatLong(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger game start.
     */
    public static boolean triggerGameStarts(Room room) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.gameStarts(room);
        return handleEvent(event);
    }

    /**
     * Trigger game end.
     */
    public static boolean triggerGameEnds(Room room) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.gameEnds(room);
        return handleEvent(event);
    }

    /**
     * Trigger bot collision.
     */
    public static boolean triggerBotCollision(Room room, RoomUnit botUnit) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.botCollision(room, botUnit);
        return handleEvent(event);
    }

    /**
     * Trigger when bot reaches furniture.
     */
    public static boolean triggerBotReachedFurni(Room room, RoomUnit botUnit, HabboItem item) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.botReachedFurni(room, botUnit, item);
        return handleEvent(event);
    }

    /**
     * Trigger when bot reaches a habbo.
     */
    public static boolean triggerBotReachedHabbo(Room room, RoomUnit botUnit, RoomUnit targetUser) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.botReachedHabbo(room, botUnit, targetUser);
        return handleEvent(event);
    }

    /**
     * Trigger when score is achieved.
     * @param room the room
     * @param user the user who scored
     * @param score the current total score
     * @param scoreAdded the amount of score just added
     */
    public static boolean triggerScoreAchieved(Room room, RoomUnit user, int score, int scoreAdded) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.scoreAchieved(room, user, score, scoreAdded);
        return handleEvent(event);
    }

    /**
     * Trigger when user starts idling.
     */
    public static boolean triggerUserIdles(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userIdles(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when user stops idling.
     */
    public static boolean triggerUserUnidles(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userUnidles(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when user starts dancing.
     */
    public static boolean triggerUserStartsDancing(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userStartsDancing(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when user stops dancing.
     */
    public static boolean triggerUserStopsDancing(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userStopsDancing(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a user performs a May 2026 Wired user action.
     */
    public static boolean triggerUserPerformsAction(Room room, RoomUnit user, int actionCode, String extra) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        if (room.getHabbo(user) == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userPerformsAction(room, user, actionCode, extra);
        return handleEvent(event);
    }

    /**
     * Trigger when a team wins a game.
     */
    public static boolean triggerTeamWins(Room room, RoomUnit user) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.teamWins(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a team loses a game.
     */
    public static boolean triggerTeamLoses(Room room, RoomUnit user) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.teamLoses(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a user clicks (uses) furniture. (Wired 2.0 trigger 18)
     */
    public static boolean triggerUserClicksFurni(Room room, RoomUnit user, HabboItem item) {
        return triggerUserClicksFurni(room, user, item, 0);
    }

    public static boolean triggerUserClicksFurni(
            Room room, RoomUnit user, HabboItem item, int heldTicks) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userClicksFurni(room, user, item, heldTicks);
        return handleEvent(event);
    }

    /**
     * Trigger when a user leaves the room. (Wired 2.0 trigger 23)
     */
    public static boolean triggerUserLeavesRoom(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userLeavesRoom(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a user clicks another user. (Wired 2.0 trigger 24)
     */
    public static boolean triggerUserClicksUser(Room room, RoomUnit user, RoomUnit target) {
        return triggerUserClicksUserWithOutcome(room, user, target).stackExecuted();
    }

    public static WiredClickUserOutcome triggerUserClicksUserWithOutcome(
            Room room, RoomUnit user, RoomUnit target) {
        return triggerUserClicksUserWithOutcome(room, user, target, 0);
    }

    public static WiredClickUserOutcome triggerUserClicksUserWithOutcome(
            Room room, RoomUnit user, RoomUnit target, int heldTicks) {
        WiredClickUserOutcome outcome = new WiredClickUserOutcome();
        if (!isEnabled() || room == null || user == null || target == null) {
            return outcome;
        }

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.USER_CLICKS_USER, room)
                .actor(user)
                .targetUnit(target)
                .clickUserOutcome(outcome)
                .heldDownContext(WiredHeldDownContext.user(target, heldTicks))
                .build();
        handleEvent(event);
        return outcome;
    }

    /**
     * Trigger when the room wired clock advances to a new second. (Wired 2.0 trigger 15)
     */
    public static boolean triggerClockReached(Room room, HabboItem clockTrigger, int totalSeconds) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.clockReached(room, clockTrigger, totalSeconds);
        return handleEvent(event);
    }

    /** Trigger July Wired 2.0 click-tile stacks from packet 443's clicked floor item. */
    public static boolean triggerUserClicksTile(Room room, RoomUnit user, HabboItem item) {
        return triggerUserClicksTile(room, user, item, 0);
    }

    public static boolean triggerUserClicksTile(
            Room room, RoomUnit user, HabboItem item, int heldTicks) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        return handleEvent(WiredEvents.userClicksTile(room, user, item, heldTicks));
    }

    /**
     * Dispatches one signal to the receive triggers associated with an antenna.
     * The active engine state is inherited through {@link WiredEngine}'s current
     * execution scope, so nested signals retain the same root safety budget.
     */
    public static boolean triggerReceiveSignal(
            Room room,
            RoomUnit actor,
            HabboItem antenna,
            WiredSignalPayload payload) {
        if (!isEnabled()
                || !WiredCapabilityService.isRoomCapabilityReady(
                WiredCapabilityService.CAPABILITY_SIGNALS)
                || room == null
                || antenna == null
                || antenna.getRoomId() != room.getId()
                || room.getHabboItemByDatabaseId(antenna.getId()) != antenna
                || !WiredSignalAntenna.isAntenna(antenna)) {
            return false;
        }

        RoomUnit currentActor = actor != null
                && actor.getRoom() == room
                && actor.isInRoom()
                && room.getRoomUnits().contains(actor)
                ? actor
                : null;
        WiredSignalPayload safePayload = payload != null ? payload : WiredSignalPayload.empty();
        if (safePayload.roomId() != room.getId()) {
            safePayload = WiredSignalPayload.capture(room, java.util.List.of(), java.util.List.of());
        }

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.RECEIVE_SIGNAL, room)
                .actor(currentActor)
                .sourceItem(antenna)
                .signalPayload(safePayload)
                .triggeredByEffect(true)
                .build();
        return handleEvent(event);
    }

    /**
     * Trigger from legacy system for parallel running.
     * This allows the new engine to run alongside the old one during migration.
     */
    public static boolean triggerFromLegacy(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.fromLegacy(triggerType, room, roomUnit, stuff);
        return handleEvent(event);
    }

    // ========== Index Management ==========

    /**
     * Invalidate the wired index for a room.
     * Call this when wired items are added/removed/moved.
     */
    public static void invalidateRoom(Room room) {
        if (stackIndex != null && room != null) {
            stackIndex.invalidateAll(room);
            if (debugEnabled) {
                LOGGER.info("[Wired] Cache invalidated for room {}", room.getId());
            }
            room.sendComposer(new WiredEnvironmentMessageComposer(room).compose());
        }
    }

    /**
     * Invalidate the wired index for a specific tile.
     */
    public static void invalidateTile(Room room, RoomTile tile) {
        if (stackIndex != null && room != null && tile != null) {
            stackIndex.invalidate(room, tile);
        }
    }

    /**
     * Rebuild the wired index for a room.
     */
    public static void rebuildRoom(Room room) {
        if (stackIndex != null && room != null) {
            stackIndex.rebuild(room);
        }
    }

    // ========== Configuration Constants (moved from WiredHandler) ==========

    /** Maximum number of furniture items that can be selected in a single wired component */
    public static int MAXIMUM_FURNI_SELECTION = 5;
    
    /** Delay in milliseconds between teleport executions */
    public static int TELEPORT_DELAY = 500;

    // ========== Debug Mode ==========
    
    /** Debug mode - when enabled, logs detailed wired execution flow */
    private static boolean debugEnabled = false;

    /**
     * Enables or disables wired debug mode.
     * When enabled, detailed execution logs are written to help troubleshoot wired stacks.
     * 
     * @param enabled true to enable debug logging, false to disable
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            LOGGER.trace("Wired Manager -> Debug mode enabled.");
        }
    }
    
    /**
     * Checks if wired debug mode is enabled.
     * 
     * @return true if debug mode is active
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     * 
     * @param message the message to log
     * @param args optional format arguments
     */
    public static void debug(String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[WIRED DEBUG] " + message, args);
        }
    }

    // ========== JSON Utilities ==========
    
    private static GsonBuilder gsonBuilder = null;
    private static Gson cachedGson = null;

    public static GsonBuilder getGsonBuilder() {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
        }
        return gsonBuilder;
    }
    
    /**
     * Gets a cached Gson instance. This is more efficient than calling
     * getGsonBuilder().create() multiple times, as Gson instances are thread-safe
     * and can be reused.
     * 
     * @return a cached Gson instance
     */
    public static Gson getGson() {
        if (cachedGson == null) {
            cachedGson = getGsonBuilder().create();
        }
        return cachedGson;
    }

    // ========== Tick Service Integration ==========
    
    /**
     * Registers a tickable wired item with the centralized tick service.
     * <p>
     * Call this when a time-based wired trigger is placed in a room or when
     * a room is loaded.
     * </p>
     * 
     * @param room the room the item is in
     * @param tickable the tickable item (e.g., WiredTriggerRepeater)
     */
    public static void registerTickable(Room room, WiredTickable tickable) {
        WiredTickService.getInstance().register(room, tickable);
    }
    
    /**
     * Unregisters a tickable wired item from the tick service.
     * <p>
     * Call this when a time-based wired trigger is picked up or when
     * a room is unloaded.
     * </p>
     * 
     * @param room the room the item was in
     * @param tickable the tickable item
     */
    public static void unregisterTickable(Room room, WiredTickable tickable) {
        WiredTickService.getInstance().unregister(room, tickable);
    }
    
    /**
     * Unregisters all tickables for a room.
     * <p>
     * Call this when a room is unloaded to clean up all tick registrations.
     * </p>
     * 
     * @param room the room
     */
    public static void unregisterRoomTickables(Room room) {
        WiredTickService.getInstance().unregisterRoom(room);
        if (room != null) {
            roomClocks.remove(room.getId());
            USAGE_TRACKER.clear(room);
        }
    }

    /** Gets or lazily creates the Wired clock for a room. */
    public static WiredRoomClock getRoomClock(Room room) {
        return roomClocks.computeIfAbsent(room.getId(), id -> new WiredRoomClock());
    }
    
    /**
     * Gets the tick service instance.
     * 
     * @return the WiredTickService
     */
    public static WiredTickService getTickService() {
        return WiredTickService.getInstance();
    }

    // ========== Timer Management ==========

    /**
     * Resets all wired timers in a room.
     * <p>
     * This uses the new tick service for managing timer resets.
     * </p>
     * 
     * @param room the room
     */
    public static void resetTimers(Room room) {
        if (!room.isLoaded())
            return;

        // Use the centralized tick service for timer resets
        WiredTickService.getInstance().resetRoomTimers(room);

        room.setLastTimerReset(Emulator.getIntUnixTimestamp());
    }

    // ========== Effect Execution ==========

    /**
     * Execute all wired effects at the specified tiles.
     * @param tiles the tiles to execute effects at
     * @param roomUnit the triggering room unit (may be null)
     * @param room the room
     * @param callStackDepth current recursion depth for trigger stacks
     * @return true if any effects were executed
     */
    public static boolean executeEffectsAtTiles(THashSet<RoomTile> tiles, final RoomUnit roomUnit, final Room room, final int callStackDepth) {
        WiredState currentState = WiredEngine.currentState();
        WiredState parentState = currentState != null
                ? currentState
                : engine != null
                        ? engine.createConfiguredRootState()
                        : new WiredState(DEFAULT_MAX_STEPS, WiredSafetyBudget.forSteps(DEFAULT_MAX_TOTAL_STEPS));
        return executeEffectsAtTiles(tiles, roomUnit, room, callStackDepth, parentState);
    }

    /** Execute nested tile effects without resetting the parent run budget. */
    public static boolean executeEffectsAtTiles(
            THashSet<RoomTile> tiles,
            final RoomUnit roomUnit,
            final Room room,
            final int callStackDepth,
            final WiredState parentState) {
        if (tiles == null || parentState == null) {
            return false;
        }
        parentState.budget().consumeTargets(tiles.size());
        for (RoomTile tile : tiles) {
            if (room != null) {
                THashSet<HabboItem> items = room.getItemsAt(tile);

                long millis = room.getCycleTimestamp();
                for (final HabboItem item : items) {
                    if (item instanceof InteractionWiredEffect && !(item instanceof WiredEffectTriggerStacks)) {
                        InteractionWiredEffect effect = (InteractionWiredEffect) item;
                        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                            .actor(roomUnit)
                            .callStackDepth(callStackDepth)
                            .build();
                        WiredState childState = engine != null
                                ? engine.forkConfiguredStackState(parentState)
                                : parentState.fork(DEFAULT_MAX_STEPS);
                        childState.budget().consumeFanOut(1);
                        childState.step();
                        WiredContext ctx = new WiredContext(event, effect, DefaultWiredServices.getInstance(), childState);
                        WiredMovementAddonRuntime.withPhysics(ctx, () -> effect.execute(ctx));
                        effect.setCooldown(millis);
                    }
                }
            }
        }

        return true;
    }

    // ========== Reward System ==========

    /**
     * Asynchronously drops/deletes all rewards given by a specific wired item.
     * Used when a wired reward box is picked up or reset.
     * 
     * @param wiredId The ID of the wired item whose rewards should be deleted
     */
    public static void dropRewards(int wiredId) {
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); 
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_rewards_given WHERE wired_item = ?")) {
                statement.setInt(1, wiredId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    private static void giveReward(Habbo habbo, WiredEffectGiveReward wiredBox, WiredGiveRewardItem reward) {
        if (wiredBox.getLimit() > 0)
            wiredBox.incrementGiven();

        final int wiredId = wiredBox.getId();
        final int habboId = habbo.getHabboInfo().getId();
        final int rewardId = reward.id;
        final int timestamp = Emulator.getIntUnixTimestamp();
        
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); 
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO wired_rewards_given (wired_item, user_id, reward_id, timestamp) VALUES ( ?, ?, ?, ?)")) {
                statement.setInt(1, wiredId);
                statement.setInt(2, habboId);
                statement.setInt(3, rewardId);
                statement.setInt(4, timestamp);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });

        if (reward.badge) {
            UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, "badge", reward.data);
            if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled())
                return;

            if (rewardReceived.value.isEmpty())
                return;
            
            if (habbo.getInventory().getBadgesComponent().hasBadge(rewardReceived.value))
                return;

            HabboBadge badge = new HabboBadge(0, rewardReceived.value, 0, habbo);
            Emulator.getThreading().run(badge);
            habbo.getInventory().getBadgesComponent().addBadge(badge);
            habbo.getClient().sendResponse(new BadgeReceivedMessageComposer(badge));
            habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_RECEIVED_BADGE));
        } else {
            String[] data = reward.data.split("#");

            if (data.length == 2) {
                UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, data[0], data[1]);
                if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled())
                    return;

                if (rewardReceived.value.isEmpty())
                    return;

                if (rewardReceived.type.equalsIgnoreCase("credits")) {
                    int credits = Integer.parseInt(rewardReceived.value);
                    habbo.giveCredits(credits);
                } else if (rewardReceived.type.equalsIgnoreCase("pixels")) {
                    int pixels = Integer.parseInt(rewardReceived.value);
                    habbo.givePixels(pixels);
                } else if (rewardReceived.type.startsWith("points")) {
                    int points = Integer.parseInt(rewardReceived.value);
                    int type = 5;

                    try {
                        type = Integer.parseInt(rewardReceived.type.replace("points", ""));
                    } catch (Exception e) {
                    }

                    habbo.givePoints(type, points);
                } else if (rewardReceived.type.equalsIgnoreCase("furni")) {
                    Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(Integer.parseInt(rewardReceived.value));
                    if (baseItem != null) {
                        HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");

                        if (item != null) {
                            habbo.getClient().sendResponse(new UnseenItemsMessageComposer(item));
                            habbo.getClient().getHabbo().getInventory().getItemsComponent().addItem(item);
                            habbo.getClient().sendResponse(new PurchaseOKMessageComposer(null));
                            habbo.getClient().sendResponse(new FurniListInvalidateMessageComposer());
                            habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_RECEIVED_ITEM));
                        }
                    }
                } else if (rewardReceived.type.equalsIgnoreCase("respect")) {
                    habbo.getHabboStats().respectPointsReceived += Integer.parseInt(rewardReceived.value);
                } else if (rewardReceived.type.equalsIgnoreCase("cata")) {
                    CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(Integer.parseInt(rewardReceived.value));

                    if (item != null) {
                        Emulator.getGameEnvironment().getCatalogManager().purchaseItem(null, item, habbo, 1, "", true);
                    }
                    habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_RECEIVED_ITEM));
                }
            }
        }
    }

    public static boolean getReward(Habbo habbo, WiredEffectGiveReward wiredBox) {
        if (wiredBox.getLimit() > 0) {
            if (wiredBox.getLimit() - wiredBox.getGiven() == 0) {
                habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.LIMITED_NO_MORE_AVAILABLE));
                return false;
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) as row_count, wired_rewards_given.* FROM wired_rewards_given WHERE user_id = ? AND wired_item = ? ORDER BY timestamp DESC LIMIT ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, wiredBox.getId());
            statement.setInt(3, wiredBox.getRewardItems().size());

            try (ResultSet set = statement.executeQuery()) {
                if (set.first()) {
                    if (set.getInt("row_count") >= 1) {
                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_ONCE) {
                            habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_ALREADY_RECEIVED));
                            return false;
                        }
                    }

                    set.beforeFirst();
                    if (set.next()) {
                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_MINUTES) {
                            if (Emulator.getIntUnixTimestamp() - set.getInt("timestamp") <= 60) {
                                habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_ALREADY_RECEIVED_THIS_MINUTE));
                                return false;
                            }
                        }

                        if (wiredBox.isUniqueRewards()) {
                            if (set.getInt("row_count") == wiredBox.getRewardItems().size()) {
                                habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_ALL_COLLECTED));
                                return false;
                            }
                        }

                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_HOURS) {
                            if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (3600 * wiredBox.getLimitationInterval()))) {
                                habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_ALREADY_RECEIVED_THIS_HOUR));
                                return false;
                            }
                        }

                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_DAY) {
                            if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (86400 * wiredBox.getLimitationInterval()))) {
                                habbo.getClient().sendResponse(new WiredRewardResultMessageComposer(WiredRewardResultMessageComposer.REWARD_ALREADY_RECEIVED_THIS_TODAY));
                                return false;
                            }
                        }
                    }

                    if (wiredBox.isUniqueRewards()) {
                        for (WiredGiveRewardItem item : wiredBox.getRewardItems()) {
                            set.beforeFirst();
                            boolean found = false;

                            while (set.next()) {
                                if (set.getInt("reward_id") == item.id)
                                    found = true;
                            }

                            if (!found) {
                                giveReward(habbo, wiredBox, item);
                                return true;
                            }
                        }
                    } else {
                        int randomNumber = Emulator.getRandom().nextInt(101);

                        int count = 0;
                        for (WiredGiveRewardItem item : wiredBox.getRewardItems()) {
                            if (randomNumber >= count && randomNumber <= (count + item.probability)) {
                                giveReward(habbo, wiredBox, item);
                                return true;
                            }

                            count += item.probability;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }
}

