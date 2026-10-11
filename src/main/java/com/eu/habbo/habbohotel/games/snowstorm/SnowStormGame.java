package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.Emulator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lock-owned waiting instance and charged-round lifecycle. Mutable simulation state never escapes. */
public final class SnowStormGame {
    public static final int MAX_PLAYERS = 12;
    public static final int COUNTDOWN_MS = 3_000;
    public static final int NEXT_GAME_COUNTDOWN_MS = 5_000;
    private static final int HARD_MAX_BOTS = MAX_PLAYERS - 1;
    private static final String DEFAULT_BOT_FIGURES =
            "hr-100.hd-190-7.ch-210-66.lg-270-82.sh-290-80,"
            + "hr-100-61.hd-190-1.ch-210-92.lg-270-82.sh-290-80,"
            + "hr-115-42.hd-195-19.ch-3030-82.lg-275-1408.fa-1201.ca-1804-64,"
            + "ch-665-92.hd-629-1.sh-740-92.hr-515-31.lg-3174-71-92.he-3082-92";

    private final int id;
    private final String name;
    private final int ownerUserId;
    private final String ownerName;
    private final SnowStormMapDefinition map;
    private final int lengthChoice;
    private final int roundSeconds;
    private final int teamCount;
    private final int teamCapacity;
    private final int maximumSpectators;
    private final long idleTimeoutMs;
    private final long idleWarningMs;
    private final Map<Integer, SnowStormPlayer> players = new LinkedHashMap<>();
    private final Set<Integer> spectators = new LinkedHashSet<>();
    private final Set<Integer> departedStartingUsers = new LinkedHashSet<>();
    private final Map<Integer, Integer> idleWarnings = new LinkedHashMap<>();
    private final Set<Integer> idleRemovals = new LinkedHashSet<>();
    private final Map<Integer, Integer> lastClientActionSequences = new LinkedHashMap<>();
    private final SnowStormBotStrategy botStrategy = new SnowStormBotStrategy();
    private SnowStormState state = SnowStormState.LOBBY;
    private SnowStormStartingRoster startingRoster;
    private SnowStormSimulation simulation;
    private int roundNumber;
    private int resultId = -1;
    private int sequence;
    private long stateEndsAt;

    public SnowStormGame(
            int id,
            String name,
            int ownerUserId,
            String ownerName,
            SnowStormMapDefinition map,
            int lengthChoice,
            int teamCount,
            int maximumSpectators) {
        this(id, name, ownerUserId, ownerName, map, lengthChoice, teamCount,
                maximumSpectators, 60, 15);
    }

    public SnowStormGame(
            int id,
            String name,
            int ownerUserId,
            String ownerName,
            SnowStormMapDefinition map,
            int lengthChoice,
            int teamCount,
            int maximumSpectators,
            int idleTimeoutSeconds,
            int idleWarningSeconds) {
        if (id <= 0 || name == null || name.isBlank() || name.trim().length() > 32
                || ownerUserId <= 0 || ownerName == null || ownerName.isBlank() || map == null
                || lengthChoice < 1 || lengthChoice > 3 || teamCount < 1 || teamCount > 4
                || maximumSpectators < 0 || maximumSpectators > 20) {
            throw new IllegalArgumentException("Invalid SnowStorm game parameters");
        }
        this.id = id;
        this.name = name.trim();
        this.ownerUserId = ownerUserId;
        this.ownerName = ownerName;
        this.map = map;
        this.lengthChoice = lengthChoice;
        this.roundSeconds = secondsForLength(lengthChoice);
        this.teamCount = teamCount;
        this.teamCapacity = capacityForTeamCount(teamCount);
        this.maximumSpectators = maximumSpectators;
        this.idleTimeoutMs = Math.max(30, idleTimeoutSeconds) * 1_000L;
        this.idleWarningMs = Math.min(this.idleTimeoutMs,
                Math.max(5, idleWarningSeconds) * 1_000L);
    }

