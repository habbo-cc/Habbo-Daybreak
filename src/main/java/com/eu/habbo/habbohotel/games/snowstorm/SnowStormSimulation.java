package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * Single-owner, deterministic r26 SnowStorm simulation.
 *
 * <p>Callers enqueue immutable intentions and advance this owner once per 300 ms turn. No state
 * transition uses wall-clock time or an independent scheduler.</p>
 */
public final class SnowStormSimulation {
    private static final long AVATAR_COLLISION_DISTANCE_SQUARED = 2_000L * 2_000L;
    private static final int FALL_POSE_NONE = 0;
    private static final int FALL_POSE_FACE_DOWN = 1;
    private static final int FALL_POSE_FACE_UP = 2;
    public static final int MAX_LIVE_SNOWBALLS = 320;

    private final SnowStormMapDefinition map;
    private final int teamCount;
    private final TreeMap<Integer, SimObject> objects = new TreeMap<>();
    private final ArrayDeque<QueuedIntention> intentions = new ArrayDeque<>();
    private final Set<Integer> queuedSystemRemovals = new java.util.TreeSet<>();
    private final Map<Integer, Integer> teamByUser = new LinkedHashMap<>();
    private final Map<Integer, Integer> departedScoresByUser = new LinkedHashMap<>();
    private int nextObjectId = 1;
    private int turnNumber;
    private long absoluteSubturn;

    public SnowStormSimulation(
            SnowStormMapDefinition map,
            int teamCount,
            Collection<PlayerSeed> players) {
        if (map == null || teamCount < 1 || teamCount > 4 || players.isEmpty()
                || players.size() > 12) {
            throw new IllegalArgumentException("Invalid SnowStorm simulation roster");
        }
        this.map = map;
        this.teamCount = teamCount;
        for (PlayerSeed player : players) {
            if (player.teamId() < 0 || player.teamId() >= teamCount) {
                throw new IllegalArgumentException("SnowStorm player has an invalid team");
            }
            SnowStormTile tile = tileAt(player.worldX(), player.worldY()).orElseThrow(
                    () -> new IllegalArgumentException("SnowStorm player spawn is outside the map"));
            if (!map.isWalkable(tile)) {
                throw new IllegalArgumentException("SnowStorm player spawn is blocked");
            }
            if (teamByUser.putIfAbsent(player.userId(), player.teamId()) != null) {
                throw new IllegalArgumentException("SnowStorm player roster contains a duplicate user");
            }
            Avatar avatar = new Avatar(nextObjectId++, player);
            objects.put(avatar.objectId, avatar);
        }
        for (SnowStormMachineDefinition definition : map.machines()) {
            Machine machine = new Machine(nextObjectId++, definition);
            objects.put(machine.objectId, machine);
        }
    }

    public void submit(int actorObjectId, SnowStormIntention intention) {
        if (intention == null) {
            throw new IllegalArgumentException("SnowStorm intention is required");
        }
        if (intention instanceof SnowStormIntention.Walk) {
            intentions.removeIf(queued -> queued.actorObjectId() == actorObjectId
                    && queued.intention() instanceof SnowStormIntention.Walk);
        }
        intentions.addLast(new QueuedIntention(actorObjectId, intention));
    }

    public boolean canSubmit(int actorObjectId, SnowStormIntention intention) {
        Avatar actor = avatar(actorObjectId);
        if (actor == null || intention == null) return false;
        return switch (intention) {
            case SnowStormIntention.Walk ignored -> actor.activity.activity().canWalk();
            case SnowStormIntention.ThrowLocation ignored -> actor.activity.activity().canThrow()
                    && actor.activity.ammo() > 0 && snowballs().size() < MAX_LIVE_SNOWBALLS
                    && SnowStormAvatarActivity.acceptsThrowAt(
                    absoluteSubturn, actor.lastAcceptedThrowSubturn);
            case SnowStormIntention.ThrowAvatar targetThrow -> {
                Avatar target = avatar(targetThrow.targetObjectId());
                yield target != null && target.objectId != actor.objectId
                        && (teamCount == 1 || target.teamId != actor.teamId)
                        && target.activity.activity().avatarCollision()
                        && actor.activity.activity().canThrow() && actor.activity.ammo() > 0
                        && snowballs().size() < MAX_LIVE_SNOWBALLS
                        && SnowStormAvatarActivity.acceptsThrowAt(
                        absoluteSubturn, actor.lastAcceptedThrowSubturn);
            }
            case SnowStormIntention.MakeSnowball ignored -> actor.activity.activity().canMake()
                    && actor.activity.ammo() < SnowStormAvatarActivity.MAX_AMMO;
            case SnowStormIntention.CancelMakeSnowball ignored ->
                    actor.activity.activity() == SnowStormActivityState.CREATING;
        };
    }

