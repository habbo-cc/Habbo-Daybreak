package com.eu.habbo.habbohotel.games.battleball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable walkability, floor height and BattleBall-tile mask loaded from the original Kepler map data. */
public final class BattleBallMap {
    private final int id;
    private final int width;
    private final int height;
    private final boolean[][] playable;
    private final boolean[][] walkable;
    private final int[][] floorHeights;
    private final List<BattleBallPosition> playablePositions;

    /** Builds a flat map; used by tests and any caller with no height data. */
    public BattleBallMap(int id, String tileMap) {
        this(id, tileMap, null);
    }

    public BattleBallMap(int id, String tileMap, String heightMap) {
        if (id < 1 || id > 5) throw new IllegalArgumentException("BattleBall map id must be 1..5");
        String[] rows = splitRows(tileMap);
        int rowCount = rows.length;
        while (rowCount > 0 && rows[rowCount - 1].isEmpty()) rowCount--;
        if (rowCount == 0) throw new IllegalArgumentException("BattleBall tile map is empty");
        String[] heightRows = splitRows(heightMap == null ? "" : heightMap);

        this.id = id;
        this.height = rowCount;
        this.width = rows[0].length();
        this.playable = new boolean[this.width][this.height];
        this.walkable = new boolean[this.width][this.height];
        this.floorHeights = new int[this.width][this.height];
        List<BattleBallPosition> positions = new ArrayList<>();

        for (int y = 0; y < this.height; y++) {
            if (rows[y].length() != this.width) {
                throw new IllegalArgumentException("BattleBall tile map rows have different widths");
            }
            String heightRow = y < heightRows.length ? heightRows[y] : "";
            for (int x = 0; x < this.width; x++) {
                boolean gameTile = rows[y].charAt(x) == '1';
                // The original Sky Peak cast has two visually valid tiles missing from its mask.
                if (id == 1 && x == 24 && (y == 17 || y == 18)) gameTile = true;
                this.playable[x][y] = gameTile;
                // The two masks mean different things. The heightmap says where a ball may stand ('x' is
                // void, a digit is the floor level there); the tile map says which of those cells score.
                // Ramps between levels are walkable but never scorable, so treating the tile map as
                // walkability is what left every raised platform stranded with no way on or off it.
                char level = x < heightRow.length() ? heightRow.charAt(x) : '0';
                boolean hasLevel = Character.isDigit(level);
                this.floorHeights[x][y] = hasLevel ? level - '0' : 0;
                this.walkable[x][y] = hasLevel || gameTile;
                if (gameTile) positions.add(new BattleBallPosition(x, y));
            }
        }
        this.playablePositions = Collections.unmodifiableList(positions);
    }

    private static String[] splitRows(String value) {
        return value.replace('\r', '|').replace('\n', '|').split("\\|", -1);
    }

    public int id() { return this.id; }
    public int width() { return this.width; }
    public int height() { return this.height; }

    /** Floor height of a cell, in original map units. Out-of-bounds cells report ground level. */
    public int floorHeight(int x, int y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height ? this.floorHeights[x][y] : 0;
    }

    /** Whether a cell scores when landed on. */
    public boolean isPlayable(int x, int y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height && this.playable[x][y];
    }

    /** Whether a ball may stand on a cell at all, including ramps that score nothing. */
    public boolean isWalkable(int x, int y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height && this.walkable[x][y];
    }

    public List<BattleBallPosition> playablePositions() {
        return this.playablePositions;
    }
}
