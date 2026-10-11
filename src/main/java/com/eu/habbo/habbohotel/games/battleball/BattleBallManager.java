package com.eu.habbo.habbohotel.games.battleball;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.tickets.HotelTicketService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallFullGameStatusComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameEndComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameInstanceComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameLocationComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameStartComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallGameStatusComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallInstanceListComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallLoungeInfoComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallPlayerRemovedComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallSignalComposer;
import com.eu.habbo.messages.outgoing.games.battleball.BattleBallSpectatorCountComposer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BattleBallManager {
    public static final int FAILURE_UNKNOWN = 0;
    public static final int FAILURE_NOT_FOUND = 1;
    public static final int FAILURE_NOT_OWNER = 2;
    public static final int FAILURE_BAD_STATE = 3;
    public static final int FAILURE_FULL = 4;
    public static final int FAILURE_BAD_TEAM = 5;
    public static final int FAILURE_BAD_PARAMETERS = 6;
    public static final int FAILURE_INSUFFICIENT_TICKETS = 7;
    public static final int FAILURE_ALREADY_IN_GAME = 8;
    public static final int FAILURE_NOT_ENOUGH_TEAMS = 9;
    public static final int FAILURE_NOT_A_PLAYER = 10;
    public static final int FAILURE_NOT_ALLOWED = 11;
    public static final int REQUEST_THROTTLED = -2;
    public static final int REMOVAL_KICKED = 0;
    public static final int REMOVAL_IDLE = 1;

    public record Creation(String name, int mapId, int roundSeconds, int teamCount, int teamCapacity,
                           int selectedTeam, boolean privateGame, Set<BattleBallPowerType> powers) {}
    public record Result(BattleBallGame game, int failure) {
        public boolean success() { return this.failure == -1; }
        static Result success(BattleBallGame game) { return new Result(game, -1); }
        static Result failed(int reason) { return new Result(null, reason); }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BattleBallManager.class);
    private static final String LOUNGE_MODEL = "bb_lobby_1";
    private static final long MOVE_INTERVAL_MS = 50L;
    private static final long ACTIVATE_INTERVAL_MS = 250L;
    private static final long FULL_STATUS_INTERVAL_MS = 1_000L;
    private static final long ROSTER_MUTATION_INTERVAL_MS = 500L;
    private static final int MAX_CONSECUTIVE_TICK_FAILURES = 3;
    private enum RequestAction { MOVE, ACTIVATE, FULL_STATUS, ROSTER_MUTATION }
    private record RequestKey(int userId, int instanceId, RequestAction action) {}
    private final BattleBallRepository repository = new BattleBallRepository();
    private final Map<Integer, BattleBallGame> games = new ConcurrentHashMap<>();
    /** Persisted, non-aborted rounds keyed by their positive database result id. */
    private final Map<Integer, BattleBallFinishedGame> finishedGames = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> userGames = new ConcurrentHashMap<>();
    /** Players currently watching the games list, so newly created games appear without a room reload. */
    private final Set<Integer> lounge = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> lastSequences = new ConcurrentHashMap<>();
    private final Set<Integer> startingGames = ConcurrentHashMap.newKeySet();
    private final Map<RequestKey, Long> requestTimestamps = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tickFailures = new ConcurrentHashMap<>();
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "BattleBallPersistence");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger nextInstanceId = new AtomicInteger(1);
    private Map<Integer, BattleBallMap> maps = Map.of();
    private Map<Integer, Map<Integer, List<BattleBallPosition>>> spawns = Map.of();
    private ScheduledFuture<?> tickTask;
    private volatile boolean enabled;

    public BattleBallManager() {
        long millis = System.currentTimeMillis();
        this.enabled = Emulator.getConfig().getBoolean("battleball.enabled", false);
        if (!this.enabled) return;
        try {
            BattleBallRepository.LoadedData loaded = this.repository.load();
            this.maps = loaded.maps();
            this.spawns = loaded.spawns();
            for (BattleBallFinishedGame result : this.repository.loadRecentResults(15)) {
                this.finishedGames.put(result.resultId(), result);
            }
            this.nextInstanceId.set(this.repository.nextInstanceId());
            this.tickTask = Emulator.getThreading().getService().scheduleAtFixedRate(this::tick, 500, 500, TimeUnit.MILLISECONDS);
            LOGGER.info("BattleBall Manager -> Loaded! ({} MS, {} maps)",
                    System.currentTimeMillis() - millis, this.maps.size());
            LOGGER.debug("BattleBall Manager -> Started authoritative 500 MS tick loop.");
        } catch (SQLException | IllegalArgumentException exception) {
            this.enabled = false;
            LOGGER.error("BattleBall Manager -> Disabled because its map or SQL data is invalid.", exception);
        }
    }

    public boolean enabled() { return this.enabled; }
    public int currencyType() { return this.ticketService().currencyType(); }
    public int ticketCost() { return Math.max(0, Emulator.getConfig().getInt("battleball.ticket.cost", 2)); }
    public int skillLevel(int userId) { return this.enabled ? this.repository.skillLevel(userId) : 1; }

    public List<BattleBallInstanceSummary> instancesFor(Habbo habbo) {
        if (!this.enabled || habbo == null || habbo.getHabboInfo() == null) return List.of();
        int userId = habbo.getHabboInfo().getId();
        List<BattleBallInstanceSummary> result = new ArrayList<>();
        this.games.values().stream()
                .filter(game -> game.state() != BattleBallState.ENDING && canDiscover(game, userId))
                .sorted(Comparator.comparingInt((BattleBallGame game) -> game.state().id())
                        .thenComparingInt(BattleBallGame::id))
                .map(BattleBallInstanceSummary::live).forEach(result::add);
        this.finishedGames.values().stream().filter(game -> game.visibleTo(userId))
                .sorted(Comparator.comparingInt(BattleBallFinishedGame::endedAt).reversed()
                        .thenComparing(Comparator.comparingInt(BattleBallFinishedGame::resultId).reversed()))
                .limit(15).map(BattleBallInstanceSummary::finished).forEach(result::add);
        return List.copyOf(result);
    }

    /**
     * Whether this user may see a game, and therefore join or watch it.
     *
     * Filtering only the initial list left "private" cosmetic: join, watch and observe accepted any
     * guessed instance id, and every lobby broadcast pushed private rows to the whole lounge.
     */
    private static boolean canDiscover(BattleBallGame game, int userId) {
        if (!game.privateGame() || game.ownerUserId() == userId) return true;
        return game.players().stream().anyMatch(player -> player.userId() == userId)
                || game.watchers().contains(userId);
    }

    public BattleBallGame get(int instanceId) { return this.enabled ? this.games.get(instanceId) : null; }

    public BattleBallGame discoverableGame(int instanceId, int userId) {
        BattleBallGame game = this.get(instanceId);
        return game != null && canDiscover(game, userId) ? game : null;
    }

    public void sendLounge(Habbo habbo) {
        if (!this.enabled || !isInLoungeRoom(habbo)) return;
        // Asking for the lounge is what marks a player as watching the games list. Without this, a new
        // game only reached players and watchers of games that already existed, so anyone standing in the
        // lounge kept an empty list until they re-entered the room.
        this.lounge.add(habbo.getHabboInfo().getId());
        this.sendSafely(habbo, new BattleBallLoungeInfoComposer(this.ticketCost(),
                this.skillLevel(habbo.getHabboInfo().getId()), this.ticketBalance(habbo)), "lounge info");
        this.sendSafely(habbo, new BattleBallInstanceListComposer(this.instancesFor(habbo)), "instance list");
    }

    /** Whether this player is watching the BattleBall games list. */
    public boolean inLounge(int userId) {
        if (!this.enabled || !this.lounge.contains(userId)) return false;
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (!isInLoungeRoom(habbo)) {
            this.lounge.remove(userId);
            return false;
        }
        return true;
    }

    /** Drops a player from the games-list audience; they re-subscribe by asking for the lounge again. */
    public void leaveLounge(int userId) { this.lounge.remove(userId); }

    public synchronized Result create(Habbo habbo, Creation creation) {
        if (!this.enabled) return Result.failed(FAILURE_NOT_ALLOWED);
        int userId = habbo.getHabboInfo().getId();
        Integer existingId = this.userGames.get(userId);
        if (existingId != null) {
            BattleBallGame existing = this.games.get(existingId);
            return existing != null && existing.ownerUserId() == userId && existing.state() == BattleBallState.LOBBY
                    ? Result.success(existing) : Result.failed(FAILURE_ALREADY_IN_GAME);
        }
        if (!this.canAffordRound(habbo)) return Result.failed(FAILURE_INSUFFICIENT_TICKETS);
        if (!valid(creation) || this.games.size() >= Emulator.getConfig().getInt("battleball.max.instances", 100)) return Result.failed(FAILURE_BAD_PARAMETERS);
        BattleBallMap map = this.maps.get(creation.mapId());
        Map<Integer, List<BattleBallPosition>> mapSpawns = this.spawns.getOrDefault(creation.mapId(), Map.of());
        for (int team = 0; team < creation.teamCount(); team++) if (mapSpawns.getOrDefault(team, List.of()).size() < creation.teamCapacity()) return Result.failed(FAILURE_BAD_PARAMETERS);

        int id = this.nextInstanceId.getAndIncrement();
        BattleBallGame game = new BattleBallGame(id, creation.name(), userId, habbo.getHabboInfo().getUsername(), map,
                creation.roundSeconds(), creation.teamCount(), creation.teamCapacity(), creation.privateGame(), creation.powers(), mapSpawns,
                Emulator.getConfig().getInt("battleball.idle.timeout.seconds", 60),
                Emulator.getConfig().getInt("battleball.idle.warning.seconds", 15));
        if (!game.addPlayer(userId, habbo.getHabboInfo().getUsername(), habbo.getHabboInfo().getMotto(), habbo.getHabboInfo().getLook(),
                habbo.getHabboInfo().getGender().name(), creation.selectedTeam())) return Result.failed(FAILURE_BAD_TEAM);
        this.games.put(id, game); this.userGames.put(userId, id); this.lastSequences.put(id, game.sequence());
        // The creator stays in the lounge. In the original game a created game sat in its lobby window
        // gathering players, and only the start of a round took anyone out of the room they were in.
        this.broadcastLobby(game);
        return Result.success(game);
    }

    public synchronized Result join(Habbo habbo, int instanceId, int teamId) {
        if (!this.enabled) return Result.failed(FAILURE_NOT_ALLOWED);
        BattleBallGame game = this.games.get(instanceId);
        int userId = habbo.getHabboInfo().getId();
        if (game == null) return Result.failed(FAILURE_NOT_FOUND);
        if (!canDiscover(game, userId)) return Result.failed(FAILURE_NOT_FOUND);
        Integer current = this.userGames.get(userId);
        if (current != null) {
            if (current != instanceId) return Result.failed(FAILURE_ALREADY_IN_GAME);
            BattleBallPlayer existingPlayer = game.players().stream()
                    .filter(player -> player.userId() == userId).findFirst().orElse(null);
            if (existingPlayer != null) {
                // Already in this game: picking another team is a switch, not a second join.
                if (!this.allowRosterMutation(userId, instanceId)) return Result.failed(REQUEST_THROTTLED);
                if (!game.changeTeam(userId, teamId)) return Result.failed(FAILURE_BAD_TEAM);
                this.publishLobbyMutation(game);
                return Result.success(game);
            }
            if (!game.watchers().contains(userId)) return Result.failed(FAILURE_NOT_A_PLAYER);
            if (game.state() != BattleBallState.LOBBY) return Result.failed(FAILURE_BAD_STATE);
            if (teamId < 0 || teamId >= game.teams().size()) return Result.failed(FAILURE_BAD_TEAM);
            if (game.teams().get(teamId).full()) return Result.failed(FAILURE_FULL);
            if (!this.canAffordRound(habbo)) return Result.failed(FAILURE_INSUFFICIENT_TICKETS);
            if (!this.allowRosterMutation(userId, instanceId)) return Result.failed(REQUEST_THROTTLED);
            if (!game.addPlayer(userId, habbo.getHabboInfo().getUsername(), habbo.getHabboInfo().getMotto(),
                    habbo.getHabboInfo().getLook(), habbo.getHabboInfo().getGender().name(), teamId)) {
                return Result.failed(FAILURE_FULL);
            }
            game.removeWatcher(userId);
            this.publishSpectatorCount(game);
            this.publishLobbyMutation(game);
            return Result.success(game);
        }
        if (game.state() != BattleBallState.LOBBY) return Result.failed(FAILURE_BAD_STATE);
        if (teamId < 0 || teamId >= game.teams().size()) return Result.failed(FAILURE_BAD_TEAM);
        if (game.teams().get(teamId).full()) return Result.failed(FAILURE_FULL);
        if (!this.canAffordRound(habbo)) return Result.failed(FAILURE_INSUFFICIENT_TICKETS);
        if (!game.addPlayer(userId, habbo.getHabboInfo().getUsername(), habbo.getHabboInfo().getMotto(), habbo.getHabboInfo().getLook(),
                habbo.getHabboInfo().getGender().name(), teamId)) return Result.failed(FAILURE_FULL);
        this.userGames.put(userId, instanceId);
        // Joining only puts the player in a team; they remain in the lounge until the round starts.
        this.publishLobbyMutation(game); return Result.success(game);
    }

    public synchronized int leave(Habbo habbo, int instanceId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        int userId = habbo.getHabboInfo().getId();
        this.clearRequestsForUser(userId);
        if (game == null) return FAILURE_NOT_FOUND;
        // An owner's explicit Leave/Close terminates their instance in every state. Keeping a running
        // ownerless game alive leaves userGames membership behind and rejects their next creation as
        // "already in a game" even though the client has fully exited the arena.
        if (game.ownerUserId() == userId) {
            game.removePlayer(userId);
            this.deleteInternal(game, Set.of(userId));
            return -1;
        }
        if (game.removePlayer(userId) == null) {
            boolean removedWatcher = game.removeWatcher(userId);
            this.userGames.remove(userId, instanceId);
            if (removedWatcher) this.publishSpectatorCount(game);
            return -1;
        }
        this.userGames.remove(userId, instanceId);
        this.publishLobbyMutation(game);
        return -1;
    }

    public synchronized int kick(Habbo actor, int instanceId, int targetUserId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != BattleBallState.LOBBY || targetUserId == game.ownerUserId()) return FAILURE_BAD_STATE;
        BattleBallPlayer target = game.players().stream()
                .filter(player -> player.userId() == targetUserId && !player.isBot()).findFirst().orElse(null);
        if (target == null) return FAILURE_NOT_A_PLAYER;
        if (!this.allowRosterMutation(actor.getHabboInfo().getId(), instanceId)) return REQUEST_THROTTLED;
        this.sendSafely(Emulator.getGameEnvironment().getHabboManager().getHabbo(targetUserId),
                new BattleBallPlayerRemovedComposer(instanceId, REMOVAL_KICKED), "player removal");
        game.removePlayer(targetUserId);
        this.userGames.remove(targetUserId, instanceId);
        this.publishLobbyMutation(game); return -1;
    }

    /** Adds one game-scoped bot to a team. Bots never enter userGames because they have no session. */
    public synchronized int addBot(Habbo actor, int instanceId, int teamId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != BattleBallState.LOBBY) return FAILURE_BAD_STATE;
        if (teamId < 0 || teamId >= game.teams().size()) return FAILURE_BAD_TEAM;
        if (game.teams().get(teamId).full()) return FAILURE_FULL;
        if (!this.allowRosterMutation(actor.getHabboInfo().getId(), instanceId)) return REQUEST_THROTTLED;
        if (!game.addBot(teamId)) return FAILURE_NOT_ALLOWED;
        // Roster commands are menu interactions, not simulation steps. Publish the compact spawn
        // delta now instead of making the UI wait for the authoritative 500 ms gameplay loop.
        this.publishLobbyMutation(game);
        return -1;
    }

    /** Removes only a bot; human players continue to use the existing owner kick path. */
    public synchronized int removeBot(Habbo actor, int instanceId, int botUserId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != BattleBallState.LOBBY) return FAILURE_BAD_STATE;
        BattleBallPlayer bot = game.players().stream()
                .filter(player -> player.userId() == botUserId && player.isBot()).findFirst().orElse(null);
        if (bot == null) return FAILURE_NOT_A_PLAYER;
        if (!this.allowRosterMutation(actor.getHabboInfo().getId(), instanceId)) return REQUEST_THROTTLED;
        game.removePlayer(botUserId);
        // removePlayer records a compact despawn event; flush it immediately for the same reason as add.
        this.publishLobbyMutation(game);
        return -1;
    }

    public synchronized int delete(Habbo actor, int instanceId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (game.ownerUserId() != actor.getHabboInfo().getId()) return FAILURE_NOT_OWNER;
        if (game.state() != BattleBallState.LOBBY) return FAILURE_BAD_STATE;
        this.deleteInternal(game); return -1;
    }

    public int start(Habbo actor, int instanceId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        if (!this.startingGames.add(instanceId)) return -1;
        try {
            synchronized (game) {
                if (this.games.get(instanceId) != game) return FAILURE_NOT_FOUND;
                int userId = actor.getHabboInfo().getId();
                if (game.ownerUserId() != userId) return FAILURE_NOT_OWNER;
                // A repeated Start is an idempotent success. The sequenced status stream performs recovery; a
                // duplicate UI click must not broadcast another full arena snapshot.
                if (game.state() == BattleBallState.COUNTDOWN || game.state() == BattleBallState.RUNNING) return -1;
                if (game.state() != BattleBallState.LOBBY) return FAILURE_BAD_STATE;
                if (!game.canStart(userId)) return FAILURE_NOT_ENOUGH_TEAMS;
                boolean requiresHumanOpponent = Emulator.getConfig().getBoolean(
                        "battleball.ranked.requires.human.opponent", true);
                boolean rankedRound = game.rankedEligible(requiresHumanOpponent);
                List<Habbo> participants = new ArrayList<>();
                for (BattleBallPlayer player : game.players()) {
                    if (player.isBot()) continue;
                    Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(player.userId());
                    if (habbo == null) return FAILURE_BAD_STATE;
                    participants.add(habbo);
                }
                BattleBallRepository.ChargeResult charge = this.ticketService().withWalletLocks(participants, () -> {
                    BattleBallRepository.ChargeResult committed = this.repository.chargeRound(
                            game, participants, this.currencyType(), this.ticketCost(), rankedRound);
                    if (committed.success()) {
                        for (Habbo habbo : participants) {
                            Integer balance = committed.balances().get(habbo.getHabboInfo().getId());
                            if (balance != null) {
                                this.ticketService().applyCommittedBalance(habbo, balance);
                            }
                        }
                    }
                    return committed;
                });
                if (!charge.success()) return switch (charge.status()) {
                    case INSUFFICIENT_TICKETS -> FAILURE_INSUFFICIENT_TICKETS;
                    case REJECTED -> FAILURE_NOT_ALLOWED;
                    default -> FAILURE_UNKNOWN;
                };
                if (!game.start(userId, System.currentTimeMillis(), rankedRound, requiresHumanOpponent)) {
                    LOGGER.error("BattleBall -> Charged instance {} but its protected start transition failed.", instanceId);
                    return FAILURE_UNKNOWN;
                }
                // Everyone leaves their room together, at the moment the round begins rather than when they
                // joined it, so the pre-game lobby window can sit over the lounge the way Shockwave's did.
                for (Habbo habbo : participants) this.leaveLounge(habbo.getHabboInfo().getId());
                for (Habbo habbo : participants) {
                    Integer balance = charge.balances().get(habbo.getHabboInfo().getId());
                    if (balance != null) {
                        int charged = charge.costs().getOrDefault(habbo.getHabboInfo().getId(), this.ticketCost());
                        this.ticketService().sendCommittedBalance(habbo, balance, -charged);
                    }
                }
                this.sendToGame(game, ignored -> new BattleBallGameStartComposer(game));
                return -1;
            }
        } finally {
            this.startingGames.remove(instanceId);
        }
    }

    public int move(Habbo habbo, int instanceId, int x, int y) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        long now = System.currentTimeMillis();
        if (!this.allowRequest(habbo.getHabboInfo().getId(), instanceId, RequestAction.MOVE, now, MOVE_INTERVAL_MS)) {
            return FAILURE_NOT_ALLOWED;
        }
        return game.requestMove(habbo.getHabboInfo().getId(), x, y, now) ? -1 : FAILURE_NOT_ALLOWED;
    }

    public int activate(Habbo habbo, int instanceId, int powerObjectId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        long now = System.currentTimeMillis();
        if (!this.allowRequest(habbo.getHabboInfo().getId(), instanceId, RequestAction.ACTIVATE, now, ACTIVATE_INTERVAL_MS)) {
            return FAILURE_NOT_ALLOWED;
        }
        return game.activatePower(habbo.getHabboInfo().getId(), powerObjectId, now) ? -1 : FAILURE_NOT_ALLOWED;
    }

    public synchronized int watch(Habbo habbo, int instanceId) {
        if (!this.enabled) return FAILURE_NOT_ALLOWED;
        if (instanceId < 0) {
            BattleBallFinishedGame finished = this.finishedGames.get(-instanceId);
            if (finished == null || !finished.visibleTo(habbo.getHabboInfo().getId())) return FAILURE_NOT_FOUND;
            this.sendSafely(habbo, new BattleBallGameInstanceComposer(finished), "finished game results");
            return -1;
        }
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) return FAILURE_NOT_FOUND;
        int userId = habbo.getHabboInfo().getId();
        if (!canDiscover(game, userId)) return FAILURE_NOT_FOUND;
        if (game.state() != BattleBallState.COUNTDOWN && game.state() != BattleBallState.RUNNING) {
            return FAILURE_BAD_STATE;
        }
        if (game.watchers().size() >= Emulator.getConfig().getInt("battleball.max.spectators", 50)) return FAILURE_FULL;
        Integer existing = this.userGames.putIfAbsent(userId, instanceId);
        if (existing != null) return FAILURE_ALREADY_IN_GAME;
        this.leaveLounge(userId);
        game.addWatcher(userId);
        this.sendSafely(habbo, new BattleBallGameLocationComposer(game, true), "spectator location");
        this.publishSpectatorCount(game);
        return -1;
    }

    public void requestFull(Habbo habbo, int instanceId) {
        if (!this.enabled) return;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null || !this.hasArenaAccess(habbo, game)) return;
        long now = System.currentTimeMillis();
        if (this.allowRequest(habbo.getHabboInfo().getId(), instanceId,
                RequestAction.FULL_STATUS, now, FULL_STATUS_INTERVAL_MS)) this.sendFull(habbo, game);
    }

    public synchronized void disconnected(int userId) {
        this.lounge.remove(userId);
        this.clearRequestsForUser(userId);
        Integer instanceId = this.userGames.get(userId); if (instanceId == null) return;
        BattleBallGame game = this.games.get(instanceId);
        if (game == null) {
            this.userGames.remove(userId, instanceId);
            return;
        }
        // Closing the client is a definitive leave; retaining it would create an unreachable ghost and
        // leave the remaining clients with a different player set.
        if (game.ownerUserId() == userId) {
            game.removePlayer(userId);
            this.deleteInternal(game, Set.of(userId));
            return;
        }
        boolean removedPlayer = game.removePlayer(userId) != null;
        boolean removedWatcher = game.removeWatcher(userId);
        this.userGames.remove(userId, instanceId);
        if (removedWatcher) this.publishSpectatorCount(game);
        if (removedPlayer) this.publishLobbyMutation(game);
    }

    /** Room departures are session departures for the windowed, non-resumable BattleBall game. */
    public void roomLeave(int userId) {
        this.disconnected(userId);
    }

    public synchronized void dispose() {
        this.enabled = false;
        if (this.tickTask != null) this.tickTask.cancel(false);
        for (BattleBallGame game : List.copyOf(this.games.values())) {
            if (game.state() != BattleBallState.LOBBY) {
                this.persistRoundAsync(BattleBallRoundResult.capture(game, true), false);
            }
            game.dispose();
        }
        this.games.clear(); this.userGames.clear(); this.lounge.clear();
        this.lastSequences.clear(); this.startingGames.clear(); this.requestTimestamps.clear(); this.tickFailures.clear();
        this.persistenceExecutor.shutdown();
        try {
            if (!this.persistenceExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.error("BattleBall -> Persistence worker did not drain within 30 seconds during shutdown.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("BattleBall -> Interrupted while draining persistence during shutdown.", exception);
        }
    }

    private void tick() {
        if (!this.enabled) return;
        long now = System.currentTimeMillis();
        for (BattleBallGame game : List.copyOf(this.games.values())) {
            if (this.games.get(game.id()) != game) continue;
            if (this.startingGames.contains(game.id())) continue;
            try {
                Set<Integer> recipientsBeforeTick = this.recipientIds(game);
                BattleBallState before = game.state();
                game.tick(now);
                if (this.games.get(game.id()) != game) continue;
                Set<Integer> idleRemovals = game.consumeIdleRemovals();
                boolean ownerPresent = game.players().stream().anyMatch(player -> player.userId() == game.ownerUserId());
                if (!ownerPresent) {
                    // Owner leave, disconnect and idle removal all have the same terminal meaning. Do not
                    // reset an active round into an ownerless lobby which can never be started or deleted.
                    this.deleteInternal(game, recipientsBeforeTick);
                    continue;
                }
                for (int removedUserId : idleRemovals) {
                    this.userGames.remove(removedUserId, game.id());
                    Habbo removed = Emulator.getGameEnvironment().getHabboManager().getHabbo(removedUserId);
                    this.sendSafely(removed, new BattleBallPlayerRemovedComposer(game.id(), REMOVAL_IDLE),
                            "idle player removal");
                }
                for (Map.Entry<Integer, Integer> warning : game.consumeIdleWarnings().entrySet()) {
                    Habbo warned = Emulator.getGameEnvironment().getHabboManager().getHabbo(warning.getKey());
                    this.sendSafely(warned, new BattleBallSignalComposer(
                            Outgoing.BattleBallIdleWarningComposer, game.id(), warning.getValue()), "idle warning");
                }
                this.reconcileMembership(game);
                BattleBallState after = game.state();
                // The lounge list carries each game's state, but only membership changes used to
                // refresh it. A game starting is a tick transition, so every list still showed it
                // waiting in its lobby: clicking one opened a "not started yet" view of a round
                // already in progress, and only re-entering the room fixed it.
                if (after != before && after != BattleBallState.ENDING) this.broadcastLobby(game);
                if (after == BattleBallState.ENDING && before != BattleBallState.ENDING) {
                    BattleBallRoundResult result = BattleBallRoundResult.capture(game, game.roundAborted());
                    this.persistRoundAsync(result, !result.aborted());
                    this.sendToRecipients(game, recipientsBeforeTick, ignored -> new BattleBallGameEndComposer(game));
                }
                // A finished match is NOT torn down on a timer. deleteInternal() ends by forwarding
                // the game five seconds after the whistle and took the final standings with it. The game
                // stays in its reset lobby state until the players leave of their own accord; the
                // ownerless check above still collects abandoned games.
                this.publishPendingStatus(game, recipientsBeforeTick, now);
                this.tickFailures.remove(game.id());
            } catch (Exception exception) {
                int failures = this.tickFailures.merge(game.id(), 1, Integer::sum);
                LOGGER.error("BattleBall -> Tick failed for instance {} ({}/{} consecutive failures).",
                        game.id(), failures, MAX_CONSECUTIVE_TICK_FAILURES, exception);
                if (failures >= MAX_CONSECUTIVE_TICK_FAILURES) {
                    try {
                        this.deleteInternal(game, this.recipientIds(game));
                    } catch (Exception quarantineException) {
                        LOGGER.error("BattleBall -> Could not quarantine failed instance {}.", game.id(), quarantineException);
                    }
                }
            }
        }
    }

    private void sendFull(Habbo habbo, BattleBallGame game) {
        long now = System.currentTimeMillis();
        this.sendSafely(habbo, new BattleBallFullGameStatusComposer(game, game.consumeUpdate(now, true)),
                "full status");
        this.sendSafely(habbo, new BattleBallSpectatorCountComposer(game.id(), game.watchers().size()),
                "spectator count recovery");
    }

    /**
     * Publishes a pre-game roster change immediately while preserving the same sequence/event stream
     * consumed by the gameplay tick. publishPendingStatus locks only this game, so its drain and
     * lastSequences update are atomic without pausing unrelated matches.
     */
    private void publishLobbyMutation(BattleBallGame game) {
        this.publishPendingStatus(game, Set.of(), System.currentTimeMillis());
        this.broadcastLobby(game);
    }

    private void publishSpectatorCount(BattleBallGame game) {
        this.sendToGame(game, ignored -> new BattleBallSpectatorCountComposer(
                game.id(), game.watchers().size()));
    }

    private void publishPendingStatus(BattleBallGame game, Set<Integer> additionalRecipients, long now) {
        synchronized (game) {
            int last = this.lastSequences.getOrDefault(game.id(), -1);
            if (game.sequence() == last) return;
            BattleBallUpdate update = game.consumeUpdate(now, false);
            this.sendToRecipients(game, additionalRecipients,
                    ignored -> new BattleBallGameStatusComposer(game, update, last));
            this.lastSequences.put(game.id(), update.sequence());
        }
    }

    private boolean hasArenaAccess(Habbo habbo, BattleBallGame game) {
        int userId = habbo.getHabboInfo().getId();
        return game.watchers().contains(userId)
                || game.players().stream().anyMatch(player -> player.userId() == userId);
    }

    private void broadcastLobby(BattleBallGame game) {
        for (Integer userId : List.copyOf(this.lounge)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (!isInLoungeRoom(habbo)) { this.lounge.remove(userId); continue; }
            if (!canDiscover(game, habbo.getHabboInfo().getId())) continue;
            this.sendSafely(habbo, new BattleBallGameInstanceComposer(game), "lobby instance update");
        }
    }

    /** A finished row replaces a live running row, so send an authoritative list rather than an upsert. */
    private void broadcastLoungeLists() {
        for (Integer userId : List.copyOf(this.lounge)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (!isInLoungeRoom(habbo)) { this.lounge.remove(userId); continue; }
            this.sendSafely(habbo, new BattleBallInstanceListComposer(this.instancesFor(habbo)),
                    "finished instance list");
        }
    }

    private void sendToGame(BattleBallGame game, java.util.function.Function<Habbo, MessageComposer> composer) {
        this.sendToRecipients(game, Set.of(), composer);
    }

    private Set<Integer> recipientIds(BattleBallGame game) {
        Set<Integer> recipients = new HashSet<>(game.watchers());
        game.players().stream().filter(player -> !player.isBot())
                .forEach(player -> recipients.add(player.userId()));
        return recipients;
    }

    private void sendToRecipients(BattleBallGame game, Set<Integer> additionalRecipients,
                                  java.util.function.Function<Habbo, MessageComposer> composer) {
        Set<Integer> recipients = this.recipientIds(game);
        recipients.addAll(additionalRecipients);
        for (int userId : recipients) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo == null) continue;
            this.sendSafely(habbo, composer.apply(habbo), "instance " + game.id() + " update");
        }
    }

    private Collection<Habbo> onlineRecipients() {
        List<Habbo> result = new ArrayList<>();
        for (BattleBallGame game : this.games.values()) {
            for (BattleBallPlayer player : game.players()) {
                if (player.isBot()) continue;
                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(player.userId());
                if (habbo != null && !result.contains(habbo)) result.add(habbo);
            }
            for (int userId : game.watchers()) {
                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
                if (habbo != null && !result.contains(habbo)) result.add(habbo);
            }
        }
        // Everyone with the games list open, whether or not they are in a game yet. Offline ids are
        // dropped as they are found so the set cannot grow without bound across sessions.
        for (Integer userId : this.lounge) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (!isInLoungeRoom(habbo)) { this.lounge.remove(userId); continue; }
            if (!result.contains(habbo)) result.add(habbo);
        }
        return result;
    }

    private void deleteInternal(BattleBallGame game) {
        this.deleteInternal(game, Set.of());
    }

    private void deleteInternal(BattleBallGame game, Set<Integer> additionalRecipients) {
        if (!this.games.remove(game.id(), game)) return;
        // A round past the lobby has already been charged. Close it before the game disappears, or the
        // tickets are gone with no result, no stats and battleball_rounds.ended_at left NULL forever.
        if (game.state() != BattleBallState.LOBBY) {
            this.persistRoundAsync(BattleBallRoundResult.capture(game, true), false);
        }
        Set<Integer> participants = this.recipientIds(game);
        participants.addAll(additionalRecipients);
        this.lastSequences.remove(game.id()); this.tickFailures.remove(game.id());
        this.clearRequestsForGame(game.id());
        participants.forEach(userId -> this.userGames.remove(userId, game.id()));
        this.sendToRecipients(game, additionalRecipients,
                ignored -> new BattleBallSignalComposer(Outgoing.BattleBallGameDeletedComposer, game.id()));
        // Everyone watching the games list has to lose the row too, not just the players who were in it.
        // Without this an owner cancelling their game left it listed for every other player in the
        // lounge until they re-entered the room.
        Set<Integer> notified = new HashSet<>(participants);
        try {
            for (Habbo habbo : this.onlineRecipients()) {
                if (!notified.add(habbo.getHabboInfo().getId())) continue;
                this.sendSafely(habbo,
                        new BattleBallSignalComposer(Outgoing.BattleBallGameDeletedComposer, game.id()),
                        "instance deletion");
            }
        } finally {
            game.dispose();
        }
    }

    private void reconcileMembership(BattleBallGame game) {
        Set<Integer> current = new HashSet<>();
        game.players().stream().filter(player -> !player.isBot())
                .forEach(player -> current.add(player.userId()));
        current.addAll(game.watchers());
        List<Integer> removed = this.userGames.entrySet().stream()
                .filter(entry -> entry.getValue() == game.id() && !current.contains(entry.getKey()))
                .map(Map.Entry::getKey).toList();
        removed.forEach(userId -> this.userGames.remove(userId, game.id()));
    }

    private int ticketBalance(Habbo habbo) {
        return this.ticketService().balance(habbo);
    }

    private HotelTicketService ticketService() {
        return Emulator.getGameEnvironment().getHotelTicketService();
    }

    private static boolean isInLoungeRoom(Habbo habbo) {
        return habbo != null && habbo.getClient() != null && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getCurrentRoom() != null
                && habbo.getHabboInfo().getCurrentRoom().getLayout() != null
                && LOUNGE_MODEL.equalsIgnoreCase(habbo.getHabboInfo().getCurrentRoom().getLayout().getName());
    }

    private boolean canAffordRound(Habbo habbo) {
        return this.ticketBalance(habbo) >= this.ticketCost();
    }

    private static boolean valid(Creation creation) {
        if (creation == null || creation.name() == null || creation.name().trim().isEmpty() || creation.name().trim().length() > 32
                || creation.mapId() < 1 || creation.mapId() > 5 || creation.roundSeconds() < 60 || creation.roundSeconds() > 600
                || creation.teamCount() < 2 || creation.teamCount() > 4 || creation.teamCapacity() < 1
                || creation.teamCapacity() > BattleBallGame.maxTeamCapacity(creation.teamCount())
                || creation.selectedTeam() < 0 || creation.selectedTeam() >= creation.teamCount() || creation.powers() == null || creation.powers().size() > 8) return false;
        return creation.powers().stream().allMatch(power -> power != null && power.id() >= 1 && power.id() <= 8);
    }

    private void persistRoundAsync(BattleBallRoundResult result, boolean notifySkills) {
        try {
            this.persistenceExecutor.execute(() -> {
                BattleBallFinishedGame finished = null;
                boolean persisted;
                if (result.aborted()) persisted = this.repository.abortRound(result);
                else {
                    finished = this.repository.saveResults(result);
                    persisted = finished != null;
                }
                if (!persisted) return;
                if (finished != null) {
                    this.finishedGames.put(finished.resultId(), finished);
                    this.trimFinishedGames();
                    this.broadcastLoungeLists();
                }
                if (!notifySkills || !BattleBallRepository.updatesRankedStats(result)) return;
                for (BattleBallRoundResult.PlayerResult player : result.players()) {
                    if (player.userId() < 1) continue;
                    Habbo participant = Emulator.getGameEnvironment().getHabboManager().getHabbo(player.userId());
                    this.sendSafely(participant, new BattleBallSignalComposer(
                            Outgoing.BattleBallSkillLevelChangedComposer,
                            this.repository.skillLevel(player.userId())), "skill-level update");
                }
            });
        } catch (RejectedExecutionException exception) {
            LOGGER.error("BattleBall -> Rejected persistence for instance {} round {}.",
                    result.instanceId(), result.roundNumber(), exception);
        }
    }

    private void trimFinishedGames() {
        List<BattleBallFinishedGame> ordered = this.finishedGames.values().stream()
                .sorted(Comparator.comparingInt(BattleBallFinishedGame::endedAt).reversed()
                        .thenComparing(Comparator.comparingInt(BattleBallFinishedGame::resultId).reversed()))
                .toList();
        for (int index = 15; index < ordered.size(); index++) {
            BattleBallFinishedGame stale = ordered.get(index);
            this.finishedGames.remove(stale.resultId(), stale);
        }
    }

    private boolean allowRequest(int userId, int instanceId, RequestAction action, long now, long intervalMs) {
        RequestKey key = new RequestKey(userId, instanceId, action);
        AtomicBoolean allowed = new AtomicBoolean(false);
        this.requestTimestamps.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= intervalMs) {
                allowed.set(true);
                return now;
            }
            return previous;
        });
        return allowed.get();
    }

    private boolean allowRosterMutation(int userId, int instanceId) {
        return this.allowRequest(userId, instanceId, RequestAction.ROSTER_MUTATION,
                System.currentTimeMillis(), ROSTER_MUTATION_INTERVAL_MS);
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
            LOGGER.error("BattleBall -> Could not send {} to user {}.", context, userId, exception);
            return false;
        }
    }

    private void clearRequestsForGame(int instanceId) {
        this.requestTimestamps.keySet().removeIf(key -> key.instanceId() == instanceId);
    }

    private void clearRequestsForUser(int userId) {
        this.requestTimestamps.keySet().removeIf(key -> key.userId() == userId);
    }
}