    /** Queues a membership departure at the next checksum-safe subturn boundary. */
    public boolean removeUser(int userId) {
        Avatar avatar = avatars().stream().filter(candidate -> candidate.userId == userId)
                .findFirst().orElse(null);
        if (avatar == null) {
            return false;
        }
        intentions.removeIf(queued -> queued.actorObjectId() == avatar.objectId);
        return queuedSystemRemovals.add(avatar.objectId);
    }

    public TurnResult advanceTurn() {
        int checksum = checksum();
        List<List<SnowStormSimulationEvent>> subturns = new ArrayList<>(SnowStormMath.SUBTURNS_PER_TURN);
        for (int subturn = 0; subturn < SnowStormMath.SUBTURNS_PER_TURN; subturn++) {
            List<SnowStormSimulationEvent> events = new ArrayList<>();
            if (subturn == 0) {
                drainSystemRemovals(events);
                drainIntentions(events);
            }
            advanceSubturn(events);
            subturns.add(List.copyOf(events));
            absoluteSubturn++;
        }
        TurnResult result = new TurnResult(turnNumber, checksum, subturns, teamScores(), snapshots());
        turnNumber++;
        return result;
    }

    public int checksum() {
        return SnowStormMath.checksum(turnNumber, objects.values().stream()
                .map(SimObject::checksumObject)
                .toList());
    }

    public int turnNumber() {
        return turnNumber;
    }

    public Map<Integer, Integer> teamScoresSnapshot() {
        return teamScores();
    }

    /** Immutable roster score projection used when the live simulation is finalized. */
    public Map<Integer, Integer> playerScoresSnapshot() {
        Map<Integer, Integer> scores = new LinkedHashMap<>(departedScoresByUser);
        for (Avatar avatar : avatars()) {
            scores.put(avatar.userId, avatar.score);
        }
        return Map.copyOf(scores);
    }

    public List<ObjectSnapshot> snapshots() {
        long elapsedMs = absoluteSubturn * SnowStormMath.SUBTURN_MS;
        return objects.values().stream().map(object -> object.snapshot(elapsedMs)).toList();
    }

    /** Typed, immutable state used by server-side game actors without decoding wire/checksum fields. */
    public List<AvatarView> avatarViews() {
        return avatars().stream().map(avatar -> new AvatarView(
                avatar.objectId, avatar.userId, avatar.teamId, avatar.bot,
                avatar.worldX, avatar.worldY, avatar.activity.activity(),
                avatar.activity.health(), avatar.activity.ammo())).toList();
    }

    private void drainIntentions(List<SnowStormSimulationEvent> events) {
        while (!intentions.isEmpty()) {
            QueuedIntention queued = intentions.removeFirst();
            Avatar actor = avatar(queued.actorObjectId());
            if (actor == null) {
                continue;
            }
            switch (queued.intention()) {
                case SnowStormIntention.Walk walk -> acceptWalk(actor, walk, events);
                case SnowStormIntention.ThrowLocation throwLocation -> acceptThrow(
                        actor, throwLocation.trajectory(), throwLocation.targetWorldX(),
                        throwLocation.targetWorldY(), events);
                case SnowStormIntention.ThrowAvatar throwAvatar -> {
                    Avatar target = avatar(throwAvatar.targetObjectId());
                    if (target != null && target.objectId != actor.objectId
                            && (teamCount == 1 || target.teamId != actor.teamId)) {
                        acceptThrow(actor, throwAvatar.trajectory(), target.worldX, target.worldY, events);
                    }
                }
                case SnowStormIntention.MakeSnowball ignored -> acceptMake(actor, events);
                case SnowStormIntention.CancelMakeSnowball ignored -> cancelMake(actor, events);
            }
        }
    }

