package com.eu.habbo.habbohotel.games.battleball;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.tickets.HotelTicketService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallPlayerRemovedComposer;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BattleBallManagerTest {
    @Test
    void roomLeaveDeletesAnOwnersLobbyAndClearsEveryMembership() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean("battleball.enabled", false)).thenReturn(false);
        GameEnvironment environment = mock(GameEnvironment.class);
        HabboManager habboManager = mock(HabboManager.class);
        HotelTicketService ticketService = mock(HotelTicketService.class);
        when(environment.getHabboManager()).thenReturn(habboManager);
        when(environment.getHotelTicketService()).thenReturn(ticketService);

        Habbo owner = habbo(1, "owner", 20);
        Habbo opponent = habbo(2, "opponent", 20);
        when(habboManager.getHabbo(1)).thenReturn(owner);
        when(habboManager.getHabbo(2)).thenReturn(opponent);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            BattleBallManager manager = new BattleBallManager();
            set(manager, "enabled", true);

            BattleBallGame game = game(91);
            assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
            assertTrue(game.addPlayer(2, "opponent", "", "", "F", 1));
            games(manager).put(game.id(), game);
            memberships(manager).put(1, game.id());
            memberships(manager).put(2, game.id());

            manager.roomLeave(1);

            assertFalse(games(manager).containsKey(game.id()));
            assertFalse(memberships(manager).containsKey(1));
            assertFalse(memberships(manager).containsKey(2));
            assertEquals(BattleBallState.DISPOSED, game.state());

            manager.dispose();
        }
    }

    @Test
    void promotionStateGuardsEvictionAndSharedMutationBackoffWorkTogether() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean("battleball.enabled", false)).thenReturn(false);
        GameEnvironment environment = mock(GameEnvironment.class);
        HabboManager habboManager = mock(HabboManager.class);
        HotelTicketService ticketService = mock(HotelTicketService.class);
        when(environment.getHabboManager()).thenReturn(habboManager);
        when(environment.getHotelTicketService()).thenReturn(ticketService);
        when(ticketService.balance(any(Habbo.class))).thenReturn(20);

        Habbo owner = habbo(1, "owner", 20);
        Habbo opponent = habbo(2, "opponent", 20);
        Habbo watcher = habbo(3, "watcher", 20);
        Habbo newcomer = habbo(4, "newcomer", 20);
        when(habboManager.getHabbo(anyInt())).thenAnswer(invocation -> switch ((int) invocation.getArgument(0)) {
            case 1 -> owner;
            case 2 -> opponent;
            case 3 -> watcher;
            case 4 -> newcomer;
            default -> null;
        });

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            BattleBallManager manager = new BattleBallManager();
            set(manager, "enabled", true);

            BattleBallGame game = game(90);
            assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
            assertTrue(game.addPlayer(2, "opponent", "", "", "F", 1));
            game.addWatcher(3);
            games(manager).put(game.id(), game);
            memberships(manager).put(1, game.id());
            memberships(manager).put(2, game.id());
            memberships(manager).put(3, game.id());

            BattleBallManager.Result promoted = manager.join(watcher, game.id(), 0);
            assertTrue(promoted.success());
            assertFalse(game.watchers().contains(3));
            assertTrue(game.players().stream().anyMatch(player -> player.userId() == 3));
            assertEquals(BattleBallManager.REQUEST_THROTTLED, manager.join(watcher, game.id(), 1).failure());
            assertEquals(BattleBallManager.FAILURE_BAD_STATE, manager.watch(newcomer, game.id()));

            GameClient watcherClient = watcher.getClient();
            doThrow(new IllegalStateException("disconnected")).when(watcherClient).sendResponse(any(MessageComposer.class));
            assertDoesNotThrow(() -> assertEquals(-1, manager.kick(owner, game.id(), 3)));
            verify(watcherClient).sendResponse(any(BattleBallPlayerRemovedComposer.class));
            assertFalse(game.players().stream().anyMatch(player -> player.userId() == 3));
            assertEquals(BattleBallManager.REQUEST_THROTTLED, manager.addBot(owner, game.id(), 1));

            assertTrue(game.start(1, 1_000, true));
            assertEquals(BattleBallManager.FAILURE_BAD_STATE, manager.join(newcomer, game.id(), 0).failure());

            games(manager).clear();
            manager.dispose();
        }
    }

    private static Habbo habbo(int id, String name, int tickets) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(client);
        when(info.getId()).thenReturn(id);
        when(info.getUsername()).thenReturn(name);
        when(info.getMotto()).thenReturn("");
        when(info.getLook()).thenReturn("hd-180-1");
        when(info.getGender()).thenReturn(HabboGender.M);
        when(info.getCurrencyAmount(anyInt())).thenReturn(tickets);
        return habbo;
    }

    private static BattleBallGame game(int id) {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        return new BattleBallGame(id, "manager", 1, "owner", map, 60, 2, 2, false, Set.of(),
                Map.of(0, List.of(new BattleBallPosition(0, 0), new BattleBallPosition(0, 1)),
                        1, List.of(new BattleBallPosition(2, 2), new BattleBallPosition(2, 1))), 60, 15);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, BattleBallGame> games(BattleBallManager manager) throws Exception {
        return (Map<Integer, BattleBallGame>) field("games").get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> memberships(BattleBallManager manager) throws Exception {
        return (Map<Integer, Integer>) field("userGames").get(manager);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = field(name);
        field.set(target, value);
    }

    private static Field field(String name) throws Exception {
        Field field = BattleBallManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
