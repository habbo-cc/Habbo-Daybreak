package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonChestItemTypeScanner;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonConditionEvaluation;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniSelectorFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniVariableFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonRandomEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUnseenEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUserSelectorFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUserVariableFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableCapturer;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerUserClicksUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredNegativeEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredSignalEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.menu.WiredRoomMonitor;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.messages.outgoing.generic.alerts.HabboBroadcastMessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.NotificationDialogMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectsDataUpdateMessageComposer;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The central engine for processing wired events.
 *
 * <p>This is the single entry point for all wired execution in the new architecture. It receives
 * {@link WiredEvent} objects, finds matching stacks via {@link WiredStackIndex}, evaluates
 * conditions, and executes effects.
 *
 * <h3>Execution Flow:</h3>
 *
 * <ol>
 *   <li>Receive event via {@link #handleEvent(WiredEvent)}
 *   <li>Find candidate stacks for the event type
 *   <li>For each stack, check if trigger matches
 *   <li>Evaluate all conditions (respecting AND/OR mode)
 *   <li>Execute effects (respecting random/unseen modifiers)
 *   <li>Handle delays for timed effects
 * </ol>
 *
 * <h3>Safety Features:</h3>
 *
 * <ul>
 *   <li>Step limits via {@link WiredState} prevent infinite loops
 *   <li>Effect cooldowns prevent rapid re-triggering
 *   <li>Exceptions are caught and logged, not propagated
 * </ul>
 *
 * @see WiredEvent
 * @see WiredContext
 * @see WiredStackIndex
 */
public final class WiredEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(WiredEngine.class);
  private static final ThreadLocal<WiredState> CURRENT_STATE = new ThreadLocal<>();
  private static final ThreadLocal<WiredContextVariableStore> CURRENT_CONTEXT_VARIABLES =
      new ThreadLocal<>();

  /** Maximum recursion depth to prevent infinite loops (e.g., collision + chase) */
  public static int MAX_RECURSION_DEPTH = 10;

  /**
   * Whether the wired abuse rate-limiter is active. emulator_settings:
   * wired.abuse.protection.enabled (default 1).
   */
  public static boolean ABUSE_PROTECTION_ENABLED = true;

  /**
   * Maximum events of same type per room within rate limit window before banning.
   * emulator_settings: wired.abuse.max_events_per_window. Default 1000 so legitimate fast wired
   * (e.g. a 50ms short repeater driving a chase = ~200 collision events/10s) does not false-trip; a
   * true runaway loop far exceeds this.
   */
  public static int MAX_EVENTS_PER_WINDOW = 1000;

  /** Time window for counting rapid events (ms). emulator_settings: wired.abuse.window_ms. */
  public static long RATE_LIMIT_WINDOW_MS = 10000;

  /**
   * Duration to ban wired execution in a room after abuse detected (ms). emulator_settings:
   * wired.abuse.ban_duration_ms.
   */
  public static long WIRED_BAN_DURATION_MS = 600000;

  private final WiredServices services;
  private final WiredStackIndex index;
  private final int maxStepsPerStack;
  private final int maxTotalStepsPerRun;
  private final int maxSignalDepth;
  private final int maxRemoteDepth;
  private final int maxTriggerStackDepth;
  private final int maxFanOut;
  private final int maxTargets;

  /** Track unseen effect indices per room+tile for round-robin selection */
  private final ConcurrentHashMap<String, Integer> unseenIndices;

  /** Track recursion depth per room to prevent infinite loops */
  private final ConcurrentHashMap<Integer, Integer> roomRecursionDepth;

  /** Track event timestamps per room+eventType for rate limiting: key = "roomId:eventType" */
  private final ConcurrentHashMap<String, EventRateTracker> eventRateLimiters;

  /** Track rooms that are banned from wired execution: roomId -> ban expiry timestamp */
  private final ConcurrentHashMap<Integer, Long> bannedRooms;

  /**
   * Create a new wired engine.
   *
   * @param services the services for performing side effects
   * @param index the stack index for finding matching stacks
   * @param maxStepsPerStack maximum steps per stack execution (loop protection)
   */
  public WiredEngine(WiredServices services, WiredStackIndex index, int maxStepsPerStack) {
    this(
        services,
        index,
        maxStepsPerStack,
        Math.max(maxStepsPerStack, 1_000),
        10,
        10,
        10,
        1_000,
        1_000);
  }

  public WiredEngine(
      WiredServices services,
      WiredStackIndex index,
      int maxStepsPerStack,
      int maxTotalStepsPerRun,
      int maxSignalDepth,
      int maxRemoteDepth,
      int maxTriggerStackDepth,
      int maxFanOut,
      int maxTargets) {
    if (services == null) throw new IllegalArgumentException("Services cannot be null");
    if (index == null) throw new IllegalArgumentException("Index cannot be null");
    if (maxStepsPerStack <= 0) throw new IllegalArgumentException("Max steps must be positive");
    if (maxTotalStepsPerRun <= 0)
      throw new IllegalArgumentException("Max total steps must be positive");
    if (maxSignalDepth <= 0)
      throw new IllegalArgumentException("Max signal depth must be positive");
    if (maxRemoteDepth <= 0)
      throw new IllegalArgumentException("Max remote depth must be positive");
    if (maxTriggerStackDepth <= 0)
      throw new IllegalArgumentException("Max trigger-stack depth must be positive");
    if (maxFanOut <= 0) throw new IllegalArgumentException("Max fan-out must be positive");
    if (maxTargets <= 0) throw new IllegalArgumentException("Max targets must be positive");

    this.services = services;
    this.index = index;
    this.maxStepsPerStack = maxStepsPerStack;
    this.maxTotalStepsPerRun = maxTotalStepsPerRun;
    this.maxSignalDepth = maxSignalDepth;
    this.maxRemoteDepth = maxRemoteDepth;
    this.maxTriggerStackDepth = maxTriggerStackDepth;
    this.maxFanOut = maxFanOut;
    this.maxTargets = maxTargets;
    this.unseenIndices = new ConcurrentHashMap<>();
    this.roomRecursionDepth = new ConcurrentHashMap<>();
    this.eventRateLimiters = new ConcurrentHashMap<>();
    this.bannedRooms = new ConcurrentHashMap<>();
    // Abuse rate-limiter settings (ABUSE_PROTECTION_ENABLED, MAX_EVENTS_PER_WINDOW,
    // RATE_LIMIT_WINDOW_MS, WIRED_BAN_DURATION_MS) are loaded from emulator_settings in
    // PluginManager.globalOnConfigurationUpdated using the wired.abuse.* keys, so they apply at
    // boot and on every config reload.
  }

  /**
   * Handle a wired event by finding and executing matching stacks.
   *
   * @param event the event to handle
   * @return true if any stack was triggered (useful for SAY_SOMETHING to suppress message)
   */
  public boolean handleEvent(WiredEvent event) {
    WiredState inheritedState = CURRENT_STATE.get();
    WiredState rootState =
        inheritedState != null
            ? inheritedState.fork(this.maxStepsPerStack)
            : new WiredState(this.maxStepsPerStack, this.createRootBudget());
    return this.handleEvent(event, rootState);
  }

  private boolean handleEvent(WiredEvent event, WiredState rootState) {
    if (event == null) {
      return false;
    }

    Room room = event.getRoom();
    if (room == null || !room.isLoaded()) {
      return false;
    }

    int roomId = room.getId();

    // Check if room is banned from wired execution
    if (isRoomBanned(roomId)) {
      return false;
    }

    // Check rate limiting to prevent rapid-fire event spam (e.g., collision + chase loop).
    // Self-paced timer events are exempt: their frequency is bounded by their own configured
    // interval (a short repeater can legitimately fire every 50ms = 200/10s), not by a runaway
    // loop, so they must not be mistaken for abuse. Any downstream events they trigger
    // (collision, etc.) are still rate-limited and recursion-guarded below.
    if (ABUSE_PROTECTION_ENABLED
        && !isSelfPacedTimerEvent(event.getType())
        && isRateLimited(roomId, room, event.getType())) {
      // Room has been banned, all events will be dropped
      return false;
    }

    // Check and increment recursion depth to prevent infinite loops
    int currentDepth = roomRecursionDepth.getOrDefault(roomId, 0);
    if (currentDepth >= MAX_RECURSION_DEPTH) {
      LOGGER.warn(
          "Wired recursion limit reached in room {} (depth: {}). "
              + "Possible infinite loop detected (e.g., collision + chase). Aborting.",
          roomId,
          currentDepth);
      debug(room, "RECURSION LIMIT REACHED - aborting to prevent crash");
      WiredRoomMonitor.recursionTimeout(room);
      return false;
    }
    roomRecursionDepth.put(roomId, currentDepth + 1);

    WiredState previousState = CURRENT_STATE.get();
    WiredContextVariableStore previousVariables = CURRENT_CONTEXT_VARIABLES.get();
    WiredTransactionOutcome transactionOutcome = event.getTransactionOutcome().orElse(null);
    boolean hasEventContext = transactionOutcome != null
        || event.getVariableMutation().isPresent()
        || event.getHeldDownContext().isPresent()
        || event.getType() == WiredEvent.Type.RECEIVE_SIGNAL
        || event.getType() == WiredEvent.Type.USER_SAYS;
    boolean ownsContext = previousVariables == null || hasEventContext;
    if (ownsContext) {
      WiredContextVariableStore eventVariables =
          previousVariables == null
              ? new WiredContextVariableStore()
              : previousVariables.snapshot();
      if (transactionOutcome != null) transactionOutcome.seed(eventVariables);
      seedEventContext(event, eventVariables);
      CURRENT_CONTEXT_VARIABLES.set(eventVariables);
    }
    CURRENT_STATE.set(rootState);
    long startedAt = System.currentTimeMillis();
    try {
      return handleEventInternal(event, room, rootState);
    } finally {
      if (ownsContext) {
        WiredContextVariableStore values = CURRENT_CONTEXT_VARIABLES.get();
        if (values != null) values.clear();
        if (previousVariables == null) {
          CURRENT_CONTEXT_VARIABLES.remove();
        } else {
          CURRENT_CONTEXT_VARIABLES.set(previousVariables);
        }
      } else {
        CURRENT_CONTEXT_VARIABLES.set(previousVariables);
      }
      restoreCurrentState(previousState);
      // Decrement recursion depth
      int newDepth = roomRecursionDepth.getOrDefault(roomId, 1) - 1;
      if (newDepth <= 0) {
        roomRecursionDepth.remove(roomId);
      } else {
        roomRecursionDepth.put(roomId, newDepth);
      }
      long overloadThreshold = Math.max(1L,
          Emulator.getConfig().getInt("wired.executor.overload.ms", 250));
      if (System.currentTimeMillis() - startedAt > overloadThreshold) {
        WiredRoomMonitor.executorOverload(room);
      }
    }
  }

  private static void seedEventContext(
      WiredEvent event, WiredContextVariableStore variables) {
    if (event == null || variables == null) return;

    WiredSignalPayload signal = event.getSignalPayload();
    if (signal != null && event.getType() == WiredEvent.Type.RECEIVE_SIGNAL) {
      variables.set("@signal_furni_count", signal.capturedItemCount());
      variables.set("@signal_user_count", signal.capturedUserCount());
      event.getSourceItem().ifPresent(
          antenna -> variables.set("@event.signal.antenna_id", antenna.getId()));
    }

    if (event.getType() == WiredEvent.Type.USER_SAYS) {
      if (event.getChatType() >= 0) {
        variables.set("@event.chat.type", event.getChatType());
      }
      if (event.getChatStyle() >= 0) {
        variables.set("@event.chat.style", event.getChatStyle());
      }
    }

    event.getVariableMutation().ifPresent(mutation -> {
      int before = mutation.beforeValue() == null ? 0 : mutation.beforeValue();
      int after = mutation.afterValue() == null ? 0 : mutation.afterValue();
      int changeType = switch (mutation.kind()) {
        case CREATED -> 0;
        case VALUE_CHANGED -> after > before ? 1 : after < before ? 2 : 3;
        case DELETED -> 4;
      };
      long difference = (long) after - before;
      variables.set("@event.variable_update.box_id", mutation.boxId());
      variables.set("@event.variable_update.change_type", changeType);
      variables.set("@event.variable_update.old_value", before);
      variables.set("@event.variable_update.new_value", after);
      variables.set("@event.variable_update.difference",
          (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, difference)));
      variables.set("@event.variable_update.change_origin", mutation.changeOrigin());
    });
    event.getHeldDownContext().ifPresent(context -> context.seed(variables));
  }

  private WiredSafetyBudget createRootBudget() {
    return new WiredSafetyBudget(
        this.maxTotalStepsPerRun,
        this.maxSignalDepth,
        this.maxRemoteDepth,
        this.maxTriggerStackDepth,
        this.maxFanOut,
        this.maxTargets);
  }

  static WiredState currentState() {
    return CURRENT_STATE.get();
  }

  WiredState createConfiguredRootState() {
    return new WiredState(this.maxStepsPerStack, this.createRootBudget());
  }

  WiredState forkConfiguredStackState(WiredState parentState) {
    return parentState.fork(this.maxStepsPerStack);
  }

  private static void restoreCurrentState(WiredState previousState) {
    if (previousState == null) {
      CURRENT_STATE.remove();
    } else {
      CURRENT_STATE.set(previousState);
    }
  }

  /**
   * Preview whether a matching User Says trigger asks the room chat message to be hidden. This
   * intentionally does not execute the stack; RoomChatManager uses it to decide whether to send the
   * normal chat bubble before it fires the wired stack.
   */
  public boolean shouldHideUserSays(WiredEvent event) {
    if (event == null || event.getType() != WiredEvent.Type.USER_SAYS) {
      return false;
    }

    Room room = event.getRoom();
    if (room == null || !room.isLoaded()) {
      return false;
    }

    for (WiredStack stack : index.getStacks(room, event.getType())) {
      if (!(stack.trigger() instanceof WiredTriggerHabboSaysKeyword)) {
        continue;
      }

      WiredTriggerHabboSaysKeyword trigger = (WiredTriggerHabboSaysKeyword) stack.trigger();
      if (!trigger.shouldHideMessage()) {
        continue;
      }

      if (trigger.requiresActor() && !event.getActor().isPresent()) {
        continue;
      }

      if (trigger.matches(stack.triggerItem(), event)) {
        return true;
      }
    }

    return false;
  }

  /** Internal event handling after recursion check. */
  private boolean handleEventInternal(WiredEvent event, Room room, WiredState rootState) {

    // Find candidate stacks for this event type
    List<WiredStack> stacks = index.getStacks(room, event.getType());
    if (stacks.isEmpty()) {
      return false;
    }

    debug(room, "Processing {} stacks for event type {}", stacks.size(), event.getType());

    boolean anyTriggered = false;
    long currentTime = System.currentTimeMillis();

    // Collect every box that lights up across all stacks for this event so they ship in one
    // ObjectsDataUpdate packet, matching how habbo.com batches box highlights per tick.
    Set<HabboItem> boxUpdates = new LinkedHashSet<>();

    for (WiredStack stack : stacks) {
      try {
        boolean triggered = processStack(stack, event, currentTime, boxUpdates, rootState);
        if (triggered) {
          anyTriggered = true;
        }
      } catch (WiredLimitException limitEx) {
        debug(room, "Stack execution stopped (limit): {}", limitEx.getMessage());
        WiredRoomMonitor.recursionTimeout(room);
        if (rootState.budget().aggregateLimitReached()) {
          break;
        }
      } catch (Exception ex) {
        LOGGER.error(
            "Error processing wired stack in room {}: {}", room.getId(), ex.getMessage(), ex);
        WiredRoomMonitor.runtimeError(room, "STACK", ex);
        debug(room, "Stack error: {}", ex.getMessage());
      }
    }

    if (!boxUpdates.isEmpty()) {
      room.sendComposer(new ObjectsDataUpdateMessageComposer(boxUpdates).compose());
    }

    return anyTriggered;
  }

  /** Process a single wired stack. */
  private boolean processStack(
      WiredStack stack,
      WiredEvent event,
      long currentTime,
      Set<HabboItem> boxUpdates,
      WiredState parentState) {
    Room room = event.getRoom();

    if (stack.triggerItem()
            instanceof com.eu.habbo.habbohotel.items.interactions.InteractionWired wiredTrigger
        && !WiredFeatureCapabilityGuard.isRuntimeReady(wiredTrigger)) {
      return false;
    }

    // Matching may invoke plugin/custom trigger code, so every candidate is
    // charged before it is evaluated, including candidates that do not match.
    WiredState state = parentState.fork(maxStepsPerStack);
    state.step();

    WiredContext ctx = new WiredContext(event, stack.triggerItem(), stack, services, state, null);
    ctx.useContextVariables(CURRENT_CONTEXT_VARIABLES.get());

    if (!WiredManager.getUsageTracker()
        .tryConsumeStack(room, stack, roomRecursionDepth.getOrDefault(room.getId(), 1) - 1)) {
      WiredRoomMonitor.executionCap(room);
      debug(
          room,
          "Execution usage cap blocked stack at item {}",
          stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
      return false;
    }

    // Check if trigger matches
    if (!matchesTrigger(stack, event, ctx)) {
      return false;
    }

    // Check if trigger requires actor
    if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
      return false;
    }

    WiredAddonExecutionLimit limit =
        addon(stack, WiredAddonType.EXECUTION_LIMIT, WiredAddonExecutionLimit.class);
    if (limit != null && !limit.allowExecution(currentTime)) {
      debug(
          room,
          "Execution-limit add-on blocked stack at item {}",
          stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
      return false;
    }

    state.budget().consumeFanOut(1);
    state.budget().consumeTargets(ctx.targets().userCount() + ctx.targets().itemCount());
    long stackKey = stablePathKey(room.getId(), stack);
    WiredState previousState = CURRENT_STATE.get();
    CURRENT_STATE.set(state);

    try (WiredSafetyBudget.PathLease ignored =
        state.enter(WiredSafetyBudget.PathKind.STACK, stackKey)) {
      // Activate the trigger box animation
      if (stack.triggerItem() instanceof InteractionWiredTrigger) {
        InteractionWiredTrigger trigger = (InteractionWiredTrigger) stack.triggerItem();
        trigger.activateBox(room, event.getActor().orElse(null), currentTime, boxUpdates);
      }

      debug(
          room,
          "Trigger matched: {} at item {} (conditions: {}, effects: {})",
          event.getType(),
          stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
          stack.conditions().size(),
          stack.effects().size());

      // Activate stack modifiers (legacy extras and Wired 2.0 add-ons).
      activateStackModifiers(
          room, stack.triggerItem(), event.getActor().orElse(null), currentTime, boxUpdates);

      // Resolve selectors before conditions so target-aware conditions evaluate
      // the same target snapshot that effects will receive.
      if (stack.hasSelectors()) {
        boolean selectorsPassed =
            resolveSelectors(
                stack, ctx, room, event.getActor().orElse(null), currentTime, boxUpdates);
        debug(room, "Selectors result: {}", selectorsPassed ? "PASSED" : "FAILED");
        if (!selectorsPassed) {
          debug(room, "Selectors failed, aborting before conditions and effects");
          return false;
        }
      }

      // July add-on 18 writes the selected chest/type count before conditions
      // and effects read its Context Variable.
      WiredAddonChestItemTypeScanner chestScanner =
          addon(
              stack, WiredAddonType.CHEST_ITEM_TYPE_SCANNER, WiredAddonChestItemTypeScanner.class);
      if (chestScanner != null && WiredFeatureCapabilityGuard.isRuntimeReady(chestScanner)) {
        state.step();
        chestScanner.scan(ctx);
      }

      // Evaluate conditions. Marked negative effects share this already-resolved
      // selector context, but are mutually exclusive with ordinary effects.
      boolean conditionsPassed = true;
      if (stack.hasConditions()) {
        debug(room, "Evaluating {} conditions...", stack.conditions().size());
        conditionsPassed = evaluateConditions(stack, ctx);
        debug(room, "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
        if (!conditionsPassed) {
          debug(room, "Conditions failed, executing negative effects only");
          return executeEffects(stack, ctx, currentTime, boxUpdates, true);
        }
      } else {
        debug(room, "No conditions in stack, proceeding to effects");
      }

      // Fire plugin event (WiredStackTriggeredEvent)
      if (!fireTriggeredEvent(stack, event)) {
        debug(room, "Stack cancelled by plugin");
        return false;
      }

      if (event.getType() == WiredEvent.Type.USER_CLICKS_USER
          && stack.triggerItem() instanceof WiredTriggerUserClicksUser clickTrigger) {
        event
            .getClickUserOutcome()
            .ifPresent(
                outcome ->
                    outcome.record(
                        clickTrigger.blocksMenuOpen(), clickTrigger.suppressesRotation()));
      }

      // Execute effects
      if (stack.hasEffects()) {
        executeEffects(stack, ctx, currentTime, boxUpdates, false);
      }

      // Fire executed event
      fireExecutedEvent(stack, event);

      return true;
    } finally {
      restoreCurrentState(previousState);
    }
  }

  private boolean matchesTrigger(WiredStack stack, WiredEvent event, WiredContext context) {
    if (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword says) {
      WiredAddonVariableCapturer capturer =
          addon(stack, WiredAddonType.VARIABLE_CAPTURER, WiredAddonVariableCapturer.class);
      String pattern = says.getKey();
      if (capturer != null && capturer.appliesTo(pattern)) {
        String input = event.getText().orElse(null);
        return says.matchesActorGate(event)
            && input != null
            && capturer.capture(context, pattern, input, says.getMatchType());
      }
    }
    return stack.trigger().matches(stack.triggerItem(), event);
  }

  private static long stablePathKey(int roomId, WiredStack stack) {
    HabboItem item = stack.triggerItem();
    int itemId = item != null ? item.getId() : System.identityHashCode(stack);
    return ((long) roomId << 32) ^ (itemId & 0xffffffffL);
  }

  private boolean resolveSelectors(
      WiredStack stack,
      WiredContext ctx,
      Room room,
      RoomUnit actor,
      long currentTime,
      Set<HabboItem> boxUpdates) {
    boolean usersTouched = false;
    boolean itemsTouched = false;
    boolean usersNonFilterSeen = false;
    boolean itemsNonFilterSeen = false;

    // The context starts with actor/trigger-item compatibility targets. Once
    // a stack enters selector mode, only actual selector output may occupy
    // these collections; otherwise an empty selector can target the Wired
    // boxes themselves.
    ctx.targets().clear();

    for (InteractionWiredSelector selector : stack.selectors()) {
      if (!WiredFeatureCapabilityGuard.isRuntimeReady(selector)) {
        return false;
      }
      ctx.state().step();
      selector.activateBox(room, actor, currentTime, boxUpdates);
      WiredTargets resolved = selector.resolve(room, ctx);
      ctx.budget().consumeTargets(resolved.userCount() + resolved.itemCount());

      if (selector.getType().isUser) {
        usersTouched = true;
        Set<RoomUnit> users = new LinkedHashSet<>(resolved.users());
        if (selector.isInvert()) {
          int resolvedCount = users.size();
          users = invertUsers(room, users);
          ctx.budget().consumeTargets(additionalTargetsAfterTransform(resolvedCount, users.size()));
        }
        if (selector.isFilter()) {
          users.retainAll(ctx.targets().users());
          ctx.targets().setUsers(users);
        } else {
          if (!usersNonFilterSeen) {
            ctx.targets().clearUsers();
          }
          for (RoomUnit unit : users) {
            ctx.targets().addUser(unit);
          }
          usersNonFilterSeen = true;
        }
      }

      if (selector.getType().isFurni) {
        itemsTouched = true;
        Set<HabboItem> items = new LinkedHashSet<>(resolved.items());
        if (selector.isInvert()) {
          int resolvedCount = items.size();
          items = invertItems(room, items);
          ctx.budget().consumeTargets(additionalTargetsAfterTransform(resolvedCount, items.size()));
        }
        if (selector.isFilter()) {
          items.retainAll(ctx.targets().items());
          ctx.targets().setItems(items);
        } else {
          if (!itemsNonFilterSeen) {
            ctx.targets().clearItems();
          }
          for (HabboItem item : items) {
            ctx.targets().addItem(item);
          }
          itemsNonFilterSeen = true;
        }
      }
    }

    WiredAddonFurniSelectorFilter furniFilter =
        addon(stack, WiredAddonType.FURNI_SELECTOR_FILTER, WiredAddonFurniSelectorFilter.class);
    if (furniFilter != null && ctx.targets().hasItems()) {
      Integer amount =
          resolveSelectorFilterAmount(
              ctx,
              furniFilter.usesReference(),
              furniFilter.referenceTarget(),
              furniFilter.referenceId(),
              furniFilter.filterAmount());
      if (amount == null) return false;
      List<HabboItem> selected = new ArrayList<>(ctx.targets().items());
      selected.sort(Comparator.comparingInt(HabboItem::getId));
      Collections.shuffle(selected, Emulator.getRandom());
      ctx.targets().setItems(selected.subList(0, Math.min(amount, selected.size())));
      ctx.budget().consumeTargets(ctx.targets().itemCount());
    }
    WiredAddonUserSelectorFilter userFilter =
        addon(stack, WiredAddonType.USER_SELECTOR_FILTER, WiredAddonUserSelectorFilter.class);
    if (userFilter != null && ctx.targets().hasUsers()) {
      Integer amount =
          resolveSelectorFilterAmount(
              ctx,
              userFilter.usesReference(),
              userFilter.referenceTarget(),
              userFilter.referenceId(),
              userFilter.filterAmount());
      if (amount == null) return false;
      List<RoomUnit> selected = new ArrayList<>(ctx.targets().users());
      selected.sort(Comparator.comparingInt(RoomUnit::getId));
      Collections.shuffle(selected, Emulator.getRandom());
      ctx.targets().setUsers(selected.subList(0, Math.min(amount, selected.size())));
      ctx.budget().consumeTargets(ctx.targets().userCount());
    }
    WiredAddonFurniVariableFilter furniVariableFilter =
        addon(stack, WiredAddonType.FURNI_VARIABLE_FILTER, WiredAddonFurniVariableFilter.class);
    if (furniVariableFilter != null && !applyFurniVariableFilter(ctx, furniVariableFilter))
      return false;
    WiredAddonUserVariableFilter userVariableFilter =
        addon(stack, WiredAddonType.USER_VARIABLE_FILTER, WiredAddonUserVariableFilter.class);
    if (userVariableFilter != null && !applyUserVariableFilter(ctx, userVariableFilter))
      return false;
    ctx.contextVariables().set("@selector_furni_count", ctx.targets().itemCount());
    ctx.contextVariables().set("@selector_user_count", ctx.targets().userCount());
    // Empty selector output is a valid result, not a failed condition. Effects
    // still execute/animate and selector-sourced operations simply receive no
    // users or furniture to mutate.
    return true;
  }

  private static boolean applyFurniVariableFilter(
      WiredContext ctx, WiredAddonFurniVariableFilter filter) {
    Integer amount =
        resolveSelectorFilterAmount(
            ctx, filter.usesReference(), filter.target(), filter.reference(), filter.amount());
    if (amount == null) return false;
    WiredVariableManager m = ctx.room().getRoomSpecialTypes().getWiredVariableManager();
    if (m == null) return false;
    WiredVariableManager.VariableSnapshot v = m.publicSnapshot().variables().get(filter.variable());
    if (v == null) return false;
    List<HabboItem> out = new ArrayList<>();
    Map<Integer, com.eu.habbo.habbohotel.wired.variables.WiredVariableValue> values =
        new HashMap<>();
    for (var x : v.values())
      if (x.holder().scope() == WiredVariableHolder.Scope.FURNI)
        values.put(x.holder().stableId(), x);
    for (HabboItem i : ctx.targets().items()) if (values.containsKey(i.getId())) out.add(i);
    out.sort(
        Comparator.comparingLong(
                (HabboItem i) -> variableOrder(values.get(i.getId()), filter.sort()))
            .thenComparingInt(HabboItem::getId));
    if (filter.sort() == 0 || filter.sort() == 3 || filter.sort() == 5) Collections.reverse(out);
    ctx.targets().setItems(out.subList(0, Math.min(amount, out.size())));
    ctx.budget().consumeTargets(ctx.targets().itemCount());
    return true;
  }

  private static boolean applyUserVariableFilter(
      WiredContext ctx, WiredAddonUserVariableFilter filter) {
    Integer amount =
        resolveSelectorFilterAmount(
            ctx, filter.usesReference(), filter.target(), filter.reference(), filter.amount());
    if (amount == null) return false;
    WiredVariableManager m = ctx.room().getRoomSpecialTypes().getWiredVariableManager();
    if (m == null) return false;
    WiredVariableManager.VariableSnapshot v = m.publicSnapshot().variables().get(filter.variable());
    if (v == null) return false;
    List<RoomUnit> out = new ArrayList<>();
    Map<Integer, com.eu.habbo.habbohotel.wired.variables.WiredVariableValue> values =
        new HashMap<>();
    for (var x : v.values())
      if (x.holder().scope() == WiredVariableHolder.Scope.USER)
        values.put(x.holder().stableId(), x);
    for (RoomUnit u : ctx.targets().users()) {
      var h = ctx.room().getHabbo(u);
      if (h != null && values.containsKey(h.getHabboInfo().getId())) out.add(u);
    }
    out.sort(
        Comparator.comparingLong(
                (RoomUnit u) ->
                    variableOrder(
                        values.get(ctx.room().getHabbo(u).getHabboInfo().getId()), filter.sort()))
            .thenComparingInt(RoomUnit::getId));
    if (filter.sort() == 0 || filter.sort() == 3 || filter.sort() == 5) Collections.reverse(out);
    ctx.targets().setUsers(out.subList(0, Math.min(amount, out.size())));
    ctx.budget().consumeTargets(ctx.targets().userCount());
    return true;
  }

  private static long variableOrder(
      com.eu.habbo.habbohotel.wired.variables.WiredVariableValue v, int sort) {
    return switch (sort) {
      case 2, 3 -> v.createdAtMs();
      case 4, 5 -> v.updatedAtMs();
      default -> v.value();
    };
  }

  /**
   * July ValueOrVariable target codes: 0 room, 1 first furni target, 2 first user target, 3
   * execution context.
   */
  private static Integer resolveSelectorFilterAmount(
      WiredContext ctx, boolean usesReference, int target, String reference, int literal) {
    if (!usesReference) return literal >= 1 && literal <= 1000 ? literal : null;
    if (ctx == null || reference == null || reference.isBlank()) return null;
    if (target == 3) return boundedAmount(ctx.contextVariables().get(reference));
    Room room = ctx.room();
    if (room == null) return null;
    WiredVariableManager manager = room.getRoomSpecialTypes().getWiredVariableManager();
    if (manager == null) return null;
    WiredVariableManager.VariableSnapshot variable =
        manager.publicSnapshot().variables().get(reference);
    if (variable == null) return null;
    WiredVariableHolder holder;
    if (target == 0) holder = WiredVariableHolder.room();
    else if (target == 1) {
      HabboItem item =
          ctx.targets().items().stream()
              .min(Comparator.comparingInt(HabboItem::getId))
              .orElse(null);
      if (item == null) return null;
      holder = WiredVariableHolder.furni(item.getId());
    } else if (target == 2) {
      RoomUnit unit =
          ctx.targets().users().stream().min(Comparator.comparingInt(RoomUnit::getId)).orElse(null);
      if (unit == null || room.getHabbo(unit) == null) return null;
      holder = WiredVariableHolder.user(room.getHabbo(unit).getHabboInfo().getId());
    } else return null;
    return variable.values().stream()
        .filter(value -> value.holder().equals(holder))
        .map(value -> boundedAmount(value.value()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static Integer boundedAmount(Integer value) {
    return value != null && value >= 1 && value <= 1000 ? value : null;
  }

  /**
   * The raw selector result is already charged. An inversion may materialize a much larger
   * room-wide complement, so charge that expansion as well.
   */
  static int additionalTargetsAfterTransform(int resolvedCount, int transformedCount) {
    if (resolvedCount < 0 || transformedCount < 0) {
      throw new IllegalArgumentException("target counts must be non-negative");
    }
    return Math.max(0, transformedCount - resolvedCount);
  }

  private Set<RoomUnit> invertUsers(Room room, Set<RoomUnit> selected) {
    Set<RoomUnit> inverted = new LinkedHashSet<>(room.getRoomUnits());
    inverted.removeAll(selected);
    return inverted;
  }

  private Set<HabboItem> invertItems(Room room, Set<HabboItem> selected) {
    Set<HabboItem> inverted = new LinkedHashSet<>(room.getFloorItems());
    inverted.removeAll(selected);
    return inverted;
  }

  /** Evaluate all conditions in a stack. */
  private boolean evaluateConditions(WiredStack stack, WiredContext ctx) {
    List<IWiredCondition> conditions = stack.conditions();
    WiredAddonConditionEvaluation evaluation =
        addon(stack, WiredAddonType.CONDITION_EVALUATION, WiredAddonConditionEvaluation.class);
    if (evaluation != null) {
      int passed = 0;
      for (IWiredCondition condition : conditions) {
        ctx.state().step();
        if (condition.evaluate(ctx)) passed++;
      }
      return evaluation.evaluate(passed, conditions.size());
    }

    if (stack.useOrMode()) {
      // OR mode: at least one condition must pass
      return evaluateOrMode(conditions, ctx);
    } else {
      // Standard mode: use individual operators
      return evaluateStandardMode(conditions, ctx);
    }
  }

  /** Evaluate conditions in OR mode (any pass = success). */
  private boolean evaluateOrMode(List<IWiredCondition> conditions, WiredContext ctx) {
    // Group by condition type (for legacy compatibility)
    Map<String, Boolean> typeResults = new HashMap<>();

    for (IWiredCondition condition : conditions) {
      ctx.state().step();

      String typeName = condition.getClass().getSimpleName();
      if (!typeResults.containsKey(typeName) && condition.evaluate(ctx)) {
        typeResults.put(typeName, true);
      }
    }

    // At least one condition type must have passed
    return !typeResults.isEmpty();
  }

  /** Evaluate conditions in standard mode using operators. */
  private boolean evaluateStandardMode(List<IWiredCondition> conditions, WiredContext ctx) {
    Room room = ctx.room();

    // First pass: collect all OR conditions that passed
    Map<String, Boolean> orResults = new HashMap<>();
    for (IWiredCondition condition : conditions) {
      if (condition.operator() == WiredConditionOperator.OR) {
        ctx.state().step();
        String typeName = condition.getClass().getSimpleName();
        boolean result = condition.evaluate(ctx);
        debug(room, "  Condition (OR) {}: {}", typeName, result ? "PASS" : "FAIL");
        if (!orResults.containsKey(typeName) && result) {
          orResults.put(typeName, true);
        }
      }
    }

    // Second pass: verify all conditions
    for (IWiredCondition condition : conditions) {
      boolean passes;
      String typeName = condition.getClass().getSimpleName();

      if (condition.operator() == WiredConditionOperator.OR) {
        // OR: passes if any of same type passed
        passes = orResults.containsKey(typeName);
        debug(room, "  Condition (OR check) {}: {}", typeName, passes ? "PASS" : "FAIL");
      } else {
        // AND: must evaluate and pass
        ctx.state().step();
        passes = condition.evaluate(ctx);
        debug(room, "  Condition (AND) {}: {}", typeName, passes ? "PASS" : "FAIL");
      }

      if (!passes) {
        return false;
      }
    }

    return true;
  }

  /** Execute effects in a stack. */
  private boolean executeEffects(
      WiredStack stack,
      WiredContext ctx,
      long currentTime,
      Set<HabboItem> boxUpdates,
      boolean negativeOnly) {
    List<IWiredEffect> effects =
        effectsForOutcome(stack.effects(), negativeOnly).stream()
            .filter(
                effect ->
                    !(effect
                            instanceof
                            com.eu.habbo.habbohotel.items.interactions.InteractionWired wired)
                        || WiredFeatureCapabilityGuard.isRuntimeReady(wired))
            .toList();

    if (effects.isEmpty()) {
      return false;
    }

    // Determine which effects to execute
    List<IWiredEffect> toExecute;

    WiredAddonRandomEffect randomAddon =
        addon(stack, WiredAddonType.EFFECT_PICK_AND_SKIP, WiredAddonRandomEffect.class);
    WiredAddonUnseenEffect unseenAddon =
        addon(stack, WiredAddonType.EFFECT_MARKER, WiredAddonUnseenEffect.class);
    if (randomAddon != null) {
      toExecute = randomAddon.selectEffects(effects, Emulator.getRandom());
      debug(ctx.room(), "Random-effect add-on selected {} of {}", toExecute.size(), effects.size());
    } else if (unseenAddon != null) {
      IWiredEffect selected = unseenAddon.selectEffect(effects);
      toExecute = selected == null ? Collections.emptyList() : Collections.singletonList(selected);
      debug(ctx.room(), "Unseen-effect add-on selected {} of {}", toExecute.size(), effects.size());
    } else if (stack.useRandom()) {
      // Random mode: pick one random effect
      int randomIndex = new Random().nextInt(effects.size());
      toExecute = Collections.singletonList(effects.get(randomIndex));
      debug(ctx.room(), "Random mode: selected effect {}/{}", randomIndex + 1, effects.size());
    } else if (stack.useUnseen()) {
      // Unseen mode: round-robin selection
      int index = getNextUnseenIndex(stack, effects.size());
      toExecute = Collections.singletonList(effects.get(index));
      debug(ctx.room(), "Unseen mode: selected effect {}/{}", index + 1, effects.size());
    } else {
      boolean executeInOrder =
          stack.executeInOrder()
              && WiredCapabilityService.isRoomCapabilityReady(
                  WiredCapabilityService.CAPABILITY_ADDONS);
      toExecute = resolveExecutionOrder(effects, executeInOrder, new Random());
      if (!executeInOrder) {
        toExecute = orderSignalEffectsLast(toExecute);
      }
      debug(
          ctx.room(),
          executeInOrder
              ? "Execute-in-order mode: selected {} effects"
              : "Normal mode: shuffled {} effects",
          toExecute.size());
    }

    // Execute selected effects
    boolean executedAny = false;
    for (IWiredEffect effect : toExecute) {
      // Check if effect requires actor
      if (effect.requiresActor() && !ctx.hasActor()) {
        continue;
      }

      // Respect cooldown per triggering room unit so one user's flood does not suppress the
      // same wired stack for everyone else. Movement effects bypass this: they re-run every
      // tick and stream smooth WiredMovements slides instead.
      if (effect instanceof InteractionWiredEffect) {
        InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
        if (!wiredEffect.bypassExecutionCooldown()) {
          RoomUnit actor = ctx.actor().orElse(null);
          boolean canExecute =
              actor != null
                  ? wiredEffect.userCanExecute(actor.getId(), currentTime)
                  : wiredEffect.canExecute(currentTime);

          if (!canExecute) {
            continue;
          }
        }
      }

      // Handle delay
      int delay = effect.getDelay();
      if (delay > 0) {
        // Schedule delayed execution
        WiredContext delayedContext = ctx.forkForDelayedExecution();
        delayedContext.state().step();
        if (!WiredManager.getUsageTracker().tryQueueDelayed(ctx.room())) {
          delayedContext.clearContextVariables();
          continue;
        }
        scheduleDelayedEffect(
            DelayedEffectSnapshot.capture(effect), delayedContext, delay, currentTime);
        executedAny = true;
      } else {
        // Execute immediately
        ctx.state().step();
        try {
          WiredMovementAddonRuntime.withPhysics(ctx, () -> effect.execute(ctx));
          executedAny = true;

          // Activate box animation after execution
          if (effect instanceof InteractionWiredEffect) {
            InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
            if (!ctx.hasActor()) {
              wiredEffect.setCooldown(currentTime);
            }
            wiredEffect.activateBox(ctx.room(), ctx.actor().orElse(null), currentTime, boxUpdates);
          }
        } catch (WiredLimitException limitException) {
          throw limitException;
        } catch (Exception e) {
          LOGGER.warn("Error executing effect: {}", e.getMessage());
          WiredRoomMonitor.runtimeError(ctx.room(), "EFFECT", e);
        }
      }
    }
    return executedAny;
  }

  /** New add-ons take precedence over same-purpose legacy extras; duplicates use lowest item id. */
  private static <T extends InteractionWiredAddon> T addon(
      WiredStack stack, WiredAddonType type, Class<T> expected) {
    InteractionWiredAddon addon = stack.addon(type);
    return expected.isInstance(addon) ? expected.cast(addon) : null;
  }

  /** Schedule a delayed effect execution. */
  private void scheduleDelayedEffect(
      DelayedEffectSnapshot effectSnapshot, WiredContext ctx, int delay, long currentTime) {
    // Delay is in 500ms ticks
    long delayMs = delay * 500L;
    Room room = ctx.room();
    DelayedContextSnapshot contextSnapshot = DelayedContextSnapshot.capture(ctx);

    ScheduledFuture<?> scheduled = Emulator.getThreading()
        .run(
            () -> {
              try {
                if (!room.isLoaded() || room.getHabbos().isEmpty()) {
                  ctx.clearContextVariables();
                  return;
                }
                if (!isDelayedContextValid(effectSnapshot, contextSnapshot, ctx)) {
                  ctx.clearContextVariables();
                  return;
                }
                IWiredEffect effect = effectSnapshot.effect();
                RoomUnit actor = ctx.actor().orElse(null);

                WiredState previousState = CURRENT_STATE.get();
                WiredContextVariableStore previousVariables = CURRENT_CONTEXT_VARIABLES.get();
                CURRENT_STATE.set(ctx.state());
                CURRENT_CONTEXT_VARIABLES.set(ctx.contextVariables());
                try {
                  WiredMovementAddonRuntime.withPhysics(ctx, () -> effect.execute(ctx));

                  // Activate box animation after execution
                  if (effect instanceof InteractionWiredEffect) {
                    InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
                    long executedAt = System.currentTimeMillis();
                    if (actor == null) {
                      wiredEffect.setCooldown(executedAt);
                    }
                    wiredEffect.activateBox(room, actor, executedAt);
                  }
                } catch (WiredLimitException limitException) {
                  WiredRoomMonitor.recursionTimeout(room);
                  debug(room, "Delayed effect stopped (limit): {}", limitException.getMessage());
                } catch (Exception e) {
                  LOGGER.warn("Error executing delayed effect: {}", e.getMessage());
                  WiredRoomMonitor.runtimeError(room, "DELAYED_EFFECT", e);
                } finally {
                  ctx.clearContextVariables();
                  if (previousVariables == null) {
                    CURRENT_CONTEXT_VARIABLES.remove();
                  } else {
                    CURRENT_CONTEXT_VARIABLES.set(previousVariables);
                  }
                  restoreCurrentState(previousState);
                }
              } finally {
                WiredManager.getUsageTracker().completeDelayed(room);
              }
            },
            delayMs);
    if (scheduled == null) {
      WiredManager.getUsageTracker().completeDelayed(room);
      ctx.clearContextVariables();
      WiredRoomMonitor.executorOverload(room);
    }
  }

  /** Package-private seam used to prove that code 17 only changes default stack ordering. */
  static List<IWiredEffect> resolveExecutionOrder(
      List<IWiredEffect> effects, boolean executeInOrder, Random random) {
    List<IWiredEffect> ordered = new ArrayList<>(effects);
    if (!executeInOrder) {
      Collections.shuffle(ordered, Objects.requireNonNull(random, "random"));
    }
    return ordered;
  }

  private boolean isDelayedContextValid(
      DelayedEffectSnapshot effectSnapshot,
      DelayedContextSnapshot contextSnapshot,
      WiredContext ctx) {
    Room room = ctx.room();
    if (!effectSnapshot.isStillCurrent(room)
        || !contextSnapshot.rebind(ctx, effectSnapshot.effect().requiresActor())) {
      return false;
    }
    return true;
  }

  static DelayedActorValidation validateDelayedActor(
      RoomUnit actor, Room expectedRoom, boolean requiresActor) {
    if (actor == null) {
      return new DelayedActorValidation(true, null);
    }
    if (actor.getRoom() == expectedRoom && actor.isInRoom()) {
      return new DelayedActorValidation(true, actor);
    }
    return requiresActor
        ? new DelayedActorValidation(false, null)
        : new DelayedActorValidation(true, null);
  }

  static record DelayedActorValidation(boolean valid, RoomUnit actor) {}

  static record DelayedContextSnapshot(
      DelayedUnitRef actor,
      List<DelayedUnitRef> users,
      List<Integer> itemIds,
      Integer triggerItemId,
      Integer sourceItemId) {

    static DelayedContextSnapshot capture(WiredContext ctx) {
      Room room = ctx.room();
      return new DelayedContextSnapshot(
          ctx.actor().map(unit -> DelayedUnitRef.capture(room, unit)).orElse(null),
          ctx.targets().users().stream()
              .map(unit -> DelayedUnitRef.capture(room, unit))
              .filter(Objects::nonNull)
              .toList(),
          ctx.targets().items().stream()
              .filter(Objects::nonNull)
              .map(HabboItem::getId)
              .distinct()
              .toList(),
          ctx.triggerItem() == null ? null : ctx.triggerItem().getId(),
          ctx.sourceItem().map(HabboItem::getId).orElse(null));
    }

    boolean rebind(WiredContext ctx, boolean requiresActor) {
      Room room = ctx.room();
      RoomUnit resolvedActor = this.actor == null ? null : this.actor.resolve(room);
      if (requiresActor && resolvedActor == null) {
        return false;
      }

      HabboItem resolvedTrigger = resolveItem(room, this.triggerItemId);
      if (this.triggerItemId != null && resolvedTrigger == null) {
        return false;
      }
      HabboItem resolvedSource = resolveItem(room, this.sourceItemId);
      if (this.sourceItemId != null && resolvedSource == null) {
        return false;
      }

      ctx.bindDelayedIdentity(resolvedActor, resolvedTrigger, resolvedSource);
      ctx.targets()
          .setUsers(
              this.users.stream().map(ref -> ref.resolve(room)).filter(Objects::nonNull).toList());
      ctx.targets()
          .setItems(
              this.itemIds.stream()
                  .map(id -> resolveItem(room, id))
                  .filter(Objects::nonNull)
                  .toList());
      return true;
    }

    private static HabboItem resolveItem(Room room, Integer databaseId) {
      if (room == null || databaseId == null) {
        return null;
      }
      HabboItem item = room.getHabboItemByDatabaseId(databaseId);
      return item != null && item.getRoomId() == room.getId() ? item : null;
    }
  }

  static record DelayedUnitRef(RoomUnitType type, int stableId, int roomUnitId) {
    static DelayedUnitRef capture(Room room, RoomUnit unit) {
      if (room == null || unit == null) {
        return null;
      }
      RoomUnitType type = unit.getRoomUnitType();
      int stableId =
          switch (type) {
            case USER -> {
              var habbo = room.getHabbo(unit);
              yield habbo == null ? -1 : habbo.getHabboInfo().getId();
            }
            case BOT -> {
              var bot = room.getBot(unit);
              yield bot == null ? -1 : bot.getId();
            }
            case PET -> {
              var pet = room.getPet(unit);
              yield pet == null ? -1 : pet.getId();
            }
            case UNKNOWN -> -1;
          };
      return new DelayedUnitRef(type, stableId, unit.getId());
    }

    RoomUnit resolve(Room room) {
      if (room == null) {
        return null;
      }
      RoomUnit unit =
          switch (this.type) {
            case USER -> {
              var habbo = this.stableId < 0 ? null : room.getHabbo(this.stableId);
              yield habbo == null ? null : habbo.getRoomUnit();
            }
            case BOT -> {
              var bot = this.stableId < 0 ? null : room.getBot(this.stableId);
              yield bot == null ? null : bot.getRoomUnit();
            }
            case PET -> {
              var pet = this.stableId < 0 ? null : room.getPet(this.stableId);
              yield pet == null ? null : pet.getRoomUnit();
            }
            case UNKNOWN ->
                room.getRoomUnits().stream()
                    .filter(
                        candidate ->
                            candidate != null
                                && candidate.getRoomUnitType() == RoomUnitType.UNKNOWN
                                && candidate.getId() == this.roomUnitId)
                    .findFirst()
                    .orElse(null);
          };
      return unit != null && unit.getRoom() == room && unit.isInRoom() ? unit : null;
    }
  }

  static record DelayedEffectSnapshot(
      IWiredEffect effect,
      InteractionWiredEffect furnitureEffect,
      String wiredData,
      String wiredSourcesData,
      int roomId,
      int x,
      int y,
      double z) {

    static DelayedEffectSnapshot capture(IWiredEffect effect) {
      if (effect instanceof InteractionWiredEffect furnitureEffect) {
        return new DelayedEffectSnapshot(
            effect,
            furnitureEffect,
            furnitureEffect.getWiredData(),
            furnitureEffect.getWiredSourcesData(),
            furnitureEffect.getRoomId(),
            furnitureEffect.getX(),
            furnitureEffect.getY(),
            furnitureEffect.getZ());
      }
      return new DelayedEffectSnapshot(effect, null, null, null, 0, 0, 0, 0.0);
    }

    boolean isStillCurrent(Room room) {
      return this.furnitureEffect == null
          || (room.getRoomSpecialTypes().getEffect(this.furnitureEffect.getId())
                  == this.furnitureEffect
              && configurationMatches());
    }

    boolean configurationMatches() {
      return this.furnitureEffect == null
          || (this.roomId == this.furnitureEffect.getRoomId()
              && this.x == this.furnitureEffect.getX()
              && this.y == this.furnitureEffect.getY()
              && Double.compare(this.z, this.furnitureEffect.getZ()) == 0
              && Objects.equals(this.wiredData, this.furnitureEffect.getWiredData())
              && Objects.equals(this.wiredSourcesData, this.furnitureEffect.getWiredSourcesData()));
    }
  }

  /** Get the next unseen index for round-robin selection. */
  private int getNextUnseenIndex(WiredStack stack, int effectCount) {
    String key =
        stack.triggerItem() != null ? String.valueOf(stack.triggerItem().getId()) : "default";

    int current = unseenIndices.getOrDefault(key, -1);
    int next = (current + 1) % effectCount;
    unseenIndices.put(key, next);

    return next;
  }

  /** Fire the WiredStackTriggeredEvent for plugin compatibility. */
  private boolean fireTriggeredEvent(WiredStack stack, WiredEvent event) {
    // Build legacy collections for event
    if (stack.triggerItem() instanceof InteractionWiredTrigger) {
      // This event is checked for cancellation
      THashSet<InteractionWiredEffect> legacyEffects = new THashSet<>();
      THashSet<InteractionWiredCondition> legacyConditions = new THashSet<>();

      // Extract effects (all effects should now implement both interfaces)
      for (IWiredEffect eff : stack.effects()) {
        if (eff instanceof InteractionWiredEffect) {
          legacyEffects.add((InteractionWiredEffect) eff);
        }
      }
      for (IWiredCondition cond : stack.conditions()) {
        if (cond instanceof InteractionWiredCondition) {
          legacyConditions.add((InteractionWiredCondition) cond);
        }
      }

      WiredStackTriggeredEvent triggeredEvent =
          new WiredStackTriggeredEvent(
              event.getRoom(),
              event.getActor().orElse(null),
              (InteractionWiredTrigger) stack.triggerItem(),
              legacyEffects,
              legacyConditions);

      return !Emulator.getPluginManager().fireEvent(triggeredEvent).isCancelled();
    }
    return true;
  }

  /** Fire the WiredStackExecutedEvent for plugin compatibility. */
  private void fireExecutedEvent(WiredStack stack, WiredEvent event) {
    if (stack.triggerItem() instanceof InteractionWiredTrigger) {
      THashSet<InteractionWiredEffect> legacyEffects = new THashSet<>();
      THashSet<InteractionWiredCondition> legacyConditions = new THashSet<>();

      for (IWiredEffect eff : stack.effects()) {
        if (eff instanceof InteractionWiredEffect) {
          legacyEffects.add((InteractionWiredEffect) eff);
        }
      }
      for (IWiredCondition cond : stack.conditions()) {
        if (cond instanceof InteractionWiredCondition) {
          legacyConditions.add((InteractionWiredCondition) cond);
        }
      }

      Emulator.getPluginManager()
          .fireEvent(
              new WiredStackExecutedEvent(
                  event.getRoom(),
                  event.getActor().orElse(null),
                  (InteractionWiredTrigger) stack.triggerItem(),
                  legacyEffects,
                  legacyConditions));
    }
  }

  /** Log a debug message if debug mode is enabled. */
  private void debug(Room room, String format, Object... args) {
    if (WiredManager.isDebugEnabled()) {
      String message = String.format(format.replace("{}", "%s"), args);
      LOGGER.info("[WiredEngine][Room {}] {}", room.getId(), message);
    }
  }

  /** Activate all legacy extras and Wired 2.0 add-ons at the trigger location. */
  private void activateStackModifiers(
      Room room, HabboItem triggerItem, RoomUnit roomUnit, long millis, Set<HabboItem> boxUpdates) {
    if (triggerItem == null || room.getRoomSpecialTypes() == null) {
      return;
    }

    THashSet<InteractionWiredExtra> extras =
        room.getRoomSpecialTypes().getExtras(triggerItem.getX(), triggerItem.getY());

    if (extras != null) {
      for (InteractionWiredExtra extra : extras) {
        extra.activateBox(room, roomUnit, millis, boxUpdates);
      }
    }

    if (WiredCapabilityService.isRoomCapabilityReady(WiredCapabilityService.CAPABILITY_ADDONS)) {
      for (InteractionWiredAddon addon :
          room.getRoomSpecialTypes().getAddons(triggerItem.getX(), triggerItem.getY())) {
        addon.activateBox(room, roomUnit, millis, boxUpdates);
      }
    }
  }

  /** Package-private seam proving positive and negative lanes are mutually exclusive. */
  static List<IWiredEffect> effectsForOutcome(List<IWiredEffect> effects, boolean negativeOnly) {
    if (effects == null || effects.isEmpty()) {
      return List.of();
    }
    return effects.stream()
        .filter(effect -> (effect instanceof IWiredNegativeEffect) == negativeOnly)
        .toList();
  }

  /** Donor-proven ordinary-stack rule: signals observe preceding effect mutations. */
  static List<IWiredEffect> orderSignalEffectsLast(List<IWiredEffect> effects) {
    List<IWiredEffect> ordered = new ArrayList<>(effects);
    ordered.sort(Comparator.comparingInt(effect -> effect instanceof IWiredSignalEffect ? 1 : 0));
    return ordered;
  }

  /**
   * Get the services used by this engine.
   *
   * @return the wired services
   */
  public WiredServices getServices() {
    return services;
  }

  /**
   * Get the stack index used by this engine.
   *
   * @return the stack index
   */
  public WiredStackIndex getIndex() {
    return index;
  }

  /**
   * Get the maximum steps per stack.
   *
   * @return max steps
   */
  public int getMaxStepsPerStack() {
    return maxStepsPerStack;
  }

  /** Clear all cached unseen indices. */
  public void clearUnseenCache() {
    unseenIndices.clear();
  }

  /**
   * Clear recursion tracking for a specific room. Should be called when a room is unloaded.
   *
   * @param roomId the room ID
   */
  public void clearRoomRecursionDepth(int roomId) {
    roomRecursionDepth.remove(roomId);
  }

  /** Clear all recursion tracking. */
  public void clearAllRecursionDepth() {
    roomRecursionDepth.clear();
  }

  /**
   * Get the current recursion depth for a room (for debugging).
   *
   * @param roomId the room ID
   * @return the current recursion depth, or 0 if not tracked
   */
  public int getRecursionDepth(int roomId) {
    return roomRecursionDepth.getOrDefault(roomId, 0);
  }

  /**
   * Clear rate limiters for a specific room. Should be called when a room is unloaded.
   *
   * @param roomId the room ID
   */
  public void clearRoomRateLimiters(int roomId) {
    String prefix = roomId + ":";
    eventRateLimiters.keySet().removeIf(key -> key.startsWith(prefix));
  }

  /**
   * Clear room ban for a specific room. Should be called when a room is unloaded.
   *
   * @param roomId the room ID
   */
  public void clearRoomBan(int roomId) {
    bannedRooms.remove(roomId);
  }

  /**
   * Check if a room is currently banned from wired execution.
   *
   * @param roomId the room ID
   * @return true if wired is banned in this room
   */
  private boolean isRoomBanned(int roomId) {
    Long banExpiry = bannedRooms.get(roomId);
    if (banExpiry == null) {
      return false;
    }

    if (System.currentTimeMillis() >= banExpiry) {
      // Ban expired, remove it
      bannedRooms.remove(roomId);
      return false;
    }

    return true;
  }

  /**
   * Ban wired execution in a room for WIRED_BAN_DURATION_MS. Sends alerts to all users in the room
   * and a scripter alert to staff.
   *
   * @param roomId the room ID
   * @param room the room object (for sending alerts)
   */
  private void banRoom(int roomId, Room room) {
    long banExpiry = System.currentTimeMillis() + WIRED_BAN_DURATION_MS;
    bannedRooms.put(roomId, banExpiry);
    WiredRoomMonitor.killed(room);

    long banMinutes = WIRED_BAN_DURATION_MS / 60000;

    // Send alert to all users in the room
    String roomAlertMessage =
        Emulator.getTexts()
            .getValue("wired.abuse.room.alert")
            .replace("%minutes%", String.valueOf(banMinutes));
    room.sendComposer(new HabboBroadcastMessageComposer(roomAlertMessage).compose());

    // Send scripter bubble alert to staff with room link
    THashMap<String, String> keys = new THashMap<>();
    keys.put("title", Emulator.getTexts().getValue("wired.abuse.staff.title"));
    keys.put(
        "message",
        Emulator.getTexts()
            .getValue("wired.abuse.staff.message")
            .replace("%roomname%", room.getName())
            .replace("%owner%", room.getOwnerName())
            .replace("%minutes%", String.valueOf(banMinutes)));
    keys.put("linkUrl", "event:navigator/goto/" + roomId);
    keys.put("linkTitle", Emulator.getTexts().getValue("wired.abuse.staff.link"));
    Emulator.getGameEnvironment()
        .getHabboManager()
        .sendPacketToHabbosWithPermission(
            new NotificationDialogMessageComposer("admin.staffalert", keys).compose(),
            "acc_modtool_room_info");

    LOGGER.warn(
        "Wired abuse detected in room {} ({}). Owner: {}. Wired banned for {} minutes.",
        roomId,
        room.getName(),
        room.getOwnerName(),
        banMinutes);
  }

  /**
   * Check if an event should be rate-limited. If rate limit exceeded, bans the room and sends
   * alerts.
   *
   * @param roomId the room ID
   * @param room the room object (for sending alerts if banned)
   * @param eventType the event type
   * @return true if the event should be blocked due to rate limiting
   */
  /**
   * Timer-driven events are self-paced by their own configured interval (and capped by the room
   * tick), so they are not subject to the loop-abuse rate limiter. A legitimate short repeater
   * fires far faster than the per-type window allows, but it is not a runaway loop. Effect->trigger
   * loops are still caught by the recursion-depth check and per-effect cooldowns.
   */
  private static boolean isSelfPacedTimerEvent(WiredEvent.Type type) {
    return type == WiredEvent.Type.TIMER_REPEAT
        || type == WiredEvent.Type.TIMER_REPEAT_SHORT
        || type == WiredEvent.Type.TIMER_REPEAT_LONG
        || type == WiredEvent.Type.TIMER_TICK;
  }

  private boolean isRateLimited(int roomId, Room room, WiredEvent.Type eventType) {
    String key = roomId + ":" + eventType.name();
    long now = System.currentTimeMillis();

    EventRateTracker tracker =
        eventRateLimiters.compute(
            key,
            (k, existing) -> {
              if (existing == null) {
                return new EventRateTracker(now);
              }
              existing.recordEvent(now);
              return existing;
            });

    boolean limited = tracker.isRateLimited(now);
    if (limited && tracker.shouldBan(now)) {
      // First time hitting limit in this suppression window - ban the room
      banRoom(roomId, room);
    }
    return limited;
  }

  /** Tracks event rate for a specific room + event type combination. */
  private static final class EventRateTracker {
    private long windowStart;
    private int eventCount;
    private boolean banned;

    EventRateTracker(long now) {
      this.windowStart = now;
      this.eventCount = 1;
      this.banned = false;
    }

    synchronized void recordEvent(long now) {
      // Reset window if expired
      if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
        windowStart = now;
        eventCount = 1;
        // Don't reset banned here - room ban is checked separately
      } else {
        eventCount++;
      }
    }

    synchronized boolean isRateLimited(long now) {
      return eventCount > MAX_EVENTS_PER_WINDOW;
    }

    /**
     * Check if this is the first time we've hit the limit (to trigger ban). Returns true only once
     * per suppression window.
     */
    synchronized boolean shouldBan(long now) {
      if (eventCount > MAX_EVENTS_PER_WINDOW && !banned) {
        banned = true;
        return true;
      }
      return false;
    }
  }
}
