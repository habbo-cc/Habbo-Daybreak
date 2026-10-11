package com.eu.habbo.habbohotel.games.gamehall.games;

import com.eu.habbo.habbohotel.games.gamehall.GamehallGame;
import com.eu.habbo.habbohotel.games.gamehall.GamehallGameType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

import java.util.HashMap;
import java.util.Map;

public class TicTacToeGame extends GamehallGame {
    private static final int BOARD_ROWS = 23;
    private static final int BOARD_COLUMNS = 24;
    private static final int NUM_IN_ROW = 5;
    private static final char EMPTY = '0';
    private static final char SPACE = ' ';
    private static final char X = 'X';
    private static final char O = 'O';
    private static final char X_WIN = '+';
    private static final char O_WIN = 'q';
    private static final String RESULT_NOTIFICATION_KEY = "gamehall.tictactoe.result";

    private final char[][] board = new char[BOARD_ROWS][BOARD_COLUMNS];
    private final Map<Integer, Character> sidesByHabboId = new HashMap<>();
    private int nextTurnHabboId;
    private boolean finished;

    public TicTacToeGame(Room room, int stationId) {
        super(room, stationId, GamehallGameType.TICTACTOE);
        this.restartMap();
    }

    @Override
    public synchronized void handleCommand(Habbo habbo, String command, String[] args) {
        if (habbo == null || command == null) {
            return;
        }

        switch (command) {
            case "CHOOSETYPE":
                this.chooseType(habbo, args);
                return;
            case "SETSECTOR":
                this.setSector(habbo, args);
                return;
            case "RESTART":
                this.restartMap();
                this.finished = false;
                this.broadcastMap();
                return;
            case "CLOSE":
                this.sendClose(habbo, "closed");
                return;
            default:
                return;
        }
    }

    @Override
    public synchronized boolean remove(Habbo habbo) {
        boolean removed = super.remove(habbo);
        if (removed && habbo != null && habbo.getHabboInfo() != null) {
            int habboId = habbo.getHabboInfo().getId();
            this.sidesByHabboId.remove(habboId);
            if (this.nextTurnHabboId == habboId) {
                this.nextTurnHabboId = 0;
            }
            this.restartMap();
            this.finished = false;
        }
        return removed;
    }

    @Override
    public synchronized void stop(String reason) {
        super.stop(reason);
        this.sidesByHabboId.clear();
        this.nextTurnHabboId = 0;
        this.finished = false;
        this.restartMap();
    }

    private void chooseType(Habbo habbo, String[] args) {
        if (args == null || args.length < 1) {
            this.sendUpdate(habbo, "TYPERESERVED");
            return;
        }

        char side = normalizeSide(args[0]);
        if (side == 0 || this.isSideTakenByOther(side, habbo)) {
            this.sendUpdate(habbo, "TYPERESERVED");
            return;
        }

        int habboId = habbo.getHabboInfo().getId();
        this.sidesByHabboId.put(habboId, side);
        this.sendUpdate(habbo, "SELECTTYPE", Character.toString(side));
        if (this.nextTurnHabboId == 0) {
            this.nextTurnHabboId = habboId;
        }

        char otherSide = side == X ? O : X;
        for (int i = 0; i < this.getSeatCount(); i++) {
            Habbo seated = this.getHabboAt(i);
            if (seated == null || seated.getHabboInfo().getId() == habboId) {
                continue;
            }
            int seatedId = seated.getHabboInfo().getId();
            Character current = this.sidesByHabboId.get(seatedId);
            if (current == null) {
                this.sidesByHabboId.put(seatedId, otherSide);
                this.sendUpdate(seated, "SELECTTYPE", Character.toString(otherSide));
            }
        }

        this.broadcastOpponents();
        this.broadcastMap();
    }

    private void setSector(Habbo habbo, String[] args) {
        if (args == null || args.length < 3 || this.getOccupiedSeatCount() < 2 || this.finished) {
            this.sendUpdate(habbo, "TYPERESERVED");
            return;
        }

        int habboId = habbo.getHabboInfo().getId();
        Character side = this.sidesByHabboId.get(habboId);
        char requestedSide = normalizeSide(args[0]);
        if (side == null || requestedSide != side) {
            this.sendUpdate(habbo, "TYPERESERVED");
            return;
        }

        if (this.nextTurnHabboId != 0 && this.nextTurnHabboId != habboId) {
            this.sendUpdate(habbo, "TYPERESERVED");
            return;
        }

        int column = parseInt(args[1], -1);
        int row = parseInt(args[2], -1);
        if (row < 0 || row >= BOARD_ROWS || column < 0 || column >= BOARD_COLUMNS
                || this.board[row][column] != EMPTY) {
            this.sendUpdate(habbo, "TYPERESERVED");
            return;
        }

        this.board[row][column] = side;
        WinResult win = this.findWin(row, column, side);
        if (win != null) {
            char winGlyph = side == X ? X_WIN : O_WIN;
            for (int i = 0; i < win.rows.length; i++) {
                this.board[win.rows[i]][win.columns[i]] = winGlyph;
            }
            this.finished = true;
            this.broadcastMap();
            this.finishWithWinner(habbo, this.getOpponent(habbo), RESULT_NOTIFICATION_KEY);
            return;
        }

        this.swapTurn(habboId);
        this.broadcastMap();
    }

