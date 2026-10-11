package com.eu.habbo.habbohotel.games.battleball;

import com.eu.habbo.Emulator;
import java.util.ArrayList;
import java.util.List;

/** Chooses normal-difficulty bot actions without owning match state or bypassing game rules. */
final class BattleBallBotStrategy {
    private static final int DEFAULT_SEARCH_RADIUS = 8;
    private static final long DEFAULT_DECISION_MS = 1_500L;
    private static final long TICK_MS = 500L;

    void direct(BattleBallGame game, long now) {
        long decisionDelay = Emulator.getConfig() == null ? DEFAULT_DECISION_MS
                : Math.max(TICK_MS, Emulator.getConfig().getInt(
                        "battleball.bots.decision.interval.ms", (int) DEFAULT_DECISION_MS));
        for (BattleBallPlayer bot : game.players()) {
            if (!bot.isBot()
                    || bot.state() == BattleBallPlayerState.STUNNED
                    || bot.state() == BattleBallPlayerState.BALL_BROKEN
                    || bot.state() == BattleBallPlayerState.FLYING_THROUGH_AIR) continue;

            // Bots collect through the normal landing path, but have no client to send the activation
            // packet. Calling the public rule method keeps effects and update events identical.
            if (bot.heldPowerObjectId() > 0
                    && game.activatePower(bot.userId(), bot.heldPowerObjectId(), now)) continue;
            if (bot.hasMovementPath()) continue;
            if (bot.lastActionAt() > 0 && now - bot.lastActionAt() < decisionDelay) continue;

            // A decision counts as activity even when no useful tile is currently reachable.
            bot.markActive(now);
            BattleBallPosition target = this.chooseTarget(game, bot);
            if (target != null) game.requestMove(bot.userId(), target.x(), target.y(), now);
        }
    }

    BattleBallPosition chooseTarget(BattleBallGame game, BattleBallPlayer bot) {
        int radius = Emulator.getConfig() == null ? DEFAULT_SEARCH_RADIUS
                : Math.max(1, Math.min(12, Emulator.getConfig().getInt(
                        "battleball.bots.search-radius.tiles", DEFAULT_SEARCH_RADIUS)));
        int radiusSquared = radius * radius;

        if (bot.heldPowerObjectId() < 0) {
            List<BattleBallPowerUp> availablePowers = game.powers().stream()
                    .filter(power -> power.holderObjectId() < 0
                            && power.position().distanceSquared(bot.position()) <= radiusSquared)
                    .sorted((left, right) -> Integer.compare(
                            left.position().distanceSquared(bot.position()),
                            right.position().distanceSquared(bot.position())))
                    .toList();
            for (BattleBallPowerUp power : availablePowers) {
                if (!game.findPath(bot.position(), power.position().x(), power.position().y()).isEmpty()) {
                    return power.position();
                }
            }
        }

        int effectiveTeam = game.scoringTeam(bot);
        List<BattleBallPosition> candidates = new ArrayList<>();
        for (int y = Math.max(0, bot.position().y() - radius);
             y <= Math.min(game.map().height() - 1, bot.position().y() + radius); y++) {
            for (int x = Math.max(0, bot.position().x() - radius);
                 x <= Math.min(game.map().width() - 1, bot.position().x() + radius); x++) {
                BattleBallTile tile = game.tile(x, y);
                if (tile == null || tile.colour() == effectiveTeam
                        || tile.state() == BattleBallTileState.SEALED) continue;
                BattleBallPosition position = new BattleBallPosition(x, y);
                int distance = position.distanceSquared(bot.position());
                if (distance > 0 && distance <= radiusSquared) candidates.add(position);
            }
        }
        candidates.sort((left, right) -> {
            BattleBallTile leftTile = game.tile(left.x(), left.y());
            BattleBallTile rightTile = game.tile(right.x(), right.y());
            int leftPriority = leftTile.colour() >= 0 ? 0 : 1;
            int rightPriority = rightTile.colour() >= 0 ? 0 : 1;
            if (leftPriority != rightPriority) return Integer.compare(leftPriority, rightPriority);
            return Integer.compare(left.distanceSquared(bot.position()),
                    right.distanceSquared(bot.position()));
        });
        for (BattleBallPosition candidate : candidates) {
            if (!game.findPath(bot.position(), candidate.x(), candidate.y()).isEmpty()) return candidate;
        }
        return null;
    }
}
