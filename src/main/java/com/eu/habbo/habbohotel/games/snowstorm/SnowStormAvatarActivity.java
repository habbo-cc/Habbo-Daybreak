package com.eu.habbo.habbohotel.games.snowstorm;

public final class SnowStormAvatarActivity {
    public static final int MAX_HEALTH = 4;
    public static final int MAX_AMMO = 5;
    public static final int CREATE_SUBTURNS = 20;
    public static final int STUN_SUBTURNS = 125;
    public static final int INVINCIBLE_SUBTURNS = 60;
    public static final int THROW_COOLDOWN_SUBTURNS = 5;

    private SnowStormAvatarActivity() {
    }

    public static State tick(State state) {
        if (state.timer() <= 0) {
            return state;
        }
        SnowStormActivityState activity = state.activity();
        int timer = state.timer();
        int health = state.health();
        int ammo = state.ammo();
        if (timer == 1) {
            if (activity == SnowStormActivityState.CREATING) {
                ammo = Math.min(ammo + 1, MAX_AMMO);
                activity = SnowStormActivityState.NORMAL;
            } else if (activity == SnowStormActivityState.STUNNED) {
                health = MAX_HEALTH;
                activity = SnowStormActivityState.INVINCIBLE_AFTER_STUN;
                timer = INVINCIBLE_SUBTURNS;
            } else if (activity == SnowStormActivityState.INVINCIBLE_AFTER_STUN) {
                activity = SnowStormActivityState.NORMAL;
            }
        }
        return new State(activity, timer - 1, health, ammo);
    }

    public static State beginCreating(State state) {
        if (!state.activity().canMake() || state.ammo() >= MAX_AMMO) {
            return state;
        }
        return new State(SnowStormActivityState.CREATING, CREATE_SUBTURNS, state.health(), state.ammo());
    }

    public static State cancelCreatingForWalk(State state) {
        if (state.activity() != SnowStormActivityState.CREATING) {
            return state;
        }
        return new State(SnowStormActivityState.NORMAL, 0, state.health(), state.ammo());
    }

    public static State knockOut(State state) {
        return new State(SnowStormActivityState.STUNNED, STUN_SUBTURNS, 0, 0);
    }

    public static boolean acceptsThrowAt(long absoluteSubturn, long lastAcceptedThrowSubturn) {
        return lastAcceptedThrowSubturn < 0
                || absoluteSubturn - lastAcceptedThrowSubturn >= THROW_COOLDOWN_SUBTURNS;
    }

    public record State(SnowStormActivityState activity, int timer, int health, int ammo) {
        public State {
            if (timer < 0 || health < 0 || health > MAX_HEALTH || ammo < 0 || ammo > MAX_AMMO) {
                throw new IllegalArgumentException("Invalid SnowStorm avatar activity values");
            }
        }
    }
}
