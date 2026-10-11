package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.Emulator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic first-pass NGH bot decisions; all actions enter the normal intention pipeline. */
final class SnowStormBotStrategy {
    private static final long DEFAULT_DECISION_MS = 1_200L;
    private static final int DEFAULT_THROW_RADIUS_TILES = 10;
    private final Map<Integer, Long> lastDecisions = new HashMap<>();

    /** Called only by SnowStormGame while its monitor owns the supplied simulation. */
    void direct(SnowStormGame game, SnowStormSimulation simulation, long now) {
        long interval = configuredDecisionInterval();
        int throwRadius = configuredThrowRadius();
        long throwRadiusWorld = (long) throwRadius * SnowStormMath.WORLD_UNITS_PER_TILE;
        long throwRadiusSquared = throwRadiusWorld * throwRadiusWorld;
        List<SnowStormSimulation.AvatarView> avatars = simulation.avatarViews();

        avatars.stream().filter(SnowStormSimulation.AvatarView::bot)
                .sorted(Comparator.comparingInt(SnowStormSimulation.AvatarView::userId))
                .forEach(bot -> {
                    if (now - lastDecisions.getOrDefault(bot.userId(), 0L) < interval) return;
                    lastDecisions.put(bot.userId(), now);
                    if (bot.activity() == SnowStormActivityState.STUNNED
                            || bot.activity() == SnowStormActivityState.CREATING) return;
                    if (bot.ammo() <= 0) {
                        game.submitBotIntention(bot.userId(), new SnowStormIntention.MakeSnowball());
                        return;
                    }
                    SnowStormSimulation.AvatarView target = avatars.stream()
                            .filter(candidate -> candidate.objectId() != bot.objectId())
                            .filter(candidate -> game.teamCount() == 1 || candidate.teamId() != bot.teamId())
                            .filter(candidate -> candidate.activity().avatarCollision())
                            .min(Comparator.<SnowStormSimulation.AvatarView>comparingLong(
                                            candidate -> distanceSquared(bot, candidate))
                                    .thenComparingInt(SnowStormSimulation.AvatarView::objectId))
                            .orElse(null);
                    if (target == null) return;

                    long distanceSquared = distanceSquared(bot, target);
                    if (distanceSquared <= throwRadiusSquared) {
                        SnowStormTrajectory shot = clearTrajectory(game.map(), bot, target, distanceSquared);
                        if (shot != null && game.submitBotIntention(bot.userId(),
                                new SnowStormIntention.ThrowAvatar(shot, target.objectId()))) return;
                    }
                    SnowStormTile approach = approachTile(game.map(), bot, target, avatars);
                    if (approach != null) {
                        game.submitBotIntention(bot.userId(), new SnowStormIntention.Walk(
                                game.map().toWorldX(approach.x()), game.map().toWorldY(approach.y())));
                    }
                });
    }

    void reset() {
        lastDecisions.clear();
    }

    private static SnowStormTrajectory trajectory(long distanceSquared) {
        long quick = 4L * SnowStormMath.WORLD_UNITS_PER_TILE;
        long shortLob = 8L * SnowStormMath.WORLD_UNITS_PER_TILE;
        if (distanceSquared <= quick * quick) return SnowStormTrajectory.QUICK;
        if (distanceSquared <= shortLob * shortLob) return SnowStormTrajectory.SHORT_LOB;
        return SnowStormTrajectory.LONG_LOB;
    }

    private static SnowStormTrajectory clearTrajectory(
            SnowStormMapDefinition map,
            SnowStormSimulation.AvatarView bot,
            SnowStormSimulation.AvatarView target,
            long distanceSquared) {
        SnowStormTrajectory preferred = trajectory(distanceSquared);
        SnowStormTrajectory[] attempts = {preferred, SnowStormTrajectory.SHORT_LOB,
                SnowStormTrajectory.LONG_LOB, SnowStormTrajectory.QUICK};
        boolean[] attempted = new boolean[SnowStormTrajectory.values().length];
        for (SnowStormTrajectory attempt : attempts) {
            if (attempted[attempt.ordinal()]) continue;
            attempted[attempt.ordinal()] = true;
            if (clearShot(map, bot, target, attempt)) return attempt;
        }
        return null;
    }

