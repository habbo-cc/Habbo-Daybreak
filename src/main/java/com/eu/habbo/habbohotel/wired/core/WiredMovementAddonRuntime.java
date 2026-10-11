package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCarryUsers;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonMovementPhysics;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonJumpStrength;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonProjectile;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.util.pathfinding.Rotation;
import com.eu.habbo.Emulator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutation;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.ItemUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredMovementsMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateMessageComposer;

/**
 * Runtime bridge for July movement add-ons. It deliberately keeps outgoing
 * packet 7115 unchanged.
 *
 * <p>Movement-physics, curve and projectile behavior was adapted in part from
 * Seth/iSetht's GPL-3.0 Arcturus-Community-Wired implementation:
 * https://github.com/Harmonicrain/Arcturus-Community-Wired. It was reworked
 * for July AIR's fields, four movement record types and local safety rules.</p>
 */
public final class WiredMovementAddonRuntime {
    private static final ThreadLocal<WiredAddonMovementPhysics> ACTIVE_PHYSICS = new ThreadLocal<>();
    private static final ThreadLocal<PhysicsPolicy> ACTIVE_PHYSICS_POLICY = new ThreadLocal<>();
    private static final ThreadLocal<MovementBatch> ACTIVE_MOVEMENTS = new ThreadLocal<>();
    private static final ThreadLocal<Map<Integer, PreparedCarry>> ACTIVE_CARRIES = new ThreadLocal<>();
    private static final ThreadLocal<Set<Integer>> ACTIVE_CARRY_TARGETS = new ThreadLocal<>();
    private static final ConcurrentHashMap<Long, ActiveUserMove> ACTIVE_USER_MOVEMENTS =
            new ConcurrentHashMap<>();
    private static final String[] PROJECTILE_VARIABLES = {
            "@projectile.animation.tiles_travelled",
            "@projectile.animation.user_collisions",
            "@projectile.animation.furni_collisions",
            "@projectile.animation.position.x",
            "@projectile.animation.position.y",
            "@projectile.animation.position.altitude",
            "@projectile.animation.is_travelling"
    };
    private static final ConcurrentHashMap<Long, ProjectileFlight> PROJECTILE_FLIGHTS = new ConcurrentHashMap<>();
    private static final AtomicLong PROJECTILE_REVISION = new AtomicLong();
    private WiredMovementAddonRuntime() { }
    public static int animationTime(WiredContext ctx) { WiredAddonAnimationTime a = addon(ctx, WiredAddonType.ANIMATION_TIME, WiredAddonAnimationTime.class); return a == null ? WiredMovementsMessageComposer.DEFAULT_ANIMATION_TIME : a.milliseconds(); }
    public static boolean suppressAnimation(WiredContext ctx) { return addon(ctx, WiredAddonType.NO_MOVE_ANIMATION, InteractionWiredAddon.class) != null; }
    public static WiredAddonMovementPhysics physics(WiredContext ctx) { return addon(ctx, WiredAddonType.MOVEMENT_PHYSICS, WiredAddonMovementPhysics.class); }
    public static WiredAddonProjectile projectile(WiredContext ctx) { return addon(ctx, WiredAddonType.PROJECTILE, WiredAddonProjectile.class); }
    public static WiredAddonMovementPhysics activePhysics() { return ACTIVE_PHYSICS.get(); }
    /** Block-by-furni deliberately wins if a stack contains both conflicting options. */
    public static boolean bypassFurniCollision(WiredAddonMovementPhysics physics) {
        PhysicsPolicy policy = policy(physics);
        return policy != null
                ? policy.throughAllFurniture && !policy.blocksAllFurniture
                : physics != null && physics.throughFurni() && !physics.blockByFurni();
    }
    public static boolean bypassFurniCollision(
            WiredAddonMovementPhysics physics, HabboItem obstacle) {
        PhysicsPolicy policy = policy(physics);
        return policy != null
                ? policy.throughFurniture.contains(obstacle)
                        && !policy.blockingFurniture.contains(obstacle)
                : bypassFurniCollision(physics);
    }
    public static boolean bypassUnitCollision(WiredAddonMovementPhysics physics) {
        PhysicsPolicy policy = policy(physics);
        return policy != null ? policy.throughAllUsers : physics != null && physics.throughUsers();
    }
    public static boolean bypassUnitCollision(
            WiredAddonMovementPhysics physics, RoomUnit obstacle) {
        PhysicsPolicy policy = policy(physics);
        return policy != null
                ? policy.throughUsers.contains(obstacle)
                : bypassUnitCollision(physics);
    }
    public static boolean blocksFurni(WiredAddonMovementPhysics physics) {
        PhysicsPolicy policy = policy(physics);
        return policy != null ? policy.blocksAllFurniture : physics != null && physics.blockByFurni();
    }
    public static boolean blocksFurni(WiredAddonMovementPhysics physics, HabboItem obstacle) {
        PhysicsPolicy policy = policy(physics);
        return policy != null
                ? policy.blockingFurniture.contains(obstacle)
                : blocksFurni(physics);
    }
    /**
     * July's Projectile source 901 means the effect's ordinary targets. Choosing
     * another source replaces that target set instead of merely decorating it.
     */
    public static List<HabboItem> furniTargets(WiredContext ctx, Collection<HabboItem> normalTargets) {
        WiredAddonProjectile projectile = projectile(ctx);
        if (projectile == null) return normalTargets == null ? List.of() : List.copyOf(normalTargets);
        return projectile.projectiles(ctx, normalTargets == null ? List.of() : normalTargets);
    }
    public static FurnitureMovementError move(WiredContext ctx, Room room, HabboItem item, RoomTile tile, int rotation, boolean sendUpdates) {
        return move(ctx, room, item, tile, rotation, sendUpdates, true);
    }
    public static FurnitureMovementError move(WiredContext ctx, Room room, HabboItem item, RoomTile tile, int rotation, boolean sendUpdates, boolean checkForUnits) {
        return withPhysics(ctx, () -> {
            WiredAddonMovementPhysics movementPhysics = physics(ctx);
            WiredAddonProjectile projectile = projectile(ctx);
            RoomTile from = room.getLayout().getTile(item.getX(), item.getY());
            RoomTile target = projectile == null ? tile : projectile.resolveTarget(ctx, from, tile);
            int targetRotation = projectile == null ? rotation : projectile.resolveRotation(from, target, rotation);
            PreparedCarry preparedCarry = prepareCarry(
                    ctx, room, item, from, item.getZ());
            boolean effectiveUnitCheck = shouldCheckForUnits(
                    room, item, target, targetRotation, checkForUnits, movementPhysics);
            FurnitureMovementError result = room.moveFurniTo(
                    item, target, targetRotation, null, sendUpdates, effectiveUnitCheck,
                    bypassFurniCollision(movementPhysics));
            if (result == FurnitureMovementError.NONE) {
                rememberCarry(item, preparedCarry);
            } else {
                releaseCarry(preparedCarry);
            }
            return result;
        });
    }
    /**
     * Applies a stack's movement policy for its complete effect execution, not merely
     * the final move call. Effects commonly preflight with furnitureFitsAt first.
     */
    public static <T> T withPhysics(WiredContext ctx, java.util.function.Supplier<T> action) {
        WiredAddonMovementPhysics previous = ACTIVE_PHYSICS.get();
        WiredAddonMovementPhysics current = physics(ctx);
        PhysicsPolicy previousPolicy = ACTIVE_PHYSICS_POLICY.get();
        PhysicsPolicy currentPolicy = current == null ? null : new PhysicsPolicy(current, ctx);
        MovementBatch previousBatch = ACTIVE_MOVEMENTS.get();
        MovementBatch ownedBatch = previousBatch == null && ctx != null && ctx.room() != null
                ? new MovementBatch(ctx.room())
                : null;
        Map<Integer, PreparedCarry> previousCarries = ACTIVE_CARRIES.get();
        Map<Integer, PreparedCarry> ownedCarries = previousCarries == null
                ? new HashMap<>()
                : null;
        Set<Integer> previousCarryTargets = ACTIVE_CARRY_TARGETS.get();
        Set<Integer> ownedCarryTargets = previousCarryTargets == null
                ? new HashSet<>()
                : null;
        if (current != null) {
            ACTIVE_PHYSICS.set(current);
            ACTIVE_PHYSICS_POLICY.set(currentPolicy);
        } else {
            ACTIVE_PHYSICS.remove();
            ACTIVE_PHYSICS_POLICY.remove();
        }
        if (ownedBatch != null) ACTIVE_MOVEMENTS.set(ownedBatch);
        if (ownedCarries != null) ACTIVE_CARRIES.set(ownedCarries);
        if (ownedCarryTargets != null) ACTIVE_CARRY_TARGETS.set(ownedCarryTargets);
        try {
            return action.get();
        } finally {
            if (previous == null) ACTIVE_PHYSICS.remove(); else ACTIVE_PHYSICS.set(previous);
            if (previousPolicy == null) ACTIVE_PHYSICS_POLICY.remove(); else ACTIVE_PHYSICS_POLICY.set(previousPolicy);
            if (ownedCarries != null) ACTIVE_CARRIES.remove();
            else if (previousCarries != null) ACTIVE_CARRIES.set(previousCarries);
            if (ownedCarryTargets != null) ACTIVE_CARRY_TARGETS.remove();
            else if (previousCarryTargets != null) ACTIVE_CARRY_TARGETS.set(previousCarryTargets);
            if (ownedBatch != null) {
                ACTIVE_MOVEMENTS.remove();
                ownedBatch.flush();
            }
        }
    }
    public static void withPhysics(WiredContext ctx, Runnable action) { withPhysics(ctx, () -> { action.run(); return null; }); }