    public int id() { return id; }
    public String name() { return name; }
    public int ownerUserId() { return ownerUserId; }
    public String ownerName() { return ownerName; }
    public SnowStormMapDefinition map() { return map; }
    public int lengthChoice() { return lengthChoice; }
    public int roundSeconds() { return roundSeconds; }
    public int teamCount() { return teamCount; }
    public int teamCapacity() { return teamCapacity; }
    public synchronized SnowStormState state() { return state; }
    public synchronized int roundNumber() { return roundNumber; }
    public synchronized int resultId() { return resultId; }
    public synchronized int sequence() { return sequence; }
    public synchronized long stateEndsAt() { return stateEndsAt; }
    public synchronized Collection<SnowStormPlayer> players() { return List.copyOf(players.values()); }
    public synchronized Set<Integer> spectators() { return Set.copyOf(spectators); }
    public synchronized SnowStormStartingRoster startingRoster() { return startingRoster; }
    public synchronized Set<Integer> departedStartingUsers() { return Set.copyOf(departedStartingUsers); }

    public synchronized boolean hasActiveSimulation() {
        return simulation != null && startingRoster != null
                && (state == SnowStormState.COUNTDOWN || state == SnowStormState.RUNNING);
    }

    /** Captures one internally consistent recovery packet without exposing the live simulation. */
    public synchronized FullStatusSnapshot fullStatusSnapshot(int userId, long now) {
        if (!hasActiveSimulation()) return null;
        int playerObjectId = startingRoster.players().stream()
                .filter(player -> player.userId() == userId)
                .mapToInt(SnowStormStartingRoster.Player::objectId)
                .findFirst().orElse(-1);
        return new FullStatusSnapshot(id, sequence, state, roundNumber, simulation.turnNumber(),
                timeRemainingMs(now), playerObjectId, startingRoster.ranked(), teamCount,
                startingRoster, simulation.teamScoresSnapshot(), simulation.snapshots(),
                simulation.checksum());
    }

    /** Captures all persistence inputs before the live simulation can advance or be disposed. */
    public synchronized SnowStormRoundResult captureRoundResult(int endReason, boolean aborted) {
        if (startingRoster == null || simulation == null
                || roundNumber != startingRoster.roundNumber()) {
            throw new IllegalStateException("SnowStorm cannot capture a round without its charged simulation");
        }
        SnowStormRoundResult.Capture capture = new SnowStormRoundResult.Capture(
                resultId, id, sequence, roundNumber, name, ownerUserId, ownerName, map.mapId(),
                teamCount, lengthChoice, roundSeconds, startingRoster,
                simulation.playerScoresSnapshot(), simulation.teamScoresSnapshot(),
                Set.copyOf(departedStartingUsers));
        return SnowStormRoundResult.capture(capture, endReason, aborted);
    }

    public static int secondsForLength(int lengthChoice) {
        return switch (lengthChoice) {
            case 1 -> 120;
            case 2 -> 180;
            case 3 -> 300;
            default -> throw new IllegalArgumentException("Unknown SnowStorm length choice");
        };
    }

    public static int capacityForTeamCount(int teamCount) {
        return switch (teamCount) {
            case 1 -> 12;
            case 2 -> 6;
            case 3 -> 4;
            case 4 -> 3;
            default -> throw new IllegalArgumentException("Unknown SnowStorm team count");
        };
    }

    public synchronized boolean addPlayer(
            int userId,
            String playerName,
            String figure,
            String sex,
            int teamId) {
        if (state != SnowStormState.LOBBY || players.containsKey(userId) || !validTeam(teamId)
                || players.size() >= MAX_PLAYERS || teamSize(teamId) >= teamCapacity) {
            return false;
        }
        SnowStormPlayer player = new SnowStormPlayer(userId, playerName, figure, sex, teamId);
        players.put(userId, player);
        spectators.remove(userId);
        sequence++;
        return true;
    }

