package com.eu.habbo.habbohotel.games.gamehall.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.gamehall.GamehallGame;
import com.eu.habbo.habbohotel.games.gamehall.GamehallGameType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BattleshipsGame extends GamehallGame {
    private static final int BOARD_WIDTH = 13;
    private static final int BOARD_HEIGHT = 12;
    private static final int SHIPS_PER_PLAYER = 10;
    private static final int PLAYER_COUNT = 2;
    private static final String RESULT_NOTIFICATION_KEY = "gamehall.battleships.result";

    private final List<Ship>[] ships;
    private final List<Move>[] moves;

    private boolean battleStarted;
    private boolean turnUsed;
    private boolean gameOver;
    private int nextTurn;

    @SuppressWarnings("unchecked")
    public BattleshipsGame(Room room, int stationId) {
        super(room, stationId, GamehallGameType.BATTLESHIPS);
        this.ships = new List[PLAYER_COUNT];
        this.moves = new List[PLAYER_COUNT];
        for (int i = 0; i < PLAYER_COUNT; i++) {
            this.ships[i] = new ArrayList<>();
            this.moves[i] = new ArrayList<>();
        }
        this.resetBattleState(false);
    }

    @Override
    public synchronized void handleCommand(Habbo habbo, String command, String[] args) {
        int player = getSeatIndex(habbo);
        if (!isPlayer(player) || command == null) {
            return;
        }

        String normalized = command.toUpperCase();
        if ("CLOSE".equals(normalized)) {
            sendClose(habbo, "closed");
            return;
        }

        if ("OPEN".equals(normalized) || "AUTOPLACE".equals(normalized)) {
            placeDefaultFleet(habbo, player);
            return;
        }

        if ("RESTART".equals(normalized) || "STARTOVER".equals(normalized)) {
            restartBattle();
            return;
        }

        if ("PLACESHIP".equals(normalized)) {
            placeShip(habbo, player, args);
            return;
        }

        if ("SHOOT".equals(normalized)) {
            shoot(habbo, player, args);
        }
    }

    @Override
    public synchronized void sendOpen(Habbo habbo) {
        super.sendOpen(habbo);

        int player = getSeatIndex(habbo);
        if (!isPlayer(player)) {
            return;
        }

        sendFleet(habbo, player);
        if (this.battleStarted) {
            sendUpdate(habbo, "SITUATION", "", generateHitGrid(player), "", generateHitGrid(oppositePlayer(player)));
            sendUpdate(habbo, "TURN", String.valueOf(this.nextTurn));
        } else if (this.ships[player].size() == SHIPS_PER_PLAYER) {
            sendUpdate(habbo, "WAIT", "Waiting for opponent");
        }
    }

    @Override
    public synchronized boolean remove(Habbo habbo) {
        int player = getSeatIndex(habbo);
        boolean removed = super.remove(habbo);
        if (removed && isPlayer(player)) {
            this.resetBattleState(false);
            if (getOccupiedSeatCount() > 0) {
                broadcastClose("opponent_left");
            }
        }
        return removed;
    }

    @Override
    public synchronized void stop(String reason) {
        super.stop(reason);
        this.resetBattleState(true);
    }

    private void placeShip(Habbo habbo, int player, String[] args) {
        if (this.battleStarted || this.gameOver || args == null || args.length < 5) {
            return;
        }

        ShipType type = ShipType.byId(parseInt(args[0], -1));
        int startX = parseInt(args[1], -1);
        int startY = parseInt(args[2], -1);
        int endX = parseInt(args[3], -1);
        int endY = parseInt(args[4], -1);

        if (type == null || countShips(player, type) >= type.maxAllowed) {
            return;
        }

        Ship ship = Ship.create(type, player, startX, startY, endX, endY);
        if (ship == null || overlapsExistingShip(player, ship)) {
            return;
        }

        this.ships[player].add(ship);
        sendFleet(habbo, player);
        if (hasEveryonePlacedShips()) {
            startBattle();
        } else {
            sendUpdate(habbo, "WAIT", "Waiting for opponent");
        }
    }

    private void placeDefaultFleet(Habbo habbo, int player) {
        if (this.battleStarted || this.gameOver) {
            return;
        }

        if (!this.ships[player].isEmpty()) {
            sendUpdate(habbo, "WAIT", "Fleet already placed");
            return;
        }

        addDefaultShip(player, ShipType.AIRCRAFT_CARRIER, 0, 0, 4, 0);
        addDefaultShip(player, ShipType.BATTLESHIP, 0, 2, 3, 2);
        addDefaultShip(player, ShipType.BATTLESHIP, 0, 4, 3, 4);
        addDefaultShip(player, ShipType.CRUISER, 0, 6, 2, 6);
        addDefaultShip(player, ShipType.CRUISER, 0, 8, 2, 8);
        addDefaultShip(player, ShipType.CRUISER, 0, 10, 2, 10);
        addDefaultShip(player, ShipType.DESTROYER, 7, 0, 8, 0);
        addDefaultShip(player, ShipType.DESTROYER, 7, 2, 8, 2);
        addDefaultShip(player, ShipType.DESTROYER, 7, 4, 8, 4);
        addDefaultShip(player, ShipType.DESTROYER, 7, 6, 8, 6);

        sendFleet(habbo, player);
        if (hasEveryonePlacedShips()) {
            startBattle();
        } else {
            sendUpdate(habbo, "WAIT", "Waiting for opponent");
        }
    }

    private void addDefaultShip(int player, ShipType type, int startX, int startY, int endX, int endY) {
        Ship ship = Ship.create(type, player, startX, startY, endX, endY);
        if (ship != null && !overlapsExistingShip(player, ship) && countShips(player, type) < type.maxAllowed) {
            this.ships[player].add(ship);
        }
    }

    private void startBattle() {
        this.battleStarted = true;
        this.gameOver = false;
        this.turnUsed = false;
        this.nextTurn = 0;
        broadcastOpponents();
        sendFleets();
        sendMarkedMap();
        broadcastUpdate("TURN", String.valueOf(this.nextTurn));
    }

    private void restartBattle() {
        if (!this.gameOver) {
            return;
        }

        resetBattleState(false);
        for (int player = 0; player < PLAYER_COUNT; player++) {
            Habbo seated = getHabboAt(player);
            if (seated == null) {
                continue;
            }

            sendUpdate(seated, "RESTART");
            sendFleet(seated, player);
        }
    }

    private void shoot(Habbo habbo, int player, String[] args) {
        if (!this.battleStarted || this.gameOver || this.nextTurn != player || this.turnUsed || args == null || args.length < 2) {
            return;
        }

        int x = parseInt(args[0], -1);
        int y = parseInt(args[1], -1);
        if (!isInsideBoard(x, y) || hasAlreadyShot(player, x, y)) {
            return;
        }

        int opponent = oppositePlayer(player);
        if (getHabboAt(opponent) == null) {
            return;
        }

        Ship hitShip = getShipAt(opponent, x, y);
        Move move = new Move(x, y, hitShip == null ? MoveResult.MISS : MoveResult.HIT, hitShip);
        this.moves[player].add(move);

        sendMarkedMap();

        if (hitShip == null) {
            broadcastUpdate("MISS");
            this.turnUsed = true;
            Emulator.getThreading().getService().schedule(this::rotateTurnIfActive, 2, TimeUnit.SECONDS);
            return;
        }

        if (hitShip.isDestroyed(this.moves[player])) {
            broadcastUpdate("SINK");
        } else if (hitShip.hitCount(this.moves[player]) >= 2) {
            broadcastUpdate("HITTWICE");
        } else {
            broadcastUpdate("HIT");
        }

        if (isGameOver(opponent)) {
            this.gameOver = true;
            finishWithWinner(habbo, getHabboAt(opponent), RESULT_NOTIFICATION_KEY);
            return;
        }

        broadcastUpdate("TURN", String.valueOf(this.nextTurn));
    }

    private synchronized void rotateTurnIfActive() {
        if (!this.battleStarted || this.gameOver) {
            return;
        }

        this.nextTurn = oppositePlayer(this.nextTurn);
        this.turnUsed = false;
        broadcastUpdate("TURN", String.valueOf(this.nextTurn));
    }

    private void sendMarkedMap() {
        for (int player = 0; player < PLAYER_COUNT; player++) {
            Habbo habbo = getHabboAt(player);
            if (habbo == null) {
                continue;
            }

            int opponent = oppositePlayer(player);
            sendUpdate(habbo, "SITUATION", "", generateHitGrid(player), "", generateHitGrid(opponent));
        }
    }

    private void sendFleets() {
        for (int player = 0; player < PLAYER_COUNT; player++) {
            Habbo habbo = getHabboAt(player);
            if (habbo != null) {
                sendFleet(habbo, player);
            }
        }
    }

    private void sendFleet(Habbo habbo, int player) {
        if (!isPlayer(player)) {
            return;
        }

        sendUpdate(habbo, "FLEET", serializeFleet(player));
    }

    private String serializeFleet(int player) {
        StringBuilder fleet = new StringBuilder();
        for (Ship ship : this.ships[player]) {
            if (fleet.length() > 0) {
                fleet.append(';');
            }
            fleet.append(ship.type.length)
                    .append(',')
                    .append(ship.startX())
                    .append(',')
                    .append(ship.startY())
                    .append(',')
                    .append(ship.isHorizontal() ? 'H' : 'V');
        }
        return fleet.toString();
    }

    private String generateHitGrid(int player) {
        StringBuilder grid = new StringBuilder(BOARD_WIDTH * BOARD_HEIGHT);
        int shooter = oppositePlayer(player);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                Move move = getMoveAt(shooter, x, y);
                if (move == null) {
                    grid.append('-');
                } else if (move.ship != null && move.ship.isDestroyed(this.moves[shooter])) {
                    grid.append('S');
                } else {
                    grid.append(move.result.symbol);
                }
            }
        }
        return grid.toString();
    }

    private void broadcastOpponents() {
        Habbo player0 = getHabboAt(0);
        Habbo player1 = getHabboAt(1);
        if (player0 == null || player1 == null) {
            return;
        }

        broadcastUpdate("OPPONENTS",
                "0 " + player0.getHabboInfo().getUsername() + "\r" +
                        "1 " + player1.getHabboInfo().getUsername());
    }

    private boolean hasEveryonePlacedShips() {
        return this.ships[0].size() == SHIPS_PER_PLAYER && this.ships[1].size() == SHIPS_PER_PLAYER
                && getHabboAt(0) != null && getHabboAt(1) != null;
    }

    private boolean isGameOver(int defender) {
        if (this.ships[defender].isEmpty()) {
            return false;
        }

        List<Move> opponentMoves = this.moves[oppositePlayer(defender)];
        for (Ship ship : this.ships[defender]) {
            if (!ship.isDestroyed(opponentMoves)) {
                return false;
            }
        }
        return true;
    }

    private boolean overlapsExistingShip(int player, Ship candidate) {
        for (Ship ship : this.ships[player]) {
            for (Cell candidateCell : candidate.cells) {
                if (ship.contains(candidateCell.x, candidateCell.y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Ship getShipAt(int player, int x, int y) {
        for (Ship ship : this.ships[player]) {
            if (ship.contains(x, y)) {
                return ship;
            }
        }
        return null;
    }

    private Move getMoveAt(int shooter, int x, int y) {
        for (Move move : this.moves[shooter]) {
            if (move.x == x && move.y == y) {
                return move;
            }
        }
        return null;
    }

    private boolean hasAlreadyShot(int shooter, int x, int y) {
        return getMoveAt(shooter, x, y) != null;
    }

    private int countShips(int player, ShipType type) {
        int count = 0;
        for (Ship ship : this.ships[player]) {
            if (ship.type == type) {
                count++;
            }
        }
        return count;
    }

    private void resetBattleState(boolean closePlayers) {
        for (int i = 0; i < PLAYER_COUNT; i++) {
            resetPlayer(i);
        }
        this.battleStarted = false;
        this.turnUsed = false;
        this.gameOver = false;
        this.nextTurn = 0;
        if (closePlayers) {
            broadcastClose("stopped");
        }
    }

    private void resetPlayer(int player) {
        if (!isPlayer(player)) {
            return;
        }
        this.ships[player].clear();
        this.moves[player].clear();
    }

    private static int oppositePlayer(int player) {
        return player == 0 ? 1 : 0;
    }

    private static boolean isPlayer(int player) {
        return player >= 0 && player < PLAYER_COUNT;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isInsideBoard(int x, int y) {
        return x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT;
    }

    private enum ShipType {
        AIRCRAFT_CARRIER(5, 1, 5),
        BATTLESHIP(4, 2, 4),
        CRUISER(3, 3, 3),
        DESTROYER(2, 4, 2);

        private final int id;
        private final int maxAllowed;
        private final int length;

        ShipType(int id, int maxAllowed, int length) {
            this.id = id;
            this.maxAllowed = maxAllowed;
            this.length = length;
        }

        private static ShipType byId(int id) {
            for (ShipType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    private enum MoveResult {
        HIT('X'),
        MISS('O');

        private final char symbol;

        MoveResult(char symbol) {
            this.symbol = symbol;
        }
    }

    private static class Ship {
        private final ShipType type;
        private final int player;
        private final List<Cell> cells;

        private Ship(ShipType type, int player, List<Cell> cells) {
            this.type = type;
            this.player = player;
            this.cells = cells;
        }

        private static Ship create(ShipType type, int player, int startX, int startY, int endX, int endY) {
            if (type == null || !isInsideBoard(startX, startY) || !isInsideBoard(endX, endY)) {
                return null;
            }

            boolean horizontal = startY == endY;
            boolean vertical = startX == endX;
            if (horizontal == vertical) {
                return null;
            }

            int minX = Math.min(startX, endX);
            int maxX = Math.max(startX, endX);
            int minY = Math.min(startY, endY);
            int maxY = Math.max(startY, endY);
            int length = horizontal ? maxX - minX + 1 : maxY - minY + 1;
            if (length != type.length) {
                return null;
            }

            List<Cell> cells = new ArrayList<>(type.length);
            for (int i = 0; i < type.length; i++) {
                cells.add(new Cell(horizontal ? minX + i : minX, horizontal ? minY : minY + i));
            }
            return new Ship(type, player, cells);
        }

        private boolean contains(int x, int y) {
            for (Cell cell : this.cells) {
                if (cell.x == x && cell.y == y) {
                    return true;
                }
            }
            return false;
        }

        private int startX() {
            return this.cells.isEmpty() ? 0 : this.cells.get(0).x;
        }

        private int startY() {
            return this.cells.isEmpty() ? 0 : this.cells.get(0).y;
        }

        private boolean isHorizontal() {
            return this.cells.size() < 2 || this.cells.get(0).y == this.cells.get(1).y;
        }

        private boolean isDestroyed(List<Move> opponentMoves) {
            return hitCount(opponentMoves) >= this.type.length;
        }

        private int hitCount(List<Move> opponentMoves) {
            int hits = 0;
            for (Move move : opponentMoves) {
                if (move.result == MoveResult.HIT && move.ship == this) {
                    hits++;
                }
            }
            return hits;
        }
    }

    private static class Cell {
        private final int x;
        private final int y;

        private Cell(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class Move {
        private final int x;
        private final int y;
        private final MoveResult result;
        private final Ship ship;

        private Move(int x, int y, MoveResult result, Ship ship) {
            this.x = x;
            this.y = y;
            this.result = result;
            this.ship = ship;
        }
    }
}