    /** Probe the authoritative projectile equations against the same map-height collision data. */
    private static boolean clearShot(
            SnowStormMapDefinition map,
            SnowStormSimulation.AvatarView bot,
            SnowStormSimulation.AvatarView target,
            SnowStormTrajectory trajectory) {
        SnowStormProjectileMath.State state = SnowStormProjectileMath.launch(trajectory,
                bot.worldX(), bot.worldY(), target.worldX(), target.worldY());
        long hitRadius = SnowStormProjectileMath.AVATAR_RADIUS + SnowStormProjectileMath.SNOWBALL_RADIUS;
        long hitRadiusSquared = hitRadius * hitRadius;
        for (int step = 0; step < 200 && !state.removed(); step++) {
            if (step > 0) {
                long dx = (long) target.worldX() - state.worldX();
                long dy = (long) target.worldY() - state.worldY();
                if (dx * dx + dy * dy < hitRadiusSquared
                        && state.worldZ() < SnowStormProjectileMath.AVATAR_HEIGHT) return true;
            }
            SnowStormProjectileMath.Projection projection = SnowStormProjectileMath.project(state);
            SnowStormTile tile = map.toTile(projection.worldX(), projection.worldY()).orElse(null);
            if (tile == null || !map.isFloor(tile)) return false;
            state = SnowStormProjectileMath.resolve(
                    state, projection, map.projectileCollisionHeight(tile)).state();
        }
        return false;
    }

    private static long distanceSquared(
            SnowStormSimulation.AvatarView first,
            SnowStormSimulation.AvatarView second) {
        long dx = (long) second.worldX() - first.worldX();
        long dy = (long) second.worldY() - first.worldY();
        return dx * dx + dy * dy;
    }

    /** Choose a reachable free tile beside the opponent; the occupied target tile is never a route goal. */
    private static SnowStormTile approachTile(
            SnowStormMapDefinition map,
            SnowStormSimulation.AvatarView bot,
            SnowStormSimulation.AvatarView target,
            List<SnowStormSimulation.AvatarView> avatars) {
        SnowStormTile start = map.toTile(bot.worldX(), bot.worldY()).orElse(null);
        SnowStormTile targetTile = map.toTile(target.worldX(), target.worldY()).orElse(null);
        if (start == null || targetTile == null) return null;
        Set<SnowStormTile> occupied = new HashSet<>();
        for (SnowStormSimulation.AvatarView avatar : avatars) {
            map.toTile(avatar.worldX(), avatar.worldY()).ifPresent(occupied::add);
        }
        SnowStormMovementPlanner.SearchGrid searchGrid =
                new SnowStormMovementPlanner.SearchGrid(map, occupied);
        Approach best = null;
        for (int y = targetTile.y() - 1; y <= targetTile.y() + 1; y++) {
            for (int x = targetTile.x() - 1; x <= targetTile.x() + 1; x++) {
                SnowStormTile candidate = new SnowStormTile(x, y);
                if (candidate.equals(targetTile) || !map.isWalkable(candidate) || occupied.contains(candidate)) {
                    continue;
                }
                int length = SnowStormMovementPlanner.findPath(searchGrid, start, candidate).size();
                if (length == 0 && !candidate.equals(start)) continue;
                Approach current = new Approach(candidate, length, coverScore(map, candidate));
                if (best == null || current.cover() > best.cover()
                        || (current.cover() == best.cover() && current.length() < best.length())
                        || (current.cover() == best.cover() && current.length() == best.length()
                        && (candidate.y() < best.tile().y()
                        || (candidate.y() == best.tile().y() && candidate.x() < best.tile().x())))) {
                    best = current;
                }
            }
        }
        return best == null ? null : best.tile();
    }

    private static int coverScore(SnowStormMapDefinition map, SnowStormTile tile) {
        int score = 0;
        for (int y = tile.y() - 1; y <= tile.y() + 1; y++) {
            for (int x = tile.x() - 1; x <= tile.x() + 1; x++) {
                if ((x != tile.x() || y != tile.y())
                        && map.projectileCollisionHeight(new SnowStormTile(x, y)) > 0) score++;
            }
        }
        return score;
    }

    private record Approach(SnowStormTile tile, int length, int cover) {}

    private static long configuredDecisionInterval() {
        int configured = Emulator.getConfig() == null ? (int) DEFAULT_DECISION_MS
                : Emulator.getConfig().getInt("snowstorm.bots.decision.interval.ms", (int) DEFAULT_DECISION_MS);
        return Math.max(SnowStormMath.TURN_MS, Math.min(5_000, configured));
    }

    private static int configuredThrowRadius() {
        int configured = Emulator.getConfig() == null ? DEFAULT_THROW_RADIUS_TILES
                : Emulator.getConfig().getInt("snowstorm.bots.throw-radius.tiles", DEFAULT_THROW_RADIUS_TILES);
        return Math.max(1, Math.min(30, configured));
    }
}