    /** Add one sessionless NGH bot during setup; normal roster capacity still applies. */
    public synchronized boolean addBot(int teamId) {
        if (state != SnowStormState.LOBBY || !validTeam(teamId)
                || players.size() >= MAX_PLAYERS || teamSize(teamId) >= teamCapacity) {
            return false;
        }
        if (Emulator.getConfig() != null
                && !Emulator.getConfig().getBoolean("snowstorm.bots.enabled", true)) {
            return false;
        }
        int configuredMaximum = Emulator.getConfig() == null ? HARD_MAX_BOTS
                : Emulator.getConfig().getInt("snowstorm.bots.max-per-game", HARD_MAX_BOTS);
        int maximum = Math.max(0, Math.min(HARD_MAX_BOTS, configuredMaximum));
        long botCount = players.values().stream().filter(SnowStormPlayer::isBot).count();
        if (botCount >= maximum) return false;

        int botNumber = 1;
        while (botNumber <= HARD_MAX_BOTS && players.containsKey(-botNumber)) botNumber++;
        if (botNumber > HARD_MAX_BOTS) return false;
        int userId = -botNumber;
        SnowStormPlayer bot = new SnowStormPlayer(userId, "Bot " + botNumber,
                botFigure(), "M", teamId);
        players.put(userId, bot);
        sequence++;
        return true;
    }

    public synchronized boolean changeTeam(int userId, int teamId) {
        SnowStormPlayer player = players.get(userId);
        if (state != SnowStormState.LOBBY || player == null || !validTeam(teamId)
                || player.teamId() == teamId || teamSize(teamId) >= teamCapacity) {
            return false;
        }
        player.setTeamId(teamId);
        sequence++;
        return true;
    }

    public synchronized SnowStormPlayer removePlayer(int userId) {
        SnowStormPlayer removed = players.remove(userId);
        if (removed != null) {
            if (simulation != null) {
                simulation.removeUser(userId);
                departedStartingUsers.add(userId);
            }
            sequence++;
        }
        return removed;
    }

    public synchronized boolean addSpectator(int userId) {
        if ((state != SnowStormState.COUNTDOWN && state != SnowStormState.RUNNING)
                || players.containsKey(userId) || spectators.size() >= maximumSpectators) {
            return false;
        }
        return spectators.add(userId);
    }

    public synchronized boolean removeSpectator(int userId) {
        return spectators.remove(userId);
    }

    public synchronized boolean hasPlayer(int userId) {
        return players.containsKey(userId);
    }

    /** Active SnowStorm simulations are never allowed to become bot-only. */
    public synchronized boolean hasHumanPlayer() {
        return players.values().stream().anyMatch(player -> !player.isBot());
    }

    public synchronized int playerTeam(int userId) {
        SnowStormPlayer player = players.get(userId);
        return player == null ? -1 : player.teamId();
    }

    public synchronized boolean canStart(int actorUserId) {
        return actorUserId == ownerUserId && state == SnowStormState.LOBBY && hasOpposition();
    }

    public synchronized boolean hasOpposition() {
        if (teamCount == 1) return players.size() >= 2;
        return players.values().stream().map(SnowStormPlayer::teamId).distinct().count() >= 2;
    }

    public synchronized boolean rankedEligible(boolean requireHumanOpposition) {
        if (!requireHumanOpposition) {
            return true;
        }
        if (teamCount == 1) return players.values().stream().filter(player -> !player.isBot()).count() >= 2;
        return players.values().stream().filter(player -> !player.isBot())
                .map(SnowStormPlayer::teamId).distinct().count() >= 2;
    }

