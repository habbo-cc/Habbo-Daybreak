package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.List;

/**
 * Wired 2.0 runtime movement packet (local outgoing 7115; Habbo header 641, May parser §_-52s§).
 * <p>
 * Carries a flat list of typed movement records so the client can interpolate the slide over
 * {@code animationTime} instead of snapping (legacy ObjectOnRoller). Replaces FloorItemOnRoller for
 * wired-driven furni movement (e.g. wf_chase), giving Habbo-parity smooth motion at fast trigger rates.
 * </p>
 * <p>Record types: 0=user move, 1=furni move, 2=wall item move, 3=user direction update.
 * Field order is the July AIR parser contract; outgoing header 7115 remains local.</p>
 */
public class WiredMovementsMessageComposer extends MessageComposer {
    public static final int DEFAULT_ANIMATION_TIME = 500;

    private static final int RECORD_USER_MOVE = 0, RECORD_FURNI_MOVE = 1,
            RECORD_WALL_ITEM_MOVE = 2, RECORD_USER_DIRECTION = 3;

    private final List<FurniMove> furniMoves;
    private final List<UserMove> userMoves;
    private final List<WallItemMove> wallItemMoves;
    private final List<UserDirection> userDirections;

    public WiredMovementsMessageComposer(FurniMove move) {
        this.furniMoves = new ArrayList<>(1);
        this.furniMoves.add(move);
        this.userMoves = List.of(); this.wallItemMoves = List.of(); this.userDirections = List.of();
    }

    public WiredMovementsMessageComposer(List<FurniMove> moves) {
        this.furniMoves = moves;
        this.userMoves = List.of(); this.wallItemMoves = List.of(); this.userDirections = List.of();
    }

    public WiredMovementsMessageComposer(List<FurniMove> furniMoves, List<UserMove> userMoves, List<UserDirection> userDirections) {
        this(furniMoves, userMoves, List.of(), userDirections);
    }

    public WiredMovementsMessageComposer(List<FurniMove> furniMoves, List<UserMove> userMoves,
            List<WallItemMove> wallItemMoves, List<UserDirection> userDirections) {
        this.furniMoves = furniMoves == null ? List.of() : furniMoves;
        this.userMoves = userMoves == null ? List.of() : userMoves;
        this.wallItemMoves = wallItemMoves == null ? List.of() : wallItemMoves;
        this.userDirections = userDirections == null ? List.of() : userDirections;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredMovementsMessageComposer);
        this.response.appendInt(this.furniMoves.size() + this.userMoves.size()
                + this.wallItemMoves.size() + this.userDirections.size());

        for (UserMove m : this.userMoves) {
            this.response.appendInt(RECORD_USER_MOVE);
            this.response.appendInt(m.fromX); this.response.appendInt(m.fromY);
            this.response.appendInt(m.toX); this.response.appendInt(m.toY);
            this.response.appendString(Double.toString(m.fromZ)); this.response.appendString(Double.toString(m.toZ));
            this.response.appendInt(m.unitId); this.response.appendInt(m.walk ? 0 : 1);
            this.response.appendInt(m.animationTime); this.response.appendInt(m.bodyDirection); this.response.appendInt(m.headDirection);
            this.response.appendBoolean(m.jumpPower != null); if (m.jumpPower != null) this.response.appendInt(m.jumpPower);
        }

        for (FurniMove m : this.furniMoves) {
            this.response.appendInt(RECORD_FURNI_MOVE);
            this.response.appendInt(m.fromX);
            this.response.appendInt(m.fromY);
            this.response.appendInt(m.toX);
            this.response.appendInt(m.toY);
            this.response.appendString(Double.toString(m.fromZ));
            this.response.appendString(Double.toString(m.toZ));
            this.response.appendInt(m.itemId);
            this.response.appendInt(m.animationTime);
            this.response.appendInt(m.rotation);

            if (m.overshootAnimationTime != null) {
                this.response.appendBoolean(true);
                this.response.appendInt(m.overshootAnimationTime);
            } else {
                this.response.appendBoolean(false);
            }

            if (m.curveStrength != null) {
                this.response.appendBoolean(true);
                this.response.appendInt(m.curveStrength);
            } else {
                this.response.appendBoolean(false);
            }
        }

        for (WallItemMove m : this.wallItemMoves) {
            this.response.appendInt(RECORD_WALL_ITEM_MOVE);
            this.response.appendInt(m.itemId);
            this.response.appendBoolean(m.directionRight);
            this.response.appendInt(m.oldWallX);
            this.response.appendInt(m.oldWallY);
            this.response.appendInt(m.oldOffsetX);
            this.response.appendInt(m.oldOffsetY);
            this.response.appendInt(m.newWallX);
            this.response.appendInt(m.newWallY);
            this.response.appendInt(m.newOffsetX);
            this.response.appendInt(m.newOffsetY);
            this.response.appendInt(m.animationTime);
        }

        for (UserDirection d : this.userDirections) {
            this.response.appendInt(RECORD_USER_DIRECTION); this.response.appendInt(d.unitId);
            this.response.appendInt(d.bodyDirection); this.response.appendInt(d.headDirection);
        }

        return this.response;
    }

    public record UserMove(int unitId, int fromX, int fromY, double fromZ, int toX, int toY, double toZ,
                           boolean walk, int animationTime, int bodyDirection, int headDirection, Integer jumpPower) { }
    public record WallItemMove(int itemId, boolean directionRight,
            int oldWallX, int oldWallY, int oldOffsetX, int oldOffsetY,
            int newWallX, int newWallY, int newOffsetX, int newOffsetY, int animationTime) {
        public WallItemMove(HabboItem item, boolean directionRight,
                int oldWallX, int oldWallY, int oldOffsetX, int oldOffsetY,
                int newWallX, int newWallY, int newOffsetX, int newOffsetY, int animationTime) {
            this(item.getRoomVisibleId(), directionRight,
                    oldWallX, oldWallY, oldOffsetX, oldOffsetY,
                    newWallX, newWallY, newOffsetX, newOffsetY, animationTime);
        }
    }
    public record UserDirection(int unitId, int bodyDirection, int headDirection) { }

    /**
     * A single furni-move record. itemId uses the room-visible id (matches FloorItemOnRoller).
     * {@code rotation} is the furni's facing (0-7); the client applies it as {@code (rotation % 8) * 45}
     * degrees, so a move-and-rotate effect (e.g. Change Furni Direction) animates the turn too. It is
     * read from the item <em>after</em> {@code moveFurniTo}, so it carries the post-move rotation.
     */
    public static class FurniMove {
        public final int itemId;
        public final int fromX, fromY, toX, toY;
        public final double fromZ, toZ;
        public final int animationTime;
        public final int rotation;
        public Integer overshootAnimationTime;
        public Integer curveStrength;

        public FurniMove(HabboItem item, RoomTile from, double fromZ, RoomTile to, double toZ, int animationTime) {
            this.itemId = item.getRoomVisibleId();
            this.fromX = from.x;
            this.fromY = from.y;
            this.fromZ = fromZ;
            this.toX = to.x;
            this.toY = to.y;
            this.toZ = toZ;
            this.animationTime = animationTime;
            this.rotation = item.getRotation();
        }

        FurniMove(
                int itemId,
                int fromX,
                int fromY,
                double fromZ,
                int toX,
                int toY,
                double toZ,
                int animationTime,
                int rotation) {
            this.itemId = itemId;
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toY = toY;
            this.toZ = toZ;
            this.animationTime = animationTime;
            this.rotation = rotation;
        }
    }
}
