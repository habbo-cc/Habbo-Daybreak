package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.tickets.HotelTicketService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormFinishedGameComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormFullGameStatusComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameEndComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameInstanceComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameLocationComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameResetComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameStartComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormGameStatusComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormIdleWarningComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormInstanceListComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormInstanceRemovedComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormJoinParametersComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormLoungeInfoComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormSkillLevelChangedComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormSpectatorCountComposer;
import com.eu.habbo.messages.outgoing.games.snowstorm.SnowStormPlayerRemovedComposer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SnowStorm lounge, membership, charging and per-instance simulation owner. */
public final class SnowStormManager {
    public static final int SUCCESS = -1;
    public static final int FAILURE_UNKNOWN = 0;
    public static final int FAILURE_NOT_FOUND = 1;
    public static final int FAILURE_NOT_OWNER = 2;
    public static final int FAILURE_BAD_STATE = 3;
    public static final int FAILURE_FULL = 4;
    public static final int FAILURE_BAD_TEAM = 5;
    public static final int FAILURE_BAD_PARAMETERS = 6;
    public static final int FAILURE_INSUFFICIENT_TICKETS = 7;
    public static final int FAILURE_ALREADY_IN_GAME = 8;
    public static final int FAILURE_NOT_ENOUGH_OPPOSITION = 9;
    public static final int FAILURE_NOT_A_PLAYER = 10;
    public static final int FAILURE_NOT_ALLOWED = 11;
    public static final int FAILURE_RATE_LIMITED = 12;
    public static final int FAILURE_MAP_UNAVAILABLE = 13;
    public static final int FAILURE_PROTOCOL_MISMATCH = 14;

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowStormManager.class);
    private static final String LOUNGE_MODEL = "snowwar_lobby_1";
    private static final int MAX_TICK_FAILURES = 3;
    private static final long LOUNGE_INTERVAL_MS = 1_000L;
    private static final long MUTATION_INTERVAL_MS = 500L;
    private static final long WALK_INTERVAL_MS = 100L;
    private static final long ACTION_INTERVAL_MS = 200L;
    private static final long FULL_STATUS_INTERVAL_MS = 1_000L;
    private static final long FINISHED_RESULT_INTERVAL_MS = 1_000L;
    private static final long REQUEST_ENTRY_RETENTION_MS = 60_000L;
    private static final int REMOVAL_KICKED = 0;
    private static final int REMOVAL_IDLE = 1;
    private static final int REMOVAL_PARTICIPANT_LEFT = 2;
    private static final long PERSISTENCE_RETRY_INITIAL_MS = 250L;
    private static final long PERSISTENCE_RETRY_MAX_MS = 30_000L;

    private enum RequestAction { LOUNGE, CREATE, DELETE, JOIN_VIEW, JOIN, LEAVE, KICK, BOT, WATCH, START, WALK, ACTION, FULL_STATUS, FINISHED_RESULT }
    private record RequestKey(int userId, int instanceId, RequestAction action) {}

    public record Creation(String name, int mapId, int lengthChoice, int teamCount, int selectedTeam) {}
    public record Result(SnowStormGame game, int failure) {
        public boolean success() { return failure == SUCCESS; }
        static Result success(SnowStormGame game) { return new Result(game, SUCCESS); }
        static Result failed(int failure) { return new Result(null, failure); }
    }

    private final SnowStormRepository repository = new SnowStormRepository();
    private final Map<Integer, SnowStormGame> games = new ConcurrentHashMap<>();
    private final Map<Integer, SnowStormFinishedGame> finishedGames = new ConcurrentHashMap<>();
    private final Map<Integer, SnowStormRoundResult> endingResults = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> userGames = new ConcurrentHashMap<>();
    private final Set<Integer> lounge = ConcurrentHashMap.newKeySet();
    private final Set<Integer> startingGames = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> tickFailures = new ConcurrentHashMap<>();
    private final Map<RequestKey, Long> requestTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastPublishedSequences = new ConcurrentHashMap<>();
    private final Map<Integer, SnowStormRoundResult> pendingPersistence = new ConcurrentHashMap<>();
    private final ScheduledExecutorService persistenceExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "SnowStormPersistence");
        thread.setDaemon(false);
        return thread;
    });
    private final AtomicInteger nextInstanceId = new AtomicInteger(1);
    private final AtomicLong lastRequestCleanup = new AtomicLong();
    private final AtomicLong lastSlowTickWarning = new AtomicLong();
    private volatile SnowStormMapCatalog mapCatalog;
    private volatile boolean enabled;
    private ScheduledFuture<?> tickTask;

    public SnowStormManager() {
        long startedAt = System.currentTimeMillis();
        enabled = Emulator.getConfig().getBoolean("snowstorm.enabled", false);
        if (!enabled) return;
        try {
            int reconciled = repository.abortUnfinishedRounds(SnowStormRoundResult.END_SERVER_SHUTDOWN);
            if (reconciled < 0) {
                throw new IllegalStateException("Could not reconcile unfinished SnowStorm rounds");
            }
            if (reconciled > 0) {
                LOGGER.warn("SnowStorm Manager -> Reconciled {} unfinished charged round(s) from a previous process.",
                        reconciled);
            }
            mapCatalog = repository.loadAndValidateMaps();
            for (SnowStormFinishedGame result : repository.loadRecentResults(SnowStormProtocol.MAX_FINISHED_ROWS)) {
                finishedGames.put(result.resultId(), result);
            }
            nextInstanceId.set(repository.nextInstanceId());
            tickTask = Emulator.getThreading().getService().scheduleAtFixedRate(
                    this::tick, SnowStormMath.TURN_MS, SnowStormMath.TURN_MS, TimeUnit.MILLISECONDS);
            LOGGER.info("SnowStorm Manager -> Loaded! ({} MS, {} maps)",
                    System.currentTimeMillis() - startedAt, mapCatalog.maps().size());
            LOGGER.debug("SnowStorm Manager -> Started authoritative 300 MS tick loop.");
        } catch (SQLException | IllegalStateException exception) {
            enabled = false;
            mapCatalog = null;
            LOGGER.error("SnowStorm Manager -> Disabled because its map or SQL data is invalid.", exception);
        }
    }

    public boolean enabled() { return enabled; }
    public int currencyType() { return ticketService().currencyType(); }
    public int ticketCost() { return Math.max(0, Emulator.getConfig().getInt("snowstorm.ticket.cost", 2)); }
    public SnowStormMapDefinition map(int mapId) {
        if (!enabled || mapCatalog == null) throw new IllegalStateException("SnowStorm is disabled");
        return mapCatalog.get(mapId);
    }
    public SnowStormGame get(int instanceId) { return enabled ? games.get(instanceId) : null; }

    public List<SnowStormInstanceSummary> instances() {
        if (!enabled) return List.of();
        List<SnowStormInstanceSummary> values = new ArrayList<>();
        games.values().stream().filter(game -> game.state() != SnowStormState.DISPOSED)
                .sorted(Comparator.comparingInt((SnowStormGame game) -> game.state().id())
                        .thenComparing(Comparator.comparingInt(SnowStormGame::id).reversed()))
                .map(SnowStormInstanceSummary::live).forEach(values::add);
        finishedGames.values().stream()
                .sorted(Comparator.comparingInt(SnowStormFinishedGame::endedAt).reversed()
                        .thenComparing(Comparator.comparingInt(SnowStormFinishedGame::resultId).reversed()))
                .limit(SnowStormProtocol.MAX_FINISHED_ROWS).map(SnowStormInstanceSummary::finished)
                .forEach(values::add);
        return List.copyOf(values);
    }

    public boolean enterLounge(Habbo habbo) {
        return subscribeLounge(habbo) == SUCCESS;
    }

    public int sendLounge(Habbo habbo) {
        int subscription = subscribeLounge(habbo);
        if (subscription != SUCCESS) return subscription;
        int userId = habbo.getHabboInfo().getId();
        SnowStormRepository.PlayerStats stats = repository.playerStats(userId);
        sendSafely(habbo, new SnowStormLoungeInfoComposer(ticketCost(), stats.skillLevel(),
                stats.rankedPoints(), ticketService().balance(habbo),
                mapCatalog.maps().stream().map(SnowStormMapDefinition::mapId).toList()), "lounge info");
        sendSafely(habbo, new SnowStormInstanceListComposer(instances()), "instance list");
        return SUCCESS;
    }

    public boolean inLounge(int userId) {
        if (!enabled || !lounge.contains(userId)) return false;
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (!isInLoungeRoom(habbo)) {
            lounge.remove(userId);
            return false;
        }
        return true;
    }

    public void leaveLounge(int userId) { lounge.remove(userId); }

    public synchronized Result create(Habbo habbo, Creation creation) {
        if (!enabled || !validHabbo(habbo)) return Result.failed(FAILURE_NOT_ALLOWED);
        int userId = habbo.getHabboInfo().getId();
        if (!isSubscribedLoungeUser(habbo)) return Result.failed(FAILURE_NOT_ALLOWED);
        Integer existingId = userGames.get(userId);
        if (existingId != null) {
            SnowStormGame existing = games.get(existingId);
            return existing != null && existing.ownerUserId() == userId
                    && existing.state() == SnowStormState.LOBBY
                    ? Result.success(existing) : Result.failed(FAILURE_ALREADY_IN_GAME);
        }
        if (!allowRequest(userId, 0, RequestAction.CREATE, MUTATION_INTERVAL_MS)) {
            return Result.failed(FAILURE_RATE_LIMITED);
        }
        if (!canAffordRound(habbo)) return Result.failed(FAILURE_INSUFFICIENT_TICKETS);
        if (!validCreation(creation)) return Result.failed(FAILURE_BAD_PARAMETERS);
        int maximumInstances = Math.max(1, Math.min(SnowStormProtocol.MAX_LIVE_INSTANCES,
                Emulator.getConfig().getInt("snowstorm.max.instances", SnowStormProtocol.MAX_LIVE_INSTANCES)));
        if (games.size() >= maximumInstances) {
            return Result.failed(FAILURE_FULL);
        }
        SnowStormMapDefinition selectedMap;
        try {
            selectedMap = map(creation.mapId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Result.failed(FAILURE_MAP_UNAVAILABLE);
        }
        SnowStormGame game = new SnowStormGame(nextInstanceId.getAndIncrement(), creation.name(),
                userId, habbo.getHabboInfo().getUsername(), selectedMap, creation.lengthChoice(),
                creation.teamCount(), Math.min(20,
                Math.max(0, Emulator.getConfig().getInt("snowstorm.max.spectators", 20))),
                Emulator.getConfig().getInt("snowstorm.idle.timeout.seconds", 60),
                Emulator.getConfig().getInt("snowstorm.idle.warning.seconds", 15));
        if (!game.addPlayer(userId, habbo.getHabboInfo().getUsername(),
                habbo.getHabboInfo().getLook(), habbo.getHabboInfo().getGender().name(),
                creation.selectedTeam())) return Result.failed(FAILURE_BAD_TEAM);
        games.put(game.id(), game);
        userGames.put(userId, game.id());
        broadcastLobby(game);
        return Result.success(game);
    }

    public Result initiateJoin(Habbo habbo, int instanceId) {
        if (!enabled || !validHabbo(habbo)) return Result.failed(FAILURE_NOT_ALLOWED);
        SnowStormGame game = games.get(instanceId);
        if (game == null) return Result.failed(FAILURE_NOT_FOUND);
        int userId = habbo.getHabboInfo().getId();
        Integer current = userGames.get(userId);
        if (current == null && !isSubscribedLoungeUser(habbo)) {
            return Result.failed(FAILURE_NOT_ALLOWED);
        }
        if (current != null && current != instanceId) return Result.failed(FAILURE_ALREADY_IN_GAME);
        if (game.state() != SnowStormState.LOBBY) return Result.failed(FAILURE_BAD_STATE);
        if (!allowRequest(userId, instanceId, RequestAction.JOIN_VIEW, MUTATION_INTERVAL_MS)) {
            return Result.failed(FAILURE_RATE_LIMITED);
        }
        if (!canAffordRound(habbo)) return Result.failed(FAILURE_INSUFFICIENT_TICKETS);
        return Result.success(game);
    }

    public synchronized Result join(Habbo habbo, int instanceId, int teamId) {
        if (!enabled || !validHabbo(habbo)) return Result.failed(FAILURE_NOT_ALLOWED);
        SnowStormGame game = games.get(instanceId);
        if (game == null) return Result.failed(FAILURE_NOT_FOUND);
        int userId = habbo.getHabboInfo().getId();
        Integer currentId = userGames.get(userId);
        if (currentId == null && !isSubscribedLoungeUser(habbo)) {
            return Result.failed(FAILURE_NOT_ALLOWED);
        }
        if (currentId != null && currentId != instanceId) return Result.failed(FAILURE_ALREADY_IN_GAME);
        if (game.state() != SnowStormState.LOBBY) return Result.failed(FAILURE_BAD_STATE);
        if (teamId < 0 || teamId >= game.teamCount()) return Result.failed(FAILURE_BAD_TEAM);
        if (game.hasPlayer(userId) && game.playerTeam(userId) == teamId) {
            publishWaitingRoster(game);
            return Result.success(game);
        }
        if (!allowRequest(userId, instanceId, RequestAction.JOIN, MUTATION_INTERVAL_MS)) {
            return Result.failed(FAILURE_RATE_LIMITED);
        }
        if (!canAffordRound(habbo)) return Result.failed(FAILURE_INSUFFICIENT_TICKETS);
        if (game.hasPlayer(userId)) {
            if (!game.changeTeam(userId, teamId)) return Result.failed(FAILURE_BAD_TEAM);
            broadcastLobby(game);
            publishWaitingRoster(game);
            return Result.success(game);
        }
        if (!game.addPlayer(userId, habbo.getHabboInfo().getUsername(),
                habbo.getHabboInfo().getLook(), habbo.getHabboInfo().getGender().name(), teamId)) {
            return Result.failed(FAILURE_FULL);
        }
        game.removeSpectator(userId);
        userGames.put(userId, instanceId);
        broadcastLobby(game);
        publishWaitingRoster(game);
        return Result.success(game);
    }

    public synchronized int leave(Habbo habbo, int instanceId) {
        if (!enabled || !validHabbo(habbo)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        int userId = habbo.getHabboInfo().getId();
        if (!allowRequest(userId, instanceId, RequestAction.LEAVE, MUTATION_INTERVAL_MS)) {
            return FAILURE_RATE_LIMITED;
        }
        if (game.hasPlayer(userId)) {
            disposeForParticipantDeparture(game, userId);
            return SUCCESS;
        }
        if (!game.removeSpectator(userId)) return FAILURE_NOT_A_PLAYER;
        userGames.remove(userId, instanceId);
        publishSpectatorCount(game);
        return SUCCESS;
    }

    public synchronized int kick(Habbo actor, int instanceId, int targetUserId) {
        if (!enabled || !validHabbo(actor)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != SnowStormState.LOBBY || targetUserId == game.ownerUserId()) return FAILURE_BAD_STATE;
        SnowStormPlayer targetPlayer = game.players().stream()
                .filter(player -> player.userId() == targetUserId && !player.isBot()).findFirst().orElse(null);
        if (targetPlayer == null) return FAILURE_NOT_A_PLAYER;
        if (!allowRequest(actor.getHabboInfo().getId(), instanceId,
                RequestAction.KICK, MUTATION_INTERVAL_MS)) return FAILURE_RATE_LIMITED;
        if (game.removePlayer(targetUserId) == null) return FAILURE_NOT_A_PLAYER;
        userGames.remove(targetUserId, instanceId);
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetUserId);
        sendSafely(target, new SnowStormPlayerRemovedComposer(
                instanceId, REMOVAL_KICKED), "kicked player removal");
        broadcastLobby(game);
        publishWaitingRoster(game);
        return SUCCESS;
    }

    /** Add one game-scoped bot. Bots never enter lounge or user/session membership maps. */
    public synchronized int addBot(Habbo actor, int instanceId, int teamId) {
        if (!enabled || !validHabbo(actor)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != SnowStormState.LOBBY) return FAILURE_BAD_STATE;
        if (teamId < 0 || teamId >= game.teamCount()) return FAILURE_BAD_TEAM;
        if (!allowRequest(actor.getHabboInfo().getId(), instanceId,
                RequestAction.BOT, MUTATION_INTERVAL_MS)) return FAILURE_RATE_LIMITED;
        if (!game.addBot(teamId)) return game.players().size() >= SnowStormGame.MAX_PLAYERS
                ? FAILURE_FULL : FAILURE_NOT_ALLOWED;
        broadcastLobby(game);
        publishWaitingRoster(game);
        return SUCCESS;
    }

    /** Remove only a sessionless bot; human removal continues to use the owner kick operation. */
    public synchronized int removeBot(Habbo actor, int instanceId, int botUserId) {
        if (!enabled || !validHabbo(actor)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != SnowStormState.LOBBY) return FAILURE_BAD_STATE;
        SnowStormPlayer bot = game.players().stream()
                .filter(player -> player.userId() == botUserId && player.isBot()).findFirst().orElse(null);
        if (bot == null) return FAILURE_NOT_A_PLAYER;
        if (!allowRequest(actor.getHabboInfo().getId(), instanceId,
                RequestAction.BOT, MUTATION_INTERVAL_MS)) return FAILURE_RATE_LIMITED;
        game.removePlayer(botUserId);
        broadcastLobby(game);
        publishWaitingRoster(game);
        return SUCCESS;
    }

    public synchronized int watch(Habbo habbo, int instanceId) {
        if (!enabled || !validHabbo(habbo)) return FAILURE_NOT_ALLOWED;
        if (!isSubscribedLoungeUser(habbo)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        int userId = habbo.getHabboInfo().getId();
        Integer currentId = userGames.get(userId);
        if (currentId != null && currentId != instanceId) return FAILURE_ALREADY_IN_GAME;
        if (game.hasPlayer(userId)) return FAILURE_BAD_STATE;
        if (!allowRequest(userId, instanceId, RequestAction.WATCH, MUTATION_INTERVAL_MS)) {
            return FAILURE_RATE_LIMITED;
        }
        if (!game.addSpectator(userId)) return FAILURE_FULL;
        userGames.put(userId, instanceId);
        lounge.remove(userId);
        sendSafely(habbo, new SnowStormGameLocationComposer(game, userId, true), "spectator location");
        publishSpectatorCount(game);
        return SUCCESS;
    }

    public synchronized int delete(Habbo actor, int instanceId) {
        if (!enabled || !validHabbo(actor)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != SnowStormState.LOBBY) return FAILURE_BAD_STATE;
        if (!allowRequest(actor.getHabboInfo().getId(), instanceId,
                RequestAction.DELETE, MUTATION_INTERVAL_MS)) return FAILURE_RATE_LIMITED;
        disposeGame(game);
        return SUCCESS;
    }

    public int start(Habbo actor, int instanceId) {
        if (!enabled || !validHabbo(actor)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (!startingGames.add(instanceId)) return SUCCESS;
        boolean disposeFailedStart = false;
        try {
            synchronized (game) {
                if (games.get(instanceId) != game) return FAILURE_NOT_FOUND;
                int actorId = actor.getHabboInfo().getId();
                if (game.ownerUserId() != actorId) return FAILURE_NOT_OWNER;
                if (game.state() == SnowStormState.COUNTDOWN || game.state() == SnowStormState.RUNNING) return SUCCESS;
                if (game.state() != SnowStormState.LOBBY) return FAILURE_BAD_STATE;
                if (!allowRequest(actorId, instanceId, RequestAction.START, MUTATION_INTERVAL_MS)) {
                    return FAILURE_RATE_LIMITED;
                }
                boolean requireHumanOpposition = Emulator.getConfig().getBoolean(
                        "snowstorm.ranked.requires.human.opponent", true);
                SnowStormStartingRoster roster = game.prepareStart(actorId, requireHumanOpposition);
                if (roster == null) return FAILURE_NOT_ENOUGH_OPPOSITION;
                List<Habbo> participants = new ArrayList<>();
                for (SnowStormStartingRoster.Player player : roster.players()) {
                    if (player.bot()) continue;
                    Habbo participant = Emulator.getGameEnvironment().getHabboManager().getHabbo(player.userId());
                    if (participant == null) return FAILURE_BAD_STATE;
                    participants.add(participant);
                }
                SnowStormRepository.ChargeResult charge = ticketService().withWalletLocks(
                        participants, () -> repository.chargeRound(game, roster, participants,
                                currencyType(), ticketCost()));
                if (!charge.success()) {
                    return switch (charge.status()) {
                        case INSUFFICIENT_TICKETS -> FAILURE_INSUFFICIENT_TICKETS;
                        case REJECTED -> FAILURE_NOT_ALLOWED;
                        default -> FAILURE_UNKNOWN;
                    };
                }
                for (Habbo participant : participants) {
                    int userId = participant.getHabboInfo().getId();
                    Integer balance = charge.balances().get(userId);
                    if (balance != null) {
                        ticketService().applyCommittedBalance(participant, balance);
                        ticketService().sendCommittedBalance(participant, balance,
                                -charge.costs().getOrDefault(userId, ticketCost()));
                    }
                }
                if (!game.beginChargedRound(roster, charge.resultId(), System.currentTimeMillis())) {
                    LOGGER.error("SnowStorm -> Charged instance {} but its protected start transition failed.", instanceId);
                    if (!repository.abortChargedRound(charge.resultId(), game.id(), roster.roundNumber(),
                            SnowStormRoundResult.END_INTERNAL_FAILURE)) {
                        LOGGER.error("SnowStorm -> Could not close failed charged instance {} round {} result {}.",
                                game.id(), roster.roundNumber(), charge.resultId());
                    }
                    disposeFailedStart = true;
                } else {
                    for (Habbo participant : participants) {
                        lounge.remove(participant.getHabboInfo().getId());
                    }
                    for (int recipientId : recipientIds(game)) {
                        Habbo recipient = Emulator.getGameEnvironment().getHabboManager().getHabbo(recipientId);
                        boolean spectator = game.spectators().contains(recipientId);
                        sendSafely(recipient, new SnowStormGameLocationComposer(
                                game, recipientId, spectator), "game location");
                        sendSafely(recipient, new SnowStormGameStartComposer(game), "game start");
                    }
                    lastPublishedSequences.put(game.id(), game.sequence());
                    broadcastLobby(game);
                    return SUCCESS;
                }
            }
            if (disposeFailedStart) disposeGame(game);
            return FAILURE_UNKNOWN;
        } finally {
            startingGames.remove(instanceId);
        }
    }

    public int action(
            Habbo actor,
            int instanceId,
            int clientActionSequence,
            SnowStormIntention intention) {
        if (!enabled || !validHabbo(actor)) return FAILURE_NOT_ALLOWED;
        SnowStormGame game = games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        int userId = actor.getHabboInfo().getId();
        if (!game.hasPlayer(userId)) return FAILURE_NOT_A_PLAYER;
        if (game.state() != SnowStormState.RUNNING) return FAILURE_BAD_STATE;
        RequestAction requestAction = intention instanceof SnowStormIntention.Walk
                ? RequestAction.WALK : RequestAction.ACTION;
        long interval = requestAction == RequestAction.WALK ? WALK_INTERVAL_MS : ACTION_INTERVAL_MS;
        if (!allowRequest(userId, instanceId, requestAction, interval)) return FAILURE_RATE_LIMITED;
        return game.submitIntention(userId, clientActionSequence, intention, System.currentTimeMillis())
                ? SUCCESS : FAILURE_NOT_ALLOWED;
    }

    public int requestFull(Habbo habbo, int instanceId, int lastSequence, int reason) {
        SnowStormGame game;
        SnowStormGame.FullStatusSnapshot snapshot;
        int spectatorCount;
        synchronized (this) {
            if (!enabled || !validHabbo(habbo)) return FAILURE_NOT_ALLOWED;
            game = games.get(instanceId);
            if (game == null) return FAILURE_NOT_FOUND;
            int userId = habbo.getHabboInfo().getId();
            if (!hasArenaAccess(userId, game)) return FAILURE_NOT_ALLOWED;
            if (reason < 0 || reason > 3 || lastSequence < -1 || lastSequence > game.sequence()) {
                return FAILURE_BAD_PARAMETERS;
            }
            if (!game.hasActiveSimulation()) return FAILURE_BAD_STATE;
            if (!allowRequest(userId, instanceId, RequestAction.FULL_STATUS, FULL_STATUS_INTERVAL_MS)) {
                return FAILURE_RATE_LIMITED;
            }
            snapshot = game.fullStatusSnapshot(userId, System.currentTimeMillis());
            if (snapshot == null) return FAILURE_BAD_STATE;
            spectatorCount = game.spectators().size();
        }
        sendSafely(habbo, new SnowStormFullGameStatusComposer(snapshot),
                "full game status");
        sendSafely(habbo, new SnowStormSpectatorCountComposer(instanceId, spectatorCount),
                "spectator count recovery");
        return SUCCESS;
    }

    public int requestFinished(Habbo habbo, int resultId) {
        if (!enabled || !validHabbo(habbo) || resultId <= 0) return FAILURE_BAD_PARAMETERS;
        if (!isSubscribedLoungeUser(habbo)) return FAILURE_NOT_ALLOWED;
        int userId = habbo.getHabboInfo().getId();
        if (!allowRequest(userId, 0, RequestAction.FINISHED_RESULT, FINISHED_RESULT_INTERVAL_MS)) {
            return FAILURE_RATE_LIMITED;
        }
        SnowStormFinishedGame result = finishedGames.get(resultId);
        if (result == null) return FAILURE_NOT_FOUND;
        sendSafely(habbo, new SnowStormFinishedGameComposer(result), "finished game result");
        return SUCCESS;
    }

    /** Disconnect boundary called by packet/session integration. */
    public synchronized void disconnect(int userId) {
        lounge.remove(userId);
        requestTimes.keySet().removeIf(key -> key.userId() == userId);
        SnowStormGame game = gameForUser(userId);
        if (game == null) return;
        if (game.hasPlayer(userId)) {
            disposeForParticipantDeparture(game, userId);
            return;
        }
        if (!game.removeSpectator(userId)) return;
        userGames.remove(userId, game.id());
        publishSpectatorCount(game);
    }

    /** Room departures are session departures for the windowed, non-resumable SnowStorm game. */
    public void roomLeave(int userId) {
        disconnect(userId);
    }

    private void tick() {
        if (!enabled) return;
        long tickStarted = System.nanoTime();
        long now = System.currentTimeMillis();
        for (SnowStormGame game : tickGames()) {
            try {
                Set<Integer> recipients = recipientIds(game);
                SnowStormState before = game.state();
                game.checkIdle(now);
                for (Map.Entry<Integer, Integer> warning : game.consumeIdleWarnings().entrySet()) {
                    Habbo warned = Emulator.getGameEnvironment().getHabboManager().getHabbo(warning.getKey());
                    sendSafely(warned, new SnowStormIdleWarningComposer(game.id(), warning.getValue()),
                            "idle warning");
                }
                Set<Integer> idleRemovals = game.consumeIdleRemovals();
                if (!idleRemovals.isEmpty()) {
                    disposeForParticipantDeparture(game, idleRemovals, REMOVAL_IDLE);
                    continue;
                }
                if ((game.state() == SnowStormState.COUNTDOWN || game.state() == SnowStormState.RUNNING)
                        && !game.hasHumanPlayer()) {
                    disposeForParticipantDeparture(game, Set.of(), REMOVAL_PARTICIPANT_LEFT);
                    continue;
                }
                SnowStormSimulation.TurnResult turn = game.advance(now);
                if (turn != null) publishTurn(game, turn, recipients, now);
                if (game.state() == SnowStormState.RUNNING && now >= game.stateEndsAt()) {
                    finalizeRound(game, SnowStormRoundResult.END_TIME_EXPIRED,
                            false, recipients, now);
                }
                if (before != game.state() && game.state() != SnowStormState.ENDING) {
                    broadcastLobby(game);
                }
                ResetDisposition reset = resetEndedGame(game, now);
                if (reset == ResetDisposition.DISPOSED) {
                    broadcastInstanceRemoved(SnowStormInstanceSummary.LIVE, game.id());
                } else if (reset == ResetDisposition.RESET) {
                    publishResetIfCurrent(game, recipients);
                } else if (game.state() == SnowStormState.LOBBY && game.players().isEmpty()) {
                    disposeGame(game);
                }
                clearTickFailure(game);
            } catch (RuntimeException exception) {
                int failures = recordTickFailure(game);
                if (failures < 0) continue;
                LOGGER.error("SnowStorm -> Instance {} tick failed ({}/{}).",
                        game.id(), failures, MAX_TICK_FAILURES, exception);
                if (failures >= MAX_TICK_FAILURES) {
                    try {
                        finalizeRound(game, SnowStormRoundResult.END_INTERNAL_FAILURE,
                                true, recipientIds(game), now);
                    } catch (RuntimeException quarantineException) {
                        LOGGER.error("SnowStorm -> Could not quarantine failed instance {}.",
                                game.id(), quarantineException);
                        SnowStormRoundResult abortedResult = abortActiveRound(
                                game, SnowStormRoundResult.END_INTERNAL_FAILURE);
                        if (abortedResult != null) persistRoundAsync(abortedResult);
                        disposeGame(game);
                    }
                }
            }
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tickStarted);
        long previousWarning = lastSlowTickWarning.get();
        if (elapsedMs > SnowStormMath.TURN_MS && now - previousWarning >= 10_000L
                && lastSlowTickWarning.compareAndSet(previousWarning, now)) {
            long bots = games.values().stream().flatMap(game -> game.players().stream())
                    .filter(SnowStormPlayer::isBot).count();
            LOGGER.warn("SnowStorm -> Tick exceeded its {} MS budget: {} MS across {} instance(s) and {} bot(s).",
                    SnowStormMath.TURN_MS, elapsedMs, games.size(), bots);
        }
    }

    private synchronized void publishTurn(
            SnowStormGame game,
            SnowStormSimulation.TurnResult turn,
            Set<Integer> additionalRecipients,
            long now) {
        if (games.get(game.id()) != game) return;
        int sequence = game.sequence();
        int baseSequence = lastPublishedSequences.getOrDefault(game.id(), sequence - 1);
        lastPublishedSequences.put(game.id(), sequence);
        sendToRecipients(game, additionalRecipients, ignored -> new SnowStormGameStatusComposer(
                game, turn, baseSequence, sequence, game.timeRemainingMs(now)));
    }

    private synchronized void finalizeRound(
            SnowStormGame game,
            int endReason,
            boolean aborted,
            Set<Integer> recipients,
            long now) {
        if (games.get(game.id()) != game || endingResults.containsKey(game.id())) return;
        SnowStormRoundResult result;
        synchronized (game) {
            result = SnowStormRoundResult.capture(game, endReason, aborted);
            if (!game.end(now)) return;
        }
        endingResults.put(game.id(), result);
        sendToRecipients(game, recipients, ignored -> new SnowStormGameEndComposer(
                result, game.nextGameCountdownMs(now)));
        persistRoundAsync(result);
        broadcastLobby(game);
    }

    private void disposeGame(SnowStormGame game) {
        boolean removed;
        synchronized (this) {
            removed = detachGame(game);
        }
        if (removed) broadcastInstanceRemoved(SnowStormInstanceSummary.LIVE, game.id());
    }

    /** Caller must hold the manager monitor. */
    private boolean detachGame(SnowStormGame game) {
        if (game == null || !games.remove(game.id(), game)) return false;
        for (SnowStormPlayer player : game.players()) {
            if (!player.isBot()) userGames.remove(player.userId(), game.id());
        }
        for (int spectator : game.spectators()) userGames.remove(spectator, game.id());
        tickFailures.remove(game.id());
        startingGames.remove(game.id());
        lastPublishedSequences.remove(game.id());
        endingResults.remove(game.id());
        game.dispose();
        return true;
    }

    private synchronized List<SnowStormGame> tickGames() {
        return games.values().stream()
                .filter(game -> !startingGames.contains(game.id()))
                .toList();
    }

    private synchronized ResetDisposition resetEndedGame(SnowStormGame game, long now) {
        if (games.get(game.id()) != game || !game.resetForNextRound(now)) {
            return ResetDisposition.NONE;
        }
        endingResults.remove(game.id());
        lastPublishedSequences.put(game.id(), game.sequence());
        boolean ownerPresent = game.players().stream()
                .anyMatch(player -> player.userId() == game.ownerUserId());
        if (!ownerPresent || game.players().isEmpty()) {
            return detachGame(game) ? ResetDisposition.DISPOSED : ResetDisposition.NONE;
        }
        return ResetDisposition.RESET;
    }

    private synchronized void publishResetIfCurrent(
            SnowStormGame game,
            Set<Integer> recipients) {
        if (games.get(game.id()) != game || game.state() != SnowStormState.LOBBY) return;
        sendToRecipients(game, recipients, ignored -> new SnowStormGameResetComposer(game));
        broadcastLobby(game);
    }

    private synchronized void clearTickFailure(SnowStormGame game) {
        if (games.get(game.id()) == game) tickFailures.remove(game.id());
    }

    private synchronized int recordTickFailure(SnowStormGame game) {
        if (games.get(game.id()) != game) return -1;
        return tickFailures.merge(game.id(), 1, Integer::sum);
    }

    private enum ResetDisposition { NONE, RESET, DISPOSED }

    /**
     * SnowStorm has no resumable participant session. Any enrolled human departure aborts the
     * charged round and closes the instance atomically.
     */
    private synchronized void disposeForParticipantDeparture(SnowStormGame game, int departedUserId) {
        disposeForParticipantDeparture(game, Set.of(departedUserId), REMOVAL_PARTICIPANT_LEFT);
    }

    private synchronized void disposeForParticipantDeparture(
            SnowStormGame game,
            Set<Integer> departedUserIds,
            int removalReason) {
        if (game == null || games.get(game.id()) != game) return;
        Set<Integer> recipients = recipientIds(game);
        recipients.addAll(departedUserIds);
        for (int departedUserId : departedUserIds) {
            game.removePlayer(departedUserId);
            userGames.remove(departedUserId, game.id());
        }
        SnowStormRoundResult abortedResult = abortActiveRound(
                game, SnowStormRoundResult.END_NOT_ENOUGH_OPPOSITION);
        for (int recipientId : recipients) {
            Habbo recipient = Emulator.getGameEnvironment().getHabboManager().getHabbo(recipientId);
            sendSafely(recipient, new SnowStormPlayerRemovedComposer(
                    game.id(), removalReason), "participant departure");
        }
        if (abortedResult != null) persistRoundAsync(abortedResult);
        disposeGame(game);
    }

    /** Captures a terminal result before destroying any charged in-memory state. */
    private SnowStormRoundResult abortActiveRound(SnowStormGame game, int endReason) {
        if (!game.hasActiveSimulation()) return null;
        try {
            synchronized (game) {
                SnowStormRoundResult result = SnowStormRoundResult.capture(game, endReason, true);
                return game.end(System.currentTimeMillis()) ? result : null;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("SnowStorm -> Could not capture aborted instance {} round {} result {}.",
                    game.id(), game.roundNumber(), game.resultId(), exception);
            if (!repository.abortChargedRound(
                    game.resultId(), game.id(), game.roundNumber(), endReason)) {
                LOGGER.error("SnowStorm -> Could not close aborted instance {} round {} result {}.",
                        game.id(), game.roundNumber(), game.resultId());
            }
            return null;
        }
    }

    /** Membership-map lookup with a bounded defensive scan for reconnect/disconnect races. */
    private SnowStormGame gameForUser(int userId) {
        Integer instanceId = userGames.get(userId);
        SnowStormGame mapped = instanceId == null ? null : games.get(instanceId);
        if (mapped != null && (mapped.hasPlayer(userId) || mapped.spectators().contains(userId))) {
            return mapped;
        }
        for (SnowStormGame game : games.values()) {
            if (game.hasPlayer(userId) || game.spectators().contains(userId)) return game;
        }
        if (instanceId != null) userGames.remove(userId, instanceId);
        return null;
    }

    private boolean hasArenaAccess(int userId, SnowStormGame game) {
        return game.hasPlayer(userId) || game.spectators().contains(userId)
                || (game.startingRoster() != null && game.startingRoster().players().stream()
                .anyMatch(player -> player.userId() == userId));
    }

    private Set<Integer> recipientIds(SnowStormGame game) {
        Set<Integer> recipients = new HashSet<>(game.spectators());
        game.players().stream().filter(player -> !player.isBot())
                .forEach(player -> recipients.add(player.userId()));
        return recipients;
    }

    private void sendToRecipients(
            SnowStormGame game,
            Set<Integer> additionalRecipients,
            java.util.function.Function<Habbo, MessageComposer> composer) {
        Set<Integer> recipients = recipientIds(game);
        recipients.addAll(additionalRecipients);
        for (int userId : recipients) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo != null) sendSafely(habbo, composer.apply(habbo), "game publication");
        }
    }

    private void publishSpectatorCount(SnowStormGame game) {
        sendToRecipients(game, Set.of(), ignored -> new SnowStormSpectatorCountComposer(
                game.id(), game.spectators().size()));
        broadcastLobby(game);
    }

    private void broadcastLobby(SnowStormGame game) {
        if (!enabled || game == null || game.state() == SnowStormState.DISPOSED) return;
        SnowStormInstanceSummary summary = SnowStormInstanceSummary.live(game);
        for (int userId : List.copyOf(lounge)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (!isInLoungeRoom(habbo)) {
                lounge.remove(userId);
                continue;
            }
            sendSafely(habbo, new SnowStormGameInstanceComposer(summary), "lobby instance update");
        }
    }

    /** Keep every enrolled waiting player on the same complete team-roster snapshot. */
    private void publishWaitingRoster(SnowStormGame game) {
        if (!enabled || game == null || game.state() != SnowStormState.LOBBY) return;
        sendToRecipients(game, Set.of(), recipient -> {
            int selectedTeam = game.playerTeam(recipient.getHabboInfo().getId());
            return new SnowStormJoinParametersComposer(game, Math.max(0, selectedTeam));
        });
    }

    private void broadcastInstanceRemoved(int rowKind, int id) {
        if (!enabled) return;
        for (int userId : List.copyOf(lounge)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (!isInLoungeRoom(habbo)) {
                lounge.remove(userId);
                continue;
            }
            sendSafely(habbo, new SnowStormInstanceRemovedComposer(rowKind, id), "instance removal");
        }
    }

    private void broadcastLoungeLists() {
        if (!enabled) return;
        List<SnowStormInstanceSummary> snapshot = instances();
        for (int userId : List.copyOf(lounge)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (!isInLoungeRoom(habbo)) {
                lounge.remove(userId);
                continue;
            }
            sendSafely(habbo, new SnowStormInstanceListComposer(snapshot), "finished instance list");
        }
    }

    private void persistRoundAsync(SnowStormRoundResult result) {
        if (result == null || pendingPersistence.putIfAbsent(result.resultId(), result) != null) return;
        schedulePersistenceAttempt(result, 1, 0L);
    }

    private void schedulePersistenceAttempt(SnowStormRoundResult result, int attempt, long delayMs) {
        try {
            persistenceExecutor.schedule(() -> persistRoundAttempt(result, attempt),
                    Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            LOGGER.error("SnowStorm -> Persistence scheduling was rejected for result {} instance {} round {}; "
                            + "the result remains pending for shutdown reconciliation.", result.resultId(),
                    result.instanceId(), result.roundNumber(), exception);
        }
    }

    private void persistRoundAttempt(SnowStormRoundResult result, int attempt) {
        if (pendingPersistence.get(result.resultId()) != result) return;
        SnowStormRepository.PersistResult persisted = repository.persistResult(result);
        if (persisted.success()) {
            pendingPersistence.remove(result.resultId(), result);
            publishPersistedResult(result, persisted);
            return;
        }
        long delay = Math.min(PERSISTENCE_RETRY_MAX_MS,
                PERSISTENCE_RETRY_INITIAL_MS << Math.min(7, Math.max(0, attempt - 1)));
        LOGGER.warn("SnowStorm -> Result {} for instance {} round {} remains pending after attempt {}; "
                        + "retrying in {} MS.", result.resultId(), result.instanceId(), result.roundNumber(),
                attempt, delay);
        schedulePersistenceAttempt(result, attempt + 1, delay);
    }

    private void publishPersistedResult(
            SnowStormRoundResult result,
            SnowStormRepository.PersistResult persisted) {
        SnowStormFinishedGame finished = persisted.finishedGame();
        if (finished != null) {
            finishedGames.put(finished.resultId(), finished);
            trimFinishedGames();
            broadcastLoungeLists();
        }
        if (!result.ranked() || result.aborted()) return;
        for (SnowStormRoundResult.PlayerResult player : result.players()) {
            if (player.userId() <= 0) continue;
            Habbo participant = Emulator.getGameEnvironment().getHabboManager().getHabbo(player.userId());
            SnowStormRepository.PlayerStats stats = repository.playerStats(player.userId());
            sendSafely(participant, new SnowStormSkillLevelChangedComposer(
                    stats.skillLevel(), stats.rankedPoints()), "skill-level update");
        }
    }

    private void trimFinishedGames() {
        List<SnowStormFinishedGame> ordered = finishedGames.values().stream()
                .sorted(Comparator.comparingInt(SnowStormFinishedGame::endedAt).reversed()
                        .thenComparing(Comparator.comparingInt(SnowStormFinishedGame::resultId).reversed()))
                .toList();
        for (int index = SnowStormProtocol.MAX_FINISHED_ROWS; index < ordered.size(); index++) {
            SnowStormFinishedGame stale = ordered.get(index);
            finishedGames.remove(stale.resultId(), stale);
        }
    }

    private boolean sendSafely(Habbo habbo, MessageComposer composer, String context) {
        if (habbo == null || composer == null) return false;
        int userId = habbo.getHabboInfo() == null ? 0 : habbo.getHabboInfo().getId();
        try {
            GameClient client = habbo.getClient();
            if (client == null) return false;
            client.sendResponse(composer);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("SnowStorm -> Could not send {} to user {}.", context, userId, exception);
            return false;
        }
    }

    private boolean canAffordRound(Habbo habbo) { return ticketService().balance(habbo) >= ticketCost(); }
    private synchronized int subscribeLounge(Habbo habbo) {
        if (!enabled || !isInLoungeRoom(habbo)) return FAILURE_NOT_ALLOWED;
        int userId = habbo.getHabboInfo().getId();
        // A room reload may establish the new lounge view before the old socket's disconnect callback
        // runs. Resolve any stale arena membership here too so it can never leave a live zombie game.
        SnowStormGame staleGame = gameForUser(userId);
        if (staleGame != null) {
            requestTimes.keySet().removeIf(key -> key.userId() == userId);
            if (staleGame.hasPlayer(userId)) {
                disposeForParticipantDeparture(staleGame, userId);
            } else if (staleGame.removeSpectator(userId)) {
                userGames.remove(userId, staleGame.id());
                publishSpectatorCount(staleGame);
            }
        }
        if (!allowRequest(userId, 0, RequestAction.LOUNGE, LOUNGE_INTERVAL_MS)) {
            return FAILURE_RATE_LIMITED;
        }
        lounge.add(userId);
        return SUCCESS;
    }
    private boolean isSubscribedLoungeUser(Habbo habbo) {
        return isInLoungeRoom(habbo) && lounge.contains(habbo.getHabboInfo().getId());
    }
    private boolean allowRequest(int userId, int instanceId, RequestAction action, long intervalMs) {
        long now = System.currentTimeMillis();
        long previousCleanup = lastRequestCleanup.get();
        if (now - previousCleanup >= REQUEST_ENTRY_RETENTION_MS
                && lastRequestCleanup.compareAndSet(previousCleanup, now)) {
            requestTimes.entrySet().removeIf(entry -> now - entry.getValue() >= REQUEST_ENTRY_RETENTION_MS);
        }
        RequestKey key = new RequestKey(userId, instanceId, action);
        AtomicBoolean accepted = new AtomicBoolean();
        requestTimes.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= intervalMs) {
                accepted.set(true);
                return now;
            }
            return previous;
        });
        return accepted.get();
    }
    private HotelTicketService ticketService() { return Emulator.getGameEnvironment().getHotelTicketService(); }
    private static boolean validCreation(Creation creation) {
        return creation != null && creation.name() != null && !creation.name().trim().isEmpty()
                && creation.name().trim().length() <= 32 && creation.mapId() >= 1 && creation.mapId() <= 7
                && creation.lengthChoice() >= 1 && creation.lengthChoice() <= 3
                && creation.teamCount() >= 1 && creation.teamCount() <= 4
                && creation.selectedTeam() >= 0 && creation.selectedTeam() < creation.teamCount();
    }
    private static boolean validHabbo(Habbo habbo) { return habbo != null && habbo.getHabboInfo() != null; }
    private static boolean isInLoungeRoom(Habbo habbo) {
        return validHabbo(habbo) && habbo.getClient() != null
                && habbo.getHabboInfo().getCurrentRoom() != null
                && habbo.getHabboInfo().getCurrentRoom().getLayout() != null
                && LOUNGE_MODEL.equalsIgnoreCase(habbo.getHabboInfo().getCurrentRoom().getLayout().getName());
    }

    public void dispose() {
        enabled = false;
        if (tickTask != null) tickTask.cancel(false);
        for (SnowStormGame game : List.copyOf(games.values())) {
            try {
                SnowStormRoundResult result = abortActiveRound(
                        game, SnowStormRoundResult.END_SERVER_SHUTDOWN);
                if (result != null) persistRoundAsync(result);
            } catch (RuntimeException exception) {
                LOGGER.error("SnowStorm -> Failed to close instance {} during shutdown; continuing with "
                        + "the remaining instances.", game.id(), exception);
                if (game.resultId() > 0) {
                    repository.abortChargedRound(game.resultId(), game.id(), game.roundNumber(),
                            SnowStormRoundResult.END_SERVER_SHUTDOWN);
                }
            } finally {
                try {
                    game.dispose();
                } catch (RuntimeException exception) {
                    LOGGER.error("SnowStorm -> Failed to dispose instance {} during shutdown.",
                            game.id(), exception);
                }
            }
        }
        games.clear();
        userGames.clear();
        lounge.clear();
        tickFailures.clear();
        startingGames.clear();
        requestTimes.clear();
        lastPublishedSequences.clear();
        endingResults.clear();
        mapCatalog = null;
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.error("SnowStorm -> Persistence worker did not drain within 30 seconds during shutdown; "
                        + "cancelling delayed retries and attempting a final synchronous pass.");
                persistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            persistenceExecutor.shutdownNow();
            LOGGER.error("SnowStorm -> Interrupted while draining persistence during shutdown.", exception);
        }
        for (SnowStormRoundResult result : List.copyOf(pendingPersistence.values())) {
            SnowStormRepository.PersistResult persisted = repository.persistResult(result);
            if (persisted.success()) {
                pendingPersistence.remove(result.resultId(), result);
            }
        }
        if (!pendingPersistence.isEmpty()) {
            LOGGER.error("SnowStorm -> Shutdown left {} result(s) pending; their result IDs are {}. "
                            + "Startup reconciliation will close them as aborted if this process exits.",
                    pendingPersistence.size(), pendingPersistence.keySet());
        }
        pendingPersistence.clear();
        finishedGames.clear();
    }
}