    private void drainSystemRemovals(List<SnowStormSimulationEvent> events) {
        for (int objectId : queuedSystemRemovals) {
            SimObject removed = objects.remove(objectId);
            if (removed instanceof Avatar avatar) {
                departedScoresByUser.put(avatar.userId, avatar.score);
            }
            if (removed != null) {
                events.add(new SnowStormSimulationEvent.RemoveObject(objectId));
            }
        }
        queuedSystemRemovals.clear();
    }

    private void acceptWalk(
            Avatar avatar,
            SnowStormIntention.Walk walk,
            List<SnowStormSimulationEvent> events) {
        if (!avatar.activity.activity().canWalk() || tileAt(walk.targetWorldX(), walk.targetWorldY())
                .filter(map::isWalkable).isEmpty()) {
            return;
        }
        SnowStormAvatarActivity.State cancelled =
                SnowStormAvatarActivity.cancelCreatingForWalk(avatar.activity);
        if (!cancelled.equals(avatar.activity)) {
            avatar.activity = cancelled;
            events.add(activityEvent(avatar));
        }
        avatar.moveTargetWorldX = walk.targetWorldX();
        avatar.moveTargetWorldY = walk.targetWorldY();
        events.add(movementEvent(avatar));
    }

    private void acceptMake(Avatar avatar, List<SnowStormSimulationEvent> events) {
        SnowStormAvatarActivity.State next = SnowStormAvatarActivity.beginCreating(avatar.activity);
        if (!next.equals(avatar.activity)) {
            // r26 start_create calls stopMovement before holding the pck pose. stopMovement snaps
            // an in-flight avatar to its already-reserved next tile; it does not leave it midway
            // between tiles. A later walk action is still allowed and explicitly cancels creation.
            stopMovementLikeShockwave(avatar);
            events.add(movementEvent(avatar));
            avatar.activity = next;
            events.add(activityEvent(avatar));
        }
    }

    private void cancelMake(Avatar avatar, List<SnowStormSimulationEvent> events) {
        SnowStormAvatarActivity.State next = SnowStormAvatarActivity.cancelCreatingForWalk(avatar.activity);
        if (!next.equals(avatar.activity)) {
            avatar.activity = next;
            events.add(activityEvent(avatar));
        }
    }

    private void acceptThrow(
            Avatar avatar,
            SnowStormTrajectory trajectory,
            int targetWorldX,
            int targetWorldY,
            List<SnowStormSimulationEvent> events) {
        if (!avatar.activity.activity().canThrow() || avatar.activity.ammo() == 0
                || snowballs().size() >= MAX_LIVE_SNOWBALLS
                || !SnowStormAvatarActivity.acceptsThrowAt(
                absoluteSubturn, avatar.lastAcceptedThrowSubturn)) {
            return;
        }
        // Shockwave handles event 3/4 by stopping the walk first, then resolving the facing and
        // event 8 snowball launch from that snapped location.
        stopMovementLikeShockwave(avatar);
        SnowStormProjectileMath.State projectile = SnowStormProjectileMath.launch(
                trajectory, avatar.worldX, avatar.worldY, targetWorldX, targetWorldY);
        avatar.bodyDirection = SnowStormMath.direction360To8(projectile.direction());
        // Publish the settled location and facing selected by startThrowAnimation before tr1.
        events.add(movementEvent(avatar));
        avatar.activity = new SnowStormAvatarActivity.State(
                avatar.activity.activity(), avatar.activity.timer(), avatar.activity.health(),
                avatar.activity.ammo() - 1);
        avatar.lastAcceptedThrowSubturn = absoluteSubturn;
        Snowball snowball = new Snowball(nextObjectId++, avatar.objectId, projectile);
        objects.put(snowball.objectId, snowball);
        events.add(new SnowStormSimulationEvent.AvatarThrow(
                avatar.objectId, trajectory.id(), targetWorldX, targetWorldY));
        events.add(new SnowStormSimulationEvent.SnowballCount(
                avatar.objectId, avatar.activity.ammo()));
        events.add(new SnowStormSimulationEvent.LaunchSnowball(
                snowball.objectId, avatar.objectId, projectile.worldX(), projectile.worldY(),
                projectile.worldZ(), projectile.direction(), trajectory.id(), projectile.ttl(),
                projectile.parabolaOffset()));
    }

