package com.eu.habbo.habbohotel.games.battleball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BattleBallTile {
    public static final int DISABLED_COLOUR = -2;
    public static final int DEFAULT_COLOUR = -1;

    private final int x;
    private final int y;
    private final int floorHeight;
    private final boolean walkable;
    private int colour;
    private BattleBallTileState state;
    private final List<BattleBallScoreReference> scoreReferences = new ArrayList<>();

    public BattleBallTile(int x, int y, boolean playable) {
        this(x, y, playable, 0, playable);
    }

    public BattleBallTile(int x, int y, boolean playable, int floorHeight, boolean walkable) {
        this.x = x;
        this.y = y;
        this.floorHeight = floorHeight;
        this.walkable = walkable;
        this.colour = playable ? DEFAULT_COLOUR : DISABLED_COLOUR;
        this.state = BattleBallTileState.DEFAULT;
    }

    public int x() { return this.x; }
    public int y() { return this.y; }
    /** Floor height in original map units; the client raises the tile and anything standing on it. */
    public int floorHeight() { return this.floorHeight; }
    /** Ramps are walkable without ever scoring, so this is wider than playable(). */
    public boolean walkable() { return this.walkable; }
    public int colour() { return this.colour; }
    public BattleBallTileState state() { return this.state; }
    public boolean playable() { return this.colour != DISABLED_COLOUR; }

    public List<BattleBallScoreReference> scoreReferences() {
        return Collections.unmodifiableList(this.scoreReferences);
    }

    /** Applies one normal landing and returns whether the tile changed. */
    public boolean land(int mapId, int teamId, int scoringUserId, boolean highJump, boolean cleaning) {
        if (!this.playable()) return false;
        if (cleaning) return this.reset();
        if (this.state == BattleBallTileState.SEALED) return false;

        if (highJump) {
            int previousColour = this.colour;
            boolean stolen = previousColour >= 0 && previousColour != teamId;
            this.colour = teamId;
            this.state = BattleBallTileState.SEALED;
            this.scoreReferences.clear();
            this.scoreReferences.add(new BattleBallScoreReference(14 + (stolen ? 2 : 0), teamId, scoringUserId));
            return true;
        }

        int previousColour = this.colour;
        BattleBallTileState nextState;
        if (previousColour == teamId) {
            nextState = this.state.next();
        } else {
            nextState = mapId == 5 ? BattleBallTileState.TOUCHED : BattleBallTileState.CLICKED;
        }

        int points = switch (nextState) {
            case TOUCHED -> 2;
            case CLICKED -> 6;
            case PRESSED -> 10;
            case SEALED -> previousColour == teamId ? 14 : 0;
            default -> 0;
        };
        if (previousColour >= 0 && previousColour != teamId) points += 2;
        if (previousColour != teamId) this.scoreReferences.clear();
        if (points > 0) this.scoreReferences.add(new BattleBallScoreReference(points, teamId, scoringUserId));
        this.colour = teamId;
        this.state = nextState;
        return true;
    }

    /** Seals a flood-filled tile and records the score Kepler omitted. */
    public boolean sealFromFill(int teamId, int scoringUserId) {
        if (!this.playable() || this.state == BattleBallTileState.SEALED) return false;
        boolean stolen = this.colour >= 0 && this.colour != teamId;
        if (this.colour != teamId) this.scoreReferences.clear();
        this.colour = teamId;
        this.state = BattleBallTileState.SEALED;
        this.scoreReferences.add(new BattleBallScoreReference(14 + (stolen ? 2 : 0), teamId, scoringUserId));
        return true;
    }

    public boolean paint(int teamId, BattleBallTileState target, int scoringUserId) {
        if (!this.playable() || this.state == BattleBallTileState.SEALED) return false;
        int oldColour = this.colour;
        if (oldColour != teamId) this.scoreReferences.clear();
        this.colour = teamId;
        this.state = target;
        int points = switch (target) {
            case TOUCHED -> 2;
            case CLICKED -> 6;
            case PRESSED -> 10;
            case SEALED -> 14;
            default -> 0;
        };
        if (oldColour >= 0 && oldColour != teamId) points += 2;
        if (points > 0) this.scoreReferences.add(new BattleBallScoreReference(points, teamId, scoringUserId));
        return true;
    }

    public boolean reset() {
        if (!this.playable() || (this.colour == DEFAULT_COLOUR && this.state == BattleBallTileState.DEFAULT)) return false;
        this.colour = DEFAULT_COLOUR;
        this.state = BattleBallTileState.DEFAULT;
        this.scoreReferences.clear();
        return true;
    }
}
