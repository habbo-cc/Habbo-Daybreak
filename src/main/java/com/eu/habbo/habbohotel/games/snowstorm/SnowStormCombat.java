package com.eu.habbo.habbohotel.games.snowstorm;

public final class SnowStormCombat {
    public static final int HIT_SCORE = 1;
    public static final int KNOCKOUT_SCORE = 5;

    private SnowStormCombat() {
    }

    public static Resolution resolve(
            int teamCount,
            boolean sameTeam,
            int currentHealth) {
        if (teamCount < 1 || teamCount > 4 || currentHealth < 0 || currentHealth > 4) {
            throw new IllegalArgumentException("Invalid SnowStorm combat input");
        }
        if (currentHealth == 0) {
            return new Resolution(0, currentHealth, 0, 0);
        }
        int health = currentHealth - 1;
        boolean opposition = teamCount == 1 || !sameTeam;
        return new Resolution(1, health, opposition ? HIT_SCORE : 0,
                opposition && health == 0 ? KNOCKOUT_SCORE : 0);
    }

    public record Resolution(int damage, int pendingHealth, int hitScore, int knockoutScore) {
        public int totalScore() {
            return hitScore + knockoutScore;
        }

        public boolean knockout() {
            return damage > 0 && pendingHealth == 0;
        }
    }
}