    /** Mirrors r26 Avatar Gameobject Class.stopMovement. */
    private void stopMovementLikeShockwave(Avatar avatar) {
        if (avatar.nextGoal != null) {
            avatar.worldX = map.toWorldX(avatar.nextGoal.x());
            avatar.worldY = map.toWorldY(avatar.nextGoal.y());
        }
        avatar.nextGoal = null;
        avatar.moveTargetWorldX = avatar.worldX;
        avatar.moveTargetWorldY = avatar.worldY;
    }

    private void advanceSubturn(List<SnowStormSimulationEvent> events) {
        Set<Integer> removed = new HashSet<>();
        Set<SnowStormTile> reservations = reservedAvatarTiles();
        List<SimObject> pass = new ArrayList<>(objects.values());
        for (SimObject object : pass) {
            if (object instanceof Avatar avatar) {
                advanceAvatar(avatar, reservations, removed, events);
            } else if (object instanceof Machine machine) {
                advanceMachine(machine, events);
            } else if (object instanceof Snowball snowball && !removed.contains(snowball.objectId)) {
                advanceSnowball(snowball, removed, events);
            }
        }
        for (int objectId : removed) {
            objects.remove(objectId);
        }
    }

    private void advanceAvatar(
            Avatar avatar,
            Set<SnowStormTile> reservations,
            Set<Integer> removed,
            List<SnowStormSimulationEvent> events) {
        SnowStormAvatarActivity.State before = avatar.activity;
        avatar.activity = SnowStormAvatarActivity.tick(avatar.activity);
        if (before.activity() == SnowStormActivityState.STUNNED
                && avatar.activity.activity() == SnowStormActivityState.INVINCIBLE_AFTER_STUN) {
            // Shockwave's start_invincible resets the prone room object to the exact direction it
            // had before start_stunned selected a presentation-only death direction.
            avatar.bodyDirection = avatar.directionBeforeStun;
            avatar.fallPose = FALL_POSE_NONE;
            events.add(movementEvent(avatar));
        }
        if (!avatar.activity.equals(before)) {
            if (avatar.activity.activity() != before.activity()
                    || avatar.activity.timer() != before.timer() - 1) {
                events.add(activityEvent(avatar));
            }
            if (avatar.activity.ammo() != before.ammo()) {
                events.add(new SnowStormSimulationEvent.SnowballCount(
                        avatar.objectId, avatar.activity.ammo()));
            }
        }
        advanceAvatarMovement(avatar, reservations, events);
        if (!avatar.activity.activity().avatarCollision() || avatar.activity.health() == 0) {
            return;
        }
        for (Snowball snowball : snowballs()) {
            if (removed.contains(snowball.objectId) || snowball.throwerObjectId == avatar.objectId
                    || snowball.state.worldZ() >= SnowStormProjectileMath.AVATAR_HEIGHT) {
                continue;
            }
            long dx = (long) avatar.worldX - snowball.state.worldX();
            long dy = (long) avatar.worldY - snowball.state.worldY();
            if (dx * dx + dy * dy >= AVATAR_COLLISION_DISTANCE_SQUARED) {
                continue;
            }
            markRemoved(snowball.objectId, removed, events);
            Avatar thrower = avatar(snowball.throwerObjectId);
            boolean sameTeam = thrower != null && thrower.teamId == avatar.teamId;
            SnowStormCombat.Resolution resolution = SnowStormCombat.resolve(
                    teamCount, sameTeam, avatar.activity.health());
            if (resolution.damage() == 0) {
                continue;
            }
            avatar.activity = new SnowStormAvatarActivity.State(
                    avatar.activity.activity(), avatar.activity.timer(),
                    resolution.pendingHealth(), avatar.activity.ammo());
            // Shockwave derives the hit direction from the impact point back toward the avatar,
            // not from the projectile's forward travel heading. This controls the fall orientation.
            int hitDirection = SnowStormMath.direction360To8(SnowStormMath.componentAngle(
                    SnowStormMath.directorDiv(snowball.state.worldX() - avatar.worldX, 200),
                    SnowStormMath.directorDiv(snowball.state.worldY() - avatar.worldY, 200)));
            events.add(new SnowStormSimulationEvent.Hit(
                    snowball.throwerObjectId, avatar.objectId,
                    snowball.state.worldX(), snowball.state.worldY(), snowball.state.worldZ(),
                    hitDirection,
                    resolution.pendingHealth()));
            if (thrower != null && resolution.hitScore() != 0) {
                thrower.score += resolution.hitScore();
                events.add(scoreEvent(thrower));
            }
            if (resolution.knockout()) {
                // r26 start_stunned calls stopMovement before choosing the two-frame fall pose.
                // This snaps an in-flight avatar to its reserved tile and discards the old route,
                // so neither movement nor facing can change while the room object is tagged dead.
                stopMovementLikeShockwave(avatar);
                avatar.directionBeforeStun = avatar.bodyDirection;
                // Publish Shockwave's stopMovement snap while the avatar still has its standing
                // direction. The following STUN event applies the presentation-only death facing.
                events.add(movementEvent(avatar));
                int ballDirection = hitDirection - hitDirection % 2;
                int currentDirection = avatar.bodyDirection - avatar.bodyDirection % 2;
                boolean faceUp = ballDirection != currentDirection
                        && ballDirection % 4 == currentDirection % 4;
                avatar.bodyDirection = faceUp ? currentDirection : ballDirection;
                avatar.fallPose = faceUp ? FALL_POSE_FACE_UP : FALL_POSE_FACE_DOWN;
                avatar.activity = SnowStormAvatarActivity.knockOut(avatar.activity);
                events.add(new SnowStormSimulationEvent.Stun(
                        snowball.throwerObjectId, avatar.objectId,
                        avatar.bodyDirection, avatar.fallPose));
                events.add(activityEvent(avatar));
                events.add(new SnowStormSimulationEvent.SnowballCount(avatar.objectId, 0));
                if (thrower != null && resolution.knockoutScore() != 0) {
                    thrower.score += resolution.knockoutScore();
                    events.add(scoreEvent(thrower));
                }
            }
        }
    }

