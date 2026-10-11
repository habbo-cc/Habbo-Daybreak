package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGate;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGate;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboard;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetDrink;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetTree;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredCrossRoomAliasRepository;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages special room item types including wired components, game elements, and pets.
 * This class is thread-safe and uses concurrent collections for wired components
 * to support high-frequency access patterns.
 */
public class RoomSpecialTypes {
    private final THashMap<Integer, InteractionBattleBanzaiTeleporter> banzaiTeleporters;
    private final THashMap<Integer, InteractionNest> nests;
    private final THashMap<Integer, InteractionPetDrink> petDrinks;
    private final THashMap<Integer, InteractionPetFood> petFoods;
    private final THashMap<Integer, InteractionPetToy> petToys;
    private final THashMap<Integer, InteractionPetTree> petTrees;
    private final THashMap<Integer, InteractionRoller> rollers;

    // Thread-safe wired collections using ConcurrentHashMap for better concurrency
    private final ConcurrentHashMap<WiredTriggerType, Set<InteractionWiredTrigger>> wiredTriggers;
    private final ConcurrentHashMap<WiredEffectType, Set<InteractionWiredEffect>> wiredEffects;
    private final ConcurrentHashMap<WiredConditionType, Set<InteractionWiredCondition>> wiredConditions;
    private final ConcurrentHashMap<WiredSelectorType, Set<InteractionWiredSelector>> wiredSelectors;
    private final ConcurrentHashMap<WiredAddonType, Set<InteractionWiredAddon>> wiredAddons;
    private final ConcurrentHashMap<WiredVariableType, Set<InteractionWiredVariable>> wiredVariables;
    private final ConcurrentHashMap<Integer, InteractionWiredAddon> wiredAddonsById;
    private final ConcurrentHashMap<Integer, InteractionWiredVariable> wiredVariablesById;
    private final WiredVariableManager wiredVariableManager;
    private final ConcurrentHashMap<Integer, InteractionWiredExtra> wiredExtras;
    
    // Spatial index for O(1) coordinate-based lookups of wired components
    private final ConcurrentHashMap<Long, Set<InteractionWiredTrigger>> wiredTriggersByLocation;
    private final ConcurrentHashMap<Long, Set<InteractionWiredEffect>> wiredEffectsByLocation;
    private final ConcurrentHashMap<Long, Set<InteractionWiredCondition>> wiredConditionsByLocation;
    private final ConcurrentHashMap<Long, Set<InteractionWiredSelector>> wiredSelectorsByLocation;
    private final ConcurrentHashMap<Long, Set<InteractionWiredAddon>> wiredAddonsByLocation;
    private final ConcurrentHashMap<Long, Set<InteractionWiredVariable>> wiredVariablesByLocation;
    private final ConcurrentHashMap<Long, Set<InteractionWiredExtra>> wiredExtrasByLocation;

    private final THashMap<Integer, InteractionGameScoreboard> gameScoreboards;
    private final THashMap<Integer, InteractionGameGate> gameGates;
    private final THashMap<Integer, InteractionGameTimer> gameTimers;

    private final THashMap<Integer, InteractionFreezeExitTile> freezeExitTile;
    private final THashMap<Integer, HabboItem> undefined;
    private final Set<ICycleable> cycleTasks;

    public RoomSpecialTypes() {
        this(null);
    }

    public RoomSpecialTypes(WiredVariableManager wiredVariableManager) {
        this.wiredVariableManager = wiredVariableManager;
        this.banzaiTeleporters = new THashMap<>(0);
        this.nests = new THashMap<>(0);
        this.petDrinks = new THashMap<>(0);
        this.petFoods = new THashMap<>(0);
        this.petToys = new THashMap<>(0);
        this.petTrees = new THashMap<>(0);
        this.rollers = new THashMap<>(0);

        this.wiredTriggers = new ConcurrentHashMap<>();
        this.wiredEffects = new ConcurrentHashMap<>();
        this.wiredConditions = new ConcurrentHashMap<>();
        this.wiredSelectors = new ConcurrentHashMap<>();
        this.wiredAddons = new ConcurrentHashMap<>();
        this.wiredVariables = new ConcurrentHashMap<>();
        this.wiredAddonsById = new ConcurrentHashMap<>();
        this.wiredVariablesById = new ConcurrentHashMap<>();
        this.wiredExtras = new ConcurrentHashMap<>();
        
        // Initialize spatial indexes
        this.wiredTriggersByLocation = new ConcurrentHashMap<>();
        this.wiredEffectsByLocation = new ConcurrentHashMap<>();
        this.wiredConditionsByLocation = new ConcurrentHashMap<>();
        this.wiredSelectorsByLocation = new ConcurrentHashMap<>();
        this.wiredAddonsByLocation = new ConcurrentHashMap<>();
        this.wiredVariablesByLocation = new ConcurrentHashMap<>();
        this.wiredExtrasByLocation = new ConcurrentHashMap<>();

        this.gameScoreboards = new THashMap<>(0);
        this.gameGates = new THashMap<>(0);
        this.gameTimers = new THashMap<>(0);

        this.freezeExitTile = new THashMap<>(0);
        this.undefined = new THashMap<>(0);
        this.cycleTasks = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Generates a unique key for spatial indexing based on x,y coordinates.
     * Uses bit shifting to combine two shorts into a single long for efficient lookups.
     */
    private static long coordinateKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }


    public InteractionBattleBanzaiTeleporter getBanzaiTeleporter(int itemId) {
        return this.banzaiTeleporters.get(itemId);
    }

    public void addBanzaiTeleporter(InteractionBattleBanzaiTeleporter item) {
        this.banzaiTeleporters.put(item.getId(), item);
    }

    public void removeBanzaiTeleporter(InteractionBattleBanzaiTeleporter item) {
        this.banzaiTeleporters.remove(item.getId());
    }

    public THashSet<InteractionBattleBanzaiTeleporter> getBanzaiTeleporters() {
        synchronized (this.banzaiTeleporters) {
            THashSet<InteractionBattleBanzaiTeleporter> battleBanzaiTeleporters = new THashSet<>();
            battleBanzaiTeleporters.addAll(this.banzaiTeleporters.values());

            return battleBanzaiTeleporters;
        }
    }

    public InteractionBattleBanzaiTeleporter getRandomTeleporter(Item baseItem, InteractionBattleBanzaiTeleporter exclude) {
        List<InteractionBattleBanzaiTeleporter> teleporterList = new ArrayList<>();
        for (InteractionBattleBanzaiTeleporter teleporter : this.banzaiTeleporters.values()) {
            if (baseItem == null || teleporter.getBaseItem() == baseItem) {
                teleporterList.add(teleporter);
            }
        }

        teleporterList.remove(exclude);

        if (!teleporterList.isEmpty()) {
            Collections.shuffle(teleporterList);
            return teleporterList.get(0);
        }

        return null;
    }


    public InteractionNest getNest(int itemId) {
        return this.nests.get(itemId);
    }

    public void addNest(InteractionNest item) {
        this.nests.put(item.getId(), item);
    }

    public void removeNest(InteractionNest item) {
        this.nests.remove(item.getId());
    }

    public THashSet<InteractionNest> getNests() {
        synchronized (this.nests) {
            THashSet<InteractionNest> nests = new THashSet<>();
            nests.addAll(this.nests.values());

            return nests;
        }
    }


    public InteractionPetDrink getPetDrink(int itemId) {
        return this.petDrinks.get(itemId);
    }

    public void addPetDrink(InteractionPetDrink item) {
        this.petDrinks.put(item.getId(), item);
    }

    public void removePetDrink(InteractionPetDrink item) {
        this.petDrinks.remove(item.getId());
    }

    public THashSet<InteractionPetDrink> getPetDrinks() {
        synchronized (this.petDrinks) {
            THashSet<InteractionPetDrink> petDrinks = new THashSet<>();
            petDrinks.addAll(this.petDrinks.values());

            return petDrinks;
        }
    }


    public InteractionPetFood getPetFood(int itemId) {
        return this.petFoods.get(itemId);
    }

    public void addPetFood(InteractionPetFood item) {
        this.petFoods.put(item.getId(), item);
    }

    public void removePetFood(InteractionPetFood petFood) {
        this.petFoods.remove(petFood.getId());
    }

    public THashSet<InteractionPetFood> getPetFoods() {
        synchronized (this.petFoods) {
            THashSet<InteractionPetFood> petFoods = new THashSet<>();
            petFoods.addAll(this.petFoods.values());

            return petFoods;
        }
    }


    public InteractionPetToy getPetToy(int itemId) {
        return this.petToys.get(itemId);
    }

    public void addPetToy(InteractionPetToy item) {
        this.petToys.put(item.getId(), item);
    }

    public void removePetToy(InteractionPetToy petToy) {
        this.petToys.remove(petToy.getId());
    }

    public THashSet<InteractionPetToy> getPetToys() {
        synchronized (this.petToys) {
            THashSet<InteractionPetToy> petToys = new THashSet<>();
            petToys.addAll(this.petToys.values());

            return petToys;
        }
    }


    public InteractionPetTree getPetTree(int itemId) {
        return this.petTrees.get(itemId);
    }

    public void addPetTree(InteractionPetTree item) {
        this.petTrees.put(item.getId(), item);
    }

    public void removePetTree(InteractionPetTree petTree) {
        this.petTrees.remove(petTree.getId());
    }

    public THashSet<InteractionPetTree> getPetTrees() {
        synchronized (this.petTrees) {
            THashSet<InteractionPetTree> petTrees = new THashSet<>();
            petTrees.addAll(this.petTrees.values());

            return petTrees;
        }
    }


    public InteractionRoller getRoller(int itemId) {
        synchronized (this.rollers) {
            return this.rollers.get(itemId);
        }
    }

    public void addRoller(InteractionRoller item) {
        synchronized (this.rollers) {
            this.rollers.put(item.getId(), item);
        }
    }

    public void removeRoller(InteractionRoller roller) {
        synchronized (this.rollers) {
            this.rollers.remove(roller.getId());
        }
    }

    public THashMap<Integer, InteractionRoller> getRollers() {
        return this.rollers;
    }


    /**
     * Finds a wired trigger by its item ID.
     * @param itemId The item ID to search for
     * @return The trigger if found, null otherwise
     */
    public InteractionWiredTrigger getTrigger(int itemId) {
        for (Set<InteractionWiredTrigger> triggers : this.wiredTriggers.values()) {
            for (InteractionWiredTrigger trigger : triggers) {
                if (trigger.getId() == itemId) {
                    return trigger;
                }
            }
        }
        return null;
    }

