package com.eu.habbo.habbohotel.games.gamehall;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.games.gamehall.CloseGameBoardMessageComposer;
import com.eu.habbo.messages.outgoing.games.gamehall.GameBoardUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.games.gamehall.OpenGameBoardMessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.NotificationDialogMessageComposer;

import gnu.trove.map.hash.THashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

public abstract class GamehallGame {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamehallGame.class);
    private static final String[] NO_ARGS = new String[0];
    private static final String RESULT_WIN = "WIN";
    private static final String RESULT_LOSE = "LOSE";
    private static final String DISPLAY_BUBBLE = "BUBBLE";

    protected final Room room;

    private final int stationId;
    private final GamehallGameType type;
    private final Habbo[] seats;

    private boolean started;
    private boolean disposed;

    protected GamehallGame(Room room, int stationId, GamehallGameType type) {
        this.room = Objects.requireNonNull(room, "room");
        this.stationId = stationId;
        this.type = Objects.requireNonNull(type, "type");
        this.seats = new Habbo[type.getMaxSeats()];
    }

    public int getStationId() {
        return this.stationId;
    }

    public GamehallGameType getType() {
        return this.type;
    }

    public int getSeatCount() {
        return this.seats.length;
    }

    public synchronized int getOccupiedSeatCount() {
        int occupied = 0;
        for (Habbo seat : this.seats) {
            if (seat != null) {
                occupied++;
            }
        }
        return occupied;
    }

    public synchronized int getSeatIndex(Habbo habbo) {
        if (habbo == null) {
            return -1;
        }

        for (int i = 0; i < this.seats.length; i++) {
            if (isSameHabbo(this.seats[i], habbo)) {
                return i;
            }
        }
        return -1;
    }

    public synchronized Habbo getHabboAt(int seatIndex) {
        return isValidSeatIndex(seatIndex) ? this.seats[seatIndex] : null;
    }

    public synchronized boolean seat(Habbo habbo, int seatIndex) {
        if (this.disposed || habbo == null || !isValidSeatIndex(seatIndex)) {
            return false;
        }

        Habbo occupant = this.seats[seatIndex];
        if (occupant != null && !isSameHabbo(occupant, habbo)) {
            return false;
        }

        int currentSeat = getSeatIndex(habbo);
        if (currentSeat >= 0 && currentSeat != seatIndex) {
            this.seats[currentSeat] = null;
        }

        this.seats[seatIndex] = habbo;
        return true;
    }

    public synchronized boolean remove(Habbo habbo) {
        int seatIndex = getSeatIndex(habbo);
        if (seatIndex < 0) {
            return false;
        }

        this.seats[seatIndex] = null;
        return true;
    }

    public synchronized boolean hasMinimumPlayers() {
        return getOccupiedSeatCount() >= this.type.getMinSeats();
    }

    public synchronized boolean isStarted() {
        return this.started;
    }

    public synchronized void start() {
        if (!this.disposed && !this.started && hasMinimumPlayers()) {
            this.started = true;
        }
    }

    public synchronized void stop(String reason) {
        this.started = false;
    }

    public synchronized void dispose() {
        if (this.disposed) {
            return;
        }

        this.started = false;
        this.disposed = true;
        Arrays.fill(this.seats, null);
    }

    public void sendOpen(Habbo habbo) {
        int localSeat = getSeatIndex(habbo);
        if (localSeat < 0 || !canSendTo(habbo)) {
            LOGGER.info("[GAMEHALL] sendOpen skipped station={} type={} user={} localSeat={} canSend={}",
                    this.stationId, this.type,
                    habbo != null && habbo.getHabboInfo() != null ? habbo.getHabboInfo().getUsername() : "null",
                    localSeat, canSendTo(habbo));
            return;
        }

        LOGGER.info("[GAMEHALL] sendOpen station={} type={} user={} localSeat={} seatCount={}",
                this.stationId, this.type, habbo.getHabboInfo().getUsername(), localSeat, this.seats.length);
        habbo.getClient().sendResponse(new OpenGameBoardMessageComposer(
                this.stationId,
                this.type.getFuseToken(),
                localSeat,
                this.seats.length));
    }

    public void sendUpdate(Habbo habbo, String verb, String... args) {
        if (!canSendTo(habbo)) {
            return;
        }

        habbo.getClient().sendResponse(new GameBoardUpdateMessageComposer(
                this.stationId,
                verb,
                normalizeArgs(args)));
    }

    public void broadcastUpdate(String verb, String... args) {
        String[] safeArgs = normalizeArgs(args);
        for (Habbo habbo : seatSnapshot()) {
            sendUpdate(habbo, verb, safeArgs);
        }
    }

    public void sendClose(Habbo habbo, String reason) {
        if (!canSendTo(habbo)) {
            return;
        }

        habbo.getClient().sendResponse(new CloseGameBoardMessageComposer(this.stationId, reason));
    }

    public void broadcastClose(String reason) {
        for (Habbo habbo : seatSnapshot()) {
            sendClose(habbo, reason);
        }
    }

    protected void finishWithWinner(Habbo winner, Habbo loser, String notificationKey) {
        String winnerName = usernameOf(winner);
        String loserName = usernameOf(loser);

        sendUpdate(winner, "GAMEEND", RESULT_WIN, winnerName, loserName);
        sendUpdate(loser, "GAMEEND", RESULT_LOSE, winnerName, loserName);
        if (Emulator.getGameEnvironment() != null && Emulator.getGameEnvironment().getLeaderboardManager() != null) {
            Emulator.getGameEnvironment().getLeaderboardManager().recordWin(
                    this.type,
                    this.room.getId(),
                    this.stationId,
                    winner,
                    loser);
        }
        broadcastUpdate("GAMEOVER");
        broadcastRoomResultNotification(notificationKey, winnerName, loserName);
    }

    public abstract void handleCommand(Habbo habbo, String command, String[] args);

    private void broadcastRoomResultNotification(String notificationKey, String winnerName, String loserName) {
        THashMap<String, String> keys = new THashMap<>();
        keys.put("display", DISPLAY_BUBBLE);
        keys.put("WINNER", winnerName);
        keys.put("LOSER", loserName);

        ServerMessage message = new NotificationDialogMessageComposer(notificationKey, keys).compose();
        for (Habbo habbo : this.room.getHabbos()) {
            if (canSendTo(habbo)) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    private synchronized Habbo[] seatSnapshot() {
        return Arrays.stream(this.seats)
                .filter(Objects::nonNull)
                .toArray(Habbo[]::new);
    }

    private boolean isValidSeatIndex(int seatIndex) {
        return seatIndex >= 0 && seatIndex < this.seats.length;
    }

    private static boolean isSameHabbo(Habbo first, Habbo second) {
        return first == second || first != null && second != null
                && first.getHabboInfo().getId() == second.getHabboInfo().getId();
    }

    private static boolean canSendTo(Habbo habbo) {
        return habbo != null && habbo.getClient() != null;
    }

    private static String usernameOf(Habbo habbo) {
        return habbo != null && habbo.getHabboInfo() != null ? habbo.getHabboInfo().getUsername() : "";
    }

    private static String[] normalizeArgs(String[] args) {
        return args == null ? NO_ARGS : args;
    }
}