    private void advanceAvatarMovement(
            Avatar avatar,
            Set<SnowStormTile> reservations,
            List<SnowStormSimulationEvent> events) {
        if (!avatar.activity.activity().canWalk()) {
            return;
        }
        SnowStormTile occupiedBefore = tileAt(avatar.worldX, avatar.worldY).orElse(null);
        if (occupiedBefore != null) reservations.remove(occupiedBefore);
        if (avatar.nextGoal != null) reservations.remove(avatar.nextGoal);
        if (avatar.nextGoal != null) {
            avatar.worldX = SnowStormMath.stepAxis(avatar.worldX, map.toWorldX(avatar.nextGoal.x()));
            avatar.worldY = SnowStormMath.stepAxis(avatar.worldY, map.toWorldY(avatar.nextGoal.y()));
            if (avatar.worldX == map.toWorldX(avatar.nextGoal.x())
                    && avatar.worldY == map.toWorldY(avatar.nextGoal.y())) {
                avatar.nextGoal = null;
            }
            // Shockwave performs one 640-unit movement step per subturn. Reaching an intermediate
            // tile must not also select and advance toward the following tile in this same pass.
            reserveAvatar(avatar, reservations);
            return;
        }
        if (avatar.worldX == avatar.moveTargetWorldX
                && avatar.worldY == avatar.moveTargetWorldY) {
            reserveAvatar(avatar, reservations);
            return;
        }
        SnowStormTile current = tileAt(avatar.worldX, avatar.worldY).orElse(null);
        if (current == null) {
            return;
        }
        Optional<SnowStormMovementPlanner.NextTile> selected =
                SnowStormMovementPlanner.chooseNextTile(map, current,
                        avatar.moveTargetWorldX, avatar.moveTargetWorldY, reservations);
        if (selected.isEmpty()) {
            reservations.add(current);
            return;
        }
        SnowStormMovementPlanner.NextTile next = selected.get();
        avatar.nextGoal = next.tile();
        avatar.bodyDirection = next.direction();
        events.add(movementEvent(avatar));
        avatar.worldX = SnowStormMath.stepAxis(avatar.worldX, next.worldX());
        avatar.worldY = SnowStormMath.stepAxis(avatar.worldY, next.worldY());
        if (avatar.worldX == next.worldX() && avatar.worldY == next.worldY()) {
            avatar.nextGoal = null;
        }
        reserveAvatar(avatar, reservations);
    }