    /**
     * Gets all wired triggers in the room.
     * @return A new set containing all triggers (safe for iteration)
     */
    public THashSet<InteractionWiredTrigger> getTriggers() {
        THashSet<InteractionWiredTrigger> result = new THashSet<>();
        for (Set<InteractionWiredTrigger> triggers : this.wiredTriggers.values()) {
            result.addAll(triggers);
        }
        return result;
    }

    /**
     * Gets all wired triggers of a specific type.
     * @param type The trigger type to filter by
     * @return A new set containing matching triggers (safe for iteration)
     */
    public THashSet<InteractionWiredTrigger> getTriggers(WiredTriggerType type) {
        Set<InteractionWiredTrigger> triggers = this.wiredTriggers.get(type);
        if (triggers == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(triggers);
    }

    /**
     * Gets all wired triggers at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing triggers at the location (safe for iteration)
     */
    public THashSet<InteractionWiredTrigger> getTriggers(int x, int y) {
        long key = coordinateKey(x, y);
        Set<InteractionWiredTrigger> triggers = this.wiredTriggersByLocation.get(key);
        if (triggers == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(triggers);
    }

    /**
     * Adds a wired trigger to the room.
     * @param trigger The trigger to add
     */
    public void addTrigger(InteractionWiredTrigger trigger) {
        // Add to type-based index
        this.wiredTriggers.computeIfAbsent(trigger.getType(), k -> ConcurrentHashMap.newKeySet())
                .add(trigger);
        
        // Add to spatial index
        long key = coordinateKey(trigger.getX(), trigger.getY());
        this.wiredTriggersByLocation.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(trigger);
    }

    /**
     * Removes a wired trigger from the room.
     * @param trigger The trigger to remove
     */
    public void removeTrigger(InteractionWiredTrigger trigger) {
        // Remove from type-based index
        Set<InteractionWiredTrigger> triggers = this.wiredTriggers.get(trigger.getType());
        if (triggers != null) {
            triggers.remove(trigger);
            if (triggers.isEmpty()) {
                this.wiredTriggers.remove(trigger.getType());
            }
        }
        
        // Remove from spatial index
        long key = coordinateKey(trigger.getX(), trigger.getY());
        Set<InteractionWiredTrigger> locationTriggers = this.wiredTriggersByLocation.get(key);
        if (locationTriggers != null) {
            locationTriggers.remove(trigger);
            if (locationTriggers.isEmpty()) {
                this.wiredTriggersByLocation.remove(key);
            }
        }
    }
    
    /**
     * Updates the spatial index when a trigger is moved.
     * @param trigger The trigger that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateTriggerLocation(InteractionWiredTrigger trigger, int oldX, int oldY) {
        // Remove from old location
        long oldKey = coordinateKey(oldX, oldY);
        Set<InteractionWiredTrigger> oldLocationTriggers = this.wiredTriggersByLocation.get(oldKey);
        if (oldLocationTriggers != null) {
            oldLocationTriggers.remove(trigger);
            if (oldLocationTriggers.isEmpty()) {
                this.wiredTriggersByLocation.remove(oldKey);
            }
        }
        
        // Add to new location
        long newKey = coordinateKey(trigger.getX(), trigger.getY());
        this.wiredTriggersByLocation.computeIfAbsent(newKey, k -> ConcurrentHashMap.newKeySet())
                .add(trigger);
    }


    /**
     * Finds a wired effect by its item ID.
     * @param itemId The item ID to search for
     * @return The effect if found, null otherwise
     */
    public InteractionWiredEffect getEffect(int itemId) {
        for (Set<InteractionWiredEffect> effects : this.wiredEffects.values()) {
            for (InteractionWiredEffect effect : effects) {
                if (effect.getId() == itemId) {
                    return effect;
                }
            }
        }
        return null;
    }

    /**
     * Gets all wired effects in the room.
     * @return A new set containing all effects (safe for iteration)
     */
    public THashSet<InteractionWiredEffect> getEffects() {
        THashSet<InteractionWiredEffect> result = new THashSet<>();
        for (Set<InteractionWiredEffect> effects : this.wiredEffects.values()) {
            result.addAll(effects);
        }
        return result;
    }

    /**
     * Gets all wired effects of a specific type.
     * @param type The effect type to filter by
     * @return A new set containing matching effects (safe for iteration)
     */
    public THashSet<InteractionWiredEffect> getEffects(WiredEffectType type) {
        Set<InteractionWiredEffect> effects = this.wiredEffects.get(type);
        if (effects == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(effects);
    }

    /**
     * Gets all wired effects at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing effects at the location (safe for iteration)
     */
    public THashSet<InteractionWiredEffect> getEffects(int x, int y) {
        long key = coordinateKey(x, y);
        Set<InteractionWiredEffect> effects = this.wiredEffectsByLocation.get(key);
        if (effects == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(effects);
    }

    /**
     * Adds a wired effect to the room.
     * @param effect The effect to add
     */
    public void addEffect(InteractionWiredEffect effect) {
        // Add to type-based index
        this.wiredEffects.computeIfAbsent(effect.getType(), k -> ConcurrentHashMap.newKeySet())
                .add(effect);
        
        // Add to spatial index
        long key = coordinateKey(effect.getX(), effect.getY());
        this.wiredEffectsByLocation.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(effect);
    }

    /**
     * Removes a wired effect from the room.
     * @param effect The effect to remove
     */
    public void removeEffect(InteractionWiredEffect effect) {
        // Remove from type-based index
        Set<InteractionWiredEffect> effects = this.wiredEffects.get(effect.getType());
        if (effects != null) {
            effects.remove(effect);
            if (effects.isEmpty()) {
                this.wiredEffects.remove(effect.getType());
            }
        }
        
        // Remove from spatial index
        long key = coordinateKey(effect.getX(), effect.getY());
        Set<InteractionWiredEffect> locationEffects = this.wiredEffectsByLocation.get(key);
        if (locationEffects != null) {
            locationEffects.remove(effect);
            if (locationEffects.isEmpty()) {
                this.wiredEffectsByLocation.remove(key);
            }
        }
    }
    
    /**
     * Updates the spatial index when an effect is moved.
     * @param effect The effect that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateEffectLocation(InteractionWiredEffect effect, int oldX, int oldY) {
        // Remove from old location
        long oldKey = coordinateKey(oldX, oldY);
        Set<InteractionWiredEffect> oldLocationEffects = this.wiredEffectsByLocation.get(oldKey);
        if (oldLocationEffects != null) {
            oldLocationEffects.remove(effect);
            if (oldLocationEffects.isEmpty()) {
                this.wiredEffectsByLocation.remove(oldKey);
            }
        }
        
        // Add to new location
        long newKey = coordinateKey(effect.getX(), effect.getY());
        this.wiredEffectsByLocation.computeIfAbsent(newKey, k -> ConcurrentHashMap.newKeySet())
                .add(effect);
    }


    /**
     * Finds a wired condition by its item ID.
     * @param itemId The item ID to search for
     * @return The condition if found, null otherwise
     */
    public InteractionWiredCondition getCondition(int itemId) {
        for (Set<InteractionWiredCondition> conditions : this.wiredConditions.values()) {
            for (InteractionWiredCondition condition : conditions) {
                if (condition.getId() == itemId) {
                    return condition;
                }
            }
        }
        return null;
    }

    /**
     * Gets all wired conditions in the room.
     * @return A new set containing all conditions (safe for iteration)
     */
    public THashSet<InteractionWiredCondition> getConditions() {
        THashSet<InteractionWiredCondition> result = new THashSet<>();
        for (Set<InteractionWiredCondition> conditions : this.wiredConditions.values()) {
            result.addAll(conditions);
        }
        return result;
    }

    /**
     * Gets all wired conditions of a specific type.
     * @param type The condition type to filter by
     * @return A new set containing matching conditions (safe for iteration)
     */
    public THashSet<InteractionWiredCondition> getConditions(WiredConditionType type) {
        Set<InteractionWiredCondition> conditions = this.wiredConditions.get(type);
        if (conditions == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(conditions);
    }

    /**
     * Gets all wired conditions at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing conditions at the location (safe for iteration)
     */
    public THashSet<InteractionWiredCondition> getConditions(int x, int y) {
        long key = coordinateKey(x, y);
        Set<InteractionWiredCondition> conditions = this.wiredConditionsByLocation.get(key);
        if (conditions == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(conditions);
    }

    /**
     * Adds a wired condition to the room.
     * @param condition The condition to add
     */
    public void addCondition(InteractionWiredCondition condition) {
        // Add to type-based index
        this.wiredConditions.computeIfAbsent(condition.getType(), k -> ConcurrentHashMap.newKeySet())
                .add(condition);
        
        // Add to spatial index
        long key = coordinateKey(condition.getX(), condition.getY());
        this.wiredConditionsByLocation.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(condition);
    }

    /**
     * Removes a wired condition from the room.
     * @param condition The condition to remove
     */
    public void removeCondition(InteractionWiredCondition condition) {
        // Remove from type-based index
        Set<InteractionWiredCondition> conditions = this.wiredConditions.get(condition.getType());
        if (conditions != null) {
            conditions.remove(condition);
            if (conditions.isEmpty()) {
                this.wiredConditions.remove(condition.getType());
            }
        }
        
        // Remove from spatial index
        long key = coordinateKey(condition.getX(), condition.getY());
        Set<InteractionWiredCondition> locationConditions = this.wiredConditionsByLocation.get(key);
        if (locationConditions != null) {
            locationConditions.remove(condition);
            if (locationConditions.isEmpty()) {
                this.wiredConditionsByLocation.remove(key);
            }
        }
    }
    
    /**
     * Updates the spatial index when a condition is moved.
     * @param condition The condition that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateConditionLocation(InteractionWiredCondition condition, int oldX, int oldY) {
        // Remove from old location
        long oldKey = coordinateKey(oldX, oldY);
        Set<InteractionWiredCondition> oldLocationConditions = this.wiredConditionsByLocation.get(oldKey);
        if (oldLocationConditions != null) {
            oldLocationConditions.remove(condition);
            if (oldLocationConditions.isEmpty()) {
                this.wiredConditionsByLocation.remove(oldKey);
            }
        }
        
        // Add to new location
        long newKey = coordinateKey(condition.getX(), condition.getY());
        this.wiredConditionsByLocation.computeIfAbsent(newKey, k -> ConcurrentHashMap.newKeySet())
                .add(condition);
    }


    public InteractionWiredSelector getSelector(int itemId) {
        for (Set<InteractionWiredSelector> selectors : this.wiredSelectors.values()) {
            for (InteractionWiredSelector selector : selectors) {
                if (selector.getId() == itemId) {
                    return selector;
                }
            }
        }
        return null;
    }

    public THashSet<InteractionWiredSelector> getSelectors() {
        THashSet<InteractionWiredSelector> result = new THashSet<>();
        for (Set<InteractionWiredSelector> selectors : this.wiredSelectors.values()) {
            result.addAll(selectors);
        }
        return result;
    }

    public THashSet<InteractionWiredSelector> getSelectors(WiredSelectorType type) {
        Set<InteractionWiredSelector> selectors = this.wiredSelectors.get(type);
        if (selectors == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(selectors);
    }

    public THashSet<InteractionWiredSelector> getSelectors(int x, int y) {
        long key = coordinateKey(x, y);
        Set<InteractionWiredSelector> selectors = this.wiredSelectorsByLocation.get(key);
        if (selectors == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(selectors);
    }

    public void addSelector(InteractionWiredSelector selector) {
        this.wiredSelectors.computeIfAbsent(selector.getType(), k -> ConcurrentHashMap.newKeySet())
                .add(selector);
        long key = coordinateKey(selector.getX(), selector.getY());
        this.wiredSelectorsByLocation.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(selector);
    }

    public void removeSelector(InteractionWiredSelector selector) {
        Set<InteractionWiredSelector> selectors = this.wiredSelectors.get(selector.getType());
        if (selectors != null) {
            selectors.remove(selector);
            if (selectors.isEmpty()) {
                this.wiredSelectors.remove(selector.getType());
            }
        }

        long key = coordinateKey(selector.getX(), selector.getY());
        Set<InteractionWiredSelector> locationSelectors = this.wiredSelectorsByLocation.get(key);
        if (locationSelectors != null) {
            locationSelectors.remove(selector);
            if (locationSelectors.isEmpty()) {
                this.wiredSelectorsByLocation.remove(key);
            }
        }
    }

    public void updateSelectorLocation(InteractionWiredSelector selector, int oldX, int oldY) {
        long oldKey = coordinateKey(oldX, oldY);
        Set<InteractionWiredSelector> oldLocationSelectors = this.wiredSelectorsByLocation.get(oldKey);
        if (oldLocationSelectors != null) {
            oldLocationSelectors.remove(selector);
            if (oldLocationSelectors.isEmpty()) {
                this.wiredSelectorsByLocation.remove(oldKey);
            }
        }

        long newKey = coordinateKey(selector.getX(), selector.getY());
        this.wiredSelectorsByLocation.computeIfAbsent(newKey, k -> ConcurrentHashMap.newKeySet())
                .add(selector);
    }

    public InteractionWiredAddon getAddon(int itemId) {
        return this.wiredAddonsById.get(itemId);
    }

    public THashSet<InteractionWiredAddon> getAddons() {
        return new THashSet<>(this.wiredAddonsById.values());
    }

    public THashSet<InteractionWiredAddon> getAddons(WiredAddonType type) {
        Set<InteractionWiredAddon> addons = this.wiredAddons.get(type);
        return addons == null ? new THashSet<>(0) : new THashSet<>(addons);
    }

    public THashSet<InteractionWiredAddon> getAddons(int x, int y) {
        Set<InteractionWiredAddon> addons = this.wiredAddonsByLocation.get(coordinateKey(x, y));
        return addons == null ? new THashSet<>(0) : new THashSet<>(addons);
    }

    public void addAddon(InteractionWiredAddon addon) {
        InteractionWiredAddon previous = this.wiredAddonsById.put(addon.getId(), addon);
        if (previous != null) {
            removeAddonIndexes(previous);
            // Re-registering the same mutable object after its coordinates
            // changed cannot recover the old key from the object itself.
            removeFromAllLocations(this.wiredAddonsByLocation, previous);
        }
        if (this.wiredAddonsById.get(addon.getId()) != addon) {
            return;
        }
        this.wiredAddons.computeIfAbsent(addon.getType(), key -> ConcurrentHashMap.newKeySet()).add(addon);
        this.wiredAddonsByLocation.computeIfAbsent(
                coordinateKey(addon.getX(), addon.getY()), key -> ConcurrentHashMap.newKeySet()).add(addon);
        if (this.wiredAddonsById.get(addon.getId()) != addon) {
            removeAddonIndexes(addon);
        }
    }

    public void removeAddon(InteractionWiredAddon addon) {
        this.wiredAddonsById.remove(addon.getId(), addon);
        removeAddonIndexes(addon);
    }

    private void removeAddonIndexes(InteractionWiredAddon addon) {
        Set<InteractionWiredAddon> byType = this.wiredAddons.get(addon.getType());
        if (byType != null) {
            byType.remove(addon);
            if (byType.isEmpty()) {
                this.wiredAddons.remove(addon.getType(), byType);
            }
        }
        removeFromLocation(this.wiredAddonsByLocation, addon, addon.getX(), addon.getY());
    }

    public void updateAddonLocation(InteractionWiredAddon addon, int oldX, int oldY) {
        if (this.wiredAddonsById.get(addon.getId()) != addon) {
            return;
        }
        removeFromLocation(this.wiredAddonsByLocation, addon, oldX, oldY);
        if (this.wiredAddonsById.get(addon.getId()) != addon) {
            return;
        }
        this.wiredAddonsByLocation.computeIfAbsent(
                coordinateKey(addon.getX(), addon.getY()), key -> ConcurrentHashMap.newKeySet()).add(addon);
        if (this.wiredAddonsById.get(addon.getId()) != addon) {
            removeFromLocation(this.wiredAddonsByLocation, addon, addon.getX(), addon.getY());
        }
    }

    public InteractionWiredVariable getVariable(int itemId) {
        return this.wiredVariablesById.get(itemId);
    }

    public WiredVariableManager getWiredVariableManager() {
        return this.wiredVariableManager;
    }

    public THashSet<InteractionWiredVariable> getVariables() {
        return new THashSet<>(this.wiredVariablesById.values());
    }

    public THashSet<InteractionWiredVariable> getVariables(WiredVariableType type) {
        Set<InteractionWiredVariable> variables = this.wiredVariables.get(type);
        return variables == null ? new THashSet<>(0) : new THashSet<>(variables);
    }

    public THashSet<InteractionWiredVariable> getVariables(int x, int y) {
        Set<InteractionWiredVariable> variables = this.wiredVariablesByLocation.get(coordinateKey(x, y));
        return variables == null ? new THashSet<>(0) : new THashSet<>(variables);
    }

    public void addVariable(InteractionWiredVariable variable) {
        InteractionWiredVariable previous = this.wiredVariablesById.put(variable.getId(), variable);
        if (previous != null) {
            removeVariableIndexes(previous);
            removeFromAllLocations(this.wiredVariablesByLocation, previous);
        }
        if (this.wiredVariablesById.get(variable.getId()) != variable) {
            return;
        }
        this.wiredVariables.computeIfAbsent(variable.getType(), key -> ConcurrentHashMap.newKeySet()).add(variable);
        this.wiredVariablesByLocation.computeIfAbsent(
                coordinateKey(variable.getX(), variable.getY()), key -> ConcurrentHashMap.newKeySet()).add(variable);
        if (this.wiredVariablesById.get(variable.getId()) != variable) {
            removeVariableIndexes(variable);
            return;
        }
        refreshVariableDefinition(variable);
    }

    public void removeVariable(InteractionWiredVariable variable) {
        boolean removed = this.wiredVariablesById.remove(variable.getId(), variable);
        removeVariableIndexes(variable);
        if (removed && this.wiredVariableManager != null) {
            this.wiredVariableManager.removeDefinition("room:" + variable.getId());
        }
        if (removed && Emulator.getDatabase() != null) {
            WiredCrossRoomAliasRepository.instance().delete(variable.getId());
        }
    }

    /** Refreshes the room manager after an editor save without coupling packet code to storage. */
    public void refreshVariableDefinition(InteractionWiredVariable variable) {
        if (this.wiredVariableManager == null || variable == null
                || this.wiredVariablesById.get(variable.getId()) != variable) {
            return;
        }
        if (variable.bindManager(this.wiredVariableManager)) {
            WiredVariableDefinition definition =
                    this.wiredVariableManager.definition("room:" + variable.getId());
            if (definition != null) {
                variable.syncDefinitionRegistry(definition);
            }
        }
    }

    private void removeVariableIndexes(InteractionWiredVariable variable) {
        Set<InteractionWiredVariable> byType = this.wiredVariables.get(variable.getType());
        if (byType != null) {
            byType.remove(variable);
            if (byType.isEmpty()) {
                this.wiredVariables.remove(variable.getType(), byType);
            }
        }
        removeFromLocation(this.wiredVariablesByLocation, variable, variable.getX(), variable.getY());
    }

    public void updateVariableLocation(InteractionWiredVariable variable, int oldX, int oldY) {
        if (this.wiredVariablesById.get(variable.getId()) != variable) {
            return;
        }
        removeFromLocation(this.wiredVariablesByLocation, variable, oldX, oldY);
        if (this.wiredVariablesById.get(variable.getId()) != variable) {
            return;
        }
        this.wiredVariablesByLocation.computeIfAbsent(
                coordinateKey(variable.getX(), variable.getY()), key -> ConcurrentHashMap.newKeySet()).add(variable);
        if (this.wiredVariablesById.get(variable.getId()) != variable) {
            removeFromLocation(this.wiredVariablesByLocation, variable, variable.getX(), variable.getY());
        }
    }

    private static <T> void removeFromLocation(
            ConcurrentHashMap<Long, Set<T>> index,
            T value,
            int x,
            int y) {
        long key = coordinateKey(x, y);
        Set<T> values = index.get(key);
        if (values != null) {
            values.remove(value);
            if (values.isEmpty()) {
                index.remove(key, values);
            }
        }
    }

    private static <T> void removeFromAllLocations(
            ConcurrentHashMap<Long, Set<T>> index,
            T value) {
        for (Map.Entry<Long, Set<T>> entry : index.entrySet()) {
            Set<T> values = entry.getValue();
            values.remove(value);
            if (values.isEmpty()) {
                index.remove(entry.getKey(), values);
            }
        }
    }


    /**
     * Gets all wired extras in the room.
     * @return A new set containing all extras (safe for iteration)
     */
    public THashSet<InteractionWiredExtra> getExtras() {
        THashSet<InteractionWiredExtra> result = new THashSet<>();
        result.addAll(this.wiredExtras.values());
        return result;
    }

    /**
     * Gets all wired extras at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing extras at the location (safe for iteration)
     */
    public THashSet<InteractionWiredExtra> getExtras(int x, int y) {
        long key = coordinateKey(x, y);
        Set<InteractionWiredExtra> extras = this.wiredExtrasByLocation.get(key);
        if (extras == null) {
            return new THashSet<>(0);
        }
        return new THashSet<>(extras);
    }

    /**
     * Adds a wired extra to the room.
     * @param extra The extra to add
     */
    public void addExtra(InteractionWiredExtra extra) {
        this.wiredExtras.put(extra.getId(), extra);
        
        // Add to spatial index
        long key = coordinateKey(extra.getX(), extra.getY());
        this.wiredExtrasByLocation.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(extra);
    }

    /**
     * Removes a wired extra from the room.
     * @param extra The extra to remove
     */
    public void removeExtra(InteractionWiredExtra extra) {
        this.wiredExtras.remove(extra.getId());
        
        // Remove from spatial index
        long key = coordinateKey(extra.getX(), extra.getY());
        Set<InteractionWiredExtra> locationExtras = this.wiredExtrasByLocation.get(key);
        if (locationExtras != null) {
            locationExtras.remove(extra);
            if (locationExtras.isEmpty()) {
                this.wiredExtrasByLocation.remove(key);
            }
        }
    }
    
    /**
     * Updates the spatial index when an extra is moved.
     * @param extra The extra that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateExtraLocation(InteractionWiredExtra extra, int oldX, int oldY) {
        // Remove from old location
        long oldKey = coordinateKey(oldX, oldY);
        Set<InteractionWiredExtra> oldLocationExtras = this.wiredExtrasByLocation.get(oldKey);
        if (oldLocationExtras != null) {
            oldLocationExtras.remove(extra);
            if (oldLocationExtras.isEmpty()) {
                this.wiredExtrasByLocation.remove(oldKey);
            }
        }
        
        // Add to new location
        long newKey = coordinateKey(extra.getX(), extra.getY());
        this.wiredExtrasByLocation.computeIfAbsent(newKey, k -> ConcurrentHashMap.newKeySet())
                .add(extra);
    }

    /**
     * Checks if there's a wired extra of a specific type at given coordinates.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param type The extra type to check for
     * @return true if an extra of the given type exists at the location
     */
    public boolean hasExtraType(short x, short y, Class<? extends InteractionWiredExtra> type) {
        long key = coordinateKey(x, y);
        Set<InteractionWiredExtra> extras = this.wiredExtrasByLocation.get(key);
        if (extras == null) {
            return false;
        }
        for (InteractionWiredExtra extra : extras) {
            if (type.isAssignableFrom(extra.getClass())) {
                return true;
            }
        }
        return false;
    }


    public InteractionGameScoreboard getGameScorebord(int itemId) {
        return this.gameScoreboards.get(itemId);
    }

    public void addGameScoreboard(InteractionGameScoreboard scoreboard) {
        this.gameScoreboards.put(scoreboard.getId(), scoreboard);
    }

    public void removeScoreboard(InteractionGameScoreboard scoreboard) {
        this.gameScoreboards.remove(scoreboard.getId());
    }

    public THashMap<Integer, InteractionFreezeScoreboard> getFreezeScoreboards() {
        synchronized (this.gameScoreboards) {
            THashMap<Integer, InteractionFreezeScoreboard> boards = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFreezeScoreboard) {
                    boards.put(set.getValue().getId(), (InteractionFreezeScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public THashMap<Integer, InteractionFreezeScoreboard> getFreezeScoreboards(GameTeamColors teamColor) {
        synchronized (this.gameScoreboards) {
            THashMap<Integer, InteractionFreezeScoreboard> boards = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFreezeScoreboard) {
                    if (((InteractionFreezeScoreboard) set.getValue()).teamColor.equals(teamColor))
                        boards.put(set.getValue().getId(), (InteractionFreezeScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public THashMap<Integer, InteractionBattleBanzaiScoreboard> getBattleBanzaiScoreboards() {
        synchronized (this.gameScoreboards) {
            THashMap<Integer, InteractionBattleBanzaiScoreboard> boards = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionBattleBanzaiScoreboard) {
                    boards.put(set.getValue().getId(), (InteractionBattleBanzaiScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public THashMap<Integer, InteractionBattleBanzaiScoreboard> getBattleBanzaiScoreboards(GameTeamColors teamColor) {
        synchronized (this.gameScoreboards) {
            THashMap<Integer, InteractionBattleBanzaiScoreboard> boards = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionBattleBanzaiScoreboard) {
                    if (((InteractionBattleBanzaiScoreboard) set.getValue()).teamColor.equals(teamColor))
                        boards.put(set.getValue().getId(), (InteractionBattleBanzaiScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public THashMap<Integer, InteractionFootballScoreboard> getFootballScoreboards() {
        synchronized (this.gameScoreboards) {
            THashMap<Integer, InteractionFootballScoreboard> boards = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFootballScoreboard) {
                    boards.put(set.getValue().getId(), (InteractionFootballScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public THashMap<Integer, InteractionFootballScoreboard> getFootballScoreboards(GameTeamColors teamColor) {
        synchronized (this.gameScoreboards) {
            THashMap<Integer, InteractionFootballScoreboard> boards = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFootballScoreboard) {
                    if (((InteractionFootballScoreboard) set.getValue()).teamColor.equals(teamColor))
                        boards.put(set.getValue().getId(), (InteractionFootballScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }


    public InteractionGameGate getGameGate(int itemId) {
        return this.gameGates.get(itemId);
    }

    public void addGameGate(InteractionGameGate gameGate) {
        this.gameGates.put(gameGate.getId(), gameGate);
    }

    public void removeGameGate(InteractionGameGate gameGate) {
        this.gameGates.remove(gameGate.getId());
    }

    public THashMap<Integer, InteractionFreezeGate> getFreezeGates() {
        synchronized (this.gameGates) {
            THashMap<Integer, InteractionFreezeGate> gates = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameGate> set : this.gameGates.entrySet()) {
                if (set.getValue() instanceof InteractionFreezeGate) {
                    gates.put(set.getValue().getId(), (InteractionFreezeGate) set.getValue());
                }
            }

            return gates;
        }
    }

    public THashMap<Integer, InteractionBattleBanzaiGate> getBattleBanzaiGates() {
        synchronized (this.gameGates) {
            THashMap<Integer, InteractionBattleBanzaiGate> gates = new THashMap<>();

            for (Map.Entry<Integer, InteractionGameGate> set : this.gameGates.entrySet()) {
                if (set.getValue() instanceof InteractionBattleBanzaiGate) {
                    gates.put(set.getValue().getId(), (InteractionBattleBanzaiGate) set.getValue());
                }
            }

            return gates;
        }
    }


    public InteractionGameTimer getGameTimer(int itemId) {
        return this.gameTimers.get(itemId);
    }

    public void addGameTimer(InteractionGameTimer gameTimer) {
        this.gameTimers.put(gameTimer.getId(), gameTimer);
    }

    public void removeGameTimer(InteractionGameTimer gameTimer) {
        this.gameTimers.remove(gameTimer.getId());
    }

    public THashMap<Integer, InteractionGameTimer> getGameTimers() {
        return this.gameTimers;
    }

    public InteractionFreezeExitTile getFreezeExitTile() {
        return this.freezeExitTile.values().stream().findFirst().orElse(null);
    }

    public InteractionFreezeExitTile getRandomFreezeExitTile() {
        synchronized (this.freezeExitTile) {
            return (InteractionFreezeExitTile) this.freezeExitTile.values().toArray()[Emulator.getRandom().nextInt(this.freezeExitTile.size())];
        }
    }

    public void addFreezeExitTile(InteractionFreezeExitTile freezeExitTile) {
        this.freezeExitTile.put(freezeExitTile.getId(), freezeExitTile);
    }

    public THashMap<Integer, InteractionFreezeExitTile> getFreezeExitTiles() {
        return this.freezeExitTile;
    }

    public void removeFreezeExitTile(InteractionFreezeExitTile freezeExitTile) {
        this.freezeExitTile.remove(freezeExitTile.getId());
    }

    public boolean hasFreezeExitTile() {
        return !this.freezeExitTile.isEmpty();
    }

    public void addUndefined(HabboItem item) {
        synchronized (this.undefined) {
            this.undefined.put(item.getId(), item);
        }
    }

    public void removeUndefined(HabboItem item) {
        synchronized (this.undefined) {
            this.undefined.remove(item.getId());
        }
    }

    public THashSet<HabboItem> getItemsOfType(Class<? extends HabboItem> type) {
        THashSet<HabboItem> items = new THashSet<>();
        
        // Check pet trees collection for InteractionPetTree type
        if (type == InteractionPetTree.class) {
            synchronized (this.petTrees) {
                items.addAll(this.petTrees.values());
            }
            return items;
        }
        
        // Check pet toys collection for InteractionPetToy type
        if (type == InteractionPetToy.class) {
            synchronized (this.petToys) {
                items.addAll(this.petToys.values());
            }
            return items;
        }
        
        synchronized (this.undefined) {
            for (HabboItem item : this.undefined.values()) {
                if (item.getClass() == type)
                    items.add(item);
            }
        }

        return items;
    }

    public HabboItem getLowestItemsOfType(Class<? extends HabboItem> type) {
        HabboItem i = null;
        synchronized (this.undefined) {
            for (HabboItem item : this.undefined.values()) {
                if (i == null || item.getZ() < i.getZ()) {
                    if (item.getClass().isAssignableFrom(type)) {
                        i = item;
                    }
                }
            }
        }

        return i;
    }

    /**
     * Gets the set of cycle tasks.
     * @return The set of cycle tasks (thread-safe)
     */
    public Set<ICycleable> getCycleTasks() {
        return this.cycleTasks;
    }

    public void addCycleTask(ICycleable task) {
        this.cycleTasks.add(task);
    }

    public void removeCycleTask(ICycleable task) {
        this.cycleTasks.remove(task);
    }

    public synchronized void dispose() {
        if (this.wiredVariableManager != null) {
            this.wiredVariableManager.close();
        }
        this.banzaiTeleporters.clear();
        this.nests.clear();
        this.petDrinks.clear();
        this.petFoods.clear();
        this.petToys.clear();
        this.petTrees.clear();
        this.rollers.clear();

        this.wiredTriggers.clear();
        this.wiredEffects.clear();
        this.wiredConditions.clear();
        this.wiredSelectors.clear();
        this.wiredSelectorsByLocation.clear();
        this.wiredAddons.clear();
        this.wiredVariables.clear();
        this.wiredAddonsById.clear();
        this.wiredVariablesById.clear();
        this.wiredAddonsByLocation.clear();
        this.wiredVariablesByLocation.clear();

        this.gameScoreboards.clear();
        this.gameGates.clear();
        this.gameTimers.clear();

        this.freezeExitTile.clear();
        this.undefined.clear();
        this.cycleTasks.clear();
    }

    public Rectangle tentAt(RoomTile location) {
        for (HabboItem item : this.getItemsOfType(InteractionTent.class)) {
            Rectangle rectangle = RoomLayout.getRectangle(item.getX(), item.getY(), item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
            if (RoomLayout.tileInSquare(rectangle, location)) {
                return rectangle;
            }
        }

        return null;
    }
}