    /** Freeze a stable user/object/spawn roster for the transaction; no state is changed yet. */
    public synchronized SnowStormStartingRoster prepareStart(
            int actorUserId,
            boolean requireHumanOpposition) {
        if (!canStart(actorUserId)) {
            return null;
        }
        List<SnowStormPlayer> ordered = players.values().stream()
                .sorted(Comparator.comparingInt(SnowStormPlayer::userId)).toList();
        int nextRound = roundNumber + 1;
        int seed = map.checksum() ^ id ^ nextRound;
        SnowStormSpawnAllocator.Allocation allocation = SnowStormSpawnAllocator.allocate(
                map, teamCount, ordered.size(), seed);
        List<SnowStormStartingRoster.Player> frozen = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            SnowStormPlayer player = ordered.get(index);
            frozen.add(new SnowStormStartingRoster.Player(index + 1, player.userId(), player.name(),
                    player.figure(), player.sex(), player.teamId(), player.isBot(),
                    allocation.positions().get(index).tile()));
        }
        return new SnowStormStartingRoster(id, nextRound, rankedEligible(requireHumanOpposition), frozen);
    }

    /** Apply the protected state transition only after the exact prepared roster was charged. */
    public synchronized boolean beginChargedRound(
            SnowStormStartingRoster chargedRoster,
            long now) {
        return beginChargedRound(chargedRoster, -1, now);
    }

    public synchronized boolean beginChargedRound(
            SnowStormStartingRoster chargedRoster,
            int chargedResultId,
            long now) {
        if (state != SnowStormState.LOBBY || chargedRoster == null || chargedRoster.instanceId() != id
                || chargedRoster.roundNumber() != roundNumber + 1
                || chargedResultId == 0
                || !sameCurrentRoster(chargedRoster)) {
            return false;
        }
        List<SnowStormSimulation.PlayerSeed> seeds = chargedRoster.players().stream()
                .map(player -> new SnowStormSimulation.PlayerSeed(player.userId(), player.teamId(),
                        map.toWorldX(player.spawn().x()), map.toWorldY(player.spawn().y()),
                        0, player.bot(), SnowStormAvatarActivity.MAX_AMMO))
                .toList();
        this.simulation = new SnowStormSimulation(map, teamCount, seeds);
        this.startingRoster = chargedRoster;
        this.roundNumber = chargedRoster.roundNumber();
        this.resultId = chargedResultId;
        this.departedStartingUsers.clear();
        this.idleWarnings.clear();
        this.idleRemovals.clear();
        this.botStrategy.reset();
        players.values().forEach(player -> player.markActive(now));
        this.state = SnowStormState.COUNTDOWN;
        this.stateEndsAt = now + COUNTDOWN_MS;
        this.sequence++;
        return true;
    }

    public synchronized boolean beginRunning(long now) {
        if (state != SnowStormState.COUNTDOWN || now < stateEndsAt || simulation == null) {
            return false;
        }
        state = SnowStormState.RUNNING;
        stateEndsAt = now + roundSeconds * 1_000L;
        sequence++;
        return true;
    }

    public synchronized SnowStormSimulation.TurnResult advance(long now) {
        if (state == SnowStormState.COUNTDOWN) {
            beginRunning(now);
        }
        if (state != SnowStormState.RUNNING || simulation == null || now >= stateEndsAt) {
            return null;
        }
        botStrategy.direct(this, simulation, now);
        SnowStormSimulation.TurnResult result = simulation.advanceTurn();
        sequence++;
        return result;
    }

    public synchronized int timeRemainingMs(long now) {
        if (state != SnowStormState.COUNTDOWN && state != SnowStormState.RUNNING) return 0;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, stateEndsAt - now));
    }

    public synchronized boolean submitIntention(
            int userId,
            int clientActionSequence,
            SnowStormIntention intention) {
        return submitIntention(userId, clientActionSequence, intention, 0L);
    }

    public synchronized boolean submitIntention(
            int userId,
            int clientActionSequence,
            SnowStormIntention intention,
            long now) {
        if (state != SnowStormState.RUNNING || simulation == null || intention == null
                || !players.containsKey(userId) || clientActionSequence < 0
                || clientActionSequence <= lastClientActionSequences.getOrDefault(userId, -1)) {
            return false;
        }
        SnowStormStartingRoster.Player actor = startingRoster.players().stream()
                .filter(player -> player.userId() == userId).findFirst().orElse(null);
        if (actor == null || !validIntention(actor, intention)
                || !simulation.canSubmit(actor.objectId(), intention)) {
            return false;
        }
        lastClientActionSequences.put(userId, clientActionSequence);
        simulation.submit(actor.objectId(), intention);
        if (now > 0) players.get(userId).markActive(now);
        return true;
    }

    synchronized boolean submitBotIntention(int userId, SnowStormIntention intention) {
        SnowStormPlayer bot = players.get(userId);
        if (state != SnowStormState.RUNNING || simulation == null || intention == null
                || bot == null || !bot.isBot()) return false;
        SnowStormStartingRoster.Player actor = startingRoster.players().stream()
                .filter(player -> player.userId() == userId && player.bot()).findFirst().orElse(null);
        if (actor == null || !validIntention(actor, intention)
                || !simulation.canSubmit(actor.objectId(), intention)) return false;
        simulation.submit(actor.objectId(), intention);
        return true;
    }

    /** Wall-clock liveness policy; simulation time and checksums remain deterministic. */
    public synchronized void checkIdle(long now) {
        if (state != SnowStormState.RUNNING) return;
        List<Integer> removals = new ArrayList<>();
        for (SnowStormPlayer player : players.values()) {
            if (player.isBot()) continue;
            if (player.lastActionAt() <= 0) player.markActive(now);
            long remaining = idleTimeoutMs - (now - player.lastActionAt());
            if (remaining <= 0) {
                removals.add(player.userId());
            } else if (remaining <= idleWarningMs && !player.idleWarningSent()) {
                player.setIdleWarningSent(true);
                idleWarnings.put(player.userId(), (int) Math.max(1L, (remaining + 999L) / 1_000L));
            }
        }
        for (int userId : removals) {
            if (removePlayer(userId) != null) idleRemovals.add(userId);
        }
    }

    public synchronized Map<Integer, Integer> consumeIdleWarnings() {
        Map<Integer, Integer> result = Map.copyOf(idleWarnings);
        idleWarnings.clear();
        return result;
    }

    public synchronized Set<Integer> consumeIdleRemovals() {
        Set<Integer> result = Set.copyOf(idleRemovals);
        idleRemovals.clear();
        return result;
    }

    public synchronized boolean end(long now) {
        if (state != SnowStormState.COUNTDOWN && state != SnowStormState.RUNNING) {
            return false;
        }
        state = SnowStormState.ENDING;
        stateEndsAt = now + NEXT_GAME_COUNTDOWN_MS;
        sequence++;
        return true;
    }

    public synchronized int nextGameCountdownMs(long now) {
        if (state != SnowStormState.ENDING) return 0;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, stateEndsAt - now));
    }

    /** Reset only after an immutable result has been captured and queued for persistence. */
    public synchronized boolean resetForNextRound(long now) {
        if (state != SnowStormState.ENDING || now < stateEndsAt) return false;
        state = SnowStormState.LOBBY;
        stateEndsAt = 0L;
        simulation = null;
        startingRoster = null;
        resultId = -1;
        departedStartingUsers.clear();
        idleWarnings.clear();
        idleRemovals.clear();
        lastClientActionSequences.clear();
        botStrategy.reset();
        sequence++;
        return true;
    }

    public synchronized void dispose() {
        state = SnowStormState.DISPOSED;
        players.clear();
        spectators.clear();
        simulation = null;
        startingRoster = null;
        resultId = -1;
        departedStartingUsers.clear();
        idleWarnings.clear();
        idleRemovals.clear();
        lastClientActionSequences.clear();
        botStrategy.reset();
        sequence++;
    }

    public record FullStatusSnapshot(
            int instanceId,
            int sequence,
            SnowStormState state,
            int roundNumber,
            int turnNumber,
            int timeRemainingMs,
            int playerObjectId,
            boolean ranked,
            int teamCount,
            SnowStormStartingRoster roster,
            Map<Integer, Integer> teamScores,
            List<SnowStormSimulation.ObjectSnapshot> objects,
            int checksum) {
        public FullStatusSnapshot {
            teamScores = Map.copyOf(teamScores);
            objects = List.copyOf(objects);
        }
    }

    private boolean sameCurrentRoster(SnowStormStartingRoster roster) {
        if (roster.players().size() != players.size()) {
            return false;
        }
        for (SnowStormStartingRoster.Player player : roster.players()) {
            SnowStormPlayer current = players.get(player.userId());
            if (current == null || current.teamId() != player.teamId()
                    || !current.name().equals(player.name()) || !current.figure().equals(player.figure())
                    || !current.sex().equals(player.sex()) || current.isBot() != player.bot()) {
                return false;
            }
        }
        return true;
    }

    private boolean validIntention(
            SnowStormStartingRoster.Player actor,
            SnowStormIntention intention) {
        return switch (intention) {
            case SnowStormIntention.Walk walk -> map.toTile(
                    walk.targetWorldX(), walk.targetWorldY()).filter(map::isWalkable).isPresent();
            case SnowStormIntention.ThrowLocation locationThrow -> map.toTile(
                    locationThrow.targetWorldX(), locationThrow.targetWorldY()).isPresent();
            case SnowStormIntention.ThrowAvatar targetThrow -> startingRoster.players().stream()
                    .anyMatch(player -> player.objectId() == targetThrow.targetObjectId()
                            && player.objectId() != actor.objectId()
                            && (teamCount == 1 || player.teamId() != actor.teamId())
                            && players.containsKey(player.userId()));
            case SnowStormIntention.MakeSnowball ignored -> true;
            case SnowStormIntention.CancelMakeSnowball ignored -> true;
        };
    }

    private boolean validTeam(int teamId) {
        return teamId >= 0 && teamId < teamCount;
    }

    private long teamSize(int teamId) {
        return players.values().stream().filter(player -> player.teamId() == teamId).count();
    }

    /**
     * Builds varied bot faces from the configured figure pool. SnowStorm replaces the body with
     * its uniform client-side, so independently choosing the visible head and hair gives useful
     * variation without making gameplay state or checksums random.
     */
    private String botFigure() {
        String configured = Emulator.getConfig() == null ? DEFAULT_BOT_FIGURES
                : Emulator.getConfig().getValue("snowstorm.bots.figures", DEFAULT_BOT_FIGURES);
        List<String> figures = new ArrayList<>();
        for (String value : configured.split(",")) if (!value.isBlank()) figures.add(value.trim());
        if (figures.isEmpty()) figures.add(DEFAULT_BOT_FIGURES.substring(0, DEFAULT_BOT_FIGURES.indexOf(',')));

        List<String> heads = figureParts(figures, "hd-");
        List<String> hairs = figureParts(figures, "hr-");
        String template = figures.get(Emulator.getRandom().nextInt(figures.size()));
        List<String> parts = new ArrayList<>();
        if (!heads.isEmpty()) parts.add(heads.get(Emulator.getRandom().nextInt(heads.size())));
        if (!hairs.isEmpty()) parts.add(hairs.get(Emulator.getRandom().nextInt(hairs.size())));
        for (String part : template.split("\\.")) {
            if (!part.startsWith("hd-") && !part.startsWith("hr-") && !part.isBlank()) {
                parts.add(part);
            }
        }
        return String.join(".", parts);
    }

    private static List<String> figureParts(List<String> figures, String prefix) {
        List<String> result = new ArrayList<>();
        for (String figure : figures) {
            for (String part : figure.split("\\.")) {
                if (part.startsWith(prefix) && !result.contains(part)) result.add(part);
            }
        }
        return result;
    }
}