    private SnowStormSimulationEvent movementEvent(Avatar avatar) {
        SnowStormTile current = tileAt(avatar.worldX, avatar.worldY).orElse(new SnowStormTile(0, 0));
        SnowStormTile goal = avatar.nextGoal == null ? current : avatar.nextGoal;
        return new SnowStormSimulationEvent.AvatarMove(
                avatar.objectId, avatar.worldX, avatar.worldY,
                avatar.moveTargetWorldX, avatar.moveTargetWorldY,
                goal.x(), goal.y(), avatar.bodyDirection);
    }

    private void reserveAvatar(Avatar avatar, Set<SnowStormTile> reservations) {
        tileAt(avatar.worldX, avatar.worldY).ifPresent(reservations::add);
        if (avatar.nextGoal != null) reservations.add(avatar.nextGoal);
    }

    private void advanceMachine(Machine machine, List<SnowStormSimulationEvent> events) {
        Set<Integer> eligible = new HashSet<>();
        Map<Integer, Integer> ammo = new LinkedHashMap<>();
        for (Avatar avatar : avatars()) {
            ammo.put(avatar.objectId, avatar.activity.ammo());
            if (avatar.activity.activity().canWalk()
                    && tileAt(avatar.worldX, avatar.worldY)
                    .filter(machine.definition.collectionTile()::equals).isPresent()) {
                eligible.add(avatar.objectId);
            }
        }
        SnowStormMachineSimulation.Result result = SnowStormMachineSimulation.advance(
                machine.state, absoluteSubturn * SnowStormMath.SUBTURN_MS, eligible, ammo,
                machine.definition.maximumStock(), machine.definition.refillIntervalMs(),
                machine.definition.transferIntervalMs());
        machine.state = result.state();
        machine.collecting = !eligible.isEmpty();
        for (SnowStormMachineSimulation.Event event : result.events()) {
            if (event.type() == SnowStormMachineSimulation.EventType.RESTOCK) {
                events.add(new SnowStormSimulationEvent.MachineRestock(
                        machine.objectId, event.machineCount()));
            } else {
                Avatar avatar = avatar(event.playerObjectId());
                if (avatar != null) {
                    avatar.activity = new SnowStormAvatarActivity.State(
                            avatar.activity.activity(), avatar.activity.timer(), avatar.activity.health(),
                            event.playerCount());
                }
                events.add(new SnowStormSimulationEvent.MachineTransfer(
                        machine.objectId, event.playerObjectId(), event.machineCount(),
                        event.playerCount()));
            }
        }
    }

    private void advanceSnowball(
            Snowball snowball,
            Set<Integer> removed,
            List<SnowStormSimulationEvent> events) {
        SnowStormProjectileMath.Projection projection = SnowStormProjectileMath.project(snowball.state);
        Optional<SnowStormTile> tile = tileAt(projection.worldX(), projection.worldY());
        if (tile.isEmpty() || !map.isFloor(tile.get())) {
            events.add(new SnowStormSimulationEvent.SnowballMiss(snowball.objectId));
            markRemoved(snowball.objectId, removed, events);
            return;
        }
        snowball.state = SnowStormProjectileMath.resolve(
                snowball.state, projection, map.projectileCollisionHeight(tile.get())).state();
        // Shockwave's time_to_live is the signed input to the projectile parabola, not an
        // expiration timer. It deliberately continues through zero into negative values until
        // the calculated height reaches the floor (or an occupied map object intercepts it).
        if (snowball.state.removed()) {
            events.add(new SnowStormSimulationEvent.SnowballMiss(snowball.objectId));
            markRemoved(snowball.objectId, removed, events);
        }
    }