    /**
     * Commits a July forced-user movement and emits its type-0 record through
     * the existing local 7115 transport. Walk mode follows the AIR editor:
     * 0 keeps a queued goal only when the move gets closer, 1 always keeps it,
     * and 2 stops it.
     */
    public static boolean moveUser(
            WiredContext ctx, RoomUnit unit, RoomTile target, int walkMode) {
        if (ctx == null || ctx.room() == null || unit == null || target == null) {
            return false;
        }
        double targetZ = unit.hasStatus(RoomUnitStatus.SIT)
                || unit.hasStatus(RoomUnitStatus.LAY)
                ? unit.getZ()
                : target.getStackHeight();
        return moveUser(ctx, unit, target, targetZ, walkMode);
    }

    /** Moves only the user's altitude while retaining the July animation path. */
    public static boolean moveUserAltitude(
            WiredContext ctx, RoomUnit unit, double targetZ) {
        return ctx != null && unit != null
                && moveUser(ctx, unit, unit.getCurrentLocation(), targetZ, 1);
    }

    /** Emits July's type-3 direction record for internal-variable mutations. */
    public static boolean updateUserDirection(
            WiredContext ctx, RoomUnit unit, int direction) {
        if (ctx == null || ctx.room() == null || unit == null
                || unit.getRoom() != ctx.room() || !unit.isInRoom()
                || direction < 0 || direction > 7) {
            return false;
        }
        unit.setRotation(RoomUserRotation.fromValue(direction));
        if (suppressAnimation(ctx)) {
            unit.statusUpdate(true);
        } else {
            emitOrQueue(
                    ctx.room(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new WiredMovementsMessageComposer.UserDirection(
                            unit.getId(), direction, direction)));
            unit.statusUpdate(false);
        }
        return true;
    }

    /** Queues July's type-2 wall-item animation for a committed wall move. */
    public static void emitWallItemMovement(
            WiredContext ctx,
            WiredMovementsMessageComposer.WallItemMove movement) {
        if (ctx == null || ctx.room() == null || movement == null) {
            return;
        }
        emitOrQueue(ctx.room(), List.of(), List.of(),
                List.of(movement), List.of());
    }

