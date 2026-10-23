package com.eu.habbo.habbohotel.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerScoreAchieved;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredScoreChange;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real mutations -> event factory/manager -> trigger matching, without a server/database. */
class WiredTeamScoreRuntimeTest {
    private final Room room = mock(Room.class);
    private final TestGame game = new TestGame(room);
    private final GameTeam red = new GameTeam(GameTeamColors.RED);
    private final List<WiredEvent> events = new ArrayList<>();
    private MockedStatic<WiredManager> manager;

    @BeforeEach
    void setup() {
        game.addTeam(red);
        when(room.getGame(TestGame.class)).thenReturn(game);
        manager = mockStatic(WiredManager.class, CALLS_REAL_METHODS);
        manager.when(WiredManager::isEnabled).thenReturn(true);
        manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(call -> {
            assertFalse(Thread.holdsLock(red), "Wired callbacks must run outside score locks");
            for (GamePlayer player : red.getMembers()) {
                assertFalse(Thread.holdsLock(player), "Wired callbacks must not hold a player score lock");
            }
            WiredEvent event = call.getArgument(0);
            events.add(event);
            return true;
        });
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void multiplePlayersCrossTheirCombinedTeamThresholdOnce() {
        GamePlayer first = player(red);
        GamePlayer second = player(red);
        first.addScore(6);
        second.addScore(4);
        second.addScore(1);

        ScoreTrigger any = trigger(10, 0);
        ScoreTrigger selected = trigger(10, 1);
        ScoreTrigger other = trigger(10, 3);
        assertEquals(List.of(false, true, false), events.stream().map(e -> any.matches(null, e)).toList());
        assertEquals(List.of(false, true, false), events.stream().map(e -> selected.matches(null, e)).toList());
        assertTrue(events.stream().noneMatch(e -> other.matches(null, e)));
        assertEquals(new WiredScoreChange(GameTeamColors.RED, 6, 10), events.get(1).getScoreChange().orElseThrow());
        assertEquals(10, events.get(1).getScore());
        assertEquals(4, events.get(1).getScoreAdded());
        assertSame(second.getHabbo().getRoomUnit(), events.get(1).getActor().orElseThrow());
    }

    @Test
    void directAwardsWithoutMembersOrActorStillTriggerTheSelectedTeam() {
        red.addTeamScore(12);
        WiredEvent event = events.getFirst();
        assertTrue(event.getActor().isEmpty());
        assertEquals(WiredEvent.Type.SCORE_ACHIEVED, event.getType());
        assertTrue(trigger(10, 1).matches(null, event));
        assertTrue(trigger(10, 0).matches(null, event));
        assertFalse(trigger(10, 2).matches(null, event));
    }

    @Test
    void personalAndDirectAwardsUseTheSameTeamTotal() {
        GamePlayer first = player(red);
        GamePlayer second = player(red);
        red.addTeamScore(3);
        first.addScore(3, true);
        second.addScore(4);
        assertEquals(10, red.getTotalScore());
        assertEquals(List.of(false, false, true), events.stream().map(e -> trigger(10, 1).matches(null, e)).toList());
        assertEquals(new WiredScoreChange(GameTeamColors.RED, 6, 10), events.getLast().getScoreChange().orElseThrow());
    }

    @Test
    void decreasesZeroAwardsAndPlayerZeroFloorCannotInventCrossings() {
        GamePlayer player = player(red);
        player.addScore(10);
        player.addScore(-20);
        player.addScore(0);
        player.addScore(5);
        assertEquals(3, events.size());
        assertEquals(new WiredScoreChange(GameTeamColors.RED, 10, 0), events.get(1).getScoreChange().orElseThrow());
        assertEquals(-10, events.get(1).getScoreAdded(), "delta must reflect the zero floor, not requested -20");
        assertEquals(List.of(true, false, true), events.stream().map(e -> trigger(5, 1).matches(null, e)).toList());
    }

    @Test
    void directDecreaseThenRecrossAndGameResetUseNewTransitions() {
        red.addTeamScore(12);
        red.addTeamScore(-10);
        red.addTeamScore(8);
        red.resetScores();
        red.addTeamScore(10);
        assertEquals(List.of(true, false, true, true), events.stream().map(e -> trigger(10, 1).matches(null, e)).toList());
        assertEquals(new WiredScoreChange(GameTeamColors.RED, 0, 10), events.getLast().getScoreChange().orElseThrow());
    }

    @Test
    void capturedTeamAndTotalDoNotChangeWhenTheActorLeaves() {
        GamePlayer player = player(red);
        player.addScore(10);
        WiredEvent captured = events.getFirst();
        red.removeMember(player);
        player.reset();
        when(player.getHabbo().getHabboInfo().getGamePlayer()).thenReturn(null);
        assertEquals(0, red.getTotalScore());
        assertTrue(trigger(10, 1).matches(null, captured));
        assertFalse(trigger(10, 2).matches(null, captured));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void predefinedTeamEffectActuallyDispatchesWithAndWithoutAnActor(boolean hasActor) throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(eq("hotel.wired.max_delay"), anyInt())).thenReturn(20);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            WiredEffectGiveScoreToTeam effect = new WiredEffectGiveScoreToTeam(1, 1, null, "", 0, 0);
            assertTrue(effect.saveData(new WiredSettings(new int[]{10, 0, 1}, "", new int[0], 0), null));
            game.state = GameState.RUNNING;
            when(room.getGames()).thenReturn(Set.of(game));
            WiredContext context = mock(WiredContext.class);
            RoomUnit actor = hasActor ? mock(RoomUnit.class) : null;
            when(context.room()).thenReturn(room);
            when(context.actor()).thenReturn(Optional.ofNullable(actor));

            effect.execute(context);

            assertEquals(1, events.size());
            assertEquals(Optional.ofNullable(actor), events.getFirst().getActor());
            assertTrue(trigger(10, 1).matches(null, events.getFirst()));
        }
    }

    @Test
    void savedTeamAndThresholdSurviveJsonReloadAndLegacyScoreOnlyData() throws Exception {
        ScoreTrigger saved = trigger(1000, 4);
        String savedJson = saved.getWiredData();
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(savedJson);
        ScoreTrigger reopened = new ScoreTrigger();
        reopened.loadWiredData(row, room);
        assertArrayEquals(new int[]{1000, 4}, reopened.params());
        assertTrue(reopened.matches(null, WiredEvents.scoreAchieved(room, null, GameTeamColors.YELLOW, 999, 1000)));
        assertFalse(reopened.matches(null, WiredEvents.scoreAchieved(room, null, GameTeamColors.RED, 999, 1000)));

        when(row.getString("wired_data")).thenReturn("25");
        ScoreTrigger legacy = new ScoreTrigger();
        legacy.loadWiredData(row, room);
        assertArrayEquals(new int[]{25, 0}, legacy.params());
        assertTrue(legacy.matches(null, WiredEvents.scoreAchieved(room, null, GameTeamColors.RED, 24, 25)));
    }

    @Test
    void rejectsInvalidSavesWithoutPartiallyReplacingAValidConfiguration() {
        ScoreTrigger trigger = trigger(50, 2);
        for (int[] values : new int[][]{{}, {0, 1}, {-1, 1}, {1001, 1}, {10, -1}, {10, 5}, {10, 1, 2}}) {
            assertFalse(trigger.saveData(new WiredSettings(values, "", new int[0], 0)));
            assertArrayEquals(new int[]{50, 2}, trigger.params());
        }
        assertFalse(trigger.saveData(null));
        assertTrue(trigger.saveData(new WiredSettings(new int[]{1}, "", new int[0], 0)));
        assertArrayEquals(new int[]{1, 0}, trigger.params());
    }

    @Test
    void legacyFactoriesRetainKnownTeamAndAllowActorlessAnyTeam() {
        GamePlayer player = player(red);
        when(room.getHabbo(player.getHabbo().getRoomUnit())).thenReturn(player.getHabbo());
        assertTrue(WiredManager.triggerScoreAchieved(room, player.getHabbo().getRoomUnit(), 10, 10));
        assertTrue(trigger(10, 1).matches(null, events.getFirst()));
        assertTrue(WiredManager.triggerScoreAchieved(room, null, 10, 10));
        assertTrue(trigger(10, 0).matches(null, events.getLast()));
        assertFalse(trigger(10, 1).matches(null, events.getLast()));
    }

    @Test
    void wrongEventTypesAndLargeNegativeLegacyDeltasNeverMatch() {
        ScoreTrigger trigger = trigger(10, 0);
        assertFalse(trigger.matches(null, WiredEvent.builder(WiredEvent.Type.USER_PERFORMS_ACTION, room).score(10).scoreAdded(10).build()));
        assertFalse(trigger.matches(null, WiredEvents.scoreAchieved(room, null, Integer.MAX_VALUE, Integer.MIN_VALUE)));
        assertTrue(trigger.matches(null, WiredEvent.builder(WiredEvent.Type.SCORE_ACHIEVED, room).score(10).scoreAdded(10).build()));
        assertFalse(trigger(10, 1).matches(null, WiredEvent.builder(WiredEvent.Type.SCORE_ACHIEVED, room).score(10).scoreAdded(10).build()));
    }

    @Test
    void unconfiguredZeroThresholdDoesNotFireWhenNegativeTeamPointsReturnToZero() {
        ScoreTrigger unconfigured = new ScoreTrigger();
        assertFalse(unconfigured.matches(null, WiredEvents.scoreAchieved(room, null, GameTeamColors.RED, -5, 0)));
        assertFalse(unconfigured.matches(null, WiredEvents.scoreAchieved(room, null, GameTeamColors.RED, 0, 10)));
    }

    @Test
    void detachedOrStalePlayersCannotChangeScoresOrEmitEvents() {
        GamePlayer player = player(red);
        when(player.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(null);
        assertDoesNotThrow(() -> player.addScore(10));
        assertEquals(0, player.getScore());
        assertTrue(events.isEmpty());
        when(player.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
        when(player.getHabbo().getHabboInfo().getGamePlayer()).thenReturn(null);
        player.addScore(10);
        assertTrue(events.isEmpty());
    }

    @Test
    void managerRejectsMissingRoomTeamAndUnchangedTransitions() {
        assertFalse(WiredManager.triggerScoreAchieved(null, null, GameTeamColors.RED, 0, 10));
        assertFalse(WiredManager.triggerScoreAchieved(room, null, null, 0, 10));
        assertFalse(WiredManager.triggerScoreAchieved(room, null, GameTeamColors.RED, 10, 10));
        assertTrue(events.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scoreMutationsReachTheRealEngineAndExecuteOnlyMatchingStacks(boolean hasActor) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
        when(room.isLoaded()).thenReturn(true);
        when(room.getId()).thenReturn(987654);
        int[] executed = new int[4];
        IWiredEffect actorOnly = new IWiredEffect() {
            @Override public void execute(WiredContext context) { executed[3]++; }
            @Override public boolean requiresActor() { return true; }
        };
        List<WiredStack> stacks = List.of(
                new WiredStack(null, trigger(10, 0), List.of(), List.of(ctx -> executed[0]++)),
                new WiredStack(null, trigger(10, 1), List.of(), List.of(ctx -> executed[1]++, actorOnly)),
                new WiredStack(null, trigger(10, 3), List.of(), List.of(ctx -> executed[2]++)));
        WiredEngine engine = new WiredEngine(mock(WiredServices.class),
                (eventRoom, type) -> type == WiredEvent.Type.SCORE_ACHIEVED ? stacks : List.of(), 100);
        manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class)))
                .thenAnswer(call -> engine.handleEvent(call.getArgument(0)));
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            RoomUnit actor = hasActor ? mock(RoomUnit.class) : null;
            red.addTeamScore(10, actor);
            assertArrayEquals(new int[]{1, 1, 0, hasActor ? 1 : 0}, executed);
        } finally {
            WiredManager.getUsageTracker().clear(room);
        }
    }

    private GamePlayer player(GameTeam team) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(info.getCurrentRoom()).thenReturn(room);
        doReturn(TestGame.class).when(info).getCurrentGame();
        GamePlayer player = new GamePlayer(habbo, team.teamColor);
        when(info.getGamePlayer()).thenReturn(player);
        team.addMember(player);
        return player;
    }

    private ScoreTrigger trigger(int threshold, int team) {
        ScoreTrigger trigger = new ScoreTrigger();
        assertTrue(trigger.saveData(new WiredSettings(new int[]{threshold, team}, "", new int[0], 0)));
        return trigger;
    }

    private static class ScoreTrigger extends WiredTriggerScoreAchieved {
        ScoreTrigger() { super(1, 1, null, "", 0, 0); }
        int[] params() { return getWiredIntParams(); }
    }

    private static class TestGame extends Game {
        TestGame(Room room) { super(GameTeam.class, GamePlayer.class, room, false); }
        @Override public void initialise() { }
        @Override public void run() { }
    }
}