    private Set<SnowStormTile> reservedAvatarTiles() {
        Set<SnowStormTile> reserved = new HashSet<>();
        for (Avatar avatar : avatars()) {
            tileAt(avatar.worldX, avatar.worldY).ifPresent(reserved::add);
            if (avatar.nextGoal != null) {
                reserved.add(avatar.nextGoal);
            }
        }
        return reserved;
    }

    private void markRemoved(
            int objectId,
            Set<Integer> removed,
            List<SnowStormSimulationEvent> events) {
        if (removed.add(objectId)) {
            events.add(new SnowStormSimulationEvent.RemoveObject(objectId));
        }
    }

    private Optional<SnowStormTile> tileAt(int worldX, int worldY) {
        return map.toTile(worldX, worldY);
    }

    private Avatar avatar(int objectId) {
        SimObject object = objects.get(objectId);
        return object instanceof Avatar avatar ? avatar : null;
    }

    private List<Avatar> avatars() {
        return objects.values().stream()
                .filter(Avatar.class::isInstance).map(Avatar.class::cast).toList();
    }

    private List<Snowball> snowballs() {
        return objects.values().stream()
                .filter(Snowball.class::isInstance).map(Snowball.class::cast).toList();
    }

    private Map<Integer, Integer> teamScores() {
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        for (int teamId = 0; teamId < teamCount; teamId++) {
            scores.put(teamId, 0);
        }
        for (Map.Entry<Integer, Integer> entry : playerScoresSnapshot().entrySet()) {
            Integer teamId = teamByUser.get(entry.getKey());
            if (teamId != null) {
                scores.computeIfPresent(teamId, (ignored, score) -> score + entry.getValue());
            }
        }
        return Map.copyOf(scores);
    }

    private SnowStormSimulationEvent activityEvent(Avatar avatar) {
        return new SnowStormSimulationEvent.ActivityState(
                avatar.objectId, avatar.activity.activity().id(), avatar.activity.timer(),
                avatar.activity.health());
    }

    private SnowStormSimulationEvent scoreEvent(Avatar avatar) {
        return new SnowStormSimulationEvent.PlayerScore(avatar.objectId, avatar.score);
    }

    public record PlayerSeed(
            int userId,
            int teamId,
            int worldX,
            int worldY,
            int bodyDirection,
            boolean bot,
            int initialSnowballs) {
        public PlayerSeed {
            if (userId == 0 || bot != (userId < 0) || bodyDirection < 0 || bodyDirection > 7
                    || initialSnowballs < 0
                    || initialSnowballs > SnowStormAvatarActivity.MAX_AMMO) {
                throw new IllegalArgumentException("Invalid SnowStorm player seed");
            }
        }
    }

    public record AvatarView(
            int objectId,
            int userId,
            int teamId,
            boolean bot,
            int worldX,
            int worldY,
            SnowStormActivityState activity,
            int health,
            int ammo) {
    }

    public record TurnResult(
            int turnNumber,
            int startChecksum,
            List<List<SnowStormSimulationEvent>> subturns,
            Map<Integer, Integer> teamScores,
            List<ObjectSnapshot> objects) {
        public TurnResult {
            subturns = subturns.stream().map(List::copyOf).toList();
            if (subturns.size() != SnowStormMath.SUBTURNS_PER_TURN) {
                throw new IllegalArgumentException("SnowStorm turns require exactly five subturns");
            }
            teamScores = Map.copyOf(teamScores);
            objects = List.copyOf(objects);
        }
    }

    public record ObjectSnapshot(int type, int objectId, List<Integer> values, int recoveryTimer) {
        public ObjectSnapshot {
            values = List.copyOf(values);
        }
    }