    /**
     * Defers a normal click-to-walk goal until the active forced tween lands.
     * The server commits forced destinations immediately, so walking before
     * the packet duration ends would otherwise make the AIR client snap.
     */
    public static boolean queueWalkAfterActiveMovement(
            Room room, RoomUnit unit, RoomTile goal) {
        if (room == null || unit == null || goal == null) {
            return false;
        }
        long key = userMovementKey(room, unit);
        ActiveUserMove active = ACTIVE_USER_MOVEMENTS.get(key);
        if (active == null || active.room != room || active.unit != unit) {
            return false;
        }
        active.queueContinuation(goal);
        return true;
    }

    private static boolean moveUser(
            WiredContext ctx,
            RoomUnit unit,
            RoomTile target,
            double targetZ,
            int walkMode) {
        Room room = ctx.room();
        if (unit.getRoom() != room || !unit.isInRoom()
                || unit.getCurrentLocation() == null
                || target.state == RoomTileState.INVALID
                || Double.isNaN(targetZ) || Double.isInfinite(targetZ)) {
            return false;
        }

        RoomTile from = unit.getCurrentLocation();
        double fromZ = unit.getZ();
        if (from.equals(target) && Double.compare(fromZ, targetZ) == 0) {
            return false;
        }

        long movementKey = userMovementKey(room, unit);
        ActiveUserMove previousMove = ACTIVE_USER_MOVEMENTS.remove(movementKey);
        RoomTile queuedGoal = previousMove != null
                && previousMove.continuationGoal() != null
                ? previousMove.continuationGoal()
                : unit.getGoal();
        boolean wasWalking = (previousMove != null
                && (previousMove.walkFlavoured || previousMove.shouldContinue()))
                || unit.isWalking()
                || unit.hasStatus(RoomUnitStatus.MOVE)
                || (queuedGoal != null && !queuedGoal.equals(from));
        boolean continueWalking = wasWalking && queuedGoal != null
                && !queuedGoal.equals(target)
                && (walkMode == 1
                    || (walkMode == 0
                        && target.distance(queuedGoal) < from.distance(queuedGoal)));
        boolean walkFlavoured = wasWalking
                && !unit.hasStatus(RoomUnitStatus.SIT)
                && !unit.hasStatus(RoomUnitStatus.LAY);
        int duration = animationTime(ctx);
        WiredAddonJumpStrength jump = addon(
                ctx, WiredAddonType.JUMP_STRENGTH, WiredAddonJumpStrength.class);
        Integer jumpPower = jump == null ? null : jump.resolve(ctx);
        if (jumpPower != null && jumpPower == 0) {
            jumpPower = null;
        }

        if (!from.equals(target)) {
            HabboItem originItem = room.getTopItemAt(from.x, from.y);
            if (originItem != null) {
                try {
                    originItem.onWalkOff(
                            unit, room, new Object[] {from, target, null});
                } catch (Exception ignored) {
                }
            }
        }
        unit.stopWalking();
        unit.setPath(new LinkedList<>());
        unit.removeStatus(RoomUnitStatus.MOVE);
        unit.setPreviousLocation(from);
        unit.setPreviousLocationZ(fromZ);
        unit.setCurrentLocationAndGoal(target);
        unit.setZ(targetZ);

        if (suppressAnimation(ctx)) {
            ACTIVE_USER_MOVEMENTS.remove(movementKey);
            unit.statusUpdate(true);
            settleUserMove(room, unit, from, target, queuedGoal,
                    continueWalking);
            return true;
        }

        emitOrQueue(
                room,
                List.of(),
                List.of(new WiredMovementsMessageComposer.UserMove(
                        unit.getId(),
                        from.x,
                        from.y,
                        fromZ,
                        target.x,
                        target.y,
                        targetZ,
                        walkFlavoured,
                        duration,
                        unit.getBodyRotation().getValue(),
                        unit.getHeadRotation().getValue(),
                        jumpPower)),
                List.of(),
                List.of());
        unit.statusUpdate(false);

        ActiveUserMove activeMove = new ActiveUserMove(
                room, unit, target, queuedGoal, continueWalking,
                walkFlavoured);
        ACTIVE_USER_MOVEMENTS.put(movementKey, activeMove);
        Emulator.getThreading().run(() -> {
            if (!ACTIVE_USER_MOVEMENTS.remove(movementKey, activeMove)) {
                return;
            }
            if (!room.isLoaded() || !unit.isInRoom() || unit.getRoom() != room
                    || unit.getCurrentLocation() == null
                    || !unit.getCurrentLocation().equals(activeMove.target)) {
                return;
            }
            settleUserMove(room, unit, from, activeMove.target,
                    activeMove.continuationGoal(),
                    activeMove.shouldContinue());
        }, Math.max(1, duration));
        return true;
    }

    private static void settleUserMove(
            Room room,
            RoomUnit unit,
            RoomTile from,
            RoomTile target,
            RoomTile continuationGoal,
            boolean continueWalking) {
        // The ordinary status composer serializes previousLocation. Point it
        // at the committed destination before the final reconciliation packet,
        // otherwise clients snap back to the forced move's origin.
        unit.setPreviousLocation(target);
        unit.setPreviousLocationZ(unit.getZ());
        if (from.equals(target)) {
            // Altitude-only internal-variable moves must retain their explicit
            // Z rather than being normalized back to the tile stack height.
            room.sendComposer(new UserUpdateMessageComposer(unit).compose());
        } else {
            room.updateHabbosAt(target.x, target.y);
            room.updateBotsAt(target.x, target.y);
            room.updatePetsAt(target.x, target.y);
        }
        HabboItem targetItem = room.getTopItemAt(target.x, target.y);
        HabboItem originItem = room.getTopItemAt(from.x, from.y);
        if (targetItem != null && targetItem != originItem) {
            try {
                targetItem.onWalkOn(unit, room, new Object[] {from, target, null});
            } catch (Exception ignored) {
            }
        }
        if (continueWalking && continuationGoal != null) {
            unit.setGoalLocation(continuationGoal);
        } else {
            unit.stopWalking();
        }
    }