    private void restartMap() {
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int column = 0; column < BOARD_COLUMNS; column++) {
                this.board[row][column] = EMPTY;
            }
        }
    }

    private void broadcastOpponents() {
        String[] players = new String[this.getSeatCount()];
        for (int i = 0; i < this.getSeatCount(); i++) {
            Habbo seated = this.getHabboAt(i);
            if (seated == null) {
                players[i] = "";
                continue;
            }
            Character side = this.sidesByHabboId.get(seated.getHabboInfo().getId());
            players[i] = (side == null ? "" : Character.toString(side) + " ") + seated.getHabboInfo().getUsername();
        }
        this.broadcastUpdate("OPPONENTS", players);
    }

    private void broadcastMap() {
        this.broadcastUpdate("BOARDDATA", this.currentTurnLabel(), "", this.serializeBoard());
    }

    private String currentTurnLabel() {
        Habbo next = this.getHabboById(this.nextTurnHabboId);
        if (next == null) {
            return "";
        }
        Character side = this.sidesByHabboId.get(next.getHabboInfo().getId());
        return (side == null ? "" : Character.toString(side) + " ") + next.getHabboInfo().getUsername();
    }

    private String serializeBoard() {
        StringBuilder builder = new StringBuilder(BOARD_ROWS * (BOARD_COLUMNS + 1));
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int column = 0; column < BOARD_COLUMNS; column++) {
                char value = this.board[row][column];
                builder.append(value == EMPTY ? SPACE : value);
            }
            builder.append(SPACE);
        }
        return builder.toString();
    }

    private void swapTurn(int currentHabboId) {
        for (int i = 0; i < this.getSeatCount(); i++) {
            Habbo seated = this.getHabboAt(i);
            if (seated != null && seated.getHabboInfo().getId() != currentHabboId
                    && this.sidesByHabboId.containsKey(seated.getHabboInfo().getId())) {
                this.nextTurnHabboId = seated.getHabboInfo().getId();
                return;
            }
        }
        this.nextTurnHabboId = currentHabboId;
    }

    private boolean isSideTakenByOther(char side, Habbo habbo) {
        int habboId = habbo.getHabboInfo().getId();
        for (Map.Entry<Integer, Character> entry : this.sidesByHabboId.entrySet()) {
            if (entry.getKey() != habboId && entry.getValue() == side) {
                return true;
            }
        }
        return false;
    }

    private Habbo getHabboById(int habboId) {
        if (habboId == 0) {
            return null;
        }
        for (int i = 0; i < this.getSeatCount(); i++) {
            Habbo seated = this.getHabboAt(i);
            if (seated != null && seated.getHabboInfo().getId() == habboId) {
                return seated;
            }
        }
        return null;
    }

    private Habbo getOpponent(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }

        int habboId = habbo.getHabboInfo().getId();
        for (int i = 0; i < this.getSeatCount(); i++) {
            Habbo seated = this.getHabboAt(i);
            if (seated != null && seated.getHabboInfo() != null && seated.getHabboInfo().getId() != habboId) {
                return seated;
            }
        }
        return null;
    }

    private WinResult findWin(int row, int column, char side) {
        int[][] directions = new int[][] {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] direction : directions) {
            WinResult result = this.collectWin(row, column, side, direction[0], direction[1]);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private WinResult collectWin(int row, int column, char side, int rowDelta, int columnDelta) {
        int startRow = row;
        int startColumn = column;
        while (this.isToken(startRow - rowDelta, startColumn - columnDelta, side)) {
            startRow -= rowDelta;
            startColumn -= columnDelta;
        }

        int[] rows = new int[NUM_IN_ROW];
        int[] columns = new int[NUM_IN_ROW];
        int count = 0;
        int currentRow = startRow;
        int currentColumn = startColumn;
        while (this.isToken(currentRow, currentColumn, side)) {
            if (count < NUM_IN_ROW) {
                rows[count] = currentRow;
                columns[count] = currentColumn;
            }
            count++;
            if (count >= NUM_IN_ROW) {
                return new WinResult(rows, columns);
            }
            currentRow += rowDelta;
            currentColumn += columnDelta;
        }
        return null;
    }

    private boolean isToken(int row, int column, char side) {
        return row >= 0 && row < BOARD_ROWS && column >= 0 && column < BOARD_COLUMNS
                && this.board[row][column] == side;
    }

    private static char normalizeSide(String value) {
        if (value == null || value.length() == 0) {
            return 0;
        }
        char side = Character.toUpperCase(value.charAt(0));
        return side == X || side == O ? side : 0;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class WinResult {
        private final int[] rows;
        private final int[] columns;

        private WinResult(int[] rows, int[] columns) {
            this.rows = rows;
            this.columns = columns;
        }
    }
}