    private record QueuedIntention(int actorObjectId, SnowStormIntention intention) {
    }

    private abstract static class SimObject {
        private final int objectId;

        private SimObject(int objectId) {
            this.objectId = objectId;
        }

        abstract int type();

        abstract List<Integer> values();

        private SnowStormMath.ChecksumObject checksumObject() {
            return new SnowStormMath.ChecksumObject(type(), objectId, values());
        }

        int recoveryTimer(long elapsedMs) {
            return -1;
        }

        private ObjectSnapshot snapshot(long elapsedMs) {
            return new ObjectSnapshot(type(), objectId, values(), recoveryTimer(elapsedMs));
        }
    }

    private static final class Avatar extends SimObject {
        private final int objectId;
        private final int userId;
        private final int teamId;
        private final boolean bot;
        private int worldX;
        private int worldY;
        private int bodyDirection;
        private SnowStormAvatarActivity.State activity;
        private SnowStormTile nextGoal;
        private int moveTargetWorldX;
        private int moveTargetWorldY;
        private int score;
        private long lastAcceptedThrowSubturn = -1;
        private int directionBeforeStun;
        private int fallPose = FALL_POSE_NONE;

        private Avatar(int objectId, PlayerSeed seed) {
            super(objectId);
            this.objectId = objectId;
            this.userId = seed.userId();
            this.teamId = seed.teamId();
            this.bot = seed.bot();
            this.worldX = seed.worldX();
            this.worldY = seed.worldY();
            this.bodyDirection = seed.bodyDirection();
            this.directionBeforeStun = this.bodyDirection;
            this.activity = new SnowStormAvatarActivity.State(
                    SnowStormActivityState.NORMAL, 0, SnowStormAvatarActivity.MAX_HEALTH,
                    seed.initialSnowballs());
            this.moveTargetWorldX = worldX;
            this.moveTargetWorldY = worldY;
        }

        @Override
        int type() {
            return 0;
        }

        @Override
        List<Integer> values() {
            int goalX = nextGoal == null ? SnowStormMath.worldToTile(worldX).orElse(0) : nextGoal.x();
            int goalY = nextGoal == null ? SnowStormMath.worldToTile(worldY).orElse(0) : nextGoal.y();
            return List.of(worldX, worldY, bodyDirection, activity.health(), activity.ammo(), bot ? 1 : 0,
                    activity.timer(), activity.activity().id(), goalX, goalY,
                    moveTargetWorldX, moveTargetWorldY, score, userId, teamId, objectId,
                    directionBeforeStun, fallPose);
        }
    }

    private static final class Snowball extends SimObject {
        private final int objectId;
        private final int throwerObjectId;
        private SnowStormProjectileMath.State state;

        private Snowball(int objectId, int throwerObjectId, SnowStormProjectileMath.State state) {
            super(objectId);
            this.objectId = objectId;
            this.throwerObjectId = throwerObjectId;
            this.state = state;
        }

        @Override
        int type() {
            return 1;
        }

        @Override
        List<Integer> values() {
            return List.of(state.worldX(), state.worldY(), state.worldZ(), state.direction(),
                    state.trajectory().id(), state.ttl(), throwerObjectId, state.parabolaOffset());
        }
    }

    private static final class Machine extends SimObject {
        private final int objectId;
        private final SnowStormMachineDefinition definition;
        private SnowStormMachineSimulation.State state;
        private boolean collecting;

        private Machine(int objectId, SnowStormMachineDefinition definition) {
            super(objectId);
            this.objectId = objectId;
            this.definition = definition;
            this.state = SnowStormMachineSimulation.State.initial(definition.initialStock());
        }

        @Override
        int type() {
            return 2;
        }

        @Override
        List<Integer> values() {
            return List.of(definition.worldX(), definition.worldY(), state.stock());
        }

        @Override
        int recoveryTimer(long elapsedMs) {
            if (!collecting) return -1;
            long remaining = Math.max(0, state.nextRefillMs() - elapsedMs);
            return (int) ((remaining + SnowStormMath.SUBTURN_MS - 1) / SnowStormMath.SUBTURN_MS);
        }
    }
}