    public static void moved(WiredContext ctx, Room room, HabboItem item, RoomTile from, double fromZ, RoomTile to) {
        if (room == null || item == null || from == null || to == null) return;
        RoomTile actual = room.getLayout().getTile(item.getX(), item.getY());
        if (actual != null) to = actual;
        WiredAddonMovementPhysics physics = physics(ctx);
        if (physics != null && physics.keepAltitude()
                && Double.compare(item.getZ(), fromZ) != 0) {
            item.setZ(fromZ);
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            room.updateTiles(room.getLayout().getTilesAt(
                    to, item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                    item.getRotation()));
        }
        boolean animated = !suppressAnimation(ctx);
        List<WiredMovementsMessageComposer.FurniMove> furniMoves = new ArrayList<>(1);
        List<WiredMovementsMessageComposer.UserMove> userMoves = new ArrayList<>();
        List<WiredMovementsMessageComposer.UserDirection> directions = new ArrayList<>();
        int duration = animationTime(ctx);
        if (!animated) {
            room.updateItem(item);
        } else {
            WiredAddonProjectile projectile = projectile(ctx);
            int tiles = Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y));
            duration = projectile == null ? duration
                    : projectile.animationTime(ctx, from, fromZ, to, item.getZ(), duration);
            WiredMovementsMessageComposer.FurniMove movement = new WiredMovementsMessageComposer.FurniMove(item, from, fromZ, to, item.getZ(), duration);
            WiredAddonJumpStrength jump = addon(ctx, WiredAddonType.JUMP_STRENGTH, WiredAddonJumpStrength.class);
            int curve = projectile != null && projectile.curveStrength() != 0 ? projectile.curveStrength() : jump == null ? 0 : jump.resolve(ctx);
            if (curve != 0) movement.curveStrength = curve;
            if (projectile != null && projectile.distanceMode() == 1 && projectile.distance(ctx) != 0 && tiles > 0) {
                long overshoot = Math.round((double)duration * projectile.distance(ctx) / tiles);
                long minimum = 1L-duration;
                long maximum = Integer.MAX_VALUE-(long)duration;
                movement.overshootAnimationTime = (int)Math.max(minimum,Math.min(maximum,overshoot));
            }
            furniMoves.add(movement);
            if (projectile != null) {
                int direction = Math.floorMod(Rotation.Calculate(from.x, from.y, to.x, to.y), 8);
                for (RoomUnit shooter : projectile.shooters(ctx)) {
                    if (shooter == null || !shooter.isInRoom()) continue;
                    int body = shooter.getBodyRotation().getValue(), head = shooter.getHeadRotation().getValue();
                    if (projectile.changeShooterDirection()) {
                        body = head = direction; shooter.setRotation(RoomUserRotation.fromValue(direction));
                        directions.add(new WiredMovementsMessageComposer.UserDirection(shooter.getId(), body, head));
                    }
                    if (projectile.bunnyHop()) userMoves.add(new WiredMovementsMessageComposer.UserMove(
                            shooter.getId(), shooter.getX(), shooter.getY(), shooter.getZ(), shooter.getX(), shooter.getY(), shooter.getZ(),
                            false, duration, body, head, Math.max(1, Math.abs(curve == 0 ? 100 : curve))));
                }
                publishProjectileVariables(ctx, room, item, from, fromZ, to, item.getZ(), duration,
                        movement.overshootAnimationTime, curve, projectile.internalVariableMask());
            }
        }

        PreparedCarry preparedCarry = consumeCarry(item);
        if (preparedCarry == null && from.equals(to)) {
            preparedCarry = prepareCarry(ctx, room, item, from, fromZ);
        }
        if (preparedCarry != null) {
            double itemHeight = item.getBaseItem() != null && !item.getBaseItem().allowSit()
                    ? Item.getCurrentHeight(item)
                    : 0D;
            double newItemTop = item.getZ() + itemHeight;
            try {
                for (CarriedUnit carried : preparedCarry.units) {
                    RoomUnit unit = carried.unit;
                    if (unit == null || !unit.isInRoom() || unit.getCurrentLocation() == null) {
                        continue;
                    }
                    if (unit.getRoom() != room
                            || !unit.getCurrentLocation().equals(carried.from)) {
                        continue;
                    }
                    RoomTile unitTo = room.getLayout().getTile(
                            (short) (to.x + carried.relativeX),
                            (short) (to.y + carried.relativeY));
                    if (unitTo == null) {
                        continue;
                    }
                    double unitToZ = newItemTop + carried.heightOffset;
                    if (animated) {
                        userMoves.add(new WiredMovementsMessageComposer.UserMove(
                                unit.getId(),
                                carried.from.x,
                                carried.from.y,
                                carried.fromZ,
                                unitTo.x,
                                unitTo.y,
                                unitToZ,
                                false,
                                duration,
                                unit.getBodyRotation().getValue(),
                                unit.getHeadRotation().getValue(),
                                null));
                    }
                    unit.stopWalking();
                    unit.getPath().clear();
                    unit.removeStatus(RoomUnitStatus.MOVE);
                    unit.setCurrentLocationAndGoal(unitTo);
                    unit.setZ(unitToZ);
                    unit.setPreviousLocation(unitTo);
                    unit.setPreviousLocationZ(unitToZ);
                    unit.setLastRollerTime(System.currentTimeMillis());
                    unit.statusUpdate(!animated);
                }
            } finally {
                releaseCarry(preparedCarry);
            }
        }

        if (animated && (!furniMoves.isEmpty() || !userMoves.isEmpty() || !directions.isEmpty())) {
            emitOrQueue(room, furniMoves, userMoves, List.of(), directions);
        }
    }

    private static void emitOrQueue(
            Room room,
            List<WiredMovementsMessageComposer.FurniMove> furniMoves,
            List<WiredMovementsMessageComposer.UserMove> userMoves,
            List<WiredMovementsMessageComposer.WallItemMove> wallItemMoves,
            List<WiredMovementsMessageComposer.UserDirection> directions) {
        MovementBatch batch = ACTIVE_MOVEMENTS.get();
        if (batch != null && batch.room == room) {
            batch.furniMoves.addAll(furniMoves);
            batch.userMoves.addAll(userMoves);
            batch.wallItemMoves.addAll(wallItemMoves);
            batch.userDirections.addAll(directions);
            return;
        }
        sendMovements(room, furniMoves, userMoves, wallItemMoves, directions);
    }

    /**
     * Sends local 7115 only to clients that negotiated its room capability.
     * Older clients receive authoritative final-state packets so mixed rooms
     * remain usable without exposing an unknown packet header.
     */
    private static void sendMovements(
            Room room,
            List<WiredMovementsMessageComposer.FurniMove> furniMoves,
            List<WiredMovementsMessageComposer.UserMove> userMoves,
            List<WiredMovementsMessageComposer.WallItemMove> wallItemMoves,
            List<WiredMovementsMessageComposer.UserDirection> directions) {
        if (room == null || (furniMoves.isEmpty()
                && userMoves.isEmpty() && wallItemMoves.isEmpty()
                && directions.isEmpty())) {
            return;
        }

        List<Habbo> legacyRecipients = new ArrayList<>();
        ServerMessage movementPacket = null;
        for (Habbo habbo : new ArrayList<>(room.getHabbos())) {
            if (habbo == null || habbo.getClient() == null) {
                continue;
            }
            if (habbo.getClient().getWiredCapabilityState().supportsRoom(
                    WiredCapabilityService.CAPABILITY_WIRED_MOVEMENTS,
                    room.getId())) {
                if (movementPacket == null) {
                    movementPacket = new WiredMovementsMessageComposer(
                            furniMoves, userMoves, wallItemMoves,
                            directions).compose();
                }
                habbo.getClient().sendResponse(movementPacket);
            } else {
                legacyRecipients.add(habbo);
            }
        }
        if (legacyRecipients.isEmpty()) {
            return;
        }

        List<ServerMessage> fallbackPackets = new ArrayList<>();
        Set<Integer> seenItems = new LinkedHashSet<>();
        for (WiredMovementsMessageComposer.FurniMove movement : furniMoves) {
            if (!seenItems.add(movement.itemId)) {
                continue;
            }
            HabboItem item = room.getHabboItem(movement.itemId);
            if (item != null) {
                fallbackPackets.add(new ObjectUpdateMessageComposer(item).compose());
            }
        }
        for (WiredMovementsMessageComposer.WallItemMove movement : wallItemMoves) {
            if (!seenItems.add(movement.itemId())) {
                continue;
            }
            HabboItem item = room.getHabboItem(movement.itemId());
            if (item != null) {
                fallbackPackets.add(new ItemUpdateMessageComposer(item).compose());
            }
        }

        Set<Integer> seenUnits = new LinkedHashSet<>();
        Set<Integer> unsettledUnits = new HashSet<>();
        for (WiredMovementsMessageComposer.UserMove movement : userMoves) {
            RoomUnit unit = findRoomUnit(room, movement.unitId());
            if (unit == null) {
                continue;
            }
            if (isUnsettledUserMove(unit)) {
                // moveUser() sends the capability-safe final position from its
                // settlement callback. Serializing now would use the origin.
                unsettledUnits.add(movement.unitId());
            } else {
                seenUnits.add(movement.unitId());
            }
        }
        for (WiredMovementsMessageComposer.UserDirection direction : directions) {
            if (!unsettledUnits.contains(direction.unitId())) {
                seenUnits.add(direction.unitId());
            }
        }
        for (Integer unitId : seenUnits) {
            RoomUnit unit = findRoomUnit(room, unitId);
            if (unit != null) {
                fallbackPackets.add(new UserUpdateMessageComposer(unit).compose());
            }
        }
        for (Habbo habbo : legacyRecipients) {
            for (ServerMessage fallbackPacket : fallbackPackets) {
                habbo.getClient().sendResponse(fallbackPacket);
            }
        }
    }

    private static boolean isUnsettledUserMove(RoomUnit unit) {
        return unit.getCurrentLocation() != null
                && unit.getPreviousLocation() != null
                && (!unit.getCurrentLocation().equals(unit.getPreviousLocation())
                    || Double.compare(unit.getZ(),
                            unit.getPreviousLocationZ()) != 0);
    }

    private static long userMovementKey(Room room, RoomUnit unit) {
        return ((long) room.getId() << 32) ^ (unit.getId() & 0xffffffffL);
    }

    private static RoomUnit findRoomUnit(Room room, int unitId) {
        for (RoomUnit unit : room.getRoomUnits()) {
            if (unit != null && unit.getId() == unitId) {
                return unit;
            }
        }
        return null;
    }
    private static void publishProjectileVariables(WiredContext ctx, Room room, HabboItem item,
            RoomTile from, double fromZ, RoomTile to, double toZ, int duration,
            Integer overshootTime, int curveStrength, int mask) {
        if(mask==0)return;

        int totalDuration=(int)Math.max(1L,Math.min(Integer.MAX_VALUE,
                (long)duration+(overshootTime==null?0L:overshootTime.longValue())));
        double extension=overshootTime==null||duration<=0?0:overshootTime/(double)duration;
        double endX=to.x+(to.x-from.x)*extension;
        double endY=to.y+(to.y-from.y)*extension;
        // July's MovingObjectLogic extrapolates X/Y for overshoot but restores
        // the original Z delta before extending the animation interval.
        double endZ=toZ;
        double distance=Math.hypot(endX-from.x,endY-from.y);
        int samples=Math.min(256,Math.max(8,(int)Math.ceil(distance*16)));
        long key=flightKey(room,item);
        ProjectileFlight flight=new ProjectileFlight(ctx,room,item,from,fromZ,endX,endY,endZ,
                totalDuration,samples,curveStrength,mask);
        ProjectileFlight previous=PROJECTILE_FLIGHTS.put(key,flight);
        if(previous!=null)previous.cancelPending();
        flight.writeVariables();
        scheduleProjectileSample(key,flight,1);
    }

    /**
     * Keep at most one pending callback per projectile. Scheduling every future
     * interpolation sample up front allowed a fast repeater to flood the global
     * scheduler with callbacks that could remain queued for minutes.
     */
    private static void scheduleProjectileSample(long key,ProjectileFlight flight,int sample){
        if(sample>flight.samples||PROJECTILE_FLIGHTS.get(key)!=flight)return;
        int previousTime=(int)Math.round(flight.duration*(sample-1)/(double)flight.samples);
        int sampleTime=(int)Math.round(flight.duration*sample/(double)flight.samples);
        int delay=Math.max(1,sampleTime-previousTime);
        ScheduledFuture<?> pending=Emulator.getThreading().run(
                ()->updateProjectileFlight(key,flight,sample),delay);
        flight.setPending(pending);
    }

    private static void updateProjectileFlight(long key,ProjectileFlight flight,int sample){
        if(PROJECTILE_FLIGHTS.get(key)!=flight||!flight.room.isLoaded()
                ||flight.room.getHabboItemByDatabaseId(flight.item.getId())!=flight.item){
            if(PROJECTILE_FLIGHTS.remove(key,flight))flight.cancelPending();
            return;
        }

        List<ProjectileMutation> mutations;
        boolean travelling;
        synchronized(flight){
            int[] before=flight.values();
            for(int current=flight.processedSamples+1;current<=sample;current++){
                double progress=current/(double)flight.samples;
                double x=flight.fromX+(flight.endX-flight.fromX)*progress;
                double y=flight.fromY+(flight.endY-flight.fromY)*progress;
                double linearZ=flight.fromZ+(flight.endZ-flight.fromZ)*progress;
                double routeLength=Math.sqrt(
                        Math.pow(flight.endX-flight.fromX,2)
                                +Math.pow(flight.endY-flight.fromY,2)
                                +Math.pow(flight.endZ-flight.fromZ,2));
                double z=linearZ+(flight.curveStrength/100.0)*routeLength*progress*(1-progress);
                short tileX=(short)Math.round(x),tileY=(short)Math.round(y);
                if(tileX!=flight.lastTileX||tileY!=flight.lastTileY){
                    flight.tilesTravelled++;
                    flight.lastTileX=tileX;
                    flight.lastTileY=tileY;
                }
                flight.x=tileX;flight.y=tileY;flight.z=z;
                flight.detectCollisions(tileX,tileY,z);
                flight.processedSamples=current;
            }
            flight.travelling=flight.processedSamples<flight.samples;
            flight.writeVariables();
            mutations=flight.mutations(before);
            travelling=flight.travelling;
        }
        flight.publishMutations(mutations);
        if(travelling)scheduleProjectileSample(key,flight,flight.processedSamples+1);
        else flight.clearPending();
    }

    private static long flightKey(Room room,HabboItem item){
        return ((long)room.getId()<<32)^(item.getId()&0xffffffffL);
    }

    private static boolean enabled(int mask,int index){return(mask&(1<<index))!=0;}

    /**
     * Adds the last July Projectile values for this exact room item. Completed
     * flights deliberately remain readable with is_travelling=0 until another
     * projectile run replaces them or the item/room is removed.
     */
    public static void appendProjectileVariables(
            Room room, HabboItem item, Map<String, Integer> values) {
        if(room==null||item==null||values==null)return;
        ProjectileFlight flight=PROJECTILE_FLIGHTS.get(flightKey(room,item));
        if(flight==null||flight.room!=room||flight.item!=item)return;
        int[] snapshot=flight.snapshotValues();
        for(int i=0;i<PROJECTILE_VARIABLES.length;i++)
            if(enabled(flight.mask,i))values.put(PROJECTILE_VARIABLES[i],snapshot[i]);
    }

    public static void discardProjectile(Room room, HabboItem item) {
        if(room==null||item==null)return;
        ProjectileFlight flight=PROJECTILE_FLIGHTS.remove(flightKey(room,item));
        if(flight!=null)flight.cancelPending();
    }

    public static void clearProjectiles(Room room) {
        if(room==null)return;
        for(Map.Entry<Long,ProjectileFlight> entry:PROJECTILE_FLIGHTS.entrySet()){
            ProjectileFlight flight=entry.getValue();
            if(flight!=null&&flight.room==room
                    &&PROJECTILE_FLIGHTS.remove(entry.getKey(),flight))
                flight.cancelPending();
        }
    }

    public static boolean isCarryTarget(RoomUnit roomUnit) {
        Set<Integer> targets = ACTIVE_CARRY_TARGETS.get();
        return roomUnit != null && targets != null && targets.contains(roomUnit.getId());
    }

    public static Set<RoomUnit> carryTargets(
            WiredContext ctx, Room room, HabboItem item, RoomTile from) {
        WiredAddonCarryUsers carry = addon(
                ctx, WiredAddonType.CARRY_USERS, WiredAddonCarryUsers.class);
        if (carry == null || room == null || item == null || from == null) {
            return Set.of();
        }
        Set<RoomUnit> units = new HashSet<>();
        for (RoomUnit unit : new ArrayList<>(carry.users(ctx))) {
            if (unit == null || !unit.isInRoom() || unit.getRoom() != room
                    || unit.getCurrentLocation() == null) {
                continue;
            }
            RoomTile unitFrom = unit.getCurrentLocation();
            boolean eligible = carry.mode() == 1
                    ? unitFrom.equals(from)
                    : room.getTopItemAt(unitFrom.x, unitFrom.y) == item;
            if (eligible) {
                units.add(unit);
            }
        }
        return Set.copyOf(units);
    }

    private static boolean shouldCheckForUnits(
            Room room,
            HabboItem item,
            RoomTile target,
            int rotation,
            boolean requested,
            WiredAddonMovementPhysics physics) {
        if (!requested || room == null || item == null || target == null
                || physics == null || !physics.throughUsers()) {
            return requested;
        }
        for (RoomTile occupied : room.getLayout().getTilesAt(
                target,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation)) {
            for (RoomUnit unit : room.getRoomUnits(occupied)) {
                if (!bypassUnitCollision(physics, unit)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PreparedCarry prepareCarry(
            WiredContext ctx,
            Room room,
            HabboItem item,
            RoomTile from,
            double itemZ) {
        Set<RoomUnit> carryTargets = carryTargets(ctx, room, item, from);
        if (carryTargets.isEmpty()) {
            return null;
        }
        double itemHeight = item.getBaseItem() != null && !item.getBaseItem().allowSit()
                ? Item.getCurrentHeight(item)
                : 0D;
        double oldItemTop = itemZ + itemHeight;
        List<CarriedUnit> units = new ArrayList<>();
        for (RoomUnit unit : carryTargets) {
            RoomTile unitFrom = unit.getCurrentLocation();
            units.add(new CarriedUnit(
                    unit,
                    unitFrom,
                    unit.getZ(),
                    unitFrom.x - from.x,
                    unitFrom.y - from.y,
                    unit.getZ() - oldItemTop));
        }
        if (units.isEmpty()) {
            return null;
        }
        PreparedCarry prepared = new PreparedCarry(List.copyOf(units));
        Set<Integer> targets = ACTIVE_CARRY_TARGETS.get();
        if (targets != null) {
            for (CarriedUnit unit : prepared.units) {
                targets.add(unit.unit.getId());
            }
        }
        return prepared;
    }

    private static void rememberCarry(HabboItem item, PreparedCarry prepared) {
        if (item == null || prepared == null) {
            return;
        }
        Map<Integer, PreparedCarry> carries = ACTIVE_CARRIES.get();
        if (carries != null) {
            PreparedCarry previous = carries.remove(item.getId());
            if (previous != null) {
                releaseCarry(previous);
            }
            carries.put(item.getId(), prepared);
            Set<Integer> targets = ACTIVE_CARRY_TARGETS.get();
            if (targets != null) {
                for (CarriedUnit unit : prepared.units) {
                    targets.add(unit.unit.getId());
                }
            }
        }
    }

    private static PreparedCarry consumeCarry(HabboItem item) {
        Map<Integer, PreparedCarry> carries = ACTIVE_CARRIES.get();
        return item == null || carries == null ? null : carries.remove(item.getId());
    }

    private static void releaseCarry(PreparedCarry prepared) {
        Set<Integer> targets = ACTIVE_CARRY_TARGETS.get();
        if (prepared == null || targets == null) {
            return;
        }
        for (CarriedUnit unit : prepared.units) {
            targets.remove(unit.unit.getId());
        }
    }

    private static PhysicsPolicy policy(WiredAddonMovementPhysics physics) {
        PhysicsPolicy policy = ACTIVE_PHYSICS_POLICY.get();
        return policy != null && policy.addon == physics ? policy : null;
    }

    private static final class PhysicsPolicy {
        private final WiredAddonMovementPhysics addon;
        private final Set<HabboItem> throughFurniture;
        private final Set<HabboItem> blockingFurniture;
        private final Set<RoomUnit> throughUsers;
        private final boolean throughAllFurniture;
        private final boolean blocksAllFurniture;
        private final boolean throughAllUsers;

        private PhysicsPolicy(WiredAddonMovementPhysics addon, WiredContext context) {
            this.addon = addon;
            this.throughFurniture = addon.throughFurni()
                    ? new HashSet<>(addon.throughFurniture(context))
                    : Set.of();
            this.blockingFurniture = addon.blockByFurni()
                    ? new HashSet<>(addon.blockingFurniture(context))
                    : Set.of();
            this.throughUsers = addon.throughUsers()
                    ? new HashSet<>(addon.throughUsers(context))
                    : Set.of();
            this.throughAllFurniture = addon.throughFurni() && addon.throughAllFurniture();
            this.blocksAllFurniture = addon.blockByFurni() && addon.blocksAllFurniture();
            this.throughAllUsers = addon.throughUsers() && addon.throughAllUsers();
        }
    }

    private static final class MovementBatch {
        private final Room room;
        private final List<WiredMovementsMessageComposer.FurniMove> furniMoves = new ArrayList<>();
        private final List<WiredMovementsMessageComposer.UserMove> userMoves = new ArrayList<>();
        private final List<WiredMovementsMessageComposer.WallItemMove> wallItemMoves =
                new ArrayList<>();
        private final List<WiredMovementsMessageComposer.UserDirection> userDirections = new ArrayList<>();

        private MovementBatch(Room room) {
            this.room = room;
        }

        private void flush() {
            if (this.furniMoves.isEmpty()
                    && this.userMoves.isEmpty()
                    && this.wallItemMoves.isEmpty()
                    && this.userDirections.isEmpty()) {
                return;
            }
            sendMovements(this.room, this.furniMoves, this.userMoves,
                    this.wallItemMoves, this.userDirections);
        }
    }

    private record PreparedCarry(List<CarriedUnit> units) { }

    private record CarriedUnit(
            RoomUnit unit,
            RoomTile from,
            double fromZ,
            int relativeX,
            int relativeY,
            double heightOffset) { }

    private static final class ActiveUserMove {
        private final Room room;
        private final RoomUnit unit;
        private final RoomTile target;
        private final boolean walkFlavoured;
        private RoomTile continuationGoal;
        private boolean continueWalking;

        private ActiveUserMove(
                Room room,
                RoomUnit unit,
                RoomTile target,
                RoomTile continuationGoal,
                boolean continueWalking,
                boolean walkFlavoured) {
            this.room = room;
            this.unit = unit;
            this.target = target;
            this.continuationGoal = continuationGoal;
            this.continueWalking = continueWalking;
            this.walkFlavoured = walkFlavoured;
        }

        private synchronized void queueContinuation(RoomTile goal) {
            this.continuationGoal = goal;
            this.continueWalking = true;
        }

        private synchronized RoomTile continuationGoal() {
            return this.continuationGoal;
        }

        private synchronized boolean shouldContinue() {
            return this.continueWalking;
        }
    }

    private static final class ProjectileFlight{
        private static final double USER_COLLISION_HEIGHT=2.0;
        private final WiredContext context;
        private final Room room;
        private final HabboItem item;
        private final short originX,originY,destinationX,destinationY;
        private final double fromX,fromY,fromZ,endX,endY,endZ;
        private final int duration,samples,curveStrength,mask;
        private final Set<Integer> userContacts=new HashSet<>();
        private final Set<Integer> furniContacts=new HashSet<>();
        private int processedSamples,tilesTravelled,userCollisions,furniCollisions;
        private short lastTileX,x,y;
        private short lastTileY;
        private double z;
        private boolean travelling=true;
        private ScheduledFuture<?> pending;

        private ProjectileFlight(WiredContext context,Room room,HabboItem item,RoomTile from,double fromZ,
                double endX,double endY,double endZ,int duration,int samples,int curveStrength,int mask){
            this.context=context;this.room=room;this.item=item;
            this.originX=from.x;this.originY=from.y;
            this.destinationX=(short)Math.round(endX);this.destinationY=(short)Math.round(endY);
            this.fromX=from.x;this.fromY=from.y;this.fromZ=fromZ;
            this.endX=endX;this.endY=endY;this.endZ=endZ;
            this.duration=duration;this.samples=samples;this.curveStrength=curveStrength;this.mask=mask;
            this.lastTileX=this.x=from.x;this.lastTileY=this.y=from.y;this.z=fromZ;
        }

        private synchronized void setPending(ScheduledFuture<?> next){
            if(PROJECTILE_FLIGHTS.get(flightKey(room,item))!=this){
                if(next!=null)next.cancel(false);
                return;
            }
            this.pending=next;
        }

        private synchronized void cancelPending(){
            if(this.pending!=null)this.pending.cancel(false);
            this.pending=null;
        }

        private synchronized void clearPending(){this.pending=null;}

        private void detectCollisions(short tileX,short tileY,double altitude){
            if((tileX==originX&&tileY==originY)||(tileX==destinationX&&tileY==destinationY)){
                userContacts.clear();furniContacts.clear();return;
            }
            RoomTile tile=room.getLayout()==null?null:room.getLayout().getTile(tileX,tileY);
            if(tile==null){userContacts.clear();furniContacts.clear();return;}

            if(enabled(mask,1)){
                Set<Integer> contacts=new HashSet<>();
                for(RoomUnit unit:room.getRoomUnitsAt(tile)){
                    if(unit!=null&&altitude>=unit.getZ()&&altitude<unit.getZ()+USER_COLLISION_HEIGHT)
                        contacts.add(unit.getId());
                }
                for(Integer id:contacts)if(!userContacts.contains(id))userCollisions++;
                userContacts.clear();userContacts.addAll(contacts);
            }
            if(enabled(mask,2)){
                Set<Integer> contacts=new HashSet<>();
                for(HabboItem other:room.getItemsAt(tile)){
                    if(other==null||other==item||other.getBaseItem()==null)continue;
                    if(altitude>=other.getZ()&&altitude<other.getZ()+Item.getCurrentHeight(other))
                        contacts.add(other.getId());
                }
                for(Integer id:contacts)if(!furniContacts.contains(id))furniCollisions++;
                furniContacts.clear();furniContacts.addAll(contacts);
            }
        }

        private void writeVariables(){
            int[] values=values();
            WiredContextVariableStore store=context.contextVariables();
            synchronized(store){
                for(int i=0;i<PROJECTILE_VARIABLES.length;i++)
                    if(enabled(mask,i))store.set(PROJECTILE_VARIABLES[i],values[i]);
            }
        }

        private int[] values(){
            return new int[]{tilesTravelled,userCollisions,furniCollisions,x,y,
                    (int)Math.round(z*100),travelling?1:0};
        }

        private synchronized int[] snapshotValues(){return values();}

        private List<ProjectileMutation> mutations(int[] before){
            int[] after=values();
            List<ProjectileMutation> result=new ArrayList<>(4);
            for(int i:new int[]{0,1,2,6})
                if(enabled(mask,i)&&before[i]!=after[i])
                    result.add(new ProjectileMutation(i,before[i],after[i]));
            return List.copyOf(result);
        }

        private void publishMutations(List<ProjectileMutation> mutations){
            if(mutations==null||mutations.isEmpty()
                    ||room.getHabboItemByDatabaseId(item.getId())!=item)return;
            RoomUnit actor=context.actor().orElse(null);
            WiredVariableHolder holder=WiredVariableHolder.furni(item.getId());
            for(ProjectileMutation mutation:mutations){
                // A Variable Changed stack can launch the same projectile again.
                // Stop publishing the superseded flight's remaining mutations.
                if(PROJECTILE_FLIGHTS.get(flightKey(room,item))!=this)return;
                WiredManager.triggerVariableChanged(room,actor,new WiredVariableMutation(
                        WiredInternalVariableRuntime.internalVariableId(
                                WiredInternalVariableRuntime.TARGET_FURNI,
                                PROJECTILE_VARIABLES[mutation.index]),
                        holder,mutation.before,mutation.after,
                        WiredVariableMutation.Kind.VALUE_CHANGED,
                        PROJECTILE_REVISION.incrementAndGet(),
                        item.getId(),
                        WiredVariableMutation.CHANGE_ORIGIN_IN_ROOM));
            }
        }
    }

    private record ProjectileMutation(int index,int before,int after){}
    private static <T> T addon(WiredContext ctx, WiredAddonType type, Class<T> expected) { if (ctx == null || ctx.stack() == null) return null; InteractionWiredAddon addon = ctx.stack().addon(type); return expected.isInstance(addon) ? expected.cast(addon) : null; }
}
