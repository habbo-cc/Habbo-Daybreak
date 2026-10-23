package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRoomInitialized;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import gnu.trove.set.hash.THashSet;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WiredRoomInitializationRuntimeTest {
    private final Room room = mock(Room.class);
    private final WiredRoomInitialization lifecycle = new WiredRoomInitialization();
    private final WiredTriggerRoomInitialized trigger = new WiredTriggerRoomInitialized(28, 1, null, "", 0, 0);
    private final THashSet<InteractionWiredTrigger> initializationBoxes = new THashSet<>();
    private final ConfigurationManager config = mock(ConfigurationManager.class);
    private MockedStatic<Emulator> emulator;
    private Field engineField;
    private Object previousEngine;

    @BeforeEach
    void setup() throws Exception {
        emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(call ->
                call.getArgument(0).equals(WiredManager.CONFIG_ENABLED) ? true : call.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
        when(room.isLoaded()).thenReturn(true);
        when(room.getId()).thenReturn(987655);
        when(room.getWiredInitialization()).thenReturn(lifecycle);
        when(room.initializeWiredForEntry()).thenCallRealMethod();
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        initializationBoxes.add(trigger);
        when(special.getTriggers(WiredTriggerType.ROOM_INITIALIZED)).thenReturn(initializationBoxes);
        engineField = WiredManager.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        previousEngine = engineField.get(null);
    }

    @AfterEach
    void restore() throws Exception {
        engineField.set(null, previousEngine);
        WiredManager.getUsageTracker().clear(room);
        emulator.close();
    }

    @Test
    void actualRoomAdmissionRunsInitializationBeforeEntryAndAllowsNestedEffects() throws Exception {
        List<String> order = new ArrayList<>();
        WiredStack init = new WiredStack(null, trigger, List.of(), List.of(ctx -> {
            assertTrue(ctx.actor().isEmpty());
            order.add("init");
            assertTrue(WiredManager.handleEvent(event(WiredEvent.Type.TIMER_TICK)));
        }));
        WiredStack timer = new WiredStack(null, ordinaryTrigger(), List.of(), List.of(ctx -> order.add("timer")));
        WiredStack entry = new WiredStack(null, ordinaryTrigger(), List.of(), List.of(ctx -> order.add("enter")));
        engineField.set(null, new WiredEngine(mock(WiredServices.class), (r, type) -> switch (type) {
            case ROOM_INITIALIZED -> List.of(init);
            case TIMER_TICK -> List.of(timer);
            case USER_ENTERS_ROOM -> List.of(entry);
            default -> List.of();
        }, 100));
        assertFalse(WiredManager.handleEvent(event(WiredEvent.Type.TIMER_TICK)));
        assertTrue(order.isEmpty(), "preloading cannot execute the initialization or timers");
        assertTrue(room.initializeWiredForEntry());
        assertTrue(WiredManager.handleEvent(event(WiredEvent.Type.USER_ENTERS_ROOM)));
        assertTrue(room.initializeWiredForEntry());
        assertEquals(List.of("init", "timer", "enter"), order);
        assertEquals(28, trigger.getType().code);
        assertFalse(trigger.requiresActor());
    }

    @Test
    void roomsWithoutInitializationBoxesKeepExistingLoadTimeDispatch() throws Exception {
        initializationBoxes.clear();
        AtomicBoolean ran = new AtomicBoolean();
        WiredStack ordinary = new WiredStack(null, ordinaryTrigger(), List.of(), List.of(ctx -> ran.set(true)));
        engineField.set(null, new WiredEngine(mock(WiredServices.class), (r, type) -> List.of(ordinary), 100));
        assertTrue(WiredManager.handleEvent(event(WiredEvent.Type.TIMER_TICK)));
        assertTrue(ran.get());
    }

    @Test
    void realTickServiceDoesNotAdvanceTimersBeforeInitializationOrAfterReset() throws Exception {
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        when(environment.getRoomManager().getRoom(room.getId())).thenReturn(room);
        ConcurrentHashMap<Integer, Habbo> occupants = new ConcurrentHashMap<>();
        occupants.put(1, mock(Habbo.class));
        when(room.getCurrentHabbos()).thenReturn(occupants);
        var constructor = WiredTickService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        WiredTickService ticks = constructor.newInstance();
        Field running = WiredTickService.class.getDeclaredField("running");
        running.setAccessible(true);
        ((AtomicBoolean) running.get(ticks)).set(true);
        var tick = WiredTickService.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        WiredTickable timer = mock(WiredTickable.class);
        int roomId = room.getId();
        when(timer.getRoomId()).thenReturn(roomId);
        ticks.register(room, timer);
        boolean shuttingDown = Emulator.isShuttingDown;
        Emulator.isShuttingDown = false;
        try {
            tick.invoke(ticks);
            verify(timer, never()).onWiredTick(any(), anyLong(), anyInt());
            lifecycle.beforeEntry(() -> {});
            tick.invoke(ticks);
            verify(timer, times(1)).onWiredTick(eq(room), anyLong(), anyInt());
            lifecycle.reset();
            tick.invoke(ticks);
            verify(timer, times(1)).onWiredTick(eq(room), anyLong(), anyInt());
        } finally {
            Emulator.isShuttingDown = shuttingDown;
        }
    }

    @Test
    void initializationSettingsAreEmptyAndOnlyInitializationEventsMatch() {
        assertTrue(trigger.saveData(new WiredSettings(new int[0], "", new int[0], 0)));
        assertFalse(trigger.saveData(new WiredSettings(new int[]{1}, "", new int[0], 0)));
        assertFalse(trigger.saveData(new WiredSettings(new int[0], "unexpected", new int[0], 0)));
        assertEquals("", trigger.getWiredData());
        trigger.loadWiredData(null, room);
        trigger.onPickUp();
        assertTrue(trigger.matches(null, event(WiredEvent.Type.ROOM_INITIALIZED)));
        assertFalse(trigger.matches(null, event(WiredEvent.Type.USER_ENTERS_ROOM)));
        assertFalse(trigger.matches(null, event(WiredEvent.Type.TIMER_TICK)));
    }

    private WiredEvent event(WiredEvent.Type type) { return WiredEvent.builder(type, room).build(); }
    private IWiredTrigger ordinaryTrigger() {
        IWiredTrigger result = mock(IWiredTrigger.class);
        when(result.matches(any(), any())).thenReturn(true);
        return result;
    }
}
