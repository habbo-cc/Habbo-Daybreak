package com.eu.habbo.habbohotel.games.snowstorm;

public final class SnowStormProjectileMath {
    public static final int QUICK_TTL = 13;
    public static final int VELOCITY = 2000;
    public static final int QUICK_HEIGHT = 4000;
    public static final int QUICK_DESCENT_POINT = 3;
    public static final int LOB_HEIGHT = 3000;
    public static final int AVATAR_RADIUS = 1600;
    public static final int SNOWBALL_RADIUS = 400;
    public static final int AVATAR_HEIGHT = 5000;

    private SnowStormProjectileMath() {
    }

    public static State launch(
            SnowStormTrajectory trajectory,
            int startWorldX,
            int startWorldY,
            int targetWorldX,
            int targetWorldY) {
        int componentX = SnowStormMath.directorDiv(targetWorldX - startWorldX, 200);
        int componentY = SnowStormMath.directorDiv(targetWorldY - startWorldY, 200);
        int direction = SnowStormMath.validateDirection360(SnowStormMath.componentAngle(componentX, componentY));
        int ttl;
        int baseHeight;
        int multiplier;
        if (trajectory == SnowStormTrajectory.QUICK) {
            ttl = QUICK_TTL;
            baseHeight = QUICK_HEIGHT;
            multiplier = 4;
        } else {
            int distance = SnowStormMath.fastSquareRoot(componentX * componentX + componentY * componentY) * 200;
            ttl = SnowStormMath.directorDiv(distance, VELOCITY);
            baseHeight = LOB_HEIGHT;
            multiplier = trajectory == SnowStormTrajectory.SHORT_LOB ? 10 : 100;
        }
        return new State(
                trajectory, startWorldX, startWorldY, 0, direction, ttl, ttl,
                SnowStormMath.directorDiv(ttl, 2), baseHeight, multiplier, false);
    }

    /** Calculates the next projectile coordinates without performing collision resolution. */
    public static Projection project(State state) {
        if (state.removed()) {
            throw new IllegalStateException("Cannot advance a removed SnowStorm projectile");
        }
        int ttl = state.ttl() - 1;
        int x = state.worldX() + SnowStormMath.directorDiv(SnowStormMath.velocityX(state.direction()) * VELOCITY, 255);
        int y = state.worldY() + SnowStormMath.directorDiv(SnowStormMath.velocityY(state.direction()) * VELOCITY, 255);
        int temp = state.trajectory() == SnowStormTrajectory.QUICK && ttl > QUICK_DESCENT_POINT
                ? QUICK_DESCENT_POINT - state.parabolaOffset()
                : ttl - state.parabolaOffset();
        int z = (state.parabolaOffset() * state.parabolaOffset() - temp * temp)
                * state.parabolaMultiplier() + state.baseHeight();
        return new Projection(x, y, z, ttl);
    }

    /** Resolves one previously projected step against the collision height at that position. */
    public static Step resolve(State state, Projection projection, int occupiedHeight) {
        if (state.removed()) {
            throw new IllegalStateException("Cannot advance a removed SnowStorm projectile");
        }
        int x = projection.worldX();
        int y = projection.worldY();
        int z = projection.worldZ();
        int ttl = projection.ttl();
        boolean belowGround = z < 1;
        boolean occupiedCollision = occupiedHeight > z;
        State next = new State(
                state.trajectory(), x, y, z, state.direction(), ttl, state.initialTtl(),
                state.parabolaOffset(), state.baseHeight(), state.parabolaMultiplier(),
                belowGround || occupiedCollision);
        return new Step(next, occupiedHeight, belowGround, occupiedCollision);
    }

    public static boolean circlesCollide(int centreDistance) {
        int combinedRadius = AVATAR_RADIUS + SNOWBALL_RADIUS;
        return centreDistance * centreDistance < combinedRadius * combinedRadius;
    }

    public static boolean collidesWithAvatarHeight(int snowballWorldZ) {
        return snowballWorldZ < AVATAR_HEIGHT;
    }

    public static boolean collidesWithOccupiedHeight(int snowballWorldZ, int occupiedHeight) {
        return occupiedHeight > snowballWorldZ;
    }

    public record State(
            SnowStormTrajectory trajectory,
            int worldX,
            int worldY,
            int worldZ,
            int direction,
            int ttl,
            int initialTtl,
            int parabolaOffset,
            int baseHeight,
            int parabolaMultiplier,
            boolean removed) {
    }

    public record Step(State state, int occupiedHeight, boolean belowGround, boolean occupiedCollision) {
    }

    public record Projection(int worldX, int worldY, int worldZ, int ttl) {
    }
}
