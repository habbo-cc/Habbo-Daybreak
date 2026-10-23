package com.eu.habbo.habbohotel.games;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerScoreAchieved;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Uses only pre-existing public APIs so the same regressions run on the clean baseline. */
class WiredTeamScoreRegressionTest {
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
            events.add(call.getArgument(0));
            return true;
        });
    }

    @AfterEach
    void close() {
        manager.close();
    }

    @Test
    void aSavedBlueTriggerMustNotMatchRedPoints() {
        GamePlayer player = player();
        when(room.getHabbo(player.getHabbo().getRoomUnit())).thenReturn(player.getHabbo());
        WiredEvent redScore = WiredEvents.scoreAchieved(room, player.getHabbo().getRoomUnit(), 10, 10);
        assertTrue(trigger(1).matches(null, redScore));
        assertFalse(trigger(3).matches(null, redScore));
    }

    @Test
    void twoPlayersContributingSixAndFourPointsReachTen() {
        GamePlayer first = player();
        GamePlayer second = player();
        first.addScore(6);
        second.addScore(4);
        assertEquals(10, red.getTotalScore());
        assertEquals(10, events.get(1).getScore());
        assertTrue(trigger(1).matches(null, events.get(1)));
    }

    @Test
    void aDirectTeamAwardDispatchesAScoreTriggerWithoutAnActor() {
        red.addTeamScore(10);
        assertEquals(1, events.size());
        assertTrue(events.get(0).getActor().isEmpty());
        assertTrue(trigger(1).matches(null, events.get(0)));
    }

    @Test
    void theLegacyDispatchApiDoesNotRequireAnActorForAnyTeam() {
        assertTrue(WiredManager.triggerScoreAchieved(room, null, 10, 10));
        assertEquals(1, events.size());
        assertTrue(trigger(0).matches(null, events.get(0)));
    }

    private WiredTriggerScoreAchieved trigger(int team) {
        WiredTriggerScoreAchieved trigger = new WiredTriggerScoreAchieved(1, 1, null, "", 0, 0);
        assertTrue(trigger.saveData(new WiredSettings(new int[]{10, team}, "", new int[0], 0)));
        return trigger;
    }

    private GamePlayer player() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(info.getCurrentRoom()).thenReturn(room);
        doReturn(TestGame.class).when(info).getCurrentGame();
        GamePlayer player = new GamePlayer(habbo, GameTeamColors.RED);
        when(info.getGamePlayer()).thenReturn(player);
        red.addMember(player);
        return player;
    }

    private static class TestGame extends Game {
        TestGame(Room room) { super(GameTeam.class, GamePlayer.class, room, false); }
        @Override public void initialise() { }
        @Override public void run() { }
    }
}
