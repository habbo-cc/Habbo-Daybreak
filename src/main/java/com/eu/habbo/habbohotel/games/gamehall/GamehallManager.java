package com.eu.habbo.habbohotel.games.gamehall;

import com.eu.habbo.habbohotel.games.gamehall.games.BattleshipsGame;
import com.eu.habbo.habbohotel.games.gamehall.games.ChessGame;
import com.eu.habbo.habbohotel.games.gamehall.games.PokerGame;
import com.eu.habbo.habbohotel.games.gamehall.games.TicTacToeGame;
import com.eu.habbo.habbohotel.items.interactions.InteractionGamehallSeat;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-room registry for the Cunning Fox gamehall tables.
 *
 * Stations are keyed independently from Arcturus' team-game framework because a public gamehall
 * room contains several simultaneous boards of the same game type. The legacy coordinates are
 * kept here as the Phase 3 bootstrap; Phase 6 replaces this source with database-backed station
 * configuration without changing the seat/game lifecycle API.
 */
public class GamehallManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamehallManager.class);
    private static final Map<String, Map<String, SeatAssignment>> LEGACY_SEATS = createLegacySeats();

    private final Room room;
    private final Map<Integer, GamehallGame> games = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> stationByHabboId = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastMoveAtByHabboId = new ConcurrentHashMap<>();
    private volatile boolean disposed;
    private volatile boolean seatingSuspended;

    public GamehallManager(Room room) {
        this.room = room;
    }

    public static boolean isGamehallModel(String roomModel) {
        return roomModel != null && LEGACY_SEATS.containsKey(roomModel);
    }

    public SeatAssignment resolveSeat(String roomModel, String sprite, int x, int y) {
        if (roomModel == null || sprite == null) {
            return null;
        }

        Map<String, SeatAssignment> seats = LEGACY_SEATS.get(roomModel);
        if (seats == null) {
            return null;
        }

        SeatAssignment assignment = seats.get(coordinateKey(x, y));
        return assignment != null && assignment.acceptsSprite(sprite) ? assignment.forRoom(this.room.getId()) : null;
    }

    public SeatAssignment resolveSeat(String roomModel, int x, int y) {
        if (roomModel == null) {
            return null;
        }

        Map<String, SeatAssignment> seats = LEGACY_SEATS.get(roomModel);
        if (seats == null) {
            return null;
        }

        SeatAssignment assignment = seats.get(coordinateKey(x, y));
        return assignment != null ? assignment.forRoom(this.room.getId()) : null;
    }

    public synchronized void onSeat(Habbo habbo, InteractionGamehallSeat seat) {
        if (this.disposed || this.seatingSuspended || habbo == null || seat == null) {
            return;
        }

        this.onSeat(habbo, seat.getAssignment());
    }

    public synchronized void onSeat(Habbo habbo, SeatAssignment assignment) {
        if (this.disposed || this.seatingSuspended || habbo == null || assignment == null) {
            LOGGER.info("[GAMEHALL] onSeat rejected disposed={} suspended={} habboNull={} assignmentNull={}",
                    this.disposed, this.seatingSuspended, habbo == null, assignment == null);
            return;
        }

        // Disabled games (emulator_settings gamehall.<game>.enabled = 0) never open a board, so an
        // uncoded game's chairs behave as ordinary seats and the coded games keep working alongside.
        if (!assignment.getType().isEnabled()) {
            return;
        }

        LOGGER.info("[GAMEHALL] onSeat user={} room={} station={} type={} seat={}",
                habbo.getHabboInfo().getUsername(), this.room.getId(), assignment.getStationId(),
                assignment.getType(), assignment.getSeatIndex());

        Integer previousStation = this.stationByHabboId.get(habbo.getHabboInfo().getId());
        if (previousStation != null && previousStation != assignment.getStationId()) {
            GamehallGame previousGame = this.games.get(previousStation);
            if (previousGame != null) {
                this.leaveGame(habbo, previousGame, "changed_station");
            }
        }

        GamehallGame game = this.games.computeIfAbsent(
                assignment.getStationId(),
                ignored -> this.createGame(assignment.getType(), assignment.getStationId()));

        if (game.getSeatIndex(habbo) == assignment.getSeatIndex()) {
            if (!game.isStarted() && game.hasMinimumPlayers()) {
                game.start();
                for (int i = 0; i < game.getSeatCount(); i++) {
                    Habbo seated = game.getHabboAt(i);
                    if (seated != null) {
                        game.sendOpen(seated);
                    }
                }
            } else if (game.isStarted()) {
                game.sendOpen(habbo);
            }
            return;
        }

        if (!game.seat(habbo, assignment.getSeatIndex())) {
            LOGGER.info("[GAMEHALL] seat failed user={} station={} requestedSeat={} occupiedSeatCount={}",
                    habbo.getHabboInfo().getUsername(), assignment.getStationId(),
                    assignment.getSeatIndex(), game.getOccupiedSeatCount());
            return;
        }

        this.stationByHabboId.put(habbo.getHabboInfo().getId(), assignment.getStationId());

        if (!game.isStarted() && game.hasMinimumPlayers()) {
            game.start();
            for (int i = 0; i < game.getSeatCount(); i++) {
                Habbo seated = game.getHabboAt(i);
                if (seated != null) {
                    game.sendOpen(seated);
                }
            }
        } else if (game.isStarted()) {
            for (int i = 0; i < game.getSeatCount(); i++) {
                Habbo seated = game.getHabboAt(i);
                if (seated != null) {
                    game.sendOpen(seated);
                }
            }
        }
    }

    public synchronized void onLeave(Habbo habbo, InteractionGamehallSeat seat) {
        if (this.disposed || habbo == null || seat == null) {
            return;
        }

        GamehallGame game = this.games.get(seat.getAssignment().getStationId());
        if (game != null) {
            this.leaveGame(habbo, game, "left_station");
        }
    }

    public synchronized void onRoomLeave(Habbo habbo, String reason) {
        if (this.disposed || habbo == null) {
            return;
        }

        int habboId = habbo.getHabboInfo().getId();
        Integer stationId = this.stationByHabboId.get(habboId);
        if (stationId == null) {
            this.lastMoveAtByHabboId.remove(habboId);
            return;
        }

        GamehallGame game = this.games.get(stationId);
        if (game != null) {
            this.leaveGame(habbo, game, reason);
        } else {
            this.stationByHabboId.remove(habboId);
            this.lastMoveAtByHabboId.remove(habboId);
        }
    }

    public synchronized void handleMove(Habbo habbo, int stationId, String command, String[] args) {
        if (this.disposed || habbo == null || command == null) {
            return;
        }

        int habboId = habbo.getHabboInfo().getId();
        long now = System.nanoTime();
        Long lastMoveAt = this.lastMoveAtByHabboId.get(habboId);
        if (lastMoveAt != null && now - lastMoveAt < 75_000_000L) {
            return;
        }

        Integer currentStation = this.stationByHabboId.get(habboId);
        GamehallGame game = this.games.get(stationId);
        if (currentStation == null || currentStation != stationId || game == null
                || game.getSeatIndex(habbo) < 0 || !game.isStarted()) {
            return;
        }

        this.lastMoveAtByHabboId.put(habboId, now);
        game.handleCommand(habbo, command.toUpperCase(Locale.ROOT), args == null ? new String[0] : args);
    }

    public GamehallGame getGame(int stationId) {
        return this.games.get(stationId);
    }

    public synchronized void resetStations(String reason) {
        for (GamehallGame game : this.games.values()) {
            game.broadcastClose(reason);
            game.dispose();
        }
        this.games.clear();
        this.stationByHabboId.clear();
        this.lastMoveAtByHabboId.clear();
    }

    public synchronized void beginStationRefresh(String reason) {
        this.seatingSuspended = true;
        this.resetStations(reason);
    }

    public synchronized void endStationRefresh() {
        if (!this.disposed) {
            this.seatingSuspended = false;
        }
    }

    public synchronized void dispose() {
        if (this.disposed) {
            return;
        }
        this.disposed = true;
        for (GamehallGame game : this.games.values()) {
            game.dispose();
        }
        this.games.clear();
        this.stationByHabboId.clear();
        this.lastMoveAtByHabboId.clear();
    }

    private void leaveGame(Habbo habbo, GamehallGame game, String reason) {
        int habboId = habbo.getHabboInfo().getId();
        if (!game.remove(habbo)) {
            return;
        }

        this.stationByHabboId.remove(habboId, game.getStationId());
        this.lastMoveAtByHabboId.remove(habboId);
        game.sendClose(habbo, reason);

        if (!game.hasMinimumPlayers()) {
            game.broadcastClose("not_enough_players");
            game.stop("not_enough_players");
            if (game.getOccupiedSeatCount() == 0 && this.games.remove(game.getStationId(), game)) {
                game.dispose();
            }
        }
    }

    private GamehallGame createGame(GamehallGameType type, int stationId) {
        switch (type) {
            case TICTACTOE:
                return new TicTacToeGame(this.room, stationId);
            case CHESS:
                return new ChessGame(this.room, stationId);
            case BATTLESHIPS:
                return new BattleshipsGame(this.room, stationId);
            case POKER:
                return new PokerGame(this.room, stationId);
            default:
                throw new IllegalArgumentException("Unsupported gamehall type " + type);
        }
    }

    private static Map<String, Map<String, SeatAssignment>> createLegacySeats() {
        Map<String, Map<String, SeatAssignment>> models = new HashMap<>();

        addStations(models, "hallA", GamehallGameType.TICTACTOE, "gamehall_chair_wood",
                new int[][][] {
                        {{15, 4}, {15, 5}}, {{15, 9}, {15, 10}}, {{15, 14}, {15, 15}},
                        {{10, 4}, {10, 5}}, {{10, 9}, {10, 10}}, {{10, 14}, {10, 15}},
                        {{5, 4}, {5, 5}}, {{5, 9}, {5, 10}}, {{5, 14}, {5, 15}}
                });
        addStations(models, "hallB", GamehallGameType.BATTLESHIPS, "gamehall_chair_green",
                new int[][][] {
                        {{15, 3}, {13, 3}}, {{8, 3}, {6, 3}}, {{2, 4}, {2, 6}},
                        {{2, 10}, {2, 12}}, {{2, 16}, {2, 18}}
                });
        addStations(models, "hallC", GamehallGameType.CHESS,
                "gamehall_chair_green,chess_king_chair",
                new int[][][] {
                        {{2, 7}, {2, 9}}, {{6, 14}, {4, 14}}, {{12, 14}, {12, 12}},
                        {{13, 7}, {13, 5}}, {{7, 3}, {9, 3}}
                });
        addStations(models, "hallD", GamehallGameType.POKER, "gamehall_chair_green",
                new int[][][] {
                        {{2, 14}, {2, 16}, {3, 15}, {1, 15}},
                        {{8, 2}, {8, 4}, {9, 3}, {7, 3}},
                        {{14, 2}, {14, 4}, {15, 3}, {13, 3}},
                        {{2, 8}, {2, 10}, {3, 9}, {1, 9}},
                        {{8, 8}, {8, 10}, {9, 9}, {7, 9}},
                        {{14, 8}, {14, 10}, {15, 9}, {13, 9}},
                        {{8, 14}, {8, 16}, {9, 15}, {7, 15}},
                        {{14, 14}, {14, 16}, {15, 15}, {13, 15}}
                });

        Map<String, Map<String, SeatAssignment>> immutable = new HashMap<>();
        for (Map.Entry<String, Map<String, SeatAssignment>> entry : models.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static void addStations(Map<String, Map<String, SeatAssignment>> models,
                                    String model,
                                    GamehallGameType type,
                                    String sprites,
                                    int[][][] stations) {
        Map<String, SeatAssignment> seats = models.computeIfAbsent(model, ignored -> new HashMap<>());
        for (int stationIndex = 0; stationIndex < stations.length; stationIndex++) {
            for (int seatIndex = 0; seatIndex < stations[stationIndex].length; seatIndex++) {
                int[] coordinate = stations[stationIndex][seatIndex];
                seats.put(coordinateKey(coordinate[0], coordinate[1]),
                        new SeatAssignment(type, stationIndex + 1, seatIndex, sprites));
            }
        }
    }

    private static String coordinateKey(int x, int y) {
        return x + ":" + y;
    }

    public static final class SeatAssignment {
        private final GamehallGameType type;
        private final int stationIndex;
        private final int stationId;
        private final int seatIndex;
        private final String sprites;

        private SeatAssignment(GamehallGameType type, int stationIndex, int seatIndex, String sprites) {
            this(type, stationIndex, 0, seatIndex, sprites);
        }

        private SeatAssignment(GamehallGameType type, int stationIndex, int stationId,
                               int seatIndex, String sprites) {
            this.type = type;
            this.stationIndex = stationIndex;
            this.stationId = stationId;
            this.seatIndex = seatIndex;
            this.sprites = sprites;
        }

        private SeatAssignment forRoom(int roomId) {
            return new SeatAssignment(this.type, this.stationIndex,
                    roomId * 100 + this.stationIndex, this.seatIndex, this.sprites);
        }

        private boolean acceptsSprite(String sprite) {
            for (String accepted : this.sprites.split(",")) {
                if (accepted.equalsIgnoreCase(sprite)) {
                    return true;
                }
            }
            return false;
        }

        public GamehallGameType getType() {
            return this.type;
        }

        public int getStationIndex() {
            return this.stationIndex;
        }

        public int getStationId() {
            return this.stationId;
        }

        public int getSeatIndex() {
            return this.seatIndex;
        }
    }
}
