package com.eu.habbo.habbohotel.rooms;

import java.awt.Color;
import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.gamehall.GamehallManager;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionBackgroundToner;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.items.interactions.InteractionFireworks;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildFurni;
import com.eu.habbo.habbohotel.items.interactions.InteractionGamehallSeat;
import com.eu.habbo.habbohotel.items.interactions.InteractionJukeBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionMultiHeight;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserAction;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.guilds.HabboGroupDetailsMessageComposer;
import com.eu.habbo.messages.outgoing.hotelview.CloseConnectionMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.UnseenItemsMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListInvalidateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.FlatAccessibleMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.HeightMapUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectAddMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.OneWayDoorStatusMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectRemoveMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemRemoveMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemAddMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectsMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateMessageComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurniturePickedUpEvent;
import com.eu.habbo.plugin.events.rooms.RoomLoadedEvent;
import com.eu.habbo.plugin.events.rooms.RoomUnloadedEvent;
import com.eu.habbo.plugin.events.rooms.RoomUnloadingEvent;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;

public class Room implements Comparable<Room>, ISerialize, Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Room.class);

  // Manager instances for better separation of concerns
  private RoomTileManager tileManager;
  private RoomGameManager gameManager;
  private GamehallManager gamehallManager;
  private RoomTradeManager tradeManager;
  private RoomPromotionManager promotionManager;
  private RoomWordQuizManager wordQuizManager;
  private RoomRightsManager rightsManager;
  private RoomUnitManager unitManager;
  private RoomItemManager itemManager;
  private RoomChatManager chatManager;
  private RoomRollerManager rollerManager;
  private RoomMessagingManager messagingManager;
  private RoomCycleManager cycleManager;

  public static final Comparator<Room> SORT_SCORE = (o1, o2) -> o2.getScore() - o1.getScore();
  public static final Comparator<Room> SORT_ID = (o1, o2) -> o2.getId() - o1.getId();
  private static final TIntObjectHashMap<RoomMoodlightData> defaultMoodData = new TIntObjectHashMap<>();
  // Configuration. Loaded from database & updated accordingly.
  public static boolean HABBO_CHAT_DELAY = false;
  public static int MAXIMUM_BOTS = 10;
  public static int MAXIMUM_PETS = 10;
  public static int MAXIMUM_FURNI = 2500;
  public static int MAXIMUM_POSTITNOTES = 200;
  public static int HAND_ITEM_TIME = 10;
  public static int IDLE_CYCLES = 240;
  public static int IDLE_CYCLES_KICK = 480;
  public static String PREFIX_FORMAT = "[<font color=\"%color%\">%prefix%</font>] ";
  public static int ROLLERS_MAXIMUM_ROLL_AVATARS = 1;
  public static boolean MUTEAREA_CAN_WHISPER = false;
  public static double MAXIMUM_FURNI_HEIGHT = 40d;

  static {
    for (int i = 1; i <= 3; i++) {
      RoomMoodlightData data = RoomMoodlightData.fromString("");
      data.setId(i);
      defaultMoodData.put(i, data);
    }
  }

  public final Object roomUnitLock = new Object();
  public final ConcurrentHashMap<RoomTile, THashSet<HabboItem>> tileCache = new ConcurrentHashMap<>();
  public final List<Integer> userVotes;
  private final TIntArrayList rights;
  private final TIntIntHashMap mutedHabbos;
  private final TIntObjectHashMap<RoomBan> bannedHabbos;
  private final Set<Game> games;
  private final TIntObjectMap<RoomMoodlightData> moodlightData;
  private final Object loadLock = new Object();
  // Use appropriately. Could potentially cause memory leaks when used
  // incorrectly.
  public volatile boolean preventUnloading = false;
  public volatile boolean preventUncaching = false;
  public Set<ServerMessage> scheduledComposers = ConcurrentHashMap.newKeySet();
  public Set<Runnable> scheduledTasks = ConcurrentHashMap.newKeySet();
  private final Object areaHideVisibilityLock = new Object();
  private final Set<Integer> areaHideHiddenFloorItems = ConcurrentHashMap.newKeySet();
  private final Set<Integer> areaHideHiddenWallItems = ConcurrentHashMap.newKeySet();
  public String wordQuiz = "";
  public int noVotes = 0;
  public int yesVotes = 0;
  public int wordQuizEnd = 0;
  public ScheduledFuture<?> roomCycleTask;
  private int id;
  private int ownerId;
  private String ownerName;
  private String name;
  private String description;
  private RoomLayout layout;
  private boolean overrideModel;
  private String layoutName;
  private String password;
  private RoomState state;
  private int usersMax;
  private volatile int score;
  private volatile int category;
  private String floorPaint;
  private String wallPaint;
  private String backgroundPaint;
  private int wallSize;
  private int wallHeight;
  private int floorSize;
  private int guild;
  private String tags;
  private volatile boolean publicRoom;
  private volatile boolean staffPicked;
  private volatile boolean allowPets;
  private volatile boolean allowPetsEat;
  private volatile boolean allowWalkthrough;
  private volatile boolean allowBotsWalk;
  private volatile boolean allowEffects;
  private volatile boolean hideWall;
  private volatile int chatMode;
  private volatile int chatWeight;
  private volatile int chatSpeed;
  private volatile int chatDistance;
  private volatile int chatProtection;
  private volatile int muteOption;
  private volatile int kickOption;
  private volatile int banOption;
  private volatile int pollId;
  private volatile boolean promoted;
  private volatile int tradeMode;
  private volatile boolean moveDiagonally;
  private volatile boolean jukeboxActive;
  private volatile boolean hideWired;
  private RoomPromotion promotion;
  private volatile boolean needsUpdate;
  private volatile boolean loaded;
  private volatile boolean preLoaded;
  private volatile boolean loadingInProgress;
  private volatile CompletableFuture<Void> loadingFuture;
  private volatile int rollerSpeed;
  private volatile int lastTimerReset = Emulator.getIntUnixTimestamp();
  private volatile boolean muted;
  private RoomSpecialTypes roomSpecialTypes;
  private TraxManager traxManager;
  /**
   * Room-load furniture baseline, ported from iSetht's Wired implementation and
   * adapted to this fork's stable/visible item-ID split.
   */
  private final TIntObjectHashMap<FurniLoadSnapshot> furniLoadSnapshot;

  public final THashMap<String, Object> cache;

  public Room(ResultSet set) throws SQLException {
    this.cache = new THashMap<>(1000);
    this.furniLoadSnapshot = new TIntObjectHashMap<>();
    this.id = set.getInt("id");
    this.ownerId = set.getInt("owner_id");
    this.ownerName = set.getString("owner_name");
    this.name = set.getString("name");
    this.description = set.getString("description");
    this.password = set.getString("password");
    this.state = RoomState.valueOf(set.getString("state").toUpperCase());
    this.usersMax = set.getInt("users_max");
    this.score = set.getInt("score");
    this.category = set.getInt("category");
    this.floorPaint = set.getString("paper_floor");
    this.wallPaint = set.getString("paper_wall");
    this.backgroundPaint = set.getString("paper_landscape");
    this.wallSize = set.getInt("thickness_wall");
    this.wallHeight = set.getInt("wall_height");
    this.floorSize = set.getInt("thickness_floor");
    this.tags = set.getString("tags");
    this.publicRoom = false;
    this.staffPicked = false;
    this.allowPets = set.getBoolean("allow_other_pets");
    this.allowPetsEat = set.getBoolean("allow_other_pets_eat");
    this.allowWalkthrough = set.getBoolean("allow_walkthrough");
    this.hideWall = set.getBoolean("allow_hidewall");
    this.chatMode = set.getInt("chat_mode");
    this.chatWeight = set.getInt("chat_weight");
    this.chatSpeed = set.getInt("chat_speed");
    this.chatDistance = set.getInt("chat_hearing_distance");
    this.chatProtection = set.getInt("chat_protection");
    this.muteOption = set.getInt("who_can_mute");
    this.kickOption = set.getInt("who_can_kick");
    this.banOption = set.getInt("who_can_ban");
    this.pollId = set.getInt("poll_id");
    this.guild = set.getInt("guild_id");
    this.rollerSpeed = set.getInt("roller_speed");
    this.overrideModel = set.getString("override_model").equals("1");
    this.layoutName = set.getString("model");
    this.promoted = set.getString("promoted").equals("1");
    this.jukeboxActive = set.getString("jukebox_active").equals("1");
    this.hideWired = set.getString("hidewired").equals("1");

    this.bannedHabbos = new TIntObjectHashMap<>();

    try (Connection connection = Emulator.getDatabase().getDataSource()
        .getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM room_promotions WHERE room_id = ? AND end_timestamp > ? LIMIT 1")) {
      if (this.promoted) {
        statement.setInt(1, this.id);
        statement.setInt(2, Emulator.getIntUnixTimestamp());

        try (ResultSet promotionSet = statement.executeQuery()) {
          this.promoted = false;
          if (promotionSet.next()) {
            this.promoted = true;
            this.promotion = new RoomPromotion(this, promotionSet);
          }
        }
      }

      this.loadBans(connection);
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }

    this.tradeMode = set.getInt("trade_mode");
    this.moveDiagonally = set.getString("move_diagonally").equals("1");

    this.preLoaded = true;
    this.allowBotsWalk = true;
    this.allowEffects = true;
    this.moodlightData = new TIntObjectHashMap<>(defaultMoodData);

    for (String s : set.getString("moodlight_data").split(";")) {
      RoomMoodlightData data = RoomMoodlightData.fromString(s);
      this.moodlightData.put(data.getId(), data);
    }

    this.mutedHabbos = new TIntIntHashMap();
    this.games = ConcurrentHashMap.newKeySet();

    this.rights = new TIntArrayList();
    this.userVotes = new ArrayList<>();

    // Initialize managers
    this.initializeManagers();
  }

  /**
   * Initializes all manager instances for this room.
   */
  private void initializeManagers() {
    this.tileManager = new RoomTileManager(this);
    this.gameManager = new RoomGameManager(this);
    this.gamehallManager = new GamehallManager(this);
    this.tradeManager = new RoomTradeManager(this);
    this.promotionManager = new RoomPromotionManager(this);
    this.wordQuizManager = new RoomWordQuizManager(this);
    this.rightsManager = new RoomRightsManager(this);
    this.unitManager = new RoomUnitManager(this);
    this.itemManager = new RoomItemManager(this);
    this.chatManager = new RoomChatManager(this);
    this.rollerManager = new RoomRollerManager(this);
    this.messagingManager = new RoomMessagingManager(this);
    this.cycleManager = new RoomCycleManager(this);
  }

  // ==================== MANAGER GETTERS ====================

  /**
   * Gets the tile manager for this room.
   */
  public RoomTileManager getTileManager() {
    return this.tileManager;
  }

  /**
   * Gets the game manager for this room.
   */
  public RoomGameManager getGameManager() {
    return this.gameManager;
  }

  public GamehallManager getGamehallManager() {
    return this.gamehallManager;
  }

  /**
   * Gets the trade manager for this room.
   */
  public RoomTradeManager getTradeManager() {
    return this.tradeManager;
  }

  /**
   * Gets the promotion manager for this room.
   */
  public RoomPromotionManager getPromotionManager() {
    return this.promotionManager;
  }

  /**
   * Gets the word quiz manager for this room.
   */
  public RoomWordQuizManager getWordQuizManager() {
    return this.wordQuizManager;
  }

  /**
   * Gets the rights manager for this room.
   */
  public RoomRightsManager getRightsManager() {
    return this.rightsManager;
  }

  /**
   * Gets the unit manager for this room.
   */
  public RoomUnitManager getUnitManager() {
    return this.unitManager;
  }

  /**
   * Gets the item manager for this room.
   */
  public RoomItemManager getItemManager() {
    return this.itemManager;
  }

  /**
   * Gets the chat manager for this room.
   */
  public RoomChatManager getChatManager() {
    return this.chatManager;
  }

  /**
   * Gets the messaging manager for this room.
   */
  public RoomMessagingManager getMessagingManager() {
    return this.messagingManager;
  }

  /**
   * Gets the cycle manager for this room.
   */
  public RoomCycleManager getCycleManager() {
    return this.cycleManager;
  }

  /**
   * Gets the roller manager for this room.
   */
  public RoomRollerManager getRollerManager() {
    return this.rollerManager;
  }

  /**
   * Checks if the room is currently loading data.
   */
  public boolean isLoadingInProgress() {
    synchronized (this.loadLock) {
      return this.loadingInProgress;
    }
  }

  /**
   * Checks if the room data is loaded or is currently being loaded.
   */
  public boolean isLoadedOrLoading() {
    synchronized (this.loadLock) {
      return this.loaded || this.loadingInProgress;
    }
  }

  /**
   * Starts loading room data asynchronously in the background.
   * This allows the room to start loading before the user fully enters,
   * reducing perceived load time.
   */
  public void startBackgroundLoad() {
    synchronized (this.loadLock) {
      if (this.loaded || this.loadingInProgress || !this.preLoaded) {
        return;
      }

      this.loadingInProgress = true;
      this.loadingFuture = CompletableFuture.runAsync(() -> {
        this.loadDataInternal();
      }, Emulator.getThreading().getService());
    }
  }

  /**
   * Waits for background loading to complete if it's in progress.
   * If loading hasn't started yet, starts loading synchronously.
   */
  public void waitForLoad() {
    CompletableFuture<Void> future;
    synchronized (this.loadLock) {
      if (this.loaded) {
        return;
      }
      future = this.loadingFuture;
    }

    if (future != null) {
      try {
        future.join();
      } catch (Exception e) {
        LOGGER.error("Error waiting for room load", e);
      }
    } else {
      this.loadData();
    }
  }

  public void loadData() {
    CompletableFuture<Void> futureToWait = null;
    boolean shouldLoad = false;

    synchronized (this.loadLock) {
      if (this.loadingInProgress) {
        // Get the future to wait on outside the lock
        futureToWait = this.loadingFuture;
      } else if (this.preLoaded && !this.loaded) {
        this.loadingInProgress = true;
        shouldLoad = true;
      }
    }

    // Wait for existing load outside the lock
    if (futureToWait != null) {
      try {
        futureToWait.join();
      } catch (Exception e) {
        LOGGER.error("Error waiting for room load", e);
      }
      return;
    }

    // Load if needed
    if (shouldLoad) {
      this.loadDataInternal();
    }
  }

  /**
   * Internal method that performs the actual room data loading.
   * Uses parallel loading for independent operations to reduce total load time.
   */
  private void loadDataInternal() {
    // Check if already loaded (with lock)
    synchronized (this.loadLock) {
      if (this.loaded) {
        this.loadingInProgress = false;
        return;
      }
      this.preLoaded = false;
      this.gamehallManager = new GamehallManager(this);
    }

    // Perform loading WITHOUT holding the lock to avoid deadlocks
    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
      synchronized (this.roomUnitLock) {
        this.unitManager.clear();
      }

      this.roomSpecialTypes = new RoomSpecialTypes(
              com.eu.habbo.habbohotel.wired.variables.WiredVariableManager.forRoom(this.id));

      // Phase 1: Load layout first (required for bots/pets positioning)
      try {
        this.loadLayout();
      } catch (Exception e) {
        LOGGER.error("Caught exception loading layout", e);
      }

      // Phase 2: Load items and rights in parallel (independent operations)
      CompletableFuture<Void> itemsFuture = CompletableFuture.runAsync(() -> {
        try (Connection itemConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadItems(itemConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading items", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> rightsFuture = CompletableFuture.runAsync(() -> {
        try (Connection rightsConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadRights(rightsConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading rights", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> wordFilterFuture = CompletableFuture.runAsync(() -> {
        try (Connection wordFilterConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadWordFilter(wordFilterConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading word filter", e);
        }
      }, Emulator.getThreading().getService());

      // Wait for items to be loaded before loading wired data (wired depends on
      // items)
      try {
        itemsFuture.join();
      } catch (Exception e) {
        LOGGER.error("Error waiting for items to load", e);
      }

      // Inject public-room collision objects before the heightmap is computed, so the heightmap
      // sweep naturally includes them (no manual tileCache rebuild needed). Gated on the same
      // model-based condition used when sending PublicRoomObjectsMessageComposer.
      try {
        RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();
        String layoutName = this.getLayout() != null ? this.getLayout().getName() : null;
        if (layoutName != null && roomManager.isPublicModel(layoutName)) {
          this.itemManager.injectPublicItems(roomManager.getPublicItems(layoutName));
        }
      } catch (Exception e) {
        LOGGER.error("Caught exception injecting public items", e);
      }

      // Phase 3: Load heightmap after items are loaded (depends on items for stack
      // heights)
      try {
        this.loadHeightmap();
      } catch (Exception e) {
        LOGGER.error("Caught exception loading heightmap", e);
      }

      // Phase 4: Load bots, pets, and wired data in parallel (all depend on layout +
      // items)
      CompletableFuture<Void> botsFuture = CompletableFuture.runAsync(() -> {
        try (Connection botsConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadBots(botsConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading bots", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> petsFuture = CompletableFuture.runAsync(() -> {
        try (Connection petsConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadPets(petsConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading pets", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> wiredFuture = CompletableFuture.runAsync(() -> {
        try (Connection wiredConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadWiredData(wiredConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading wired data", e);
        }
      }, Emulator.getThreading().getService());

      // Wait for all parallel operations to complete
      try {
        CompletableFuture.allOf(rightsFuture, wordFilterFuture, botsFuture, petsFuture, wiredFuture).join();
      } catch (Exception e) {
        LOGGER.error("Error waiting for parallel room data loading", e);
      }

      if (this.roomSpecialTypes.getWiredVariableManager() != null
              && !this.roomSpecialTypes.getWiredVariableManager().finishHydration()) {
        LOGGER.warn("Wired variable persistence reconciliation failed for room {}; runtime remains capability-dark",
                this.id);
      }

      this.captureFurniLoadSnapshot();

      this.cycleManager.resetIdleCycles();

      // Cancel any existing cycle task before creating a new one
      if (this.roomCycleTask != null) {
        this.roomCycleTask.cancel(false);
      }
      this.roomCycleTask = Emulator.getThreading().getService()
          .scheduleAtFixedRate(this, 500, 500, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      LOGGER.error("Caught exception during room load", e);
    }

    this.traxManager = new TraxManager(this);

    if (this.jukeboxActive) {
      this.traxManager.play(0);
      for (HabboItem item : this.roomSpecialTypes.getItemsOfType(InteractionJukeBox.class)) {
        item.setExtradata("1");
        this.updateItem(item);
      }
    }

    for (HabboItem item : this.roomSpecialTypes.getItemsOfType(InteractionFireworks.class)) {
      item.setExtradata("1");
      this.updateItem(item);
    }

    // Set loaded flag with lock
    synchronized (this.loadLock) {
      this.loaded = true;
      this.loadingInProgress = false;
      this.loadingFuture = null;
    }

    this.snapshotAreaHideVisibility();

    Emulator.getPluginManager().fireEvent(new RoomLoadedEvent(this));
  }

  private synchronized void loadLayout() {
    if (this.layout == null) {
      if (this.overrideModel) {
        this.layout = Emulator.getGameEnvironment().getRoomManager().loadCustomLayout(this);
      } else {
        this.layout = Emulator.getGameEnvironment().getRoomManager()
            .loadLayout(this.layoutName, this);
      }
    }
  }

  private synchronized void loadHeightmap() {
    if (this.layout != null) {
      for (short x = 0; x < this.layout.getMapSizeX(); x++) {
        for (short y = 0; y < this.layout.getMapSizeY(); y++) {
          RoomTile tile = this.layout.getTile(x, y);
          if (tile != null) {
            this.updateTile(tile);
          }
        }
      }
    } else {
      LOGGER.error("Unknown Room Layout for Room (ID: {})", this.id);
    }
  }

  public synchronized void refreshPublicItems(List<PublicItem> publicItems) {
    this.itemManager.injectPublicItems(publicItems);
    this.tileCache.clear();
    this.loadHeightmap();
  }

  private synchronized void loadItems(Connection connection) {
    this.itemManager.loadItems(connection);
  }

  private synchronized void captureFurniLoadSnapshot() {
    this.furniLoadSnapshot.clear();
    for (HabboItem item : this.getFloorItems()) {
      this.furniLoadSnapshot.put(item.getId(), new FurniLoadSnapshot(item));
    }
    for (HabboItem item : this.getWallItems()) {
      this.furniLoadSnapshot.put(item.getId(), new FurniLoadSnapshot(item));
    }
  }

  /**
   * Restores furniture that existed at room load. Items created or removed
   * afterwards are deliberately not resurrected/deleted, matching Seth's
   * Wired Menu rollback semantics.
   */
  public synchronized int rollbackFurniLoadSnapshot() {
    if (this.furniLoadSnapshot.isEmpty() || this.layout == null) {
      return 0;
    }
    int restored = 0;
    THashSet<RoomTile> affectedTiles = new THashSet<>();
    for (FurniLoadSnapshot snapshot : this.furniLoadSnapshot.valueCollection()) {
      HabboItem item = this.getHabboItemByDatabaseId(snapshot.itemId);
      if (item == null || item.getRoomId() != this.id) {
        continue;
      }
      if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
        RoomTile oldTile = this.layout.getTile(item.getX(), item.getY());
        if (oldTile != null) {
          affectedTiles.addAll(this.layout.getTilesAt(oldTile,
              item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
              item.getRotation()));
        }
      }
      boolean stateChanged = !String.valueOf(item.getExtradata())
          .equals(String.valueOf(snapshot.extradata));
      if (!snapshot.applyTo(item)) {
        continue;
      }
      item.needsUpdate(true);
      this.updateItem(item);
      if (stateChanged) {
        this.updateItemState(item);
      }
      if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
        RoomTile newTile = this.layout.getTile(item.getX(), item.getY());
        if (newTile != null) {
          affectedTiles.addAll(this.layout.getTilesAt(newTile,
              item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
              item.getRotation()));
        }
      }
      restored++;
    }
    this.itemManager.tileCache.clear();
    this.updateTiles(affectedTiles);
    for (RoomTile tile : affectedTiles) {
      this.updateHabbosAt(tile.x, tile.y);
      this.updateBotsAt(tile.x, tile.y);
    }
    return restored;
  }

  private synchronized void loadWiredData(Connection connection) {
    this.itemManager.loadWiredData(connection);
  }

  private synchronized void loadBots(Connection connection) {
    this.unitManager.clearBots();

    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT users.username AS owner_name, bots.* FROM bots LEFT JOIN users ON bots.user_id = users.id WHERE room_id = ?")) {
      statement.setInt(1, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          Bot b = Emulator.getGameEnvironment().getBotManager().loadBot(set);

          if (b != null) {
            b.setRoom(this);
            b.setRoomUnit(new RoomUnit());
            b.getRoomUnit().setPathFinderRoom(this);
            b.getRoomUnit()
                .setLocation(this.layout.getTile((short) set.getInt("x"), (short) set.getInt("y")));
            if (b.getRoomUnit().getCurrentLocation() == null) {
              b.getRoomUnit().setLocation(this.getLayout().getDoorTile());
              b.getRoomUnit()
                  .setRotation(RoomUserRotation.fromValue(this.getLayout().getDoorDirection()));
            } else {
              b.getRoomUnit().setZ(set.getDouble("z"));
              b.getRoomUnit().setPreviousLocationZ(set.getDouble("z"));
              b.getRoomUnit().setRotation(RoomUserRotation.values()[set.getInt("rot")]);
            }
            b.getRoomUnit().setRoomUnitType(RoomUnitType.BOT);
            b.getRoomUnit().setDanceType(DanceType.values()[set.getInt("dance")]);
            // b.getRoomUnit().setCanWalk(set.getBoolean("freeroam"));
            b.getRoomUnit().setInRoom(true);
            this.giveEffect(b.getRoomUnit(), set.getInt("effect"), Integer.MAX_VALUE);
            this.addBot(b);
          }
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private synchronized void loadPets(Connection connection) {
    this.unitManager.clearPets();

    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT users.username as pet_owner_name, users_pets.* FROM users_pets INNER JOIN users ON users_pets.user_id = users.id WHERE room_id = ?")) {
      statement.setInt(1, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          try {
            Pet pet = PetManager.loadPet(set);
            pet.setRoom(this);
            pet.setRoomUnit(new RoomUnit());
            pet.getRoomUnit().setPathFinderRoom(this);
            pet.getRoomUnit()
                .setLocation(this.layout.getTile((short) set.getInt("x"), (short) set.getInt("y")));
            if (pet.getRoomUnit().getCurrentLocation() == null) {
              pet.getRoomUnit().setLocation(this.getLayout().getDoorTile());
              pet.getRoomUnit()
                  .setRotation(RoomUserRotation.fromValue(this.getLayout().getDoorDirection()));
            } else {
              pet.getRoomUnit().setZ(set.getDouble("z"));
              pet.getRoomUnit().setRotation(RoomUserRotation.values()[set.getInt("rot")]);
            }
            pet.getRoomUnit().setRoomUnitType(RoomUnitType.PET);
            pet.getRoomUnit().setCanWalk(true);
            this.addPet(pet);

            this.getFurniOwnerNames().put(pet.getUserId(), set.getString("pet_owner_name"));
          } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
          }
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private synchronized void loadWordFilter(Connection connection) {
    this.chatManager.loadWordFilter(connection);
  }

  public void updateTile(RoomTile tile) {
    this.tileManager.updateTile(tile);
  }

  public void updateTiles(THashSet<RoomTile> tiles) {
    this.tileManager.updateTiles(tiles);
  }

  public RoomTileState calculateTileState(RoomTile tile) {
    return this.tileManager.calculateTileState(tile);
  }

  public RoomTileState calculateTileState(RoomTile tile, HabboItem exclude) {
    return this.tileManager.calculateTileState(tile, exclude);
  }

  public boolean tileWalkable(RoomTile t) {
    return this.tileManager.tileWalkable(t);
  }

  public boolean tileWalkable(short x, short y) {
    return this.tileManager.tileWalkable(x, y);
  }

  public void pickUpItem(HabboItem item, Habbo picker) {
    if (item == null) {
      return;
    }

    if (Emulator.getPluginManager().isRegistered(FurniturePickedUpEvent.class, true)) {
      Event furniturePickedUpEvent = new FurniturePickedUpEvent(item, picker);
      Emulator.getPluginManager().fireEvent(furniturePickedUpEvent);

      if (furniturePickedUpEvent.isCancelled()) {
        return;
      }
    }

    this.removeHabboItem(item.getId());
    item.onPickUp(this);
    item.setRoomId(0);
    item.needsUpdate(true);

    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
      this.sendComposer(new ObjectRemoveMessageComposer(item).compose());

      THashSet<RoomTile> updatedTiles = new THashSet<>();
      Rectangle rectangle = RoomLayout.getRectangle(item.getX(), item.getY(),
          item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());

      for (short x = (short) rectangle.x; x < rectangle.x + rectangle.getWidth(); x++) {
        for (short y = (short) rectangle.y; y < rectangle.y + rectangle.getHeight(); y++) {
          double stackHeight = this.getStackHeight(x, y, false);
          RoomTile tile = this.layout.getTile(x, y);

          if (tile != null) {
            tile.setStackHeight(stackHeight);
            updatedTiles.add(tile);
          }
        }
      }
      this.sendComposer(new HeightMapUpdateMessageComposer(this, updatedTiles).compose());
      this.updateTiles(updatedTiles);
      for (RoomTile tile : updatedTiles) {
        this.updateHabbosAt(tile.x, tile.y);
        this.updateBotsAt(tile.x, tile.y);
      }
    } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
      this.sendComposer(new ItemRemoveMessageComposer(item).compose());
    }

    Habbo habbo = (picker != null && picker.getHabboInfo().getId() == item.getId() ? picker
        : Emulator.getGameServer().getGameClientManager().getHabbo(item.getUserId()));
    if (habbo != null) {
      habbo.getInventory().getItemsComponent().addItem(item);
      habbo.getClient().sendResponse(new UnseenItemsMessageComposer(item));
      habbo.getClient().sendResponse(new FurniListInvalidateMessageComposer());
    }
    Emulator.getThreading().run(item);
  }

  public void updateHabbosAt(Rectangle rectangle) {
    for (short i = (short) rectangle.x; i < rectangle.x + rectangle.width; i++) {
      for (short j = (short) rectangle.y; j < rectangle.y + rectangle.height; j++) {
        this.updateHabbosAt(i, j);
      }
    }
  }

  public void updateHabbo(Habbo habbo) {
    this.updateRoomUnit(habbo.getRoomUnit());
  }

  public void updateRoomUnit(RoomUnit roomUnit) {
    HabboItem item = this.getTopItemAt(roomUnit.getX(), roomUnit.getY());

    if ((item == null && !roomUnit.cmdSit) || (item != null && !item.getBaseItem().allowSit())) {
      roomUnit.removeStatus(RoomUnitStatus.SIT);
    }

    double oldZ = roomUnit.getZ();

    if (item != null) {
      if (item.getBaseItem().allowSit()) {
        roomUnit.setZ(item.getZ());
      } else {
        roomUnit.setZ(item.getZ() + Item.getCurrentHeight(item));
      }

      if (oldZ != roomUnit.getZ()) {
        this.scheduledTasks.add(() -> {
          try {
            item.onWalkOn(roomUnit, Room.this, null);
          } catch (Exception e) {

          }
        });
      }
    }

    this.sendComposer(new UserUpdateMessageComposer(roomUnit).compose());
    if (roomUnit.getRoomUnitType() == RoomUnitType.USER && roomUnit.hasStatus(RoomUnitStatus.SIT)) {
      Habbo habbo = this.getHabbo(roomUnit);
      if (habbo != null) {
        this.triggerGamehallSeatWalkOn(habbo);
      }
    }
  }

  public void updateHabbosAt(short x, short y) {
    this.unitManager.updateHabbosAt(x, y);
  }

  public void updateHabbosAt(short x, short y, THashSet<Habbo> habbos) {
    this.unitManager.updateHabbosAt(x, y, habbos);
  }

  public void updateBotsAt(short x, short y) {
    this.unitManager.updateBotsAt(x, y);
  }

  public void updatePetsAt(short x, short y) {
    this.unitManager.updatePetsAt(x, y);
  }

  public void pickupPetsForHabbo(Habbo habbo) {
    this.unitManager.pickupPetsForHabbo(habbo);
  }

  public void startTrade(Habbo userOne, Habbo userTwo) {
    this.tradeManager.startTrade(userOne, userTwo);
  }

  public void stopTrade(RoomTrade trade) {
    this.tradeManager.stopTrade(trade);
  }

  public RoomTrade getActiveTradeForHabbo(Habbo user) {
    return this.tradeManager.getActiveTradeForHabbo(user);
  }

  public synchronized void dispose() {
    synchronized (this.loadLock) {
      if (this.preventUnloading) {
        return;
      }

      if (Emulator.getPluginManager().fireEvent(new RoomUnloadingEvent(this)).isCancelled()) {
        return;
      }

      if (this.loaded) {
        // Set loaded to false FIRST to prevent re-entry and ensure cycle stops
        this.loaded = false;

        try {
          if (this.traxManager != null && !this.traxManager.disposed()) {
            this.traxManager.dispose();
          }

          if (this.roomCycleTask != null) {
            this.roomCycleTask.cancel(false);
            this.roomCycleTask = null;
          }
          this.scheduledTasks.clear();
          this.scheduledComposers.clear();

          this.tileCache.clear();

          synchronized (this.mutedHabbos) {
            this.mutedHabbos.clear();
          }

          for (InteractionGameTimer timer : this.getRoomSpecialTypes().getGameTimers().values()) {
            timer.setRunning(false);
          }

          for (Game game : this.games) {
            game.dispose();
          }
          this.games.clear();

          if (this.gamehallManager != null) {
            this.gamehallManager.dispose();
          }

          removeAllPets(ownerId);

          this.itemManager.saveAllPendingItems();

          if (this.roomSpecialTypes != null) {
            this.roomSpecialTypes.dispose();
          }

          // Unregister all wired tickables for this room from the tick service
          com.eu.habbo.habbohotel.wired.core.WiredManager.unregisterRoomTickables(this);

          // Clear wired engine caches for this room
          if (com.eu.habbo.habbohotel.wired.core.WiredManager.getStackIndex() != null) {
            com.eu.habbo.habbohotel.wired.core.WiredManager.getStackIndex().invalidateAll(this);
          }
          if (com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine() != null) {
            com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine().clearRoomRecursionDepth(this.id);
            com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine().clearRoomRateLimiters(this.id);
          }
          com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime.clearProjectiles(this);

          this.itemManager.clear();

          this.unitManager.clearQueue();

          for (Habbo habbo : this.getCurrentHabbos().values()) {
            Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, this);
          }

          this.sendComposer(new CloseConnectionMessageComposer().compose());

          // Save bots BEFORE clearing - must happen before unitManager.clear()
          TIntObjectIterator<Bot> botIterator = this.getCurrentBots().iterator();

          for (int i = this.getCurrentBots().size(); i-- > 0;) {
            try {
              botIterator.advance();
              botIterator.value().needsUpdate(true);
              botIterator.value().run(); // Run synchronously to ensure DB is updated before room reload
            } catch (NoSuchElementException e) {
              LOGGER.error("Caught exception", e);
              break;
            }
          }

          // Save ALL remaining pets (including owner's pets) BEFORE clearing
          TIntObjectIterator<Pet> petIterator = this.getCurrentPets().iterator();
          for (int i = this.getCurrentPets().size(); i-- > 0;) {
            try {
              petIterator.advance();
              petIterator.value().needsUpdate = true;
              petIterator.value().run(); // Run synchronously to ensure DB is updated before room reload
            } catch (NoSuchElementException e) {
              LOGGER.error("Caught exception", e);
              break;
            }
          }

          this.unitManager.clear();

          this.unitManager.clearBots();
          this.unitManager.clearPets();
        } catch (Exception e) {
          LOGGER.error("Caught exception", e);
        }
      }

      try {
        this.wordQuiz = "";
        this.yesVotes = 0;
        this.noVotes = 0;
        this.updateDatabaseUserCount();
        this.preLoaded = true;
        this.layout = null;
      } catch (Exception e) {
        LOGGER.error("Caught exception", e);
      }

      Emulator.getGameEnvironment().getRoomPollManager().onRoomUnload(this);
    }

    Emulator.getPluginManager().fireEvent(new RoomUnloadedEvent(this));
  }

  @Override
  public int compareTo(Room o) {
    if (o.getUserCount() != this.getUserCount()) {
      return o.getCurrentHabbos().size() - this.getCurrentHabbos().size();
    }

    return this.id - o.id;
  }

  @Override
  public void serialize(ServerMessage message) {
    message.appendInt(this.id);
    message.appendString(this.name);
    if (this.isPublicRoom()) {
      message.appendInt(0);
      message.appendString("");
    } else {
      message.appendInt(this.ownerId);
      message.appendString(this.ownerName);
    }
    message.appendInt(this.state.getState());
    message.appendInt(this.getUserCount());
    message.appendInt(this.usersMax);
    message.appendString(this.description);
    message.appendInt(0);
    message.appendInt(this.score);
    message.appendInt(0);
    message.appendInt(this.category);

    String[] tags = Arrays.stream(this.tags.split(";")).filter(t -> !t.isEmpty())
        .toArray(String[]::new);
    message.appendInt(tags.length);
    for (String s : tags) {
      message.appendString(s);
    }

    int base = 0;

    if (this.getGuildId() > 0) {
      base = base | 2;
    }

    if (this.isPromoted()) {
      base = base | 4;
    }

    if (!this.isPublicRoom()) {
      base = base | 8;
    }

    message.appendInt(base);

    if (this.getGuildId() > 0) {
      Guild g = Emulator.getGameEnvironment().getGuildManager().getGuild(this.getGuildId());
      if (g != null) {
        message.appendInt(g.getId());
        message.appendString(g.getName());
        message.appendString(g.getBadge());
      } else {
        message.appendInt(0);
        message.appendString("");
        message.appendString("");
      }
    }

    if (this.promoted) {
      message.appendString(this.promotion.getTitle());
      message.appendString(this.promotion.getDescription());
      message.appendInt((this.promotion.getEndTimestamp() - Emulator.getIntUnixTimestamp()) / 60);
    }

  }

  @Override
  public void run() {
    synchronized (this.loadLock) {
      if (this.loaded) {
        try {
          // Run cycle directly instead of scheduling on thread pool
          // This ensures all cycle tasks in the same tick execute synchronously
          // preventing wired desync issues
          this.cycle();
        } catch (Exception e) {
          LOGGER.error("Caught exception", e);
        }
      }
    }

    this.save();
  }

  public void save() {
    if (this.needsUpdate) {
      try (Connection connection = Emulator.getDatabase().getDataSource()
          .getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "UPDATE rooms SET name = ?, description = ?, password = ?, state = ?, users_max = ?, category = ?, score = ?, paper_floor = ?, paper_wall = ?, paper_landscape = ?, thickness_wall = ?, wall_height = ?, thickness_floor = ?, moodlight_data = ?, tags = ?, allow_other_pets = ?, allow_other_pets_eat = ?, allow_walkthrough = ?, allow_hidewall = ?, chat_mode = ?, chat_weight = ?, chat_speed = ?, chat_hearing_distance = ?, chat_protection =?, who_can_mute = ?, who_can_kick = ?, who_can_ban = ?, poll_id = ?, guild_id = ?, roller_speed = ?, override_model = ?, promoted = ?, trade_mode = ?, move_diagonally = ?, owner_id = ?, owner_name = ?, jukebox_active = ?, hidewired = ? WHERE id = ?")) {
        statement.setString(1, this.name);
        statement.setString(2, this.description);
        statement.setString(3, this.password);
        statement.setString(4, this.state.name().toLowerCase());
        statement.setInt(5, this.usersMax);
        statement.setInt(6, this.category);
        statement.setInt(7, this.score);
        statement.setString(8, this.floorPaint);
        statement.setString(9, this.wallPaint);
        statement.setString(10, this.backgroundPaint);
        statement.setInt(11, this.wallSize);
        statement.setInt(12, this.wallHeight);
        statement.setInt(13, this.floorSize);
        StringBuilder moodLightData = new StringBuilder();

        int id = 1;
        for (RoomMoodlightData data : this.moodlightData.valueCollection()) {
          data.setId(id);
          moodLightData.append(data.toString()).append(";");
          id++;
        }

        statement.setString(14, moodLightData.toString());
        statement.setString(15, this.tags);
        statement.setString(16, this.allowPets ? "1" : "0");
        statement.setString(17, this.allowPetsEat ? "1" : "0");
        statement.setString(18, this.allowWalkthrough ? "1" : "0");
        statement.setString(19, this.hideWall ? "1" : "0");
        statement.setInt(20, this.chatMode);
        statement.setInt(21, this.chatWeight);
        statement.setInt(22, this.chatSpeed);
        statement.setInt(23, this.chatDistance);
        statement.setInt(24, this.chatProtection);
        statement.setInt(25, this.muteOption);
        statement.setInt(26, this.kickOption);
        statement.setInt(27, this.banOption);
        statement.setInt(28, this.pollId);
        statement.setInt(29, this.guild);
        statement.setInt(30, this.rollerSpeed);
        statement.setString(31, this.overrideModel ? "1" : "0");
        statement.setString(32, this.promoted ? "1" : "0");
        statement.setInt(33, this.tradeMode);
        statement.setString(34, this.moveDiagonally ? "1" : "0");
        statement.setInt(35, this.ownerId);
        statement.setString(36, this.ownerName);
        statement.setString(37, this.jukeboxActive ? "1" : "0");
        statement.setString(38, this.hideWired ? "1" : "0");
        statement.setInt(39, this.id);
        statement.executeUpdate();
        this.needsUpdate = false;
      } catch (SQLException e) {
        LOGGER.error("Caught SQL exception", e);
      }
    }
  }

  /**
   * Updates the user count in the database.
   * Made public for access by RoomUnitManager.
   */
  public void updateDatabaseUserCount() {
    try (Connection connection = Emulator.getDatabase().getDataSource()
        .getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "UPDATE rooms SET users = ? WHERE id = ? LIMIT 1")) {
      statement.setInt(1, this.getUserCount());
      statement.setInt(2, this.id);
      statement.executeUpdate();
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private void cycle() {
    this.cycleManager.cycle();
  }

  public int getId() {
    return this.id;
  }

  public int getOwnerId() {
    return this.ownerId;
  }

  public void setOwnerId(int ownerId) {
    this.ownerId = ownerId;
  }

  public String getOwnerName() {
    return this.ownerName;
  }

  public void setOwnerName(String ownerName) {
    this.ownerName = ownerName;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;

    if (this.name.length() > 50) {
      this.name = this.name.substring(0, 50);
    }

    if (this.hasGuild()) {
      Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guild);

      if (guild != null) {
        guild.setRoomName(name);
      }
    }
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;

    if (this.description.length() > 250) {
      this.description = this.description.substring(0, 250);
    }
  }

  public RoomLayout getLayout() {
    return this.layout;
  }

  public void setLayout(RoomLayout layout) {
    this.layout = layout;
  }

  public boolean hasCustomLayout() {
    return this.overrideModel;
  }

  public void setHasCustomLayout(boolean overrideModel) {
    this.overrideModel = overrideModel;
  }

  public String getPassword() {
    return this.password;
  }

  public void setPassword(String password) {
    this.password = password;

    if (this.password.length() > 20) {
      this.password = this.password.substring(0, 20);
    }
  }

  public RoomState getState() {
    return this.state;
  }

  public void setState(RoomState state) {
    this.state = state;
  }

  public int getUsersMax() {
    return this.usersMax;
  }

  public void setUsersMax(int usersMax) {
    this.usersMax = usersMax;
  }

  public int getScore() {
    return this.score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public int getCategory() {
    return this.category;
  }

  public void setCategory(int category) {
    this.category = category;
  }

  public String getFloorPaint() {
    return this.floorPaint;
  }

  public void setFloorPaint(String floorPaint) {
    this.floorPaint = floorPaint;
  }

  public String getWallPaint() {
    return this.wallPaint;
  }

  public void setWallPaint(String wallPaint) {
    this.wallPaint = wallPaint;
  }

  public String getBackgroundPaint() {
    return this.backgroundPaint;
  }

  public void setBackgroundPaint(String backgroundPaint) {
    this.backgroundPaint = backgroundPaint;
  }

  public int getWallSize() {
    return this.wallSize;
  }

  public void setWallSize(int wallSize) {
    this.wallSize = wallSize;
  }

  public int getWallHeight() {
    return this.wallHeight;
  }

  public void setWallHeight(int wallHeight) {
    this.wallHeight = wallHeight;
  }

  public int getFloorSize() {
    return this.floorSize;
  }

  public void setFloorSize(int floorSize) {
    this.floorSize = floorSize;
  }

  public String getTags() {
    return this.tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public int getTradeMode() {
    return this.tradeMode;
  }

  public void setTradeMode(int tradeMode) {
    this.tradeMode = tradeMode;
  }

  public boolean moveDiagonally() {
    return this.moveDiagonally;
  }

  public void moveDiagonally(boolean moveDiagonally) {
    this.moveDiagonally = moveDiagonally;
    this.layout.moveDiagonally(this.moveDiagonally);
    this.needsUpdate = true;
  }

  public int getGuildId() {
    return this.guild;
  }

  public boolean hasGuild() {
    return this.guild != 0;
  }

  public void setGuild(int guild) {
    this.guild = guild;
  }

  public String getGuildName() {
    if (this.hasGuild()) {
      Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guild);

      if (guild != null) {
        return guild.getName();
      }
    }

    return "";
  }

  public boolean isPublicRoom() {
    return this.publicRoom;
  }

  public void setPublicRoom(boolean publicRoom) {
    this.publicRoom = publicRoom;
  }

  public boolean isStaffPicked() {
    return this.staffPicked;
  }

  public void setStaffPicked(boolean staffPicked) {
    this.staffPicked = staffPicked;
  }

  public boolean isAllowPets() {
    return this.allowPets;
  }

  public void setAllowPets(boolean allowPets) {
    this.allowPets = allowPets;
    if (!allowPets) {
      removeAllPets(ownerId);
    }
  }

  public boolean isAllowPetsEat() {
    return this.allowPetsEat;
  }

  public void setAllowPetsEat(boolean allowPetsEat) {
    this.allowPetsEat = allowPetsEat;
  }

  public boolean isAllowWalkthrough() {
    return this.allowWalkthrough;
  }

  public void setAllowWalkthrough(boolean allowWalkthrough) {
    this.allowWalkthrough = allowWalkthrough;
  }

  public boolean isAllowBotsWalk() {
    return this.allowBotsWalk;
  }

  public void setAllowBotsWalk(boolean allowBotsWalk) {
    this.allowBotsWalk = allowBotsWalk;
  }

  public boolean isAllowEffects() {
    return this.allowEffects;
  }

  public void setAllowEffects(boolean allowEffects) {
    this.allowEffects = allowEffects;
  }

  public boolean isHideWall() {
    return this.hideWall;
  }

  public void setHideWall(boolean hideWall) {
    this.hideWall = hideWall;
  }

  public Color getBackgroundTonerColor() {
    Color color = new Color(0, 0, 0);
    TIntObjectMap<HabboItem> items = this.itemManager.getRoomItems();
    TIntObjectIterator<HabboItem> iterator = items.iterator();

    for (int i = items.size(); i > 0; i--) {
      try {
        iterator.advance();
        HabboItem object = iterator.value();

        if (object instanceof InteractionBackgroundToner) {
          String[] extraData = object.getExtradata().split(":");

          if (extraData.length == 4) {
            if (extraData[0].equalsIgnoreCase("1")) {
              return Color.getHSBColor(Integer.parseInt(extraData[1]),
                  Integer.parseInt(extraData[2]), Integer.parseInt(extraData[3]));
            }
          }
        }
      } catch (Exception e) {
      }
    }

    return color;
  }

  public int getChatMode() {
    return this.chatMode;
  }

  public void setChatMode(int chatMode) {
    this.chatMode = chatMode;
  }

  public int getChatWeight() {
    return this.chatWeight;
  }

  public void setChatWeight(int chatWeight) {
    this.chatWeight = chatWeight;
  }

  public int getChatSpeed() {
    return this.chatSpeed;
  }

  public void setChatSpeed(int chatSpeed) {
    this.chatSpeed = chatSpeed;
  }

  public int getChatDistance() {
    return this.chatDistance;
  }

  public void setChatDistance(int chatDistance) {
    this.chatDistance = chatDistance;
  }

  public void removeAllPets() {
    this.unitManager.removeAllPets();
  }

  /**
   * Removes all pets from the room except if the owner id is excludeUserId
   *
   * @param excludeUserId Habbo id to keep pets
   */
  public void removeAllPets(int excludeUserId) {
    this.unitManager.removeAllPets(excludeUserId);
  }

  public int getChatProtection() {
    return this.chatProtection;
  }

  public void setChatProtection(int chatProtection) {
    this.chatProtection = chatProtection;
  }

  public int getMuteOption() {
    return this.muteOption;
  }

  public void setMuteOption(int muteOption) {
    this.muteOption = muteOption;
  }

  public int getKickOption() {
    return this.kickOption;
  }

  public void setKickOption(int kickOption) {
    this.kickOption = kickOption;
  }

  public int getBanOption() {
    return this.banOption;
  }

  public void setBanOption(int banOption) {
    this.banOption = banOption;
  }

  public int getPollId() {
    return this.pollId;
  }

  public void setPollId(int pollId) {
    this.pollId = pollId;
  }

  public int getRollerSpeed() {
    return this.rollerSpeed;
  }

  public void setRollerSpeed(int rollerSpeed) {
    this.rollerSpeed = rollerSpeed;
    this.needsUpdate = true;
  }

  public String[] filterAnything() {
    String guildDescription = "";
    if (this.hasGuild()) {
      Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guild);
      if (guild != null) {
        guildDescription = guild.getDescription();
      }
    }

    return new String[] {
        this.getOwnerName(),
        this.getName(),
        this.getDescription(),
        this.getTags(),
        this.getGuildName(),
        guildDescription,
        this.getPromotionDesc()
    };
  }

  public long getCycleTimestamp() {
    return this.cycleManager.getCycleTimestamp();
  }

  public boolean isPromoted() {
    return this.promotionManager.isPromoted();
  }

  public RoomPromotion getPromotion() {
    return this.promotion;
  }

  public String getPromotionDesc() {
    if (this.promotion != null) {
      return this.promotion.getDescription();
    }

    return "";
  }

  public void createPromotion(String title, String description, int category) {
    this.promotionManager.createPromotion(title, description, category);
  }

  public boolean addGame(Game game) {
    return this.gameManager.addGame(game);
  }

  public boolean deleteGame(Game game) {
    return this.gameManager.deleteGame(game);
  }

  public Game getGame(Class<? extends Game> gameType) {
    return this.gameManager.getGame(gameType);
  }

  public Game getGameOrCreate(Class<? extends Game> gameType) {
    return this.gameManager.getGameOrCreate(gameType);
  }

  public Set<Game> getGames() {
    return this.gameManager.getGames();
  }

  public int getUserCount() {
    return this.unitManager.getHabboCount();
  }

  public ConcurrentHashMap<Integer, Habbo> getCurrentHabbos() {
    return this.unitManager.getCurrentHabbos();
  }

  public Collection<Habbo> getHabbos() {
    return this.unitManager.getHabbos();
  }

  public TIntObjectMap<Habbo> getHabboQueue() {
    return this.unitManager.getHabboQueue();
  }

  public TIntObjectMap<String> getFurniOwnerNames() {
    return this.itemManager.getFurniOwnerNames();
  }

  public String getFurniOwnerName(int userId) {
    return this.itemManager.getFurniOwnerName(userId);
  }

  public TIntIntMap getFurniOwnerCount() {
    return this.itemManager.getFurniOwnerCount();
  }

  public TIntObjectMap<RoomMoodlightData> getMoodlightData() {
    return this.moodlightData;
  }

  public int getLastTimerReset() {
    return this.lastTimerReset;
  }

  public void setLastTimerReset(int lastTimerReset) {
    this.lastTimerReset = lastTimerReset;
  }

  public void addToQueue(Habbo habbo) {
    this.unitManager.addToQueue(habbo);
  }

  public boolean removeFromQueue(Habbo habbo) {
    try {
      this.sendComposer(new FlatAccessibleMessageComposer(habbo.getHabboInfo().getUsername()).compose());

      return this.unitManager.removeFromQueue(habbo.getHabboInfo().getId()) != null;
    } catch (Exception e) {
      LOGGER.error("Caught exception", e);
    }

    return true;
  }

  public TIntObjectMap<Bot> getCurrentBots() {
    return this.unitManager.getCurrentBots();
  }

  public TIntObjectMap<Pet> getCurrentPets() {
    return this.unitManager.getCurrentPets();
  }

  public THashSet<String> getWordFilterWords() {
    return this.chatManager.getWordFilterWords();
  }

  public RoomSpecialTypes getRoomSpecialTypes() {
    return this.roomSpecialTypes;
  }

  /**
   * Alias for getRoomSpecialTypes() for shorter access.
   */
  public RoomSpecialTypes getSpecialTypes() {
    return this.roomSpecialTypes;
  }

  public boolean isPreLoaded() {
    return this.preLoaded;
  }

  public boolean isLoaded() {
    return this.loaded;
  }

  public void setNeedsUpdate(boolean needsUpdate) {
    this.needsUpdate = needsUpdate;
  }

  public TIntArrayList getRights() {
    return this.rights;
  }

  public boolean isMuted() {
    return this.muted;
  }

  public void setMuted(boolean muted) {
    this.muted = muted;
  }

  public TraxManager getTraxManager() {
    return this.traxManager;
  }

  public void addHabboItem(HabboItem item) {
    this.itemManager.addHabboItem(item);
  }

  public HabboItem getHabboItem(int id) {
    return this.itemManager.getHabboItemByVisibleId(id);
  }

  public HabboItem getHabboItemByDatabaseId(int id) {
    return this.itemManager.getHabboItem(id);
  }

  void removeHabboItem(int id) {
    this.itemManager.removeHabboItem(id);
  }

  public void removeHabboItem(HabboItem item) {
    this.itemManager.removeHabboItem(item);
  }

  public THashSet<HabboItem> getFloorItems() {
    return this.itemManager.getFloorItems();
  }

  public THashSet<HabboItem> getWallItems() {
    return this.itemManager.getWallItems();
  }

  public THashSet<HabboItem> getPostItNotes() {
    return this.itemManager.getPostItNotes();
  }

  public void addHabbo(Habbo habbo) {
    this.unitManager.addHabbo(habbo);
  }

  public void kickHabbo(Habbo habbo, boolean alert) {
    this.unitManager.kickHabbo(habbo, alert);
  }

  public void removeHabbo(Habbo habbo) {
    if (this.gamehallManager != null) {
      this.gamehallManager.onRoomLeave(habbo, "left_room");
    }
    this.unitManager.removeHabbo(habbo);
  }

  public boolean isItemHiddenByAreaHide(HabboItem item) {
    if (item == null || this.roomSpecialTypes == null) {
      return false;
    }
    THashSet<HabboItem> controllers = this.roomSpecialTypes.getItemsOfType(InteractionAreaHide.class);
    if (item instanceof InteractionAreaHide areaHide && areaHide.shouldHideSelf(this)) {
      return true;
    }
    for (HabboItem controller : controllers) {
      InteractionAreaHide areaHide = (InteractionAreaHide) controller;
      if (areaHide.hidesFloorItem(this, item) || areaHide.hidesWallItem(this, item)) {
        return true;
      }
    }
    return false;
  }

  private void snapshotAreaHideVisibility() {
    synchronized (this.areaHideVisibilityLock) {
      this.areaHideHiddenFloorItems.clear();
      for (HabboItem item : this.getFloorItems()) {
        if (this.isItemHiddenByAreaHide(item)) {
          this.areaHideHiddenFloorItems.add(item.getId());
        }
      }
      this.areaHideHiddenWallItems.clear();
      for (HabboItem item : this.getWallItems()) {
        if (this.isItemHiddenByAreaHide(item)) {
          this.areaHideHiddenWallItems.add(item.getId());
        }
      }
    }
  }

  public void refreshAreaHideVisibility() {
    if (!this.isLoaded() || this.roomSpecialTypes == null) {
      return;
    }
    THashSet<HabboItem> controllers =
        this.roomSpecialTypes.getItemsOfType(InteractionAreaHide.class);
    if (controllers.isEmpty()
        && this.areaHideHiddenFloorItems.isEmpty()
        && this.areaHideHiddenWallItems.isEmpty()) {
      return;
    }
    synchronized (this.areaHideVisibilityLock) {
      Set<Integer> hiddenFloor = new java.util.HashSet<>();
      Set<Integer> hiddenWall = new java.util.HashSet<>();
      for (HabboItem item : this.getFloorItems()) {
        if (this.isItemHiddenByAreaHide(item)) {
          hiddenFloor.add(item.getId());
        }
      }
      for (HabboItem item : this.getWallItems()) {
        if (this.isItemHiddenByAreaHide(item)) {
          hiddenWall.add(item.getId());
        }
      }

      for (Integer itemId : this.areaHideHiddenFloorItems) {
        if (!hiddenFloor.contains(itemId)) {
          HabboItem item = this.getHabboItem(itemId);
          if (item != null) {
            this.sendComposer(new ObjectAddMessageComposer(item, this.getFurniOwnerName(item.getUserId())).compose());
          }
        }
      }
      for (Integer itemId : this.areaHideHiddenWallItems) {
        if (!hiddenWall.contains(itemId)) {
          HabboItem item = this.getHabboItem(itemId);
          if (item != null) {
            this.sendComposer(new ItemAddMessageComposer(item, this.getFurniOwnerName(item.getUserId())).compose());
          }
        }
      }
      for (Integer itemId : hiddenFloor) {
        if (!this.areaHideHiddenFloorItems.contains(itemId)) {
          HabboItem item = this.getHabboItem(itemId);
          if (item != null) {
            this.sendComposer(new ObjectRemoveMessageComposer(item).compose());
          }
        }
      }
      for (Integer itemId : hiddenWall) {
        if (!this.areaHideHiddenWallItems.contains(itemId)) {
          HabboItem item = this.getHabboItem(itemId);
          if (item != null) {
            this.sendComposer(new ItemRemoveMessageComposer(item).compose());
          }
        }
      }
      this.areaHideHiddenFloorItems.clear();
      this.areaHideHiddenFloorItems.addAll(hiddenFloor);
      this.areaHideHiddenWallItems.clear();
      this.areaHideHiddenWallItems.addAll(hiddenWall);
    }
  }

  /**
   * A user cannot safely enter on a tile removed by Area Hide: AIR renders the
   * unit at the void-floor fallback altitude. Keep the real door direction but
   * use the nearest reachable visible floor tile when the door is masked.
   */
  public RoomTile getAreaHideSafeSpawnTile() {
    if (this.layout == null) {
      return null;
    }
    int doorX = this.layout.getDoorX();
    int doorY = this.layout.getDoorY();
    boolean doorHidden = false;
    for (HabboItem item : this.roomSpecialTypes.getItemsOfType(InteractionAreaHide.class)) {
      if (((InteractionAreaHide) item).hidesRoomTile(doorX, doorY)) {
        doorHidden = true;
        break;
      }
    }
    if (!doorHidden) {
      return null;
    }

    int mapWidth = this.layout.getMapSizeX();
    int mapHeight = this.layout.getMapSizeY();
    boolean[][] visited = new boolean[mapWidth][mapHeight];
    ArrayDeque<int[]> pending = new ArrayDeque<>();
    if (doorX < 0 || doorY < 0 || doorX >= mapWidth || doorY >= mapHeight) {
      return null;
    }
    visited[doorX][doorY] = true;
    pending.add(new int[]{doorX, doorY});
    int[] dx = {0, 1, 0, -1};
    int[] dy = {-1, 0, 1, 0};

    while (!pending.isEmpty()) {
      int[] current = pending.removeFirst();
      for (int direction = 0; direction < 4; direction++) {
        int x = current[0] + dx[direction];
        int y = current[1] + dy[direction];
        if (x < 0 || y < 0 || x >= mapWidth || y >= mapHeight || visited[x][y]) {
          continue;
        }
        visited[x][y] = true;
        if (this.layout.isVoidTile((short) x, (short) y)) {
          continue;
        }
        boolean hidden = false;
        for (HabboItem item : this.roomSpecialTypes.getItemsOfType(InteractionAreaHide.class)) {
          if (((InteractionAreaHide) item).hidesRoomTile(x, y)) {
            hidden = true;
            break;
          }
        }
        if (!hidden) {
          return this.layout.getTile((short) x, (short) y);
        }
        pending.addLast(new int[]{x, y});
      }
    }
    return null;
  }

  public void removeHabbo(Habbo habbo, boolean sendRemovePacket) {
    if (this.gamehallManager != null) {
      this.gamehallManager.onRoomLeave(habbo, "left_room");
    }
    this.unitManager.removeHabbo(habbo, sendRemovePacket);
  }

  public void addBot(Bot bot) {
    this.unitManager.addBot(bot);
  }

  public void addPet(Pet pet) {
    this.unitManager.addPet(pet);
  }

  public Bot getBot(int botId) {
    return this.unitManager.getBot(botId);
  }

  public Bot getBot(RoomUnit roomUnit) {
    return this.unitManager.getBot(roomUnit);
  }

  public Bot getBotByRoomUnitId(int id) {
    return this.unitManager.getBotByRoomUnitId(id);
  }

  public List<Bot> getBots(String name) {
    return this.unitManager.getBots(name);
  }

  public boolean hasBotsAt(final int x, final int y) {
    return this.unitManager.hasBotsAt(x, y);
  }

  public Pet getPet(int petId) {
    return this.unitManager.getPet(petId);
  }

  public Pet getPet(RoomUnit roomUnit) {
    return this.unitManager.getPet(roomUnit);
  }

  public boolean removeBot(Bot bot) {
    return this.unitManager.removeBot(bot);
  }

  public void placePet(Pet pet, short x, short y, double z, int rot) {
    this.unitManager.placePet(pet, x, y, z, rot);
  }

  public Pet removePet(int petId) {
    return this.unitManager.removePet(petId);
  }

  public boolean hasHabbosAt(int x, int y) {
    return this.unitManager.hasHabbosAt(x, y);
  }

  public boolean hasPetsAt(int x, int y) {
    return this.unitManager.hasPetsAt(x, y);
  }

  public THashSet<Bot> getBotsAt(RoomTile tile) {
    return this.unitManager.getBotsAt(tile);
  }

  public THashSet<Pet> getPetsAt(RoomTile tile) {
    return this.unitManager.getPetsAt(tile);
  }

  public THashSet<Habbo> getHabbosAt(short x, short y) {
    return this.unitManager.getHabbosAt(x, y);
  }

  public THashSet<Habbo> getHabbosAt(RoomTile tile) {
    return this.unitManager.getHabbosAt(tile);
  }

  public THashSet<RoomUnit> getHabbosAndBotsAt(short x, short y) {
    return this.unitManager.getHabbosAndBotsAt(x, y);
  }

  public THashSet<RoomUnit> getHabbosAndBotsAt(RoomTile tile) {
    return this.unitManager.getHabbosAndBotsAt(tile);
  }

  public THashSet<Habbo> getHabbosOnItem(HabboItem item) {
    return this.unitManager.getHabbosOnItem(item);
  }

  public THashSet<Bot> getBotsOnItem(HabboItem item) {
    return this.unitManager.getBotsOnItem(item);
  }

  public void teleportHabboToItem(Habbo habbo, HabboItem item) {
    this.unitManager.teleportHabboToItem(habbo, item);
  }

  public void teleportHabboToLocation(Habbo habbo, short x, short y) {
    this.unitManager.teleportHabboToLocation(habbo, x, y);
  }

  public void teleportRoomUnitToItem(RoomUnit roomUnit, HabboItem item) {
    this.unitManager.teleportRoomUnitToItem(roomUnit, item);
  }

  public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y) {
    this.unitManager.teleportRoomUnitToLocation(roomUnit, x, y);
  }

  public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y, double z) {
    this.unitManager.teleportRoomUnitToLocation(roomUnit, x, y, z);
  }

  public void muteHabbo(Habbo habbo, int minutes) {
    this.rightsManager.muteHabbo(habbo, minutes);
  }

  public boolean isMuted(Habbo habbo) {
    return this.rightsManager.isMuted(habbo);
  }

  public void habboEntered(Habbo habbo) {
    this.unitManager.habboEntered(habbo);
  }

  public void floodMuteHabbo(Habbo habbo, int timeOut) {
    this.chatManager.floodMuteHabbo(habbo, timeOut);
  }

  public void talk(Habbo habbo, RoomChatMessage roomChatMessage, RoomChatType chatType) {
    this.chatManager.talk(habbo, roomChatMessage, chatType);
  }

  public void talk(final Habbo habbo, final RoomChatMessage roomChatMessage, RoomChatType chatType,
      boolean ignoreWired) {
    this.chatManager.talk(habbo, roomChatMessage, chatType, ignoreWired);
  }

  public THashSet<RoomTile> getLockedTiles() {
    return this.itemManager.getLockedTiles();
  }

  @Deprecated
  public THashSet<HabboItem> getItemsAt(int x, int y) {
    return this.itemManager.getItemsAt(x, y);
  }

  public THashSet<HabboItem> getItemsAt(RoomTile tile) {
    return this.itemManager.getItemsAt(tile);
  }

  public THashSet<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
    return this.itemManager.getItemsAt(tile, returnOnFirst);
  }

  public THashSet<HabboItem> getItemsAt(int x, int y, double minZ) {
    return this.itemManager.getItemsAt(x, y, minZ);
  }

  public THashSet<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
    return this.itemManager.getItemsAt(type, x, y);
  }

  public boolean hasItemsAt(int x, int y) {
    return this.itemManager.hasItemsAt(x, y);
  }

  public HabboItem getTopItemAt(int x, int y) {
    return this.itemManager.getTopItemAt(x, y);
  }

  public HabboItem getTopItemAt(int x, int y, HabboItem exclude) {
    return this.itemManager.getTopItemAt(x, y, exclude);
  }

  public HabboItem getTopItemAt(THashSet<RoomTile> tiles, HabboItem exclude) {
    return this.itemManager.getTopItemAt(tiles, exclude);
  }

  public double getTopHeightAt(int x, int y) {
    return this.itemManager.getTopHeightAt(x, y);
  }

  @Deprecated
  public HabboItem getLowestChair(int x, int y) {
    return this.itemManager.getLowestChair(x, y);
  }

  public HabboItem getLowestChair(RoomTile tile) {
    return this.itemManager.getLowestChair(tile);
  }

  public HabboItem getTallestChair(RoomTile tile) {
    return this.itemManager.getTallestChair(tile);
  }

  public double getStackHeight(short x, short y, boolean calculateHeightmap, HabboItem exclude) {
    return this.tileManager.getStackHeight(x, y, calculateHeightmap, exclude);
  }

  public double getStackHeight(short x, short y, boolean calculateHeightmap) {
    return this.tileManager.getStackHeight(x, y, calculateHeightmap);
  }

  public boolean hasObjectTypeAt(Class<?> type, int x, int y) {
    return this.itemManager.hasObjectTypeAt(type, x, y);
  }

  public boolean canSitOrLayAt(int x, int y) {
    return this.tileManager.canSitOrLayAt(x, y);
  }

  public boolean canSitAt(int x, int y) {
    return this.tileManager.canSitAt(x, y);
  }

  boolean canWalkAt(RoomTile roomTile) {
    return this.tileManager.canWalkAt(roomTile);
  }

  boolean canSitAt(THashSet<HabboItem> items) {
    return this.tileManager.canSitAt(items);
  }

  public boolean canLayAt(int x, int y) {
    return this.tileManager.canLayAt(x, y);
  }

  boolean canLayAt(THashSet<HabboItem> items) {
    return this.tileManager.canLayAt(items);
  }

  public RoomTile getRandomWalkableTile() {
    return this.tileManager.getRandomWalkableTile();
  }

  public RoomTile getRandomWalkableTilesAround(RoomUnit roomUnit, RoomTile tile, int radius) {
    return this.tileManager.getRandomWalkableTilesAround(roomUnit, tile, radius);
  }

  public Habbo getHabbo(String username) {
    return this.unitManager.getHabbo(username);
  }

  public Habbo getHabbo(RoomUnit roomUnit) {
    return this.unitManager.getHabboByRoomUnit(roomUnit);
  }

  public Habbo getHabbo(int userId) {
    return this.unitManager.getHabbo(userId);
  }

  public Habbo getHabboByRoomUnitId(int roomUnitId) {
    return this.unitManager.getHabboByRoomUnitId(roomUnitId);
  }

  public void sendComposer(ServerMessage message) {
    this.messagingManager.sendComposer(message);
  }

  public void sendComposerToHabbosWithRights(ServerMessage message) {
    this.messagingManager.sendComposerToHabbosWithRights(message);
  }

  public void petChat(ServerMessage message) {
    this.messagingManager.petChat(message);
  }

  public void botChat(ServerMessage message) {
    this.messagingManager.botChat(message);
  }

  private void loadRights(Connection connection) {
    this.rights.clear();
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT user_id FROM room_rights WHERE room_id = ?")) {
      statement.setInt(1, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          this.rights.add(set.getInt("user_id"));
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private void loadBans(Connection connection) {
    this.bannedHabbos.clear();

    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT users.username, users.id, room_bans.* FROM room_bans INNER JOIN users ON room_bans.user_id = users.id WHERE ends > ? AND room_bans.room_id = ?")) {
      statement.setInt(1, Emulator.getIntUnixTimestamp());
      statement.setInt(2, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          if (this.bannedHabbos.containsKey(set.getInt("user_id"))) {
            continue;
          }

          this.bannedHabbos.put(set.getInt("user_id"), new RoomBan(set));
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  public RoomRightLevels getGuildRightLevel(Habbo habbo) {
    if (this.guild > 0 && habbo.getHabboStats().hasGuild(this.guild)) {
      Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guild);

      if (Emulator.getGameEnvironment().getGuildManager().getOnlyAdmins(guild)
          .get(habbo.getHabboInfo().getId()) != null) {
        return RoomRightLevels.GUILD_ADMIN;
      }

      if (guild.getRights()) {
        return RoomRightLevels.GUILD_RIGHTS;
      }
    }

    return RoomRightLevels.NONE;
  }

  /**
   * @deprecated Deprecated since 2.5.0. Use {@link #getGuildRightLevel(Habbo)}
   *             instead.
   */
  @Deprecated
  public int guildRightLevel(Habbo habbo) {
    return this.rightsManager.guildRightLevel(habbo);
  }

  public boolean isOwner(Habbo habbo) {
    if (habbo != null && habbo.getHabboInfo() != null
        && com.eu.habbo.habbohotel.wired.menu.WiredMenuPreferences.isPlaytesting(
            habbo.getHabboInfo().getId())) {
      return false;
    }
    return this.rightsManager.isOwner(habbo);
  }

  public boolean hasRights(Habbo habbo) {
    if (habbo != null && habbo.getHabboInfo() != null
        && com.eu.habbo.habbohotel.wired.menu.WiredMenuPreferences.isPlaytesting(
            habbo.getHabboInfo().getId())) {
      return false;
    }
    return this.rightsManager.hasRights(habbo);
  }

  public void giveRights(Habbo habbo) {
    this.rightsManager.giveRights(habbo);
  }

  public void giveRights(int userId) {
    this.rightsManager.giveRights(userId);
  }

  public void removeRights(int userId) {
    this.rightsManager.removeRights(userId);
  }

  public void removeAllRights() {
    this.rightsManager.removeAllRights();
  }

  void refreshRightsInRoom() {
    this.rightsManager.refreshRightsInRoom();
  }

  public void refreshRightsForHabbo(Habbo habbo) {
    this.rightsManager.refreshRightsForHabbo(habbo);
  }

  public THashMap<Integer, String> getUsersWithRights() {
    return this.rightsManager.getUsersWithRights();
  }

  public void unbanHabbo(int userId) {
    this.rightsManager.unbanHabbo(userId);
  }

  public boolean isBanned(Habbo habbo) {
    return this.rightsManager.isBanned(habbo);
  }

  public TIntObjectHashMap<RoomBan> getBannedHabbos() {
    return this.bannedHabbos;
  }

  public void addRoomBan(RoomBan roomBan) {
    this.rightsManager.addRoomBan(roomBan);
  }

  public void makeSit(Habbo habbo) {
    if (habbo.getRoomUnit() == null) {
      return;
    }

    if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
      this.triggerGamehallSeatWalkOn(habbo);
      return;
    }

    if (!habbo.getRoomUnit().canForcePosture()) {
      return;
    }

    this.dance(habbo, DanceType.NONE);
    habbo.getRoomUnit().cmdSit = true;
    habbo.getRoomUnit().setBodyRotation(
        RoomUserRotation.values()[habbo.getRoomUnit().getBodyRotation().getValue()
            - habbo.getRoomUnit().getBodyRotation().getValue() % 2]);
    habbo.getRoomUnit().setStatus(RoomUnitStatus.SIT, 0.5 + "");
    this.sendComposer(new UserUpdateMessageComposer(habbo.getRoomUnit()).compose());
    this.triggerGamehallSeatWalkOn(habbo);
    WiredManager.triggerUserPerformsAction(this, habbo.getRoomUnit(), WiredUserAction.SIT, "");
  }

  void triggerGamehallSeatWalkOn(Habbo habbo) {
    if (habbo == null || habbo.getRoomUnit() == null) {
      return;
    }

    int x = habbo.getRoomUnit().getX();
    int y = habbo.getRoomUnit().getY();
    for (HabboItem item : this.getItemsAt(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY())) {
      if (item instanceof InteractionGamehallSeat) {
        InteractionGamehallSeat gamehallSeat = (InteractionGamehallSeat) item;
        LOGGER.info("[GAMEHALL] sit-hook object user={} room={} model={} tile={},{} itemId={} station={} seat={} type={}",
            habbo.getHabboInfo().getUsername(), this.id,
            this.getLayout() != null ? this.getLayout().getName() : "null", x, y, item.getId(),
            gamehallSeat.getAssignment().getStationId(), gamehallSeat.getAssignment().getSeatIndex(),
            gamehallSeat.getAssignment().getType());
        this.gamehallManager.onSeat(habbo, gamehallSeat.getAssignment());
        return;
      }
    }

    if (this.getLayout() != null && this.gamehallManager != null) {
      GamehallManager.SeatAssignment assignment =
          this.gamehallManager.resolveSeat(this.getLayout().getName(), x, y);
      if (assignment != null) {
        LOGGER.info("[GAMEHALL] sit-hook direct user={} room={} model={} tile={},{} station={} seat={} type={}",
            habbo.getHabboInfo().getUsername(), this.id, this.getLayout().getName(), x, y,
            assignment.getStationId(), assignment.getSeatIndex(), assignment.getType());
        this.gamehallManager.onSeat(habbo, assignment);
      } else {
        LOGGER.info("[GAMEHALL] sit-hook miss user={} room={} model={} tile={},{}",
            habbo.getHabboInfo().getUsername(), this.id, this.getLayout().getName(), x, y);
      }
    }
  }

  public void makeStand(Habbo habbo) {
    if (habbo.getRoomUnit() == null) {
      return;
    }

    HabboItem item = this.getTopItemAt(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY());
    boolean wasSittingOrLaying = habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)
        || habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY);
    if (wasSittingOrLaying && item instanceof InteractionGamehallSeat) {
      try {
        item.onWalkOff(habbo.getRoomUnit(), this, null);
      } catch (Exception ignored) {
      }
    }

    if (item == null || !item.getBaseItem().allowSit() || !item.getBaseItem().allowLay()) {
      habbo.getRoomUnit().cmdStand = true;
      habbo.getRoomUnit().setBodyRotation(
          RoomUserRotation.values()[habbo.getRoomUnit().getBodyRotation().getValue()
              - habbo.getRoomUnit().getBodyRotation().getValue() % 2]);
      habbo.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
      habbo.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
      this.sendComposer(new UserUpdateMessageComposer(habbo.getRoomUnit()).compose());
      if (wasSittingOrLaying) {
        WiredManager.triggerUserPerformsAction(this, habbo.getRoomUnit(), WiredUserAction.STAND, "");
      }
    }
  }

  public void giveEffect(Habbo habbo, int effectId, int duration) {
    this.unitManager.giveEffect(habbo, effectId, duration);
  }

  public void giveEffect(RoomUnit roomUnit, int effectId, int duration) {
    this.unitManager.giveEffect(roomUnit, effectId, duration);
  }

  public void giveHandItem(Habbo habbo, int handItem) {
    this.unitManager.giveHandItem(habbo, handItem);
  }

  public void giveHandItem(RoomUnit roomUnit, int handItem) {
    this.unitManager.giveHandItem(roomUnit, handItem);
  }

  public void updateItem(HabboItem item) {
    if (this.isLoaded()) {
      if (item != null && item.getRoomId() == this.id) {
        if (item.getBaseItem() != null) {
          if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (!this.isItemHiddenByAreaHide(item)) {
              this.sendComposer(new ObjectUpdateMessageComposer(item).compose());
            }
            this.updateTiles(this.getLayout()
                .getTilesAt(this.layout.getTile(item.getX(), item.getY()),
                    item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                    item.getRotation()));
          } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            if (!this.isItemHiddenByAreaHide(item)) {
              this.sendComposer(new ItemUpdateMessageComposer(item).compose());
            }
          }
        }
      }
    }
  }

  public void updateItemState(HabboItem item) {
    if (this.isItemHiddenByAreaHide(item)) {
      return;
    }
    if (!item.isLimited()) {
      this.sendComposer(new OneWayDoorStatusMessageComposer(item).compose());
    } else {
      this.sendComposer(new ObjectUpdateMessageComposer(item).compose());
    }

    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
      if (this.layout == null) {
        return;
      }

      this.updateTiles(this.getLayout()
          .getTilesAt(this.layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
              item.getBaseItem().getLength(), item.getRotation()));

      if (item instanceof InteractionMultiHeight) {
        ((InteractionMultiHeight) item).updateUnitsOnItem(this);
      }
    }
  }

  public int getUserFurniCount(int userId) {
    return this.itemManager.getFurniOwnerCount().get(userId);
  }

  public int getUserUniqueFurniCount(int userId) {
    return this.itemManager.getUserUniqueFurniCount(userId);
  }

  public void ejectUserFurni(int userId) {
    this.itemManager.ejectUserFurni(userId);
  }

  public void pickUpBuildersClubItems(int userId, Habbo picker) {
    this.itemManager.pickUpBuildersClubItems(userId, picker);
  }

  public void pickUpBuildersClubItem(HabboItem item, Habbo picker) {
    this.itemManager.pickUpBuildersClubItem(item, picker);
  }

  public boolean hasBuildersClubItems() {
    return this.itemManager.hasBuildersClubItems();
  }

  public boolean hasUserBuildersClubItems(int userId) {
    return this.itemManager.hasUserBuildersClubItems(userId);
  }

  public void ejectUserItem(HabboItem item) {
    this.itemManager.ejectUserItem(item);
  }

  public void ejectAll() {
    this.itemManager.ejectAll();
  }

  public void ejectAll(Habbo habbo) {
    this.itemManager.ejectAll(habbo);
  }

  public void refreshGuild(Guild guild) {
    if (guild.getRoomId() == this.id) {
      THashSet<GuildMember> members = Emulator.getGameEnvironment().getGuildManager()
          .getGuildMembers(guild.getId());

      for (Habbo habbo : this.getHabbos()) {
        Optional<GuildMember> member = members.stream()
            .filter(m -> m.getUserId() == habbo.getHabboInfo().getId()).findAny();

        if (!member.isPresent()) {
          continue;
        }

        habbo.getClient()
            .sendResponse(new HabboGroupDetailsMessageComposer(guild, habbo.getClient(), false, member.get()));
      }
    }

    this.refreshGuildRightsInRoom();
  }

  public void refreshGuildColors(Guild guild) {
    if (guild.getRoomId() == this.id) {
      TIntObjectMap<HabboItem> items = this.itemManager.getRoomItems();
      TIntObjectIterator<HabboItem> iterator = items.iterator();

      for (int i = items.size(); i-- > 0;) {
        try {
          iterator.advance();
        } catch (Exception e) {
          break;
        }

        HabboItem habboItem = iterator.value();

        if (habboItem instanceof InteractionGuildFurni) {
          if (((InteractionGuildFurni) habboItem).getGuildId() == guild.getId()) {
            this.updateItem(habboItem);
          }
        }
      }
    }
  }

  public void refreshGuildRightsInRoom() {
    for (Habbo habbo : this.getHabbos()) {
      if (habbo.getHabboInfo().getCurrentRoom() == this) {
        if (habbo.getHabboInfo().getId() != this.ownerId) {
          if (!(habbo.hasPermission(Permission.ACC_ANYROOMOWNER) || habbo.hasPermission(
              Permission.ACC_MOVEROTATE))) {
            this.refreshRightsForHabbo(habbo);
          }
        }
      }
    }
  }

  public void idle(Habbo habbo) {
    this.unitManager.idle(habbo);
  }

  public void unIdle(Habbo habbo) {
    this.unitManager.unIdle(habbo);
  }

  public void dance(Habbo habbo, DanceType danceType) {
    this.unitManager.dance(habbo, danceType);
  }

  public void dance(RoomUnit unit, DanceType danceType) {
    this.unitManager.dance(unit, danceType);
  }

  public void addToWordFilter(String word) {
    this.chatManager.addToWordFilter(word);
  }

  public void removeFromWordFilter(String word) {
    this.chatManager.removeFromWordFilter(word);
  }

  public void handleWordQuiz(Habbo habbo, String answer) {
    this.wordQuizManager.handleWordQuiz(habbo, answer);
  }

  public void startWordQuiz(String question, int duration) {
    this.wordQuizManager.startWordQuiz(question, duration);
  }

  public boolean hasActiveWordQuiz() {
    return this.wordQuizManager.hasActiveWordQuiz();
  }

  public boolean hasVotedInWordQuiz(Habbo habbo) {
    return this.wordQuizManager.hasVotedInWordQuiz(habbo);
  }

  public void alert(String message) {
    this.messagingManager.alert(message);
  }

  public int itemCount() {
    return this.itemManager.itemCount();
  }

  public void setJukeBoxActive(boolean jukeBoxActive) {
    this.jukeboxActive = jukeBoxActive;
    this.needsUpdate = true;
  }

  public boolean isHideWired() {
    return this.hideWired;
  }

  public void setHideWired(boolean hideWired) {
    this.hideWired = hideWired;

    if (this.hideWired) {
      for (HabboItem item : this.roomSpecialTypes.getTriggers()) {
        this.sendComposer(new ObjectRemoveMessageComposer(item).compose());
      }

      for (HabboItem item : this.roomSpecialTypes.getEffects()) {
        this.sendComposer(new ObjectRemoveMessageComposer(item).compose());
      }

      for (HabboItem item : this.roomSpecialTypes.getConditions()) {
        this.sendComposer(new ObjectRemoveMessageComposer(item).compose());
      }

      for (HabboItem item : this.roomSpecialTypes.getExtras()) {
        this.sendComposer(new ObjectRemoveMessageComposer(item).compose());
      }
    } else {
      this.sendComposer(new ObjectsMessageComposer(this.itemManager.getFurniOwnerNames(),
          this.roomSpecialTypes.getTriggers()).compose());
      this.sendComposer(new ObjectsMessageComposer(this.itemManager.getFurniOwnerNames(),
          this.roomSpecialTypes.getEffects()).compose());
      this.sendComposer(new ObjectsMessageComposer(this.itemManager.getFurniOwnerNames(),
          this.roomSpecialTypes.getConditions()).compose());
      this.sendComposer(new ObjectsMessageComposer(this.itemManager.getFurniOwnerNames(),
          this.roomSpecialTypes.getExtras()).compose());
    }
  }

  public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile,
      int rotation) {
    return this.itemManager.canPlaceFurnitureAt(item, habbo, tile, rotation);
  }

  public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation) {
    return this.itemManager.furnitureFitsAt(tile, item, rotation);
  }

  public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation,
      boolean checkForUnits) {
    return this.itemManager.furnitureFitsAt(tile, item, rotation, checkForUnits);
  }

  public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation,
      boolean checkForUnits, boolean ignoreFurniStacking) {
    return this.itemManager.furnitureFitsAt(tile, item, rotation, checkForUnits,
        ignoreFurniStacking);
  }

  public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation,
      Habbo owner) {
    return this.itemManager.placeFloorFurniAt(item, tile, rotation, owner);
  }

  public FurnitureMovementError placeTemporaryFloorFurniAt(HabboItem item, RoomTile tile, int rotation,
      double z, String ownerName, boolean checkForUnits, boolean ignoreFurniStacking) {
    return this.itemManager.placeTemporaryFloorFurniAt(item, tile, rotation, z, ownerName,
        checkForUnits, ignoreFurniStacking);
  }

  public void removeTemporaryFloorFurni(HabboItem item) {
    this.itemManager.removeTemporaryFloorFurni(item);
  }

  public int getRoomItemCount() {
    return this.itemManager.getRoomItems().size();
  }

  public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
    return this.itemManager.placeWallFurniAt(item, wallPosition, owner);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
      Habbo actor) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
      Habbo actor, boolean sendUpdates) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
      Habbo actor, boolean sendUpdates, boolean checkForUnits) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
      Habbo actor, boolean sendUpdates, boolean checkForUnits, boolean ignoreFurniStacking) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits,
        ignoreFurniStacking);
  }

  public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
    return this.itemManager.slideFurniTo(item, tile, rotation);
  }

  public THashSet<RoomUnit> getRoomUnits() {
    return this.unitManager.getRoomUnits();
  }

  public THashSet<RoomUnit> getRoomUnits(RoomTile atTile) {
    return this.unitManager.getRoomUnits(atTile);
  }

  public Collection<RoomUnit> getRoomUnitsAt(RoomTile tile) {
    return this.unitManager.getRoomUnitsAt(tile);
  }

  private static final class FurniLoadSnapshot {
    private final int itemId;
    private final short x;
    private final short y;
    private final int rotation;
    private final double z;
    private final String wallPosition;
    private final String extradata;

    private FurniLoadSnapshot(HabboItem item) {
      this.itemId = item.getId();
      this.x = item.getX();
      this.y = item.getY();
      this.rotation = item.getRotation();
      this.z = item.getZ();
      this.wallPosition = item.getWallPosition();
      this.extradata = item.getExtradata();
    }

    private boolean applyTo(HabboItem item) {
      boolean changed = false;
      if (item.getX() != this.x) { item.setX(this.x); changed = true; }
      if (item.getY() != this.y) { item.setY(this.y); changed = true; }
      if (item.getRotation() != this.rotation) {
        item.setRotation(this.rotation); changed = true;
      }
      if (Double.compare(item.getZ(), this.z) != 0) {
        item.setZ(this.z); changed = true;
      }
      if (!String.valueOf(item.getWallPosition()).equals(String.valueOf(this.wallPosition))) {
        item.setWallPosition(this.wallPosition); changed = true;
      }
      if (!String.valueOf(item.getExtradata()).equals(String.valueOf(this.extradata))) {
        item.setExtradata(this.extradata); changed = true;
      }
      return changed;
    }
  }
}
