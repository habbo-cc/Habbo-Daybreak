package com.eu.habbo.habbohotel.games.battleball;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.pathfinding.impl.GridPathfinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Server-authoritative original BattleBall match. One manager-owned 500 ms task calls {@link #tick}. */
public final class BattleBallGame {
    public static final int MAX_ACTIVE_POWERS = 2;
    /** Havana gives a player fifteen seconds to use a collected power. */
    private static final long HELD_POWER_LIFETIME_MS = 15_000L;
    /**
     * Enough bots to fill every seat but the owner's. The largest game is 12 players however the
     * teams are split -- 2x6, 3x4 or 4x3 -- so 11 bots fills it.
     */
    private static final int HARD_MAX_BOTS = 11;
    private static final String DEFAULT_BOT_FIGURES =
            "hr-100.hd-190-7.ch-210-66.lg-270-82.sh-290-80,"
            + "hr-100-61.hd-190-1.ch-210-92.lg-270-82.sh-290-80,"
            + "hr-115-42.hd-195-19.ch-3030-82.lg-275-1408.fa-1201.ca-1804-64,"
            + "ch-665-92.hd-629-1.sh-740-92.hr-515-31.lg-3174-71-92.he-3082-92";
    private static final long TICK_MS = 500L;
    private static final int[][] DIRECTIONS = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};

    private final int id;
    private final String name;
    private final int ownerUserId;
    private final String ownerName;
    private final BattleBallMap map;
    private final int roundSeconds;
    private final boolean privateGame;
    private final List<BattleBallTeam> teams;
    private final Set<BattleBallPowerType> enabledPowers;
    private final Map<Integer, List<BattleBallPosition>> spawns;
    private final BattleBallTile[][] tiles;
    private final Map<Integer, BattleBallPlayer> players = new LinkedHashMap<>();
    private final Set<Integer> watchers = new HashSet<>();
    private final Map<Integer, BattleBallPowerUp> powers = new LinkedHashMap<>();
    private final LinkedHashSet<BattleBallTile> priorityTiles = new LinkedHashSet<>();
    private final LinkedHashSet<BattleBallTile> fillTiles = new LinkedHashSet<>();
    private final List<BattleBallEvent> events = new ArrayList<>();
    private final Map<Integer, Integer> lastDeltaScores = new LinkedHashMap<>();
    private final Random random;
    private final BattleBallPowerResolver powerResolver;
    private final BattleBallBotStrategy botStrategy = new BattleBallBotStrategy();
    private final long idleTimeoutMs;
    private final long idleWarningMs;
    private final Map<Integer, Integer> idleWarnings = new LinkedHashMap<>();
    private final Set<Integer> idleRemovals = new LinkedHashSet<>();
    private BattleBallState state = BattleBallState.LOBBY;
    private int nextObjectId = 1;
    private int nextBotUserId = -1;
    private int sequence;
    private long stateEndsAt;
    private boolean initialPowersSpawned;
    private boolean boardReset;
    private int roundNumber;
    private boolean rankedRound;
    private boolean rankedRequiresHumanOpponent;
    private BattleBallRoundEndReason roundEndReason = BattleBallRoundEndReason.NONE;

    public BattleBallGame(int id, String name, int ownerUserId, String ownerName, BattleBallMap map,
                          int roundSeconds, int teamCount, int teamCapacity, boolean privateGame,
                          Collection<BattleBallPowerType> enabledPowers,
                          Map<Integer, List<BattleBallPosition>> spawns, int idleTimeoutSeconds, int idleWarningSeconds) {
        if (id <= 0 || ownerUserId <= 0 || name == null || name.isBlank() || name.trim().length() > 32) {
            throw new IllegalArgumentException("Invalid BattleBall identity");
        }
        if (roundSeconds < 60 || roundSeconds > 600 || teamCount < 2 || teamCount > 4
                || teamCapacity < 1 || teamCapacity > maxTeamCapacity(teamCount)) {
            throw new IllegalArgumentException("Invalid BattleBall parameters");
        }
        this.id = id;
        this.name = name.trim();
        this.ownerUserId = ownerUserId;
        this.ownerName = ownerName;
        this.map = map;
        this.roundSeconds = roundSeconds;
        this.privateGame = privateGame;
        this.idleTimeoutMs = Math.max(30, idleTimeoutSeconds) * 1_000L;
        this.idleWarningMs = Math.min(this.idleTimeoutMs, Math.max(5, idleWarningSeconds) * 1_000L);
        this.enabledPowers = Set.copyOf(enabledPowers);
        this.spawns = new HashMap<>();
        spawns.forEach((team, positions) -> this.spawns.put(team, List.copyOf(positions)));
        this.teams = new ArrayList<>(teamCount);
        for (int team = 0; team < teamCount; team++) this.teams.add(new BattleBallTeam(team, teamCapacity));
        this.tiles = new BattleBallTile[map.width()][map.height()];
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) this.tiles[x][y] = new BattleBallTile(x, y, map.isPlayable(x, y), map.floorHeight(x, y), map.isWalkable(x, y));
        }
        this.random = new Random(((long) id << 32) ^ ownerUserId);
        this.powerResolver = new BattleBallPowerResolver(this.map, this.tiles, this.players,
                this.priorityTiles, this.fillTiles, this.events, this.random, this::nextObjectId);
    }

    public synchronized int id() { return this.id; }
    public synchronized String name() { return this.name; }
    public synchronized int ownerUserId() { return this.ownerUserId; }
    public synchronized String ownerName() { return this.ownerName; }
    public synchronized BattleBallMap map() { return this.map; }
    public synchronized int roundSeconds() { return this.roundSeconds; }
    public synchronized boolean privateGame() { return this.privateGame; }
    public synchronized BattleBallState state() { return this.state; }
    public synchronized int sequence() { return this.sequence; }
    public synchronized int roundNumber() { return this.roundNumber; }
    public synchronized boolean rankedRound() { return this.rankedRound; }
    public synchronized BattleBallRoundEndReason roundEndReason() { return this.roundEndReason; }
    public synchronized boolean roundAborted() { return this.roundEndReason.aborted(); }
    public synchronized List<BattleBallTeam> teams() { return List.copyOf(this.teams); }
    public synchronized Collection<BattleBallPlayer> players() { return List.copyOf(this.players.values()); }
    public synchronized Collection<BattleBallPowerUp> powers() { return List.copyOf(this.powers.values()); }
    public synchronized Collection<BattleBallPin> pins() { return this.powerResolver.pins(); }
    public synchronized Set<Integer> watchers() { return Set.copyOf(this.watchers); }
    public synchronized Set<BattleBallPowerType> enabledPowers() { return this.enabledPowers; }
    public synchronized int capacity() { return this.teams.stream().mapToInt(BattleBallTeam::capacity).sum(); }

    /**
     * Largest team for a given team count: 6, 4 or 3.
     *
     * Three sources agree. The cast sizes bb_area_teamN by team count -- 112px, 80px, 64px -- and each
     * ends with a 16px Join strip, leaving six, four and three 16px rows; bb_ginfo4t declares exactly
     * three kick icons. Havana's canSwitchTeam admits a player while size() <= 5/3/2, settling on the
     * same numbers. Every map defines six spawn tiles per team, which covers the largest case.
     */
    public static int maxTeamCapacity(int teamCount) {
        return switch (teamCount) {
            case 2 -> 6;
            case 3 -> 4;
            default -> 3;
        };
    }

    public synchronized BattleBallTile tile(int x, int y) {
        return this.map.isPlayable(x, y) ? this.tiles[x][y] : null;
    }

    /** Movement target lookup: ramps are stood on but never scored, so this is wider than {@link #tile}. */
    public synchronized BattleBallTile walkableTile(int x, int y) {
        return this.map.isWalkable(x, y) ? this.tiles[x][y] : null;
    }

    public synchronized boolean addPlayer(int userId, String name, String motto, String figure, String sex, int teamId) {
        if (this.state != BattleBallState.LOBBY || this.players.containsKey(userId) || teamId < 0 || teamId >= this.teams.size()) return false;
        BattleBallTeam team = this.teams.get(teamId);
        BattleBallPosition spawn = this.nextSpawn(teamId, team.players().size());
        if (team.full() || spawn == null) return false;
        BattleBallPlayer player = new BattleBallPlayer(this.nextObjectId++, userId, name, motto, figure, sex, teamId, spawn);
        if (!team.add(player)) return false;
        this.players.put(userId, player);
        this.sequence++;
        this.events.add(BattleBallEvent.spawn(player.objectId()));
        this.claimSpawnTile(player);
        return true;
    }

    /** Adds a sessionless player during setup; all gameplay after this point uses the normal player path. */
    public synchronized boolean addBot(int teamId) {
        if (this.state != BattleBallState.LOBBY || teamId < 0 || teamId >= this.teams.size()) return false;
        if (Emulator.getConfig() != null && !Emulator.getConfig().getBoolean("battleball.bots.enabled", true)) return false;
        int configuredMaximum = Emulator.getConfig() == null ? HARD_MAX_BOTS
                : Emulator.getConfig().getInt("battleball.bots.max-per-game", HARD_MAX_BOTS);
        int maximum = Math.max(0, Math.min(HARD_MAX_BOTS, configuredMaximum));
        long botCount = this.players.values().stream().filter(BattleBallPlayer::isBot).count();
        if (botCount >= maximum) return false;

        BattleBallTeam team = this.teams.get(teamId);
        BattleBallPosition spawn = this.nextSpawn(teamId, team.players().size());
        if (team.full() || spawn == null) return false;
        int userId = this.nextBotUserId--;
        int botNumber = -userId;
        BattleBallPlayer bot = new BattleBallPlayer(this.nextObjectId++, userId, "Bot " + botNumber,
                "", this.botFigure(botNumber - 1), "M", teamId, spawn);
        if (!team.add(bot)) return false;
        this.players.put(userId, bot);
        this.sequence++;
        this.events.add(BattleBallEvent.spawn(bot.objectId()));
        this.claimSpawnTile(bot);
        return true;
    }

    public synchronized boolean changeTeam(int userId, int teamId) {
        if (this.state != BattleBallState.LOBBY || teamId < 0 || teamId >= this.teams.size()) return false;
        BattleBallPlayer player = this.players.get(userId);
        BattleBallTeam target = this.teams.get(teamId);
        if (player == null || player.teamId() == teamId || target.full()) return false;
        this.teams.get(player.teamId()).remove(userId);
        if (!target.add(player)) return false;
        player.setTeamId(teamId);
        player.setPosition(this.nextSpawn(teamId, target.players().size() - 1));
        this.claimSpawnTile(player);
        this.events.add(BattleBallEvent.update(player.objectId()));
        this.sequence++;
        return true;
    }

    public synchronized BattleBallPlayer removePlayer(int userId) {
        BattleBallPlayer player = this.players.remove(userId);
        if (player == null) return null;
        this.teams.get(player.teamId()).remove(userId);
        this.events.add(BattleBallEvent.despawn(player.objectId()));
        this.sequence++;
        return player;
    }

    public synchronized void addWatcher(int userId) { this.watchers.add(userId); }
    /** @return whether this user actually was a watcher. */
    public synchronized boolean removeWatcher(int userId) { return this.watchers.remove(userId); }

    public synchronized boolean start(int actorUserId, long now, boolean rankedRound) {
        return this.start(actorUserId, now, rankedRound, false);
    }

    public synchronized boolean start(int actorUserId, long now, boolean rankedRound,
                                      boolean rankedRequiresHumanOpponent) {
        if (!this.canStart(actorUserId)) return false;
        this.resetBoard();
        this.rankedRound = rankedRound;
        this.rankedRequiresHumanOpponent = rankedRequiresHumanOpponent;
        this.roundEndReason = BattleBallRoundEndReason.NONE;
        this.roundNumber++;
        this.state = BattleBallState.COUNTDOWN;
        this.stateEndsAt = now + 3_000L;
        this.sequence++;
        return true;
    }

    public synchronized boolean canStart(int actorUserId) {
        return actorUserId == this.ownerUserId && this.state == BattleBallState.LOBBY
                && this.teams.stream().filter(team -> !team.players().isEmpty()).count() >= 2;
    }

    /** Whether the current lobby roster satisfies the configured ranked-match policy. */
    public synchronized boolean rankedEligible(boolean requiresHumanOpponent) {
        if (!requiresHumanOpponent) return true;
        return this.teams.stream()
                .filter(team -> team.players().stream().anyMatch(player -> !player.isBot()))
                .count() >= 2;
    }

    public synchronized boolean requestMove(int userId, int x, int y, long now) {
        if (this.state != BattleBallState.RUNNING) return false;
        BattleBallPlayer player = this.players.get(userId);
        BattleBallTile target = this.walkableTile(x, y);
        if (player == null || target == null || player.state() == BattleBallPlayerState.STUNNED
                || player.state() == BattleBallPlayerState.BALL_BROKEN) return false;
        List<BattleBallPosition> path = this.findPath(player.position(), x, y);
        if (path.isEmpty()) return false;
        player.replaceMovementPath(path, new BattleBallPosition(x, y));
        player.markActive(now);
        return true;
    }

    public synchronized boolean activatePower(int userId, int powerObjectId, long now) {
        if (this.state != BattleBallState.RUNNING) return false;
        BattleBallPlayer player = this.players.get(userId);
        BattleBallPowerUp power = this.powers.get(powerObjectId);
        if (player == null || power == null || power.holderObjectId() != player.objectId()
                || player.heldPowerObjectId() != powerObjectId || power.expiresAt() <= now) return false;
        player.setHeldPowerObjectId(-1);
        player.markActive(now);
        this.powers.remove(powerObjectId);
        this.events.add(BattleBallEvent.activation(player.objectId(), powerObjectId, player.position().direction(), power.type().id()));
        this.events.add(BattleBallEvent.despawn(powerObjectId));
        this.powerResolver.apply(player, power.type(), now);
        this.sequence++;
        return true;
    }

    public synchronized void tick(long now) {
        if (this.state == BattleBallState.DISPOSED || this.state == BattleBallState.LOBBY) return;
        boolean changed = this.powerResolver.expire(now) | this.expireObjects(now);
        if (this.state == BattleBallState.COUNTDOWN || this.state == BattleBallState.RUNNING) {
            BattleBallRoundEndReason invalidReason = this.invalidActiveRosterReason();
            if (invalidReason != BattleBallRoundEndReason.NONE) {
                this.endRound(now, invalidReason);
                changed = true;
            }
        }
        if (this.state == BattleBallState.COUNTDOWN && now >= this.stateEndsAt) {
            this.state = BattleBallState.RUNNING;
            this.stateEndsAt = now + this.roundSeconds * 1_000L;
            this.initialPowersSpawned = false;
            changed = true;
        } else if (this.state == BattleBallState.RUNNING) {
            changed |= this.checkIdle(now);
            if (now < this.stateEndsAt) {
                changed |= this.advanceMovingPlayers(now);
                this.botStrategy.direct(this, now);
            }
            if (!this.initialPowersSpawned) {
                int count = this.random.nextInt(MAX_ACTIVE_POWERS + 1);
                for (int i = 0; i < count; i++) this.spawnPower(now);
                this.initialPowersSpawned = true;
                changed = true;
            } else if (now >= this.stateEndsAt) {
                this.endRound(now, BattleBallRoundEndReason.TIME_EXPIRED);
                changed = true;
            } else if (this.random.nextDouble() < 0.06) {
                changed |= this.spawnPower(now);
            }
        } else if (this.state == BattleBallState.ENDING && now >= this.stateEndsAt) {
            this.state = BattleBallState.LOBBY;
            this.resetBoard();
            changed = true;
        }
        if (changed) this.sequence++;
    }

    public synchronized BattleBallUpdate consumeUpdate(long now, boolean full) {
        List<BattleBallTile> priority = full ? this.allTiles() : List.copyOf(this.priorityTiles);
        List<BattleBallTile> fill = full ? List.of() : List.copyOf(this.fillTiles);
        Map<Integer, Integer> currentScores = this.scores();
        Map<Integer, Integer> sentScores = new LinkedHashMap<>();
        if (full) sentScores.putAll(currentScores);
        else currentScores.forEach((team, score) -> {
            if (!score.equals(this.lastDeltaScores.get(team))) sentScores.put(team, score);
        });
        BattleBallUpdate update = new BattleBallUpdate(this.sequence, this.state, this.timeRemaining(now),
                List.copyOf(this.players.values()), List.copyOf(this.powers.values()), List.copyOf(this.powerResolver.pins()),
                priority, fill, Collections.unmodifiableMap(sentScores), full ? List.of() : List.copyOf(this.events),
                !full && this.boardReset);
        if (!full) {
            this.priorityTiles.clear();
            this.fillTiles.clear();
            this.events.clear();
            this.boardReset = false;
            this.lastDeltaScores.clear();
            this.lastDeltaScores.putAll(currentScores);
        }
        return update;
    }

    public synchronized void dispose() {
        if (this.state == BattleBallState.DISPOSED) return;
        this.state = BattleBallState.DISPOSED;
        this.powers.clear();
        this.powerResolver.reset();
        this.watchers.clear();
        this.idleWarnings.clear();
        this.idleRemovals.clear();
        this.sequence++;
    }

    public synchronized Map<Integer, Integer> consumeIdleWarnings() {
        Map<Integer, Integer> result = Map.copyOf(this.idleWarnings);
        this.idleWarnings.clear();
        return result;
    }

    public synchronized Set<Integer> consumeIdleRemovals() {
        Set<Integer> result = Set.copyOf(this.idleRemovals);
        this.idleRemovals.clear();
        return result;
    }

    public synchronized Map<Integer, Integer> scores() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (BattleBallTeam team : this.teams) result.put(team.id(), 0);
        for (int y = 0; y < this.map.height(); y++) {
            for (int x = 0; x < this.map.width(); x++) {
                for (BattleBallScoreReference reference : this.tiles[x][y].scoreReferences()) {
                    result.computeIfPresent(reference.teamId(), (ignored, score) -> score + reference.points());
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized Map<Integer, Integer> playerScores() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        this.players.values().forEach(player -> result.put(player.userId(), 0));
        for (int y = 0; y < this.map.height(); y++) {
            for (int x = 0; x < this.map.width(); x++) {
                for (BattleBallScoreReference reference : this.tiles[x][y].scoreReferences()) {
                    result.computeIfPresent(reference.userId(), (ignored, score) -> score + reference.points());
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void collectPower(BattleBallPlayer player, int x, int y, long now) {
        BattleBallPowerUp found = null;
        for (BattleBallPowerUp power : this.powers.values()) {
            if (power.holderObjectId() < 0 && power.position().x() == x && power.position().y() == y) { found = power; break; }
        }
        if (found == null) return;
        if (player.heldPowerObjectId() > 0) {
            BattleBallPowerUp old = this.powers.remove(player.heldPowerObjectId());
            if (old != null) this.events.add(BattleBallEvent.despawn(old.objectId()));
        }
        BattleBallPowerType type = found.type();
        if (type == BattleBallPowerType.QUESTION_MARK) {
            List<BattleBallPowerType> choices = this.enabledPowers.stream().filter(value -> value != BattleBallPowerType.QUESTION_MARK).toList();
            if (choices.isEmpty()) return;
            type = choices.get(this.random.nextInt(choices.size()));
        }
        found.collect(player, type, now + HELD_POWER_LIFETIME_MS);
        player.setHeldPowerObjectId(found.objectId());
        this.events.add(BattleBallEvent.pickup(player.objectId(), found.objectId(), type.id()));
    }

    private boolean expireObjects(long now) {
        boolean changed = false;
        List<Integer> expiredPowers = this.powers.values().stream().filter(power -> power.expiresAt() <= now).map(BattleBallPowerUp::objectId).toList();
        for (int id : expiredPowers) {
            BattleBallPowerUp power = this.powers.remove(id);
            if (power != null) {
                if (power.holderObjectId() > 0) this.players.values().stream().filter(player -> player.objectId() == power.holderObjectId()).findFirst().ifPresent(player -> player.setHeldPowerObjectId(-1));
                this.events.add(BattleBallEvent.despawn(id));
                changed = true;
            }
        }
        return changed;
    }

    private boolean checkIdle(long now) {
        boolean changed = false;
        List<Integer> remove = new ArrayList<>();
        for (BattleBallPlayer player : this.players.values()) {
            if (player.lastActionAt() == 0) player.markActive(now);
            long remaining = this.idleTimeoutMs - (now - player.lastActionAt());
            if (remaining <= 0) remove.add(player.userId());
            else if (remaining <= this.idleWarningMs && !player.idleWarningSent()) {
                player.setIdleWarningSent(true);
                this.idleWarnings.put(player.userId(), (int) Math.max(1, (remaining + 999) / 1_000));
            }
        }
        for (int userId : remove) {
            BattleBallPlayer player = this.removePlayer(userId);
            if (player != null && !player.isBot()) this.idleRemovals.add(userId);
            changed = true;
        }
        return changed;
    }

    private boolean spawnPower(long now) {
        if (this.map.id() == 5 || this.enabledPowers.isEmpty()) return false;
        long active = this.powers.values().stream().filter(power -> power.holderObjectId() < 0).count();
        if (active >= MAX_ACTIVE_POWERS) return false;
        List<BattleBallPowerType> choices = new ArrayList<>(this.enabledPowers);
        if (choices.size() >= 2) choices.add(BattleBallPowerType.QUESTION_MARK);
        List<BattleBallPosition> free = this.map.playablePositions().stream()
                .filter(position -> this.powers.values().stream().noneMatch(power -> power.holderObjectId() < 0 && power.position().x() == position.x() && power.position().y() == position.y()))
                .toList();
        if (free.isEmpty()) return false;
        BattleBallPowerUp power = new BattleBallPowerUp(this.nextObjectId++, choices.get(this.random.nextInt(choices.size())),
                free.get(this.random.nextInt(free.size())), now + (20 + this.random.nextInt(11)) * TICK_MS);
        this.powers.put(power.objectId(), power);
        this.events.add(BattleBallEvent.spawn(power.objectId()));
        return true;
    }

    private void resetBoard() {
        for (int y = 0; y < this.map.height(); y++) for (int x = 0; x < this.map.width(); x++) this.tiles[x][y].reset();
        this.powers.clear();
        this.powerResolver.reset();
        this.idleWarnings.clear(); this.priorityTiles.clear(); this.fillTiles.clear(); this.events.clear();
        this.initialPowersSpawned = false;
        this.boardReset = true;
        for (BattleBallTeam team : this.teams) {
            int index = 0;
            for (BattleBallPlayer player : team.players()) {
                player.setPosition(this.nextSpawn(team.id(), index++));
                player.setState(BattleBallPlayerState.NORMAL, 0);
                player.setHeldPowerObjectId(-1);
                player.setColouringForUserId(BattleBallPlayer.NOT_COLOURING);
                player.clearMovementPath();
                this.claimSpawnTile(player);
            }
        }
    }

    /**
     * Colours the tile a player occupies for their team, as Havana does when it seats a player at their
     * spawn. Claimed the moment they take the square -- on join, on a team switch and again when the
     * board is reset for a round -- so it is already their colour before play begins; it is simply
     * hidden under the rider until they move, which is why it looks like stepping off paints it.
     */
    private void claimSpawnTile(BattleBallPlayer player) {
        BattleBallPosition position = player.position();
        if (position == null) return;
        BattleBallTile tile = this.tile(position.x(), position.y());
        if (tile == null || !tile.playable()) return;
        if (tile.land(this.map.id(), player.teamId(), player.userId(), false, false)) {
            this.priorityTiles.add(tile);
        }
    }

    /** Advances every requested destination by one tile per game tick, applying normal landing rules at each step. */
    private boolean advanceMovingPlayers(long now) {
        boolean changed = false;
        for (BattleBallPlayer player : this.players.values()) {
            if (player.state() == BattleBallPlayerState.STUNNED
                    || player.state() == BattleBallPlayerState.BALL_BROKEN
                    || player.state() == BattleBallPlayerState.FLYING_THROUGH_AIR) {
                player.clearMovementPath();
                continue;
            }
            BattleBallPosition next = player.pollMovementStep();
            if (next == null) continue;
            if (!this.map.isWalkable(next.x(), next.y()) || this.isOccupiedByOther(player, next.x(), next.y())) {
                BattleBallPosition goal = player.movementGoal();
                List<BattleBallPosition> replacement = goal == null ? List.of()
                        : this.findPath(player.position(), goal.x(), goal.y());
                if (replacement.isEmpty()) {
                    player.clearMovementPath();
                    continue;
                }
                player.replaceMovementPath(replacement, goal);
                next = player.pollMovementStep();
                if (next == null || this.isOccupiedByOther(player, next.x(), next.y())) continue;
            }
            int facing = direction(player.position().x(), player.position().y(), next.x(), next.y());
            player.setPosition(new BattleBallPosition(next.x(), next.y(), 0.0, facing));
            player.markActive(now);
            this.events.add(BattleBallEvent.move(player.objectId(), next.x(), next.y()));
            this.collectPower(player, next.x(), next.y(), now);
            changed = true;
            if (this.powerResolver.hitPin(player, next.x(), next.y(), now)) continue;

            BattleBallTile tile = this.walkableTile(next.x(), next.y());
            if (tile == null || !tile.playable()) continue;
            int effectiveTeam = this.powerResolver.scoringTeam(player);
            boolean landed = tile.land(this.map.id(), effectiveTeam, this.powerResolver.scoringUser(player),
                    player.state() == BattleBallPlayerState.HIGH_JUMPS,
                    player.state() == BattleBallPlayerState.CLEANING_TILES);
            if (landed) {
                this.priorityTiles.add(tile);
                if (tile.state() == BattleBallTileState.SEALED) {
                    this.powerResolver.fillFrom(tile, effectiveTeam, this.powerResolver.scoringUser(player));
                }
            }
        }
        return changed;
    }

    private boolean isOccupiedByOther(BattleBallPlayer movingPlayer, int x, int y) {
        return this.players.values().stream().anyMatch(player -> player != movingPlayer
                && player.position().x() == x && player.position().y() == y);
    }

    /** Uses the hotel's shared grid pathfinder over the arena floor, including non-scoring ramps. */
    List<BattleBallPosition> findPath(BattleBallPosition start, int targetX, int targetY) {
        GridPathfinder.Grid grid = new GridPathfinder.Grid() {
            @Override public int width() { return map.width(); }
            @Override public int height() { return map.height(); }
            @Override public boolean isWalkable(int x, int y) { return map.isWalkable(x, y); }
            @Override public double floorHeight(int x, int y) { return map.floorHeight(x, y); }
            @Override public boolean isOccupied(int x, int y) {
                return players.values().stream().anyMatch(player -> player.position().x() == x
                        && player.position().y() == y && player.position() != start);
            }
        };
        boolean allowFalling = Emulator.getConfig() == null
                || Emulator.getConfig().getBoolean("pathfinder.step.allow.falling", true);
        boolean retroStyleDiagonals = Emulator.getConfig() != null
                && Emulator.getConfig().getBoolean("pathfinder.retro-style.diagonals", false);
        return GridPathfinder.findPath(grid, start.x(), start.y(), targetX, targetY,
                        RoomLayout.MAXIMUM_STEP_HEIGHT, allowFalling, retroStyleDiagonals)
                .stream().map(step -> new BattleBallPosition(step.x(), step.y(), 0.0, 0)).toList();
    }

    private List<BattleBallTile> allTiles() {
        List<BattleBallTile> result = new ArrayList<>(this.map.width() * this.map.height());
        for (int y = 0; y < this.map.height(); y++) for (int x = 0; x < this.map.width(); x++) result.add(this.tiles[x][y]);
        return result;
    }

    private BattleBallPosition nextSpawn(int teamId, int index) {
        List<BattleBallPosition> teamSpawns = this.spawns.getOrDefault(teamId, List.of());
        return teamSpawns.isEmpty() ? null : teamSpawns.get(index % teamSpawns.size());
    }

    private String botFigure(int index) {
        String configured = Emulator.getConfig() == null ? DEFAULT_BOT_FIGURES
                : Emulator.getConfig().getValue("battleball.bots.figures", DEFAULT_BOT_FIGURES);
        List<String> figures = new ArrayList<>();
        for (String value : configured.split(",")) if (!value.isBlank()) figures.add(value.trim());
        if (figures.isEmpty()) figures.add(DEFAULT_BOT_FIGURES.substring(0, DEFAULT_BOT_FIGURES.indexOf(',')));
        return figures.get(Math.floorMod(index, figures.size()));
    }

    int scoringTeam(BattleBallPlayer player) { return this.powerResolver.scoringTeam(player); }

    private int nextObjectId() { return this.nextObjectId++; }

    private long timeRemaining(long now) {
        return this.stateEndsAt == 0 ? 0 : Math.max(0, this.stateEndsAt - now);
    }

    public synchronized int timeRemainingMs(long now) {
        return (int) Math.min(Integer.MAX_VALUE, this.timeRemaining(now));
    }

    private BattleBallRoundEndReason invalidActiveRosterReason() {
        long activeTeams = this.teams.stream().filter(team -> !team.players().isEmpty()).count();
        if (activeTeams < 2) return BattleBallRoundEndReason.INSUFFICIENT_ACTIVE_TEAMS;
        if (this.rankedRound && this.rankedRequiresHumanOpponent) {
            long humanTeams = this.teams.stream()
                    .filter(team -> team.players().stream().anyMatch(player -> !player.isBot()))
                    .count();
            if (humanTeams < 2) return BattleBallRoundEndReason.HUMAN_OPPOSITION_LOST;
        }
        return BattleBallRoundEndReason.NONE;
    }

    private void endRound(long now, BattleBallRoundEndReason reason) {
        this.state = BattleBallState.ENDING;
        this.stateEndsAt = now + 5_000L;
        this.roundEndReason = reason;
        for (BattleBallPlayer player : this.players.values()) player.clearMovementPath();
    }

    private static int direction(int fromX, int fromY, int toX, int toY) {
        int dx = Integer.compare(toX, fromX);
        int dy = Integer.compare(toY, fromY);
        for (int i = 0; i < DIRECTIONS.length; i++) if (DIRECTIONS[i][0] == dx && DIRECTIONS[i][1] == dy) return i;
        return 0;
    }

}
