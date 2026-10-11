package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.*;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateBlue;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateGreen;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateRed;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateYellow;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardBlue;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardGreen;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardRed;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardYellow;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootball;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalBlue;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalGreen;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalRed;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalYellow;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardBlue;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardGreen;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardRed;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardYellow;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeBlock;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateBlue;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateGreen;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateRed;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateYellow;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardBlue;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardGreen;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardRed;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardYellow;
import com.eu.habbo.habbohotel.items.interactions.games.tag.bunnyrun.InteractionBunnyrunField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.bunnyrun.InteractionBunnyrunPole;
import com.eu.habbo.habbohotel.items.interactions.games.tag.icetag.InteractionIceTagField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.icetag.InteractionIceTagPole;
import com.eu.habbo.habbohotel.items.interactions.games.tag.rollerskate.InteractionRollerskateField;
import com.eu.habbo.habbohotel.items.interactions.pets.*;
import com.eu.habbo.habbohotel.items.interactions.totems.InteractionTotemHead;
import com.eu.habbo.habbohotel.items.interactions.totems.InteractionTotemLegs;
import com.eu.habbo.habbohotel.items.interactions.totems.InteractionTotemPlanet;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonExecuteInOrder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonConditionEvaluation;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonRandomEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUnseenEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonNoMoveAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonMovementPhysics;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCarryUsers;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniSelectorFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUserSelectorFilter;
import java.util.concurrent.atomic.AtomicInteger;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniVariableFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUserVariableFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUsernamePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniNamePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableLevelUp;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariablePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableCapturer;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableTextConverter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableTimeUtility;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonGlobalPlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonAchievementEnabler;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonProjectile;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonJumpStrength;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonChestItemTypeScanner;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.*;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.*;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseen;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.*;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableGlobal;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableQuest;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableQuestChain;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableUser;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreManager;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import com.eu.habbo.messages.outgoing.inventory.UnseenItemsMessageComposer;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadItemsManagerEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import java.sql.Statement;
import gnu.trove.TCollections;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;

public class ItemManager {
    private static final AtomicInteger TEMPORARY_ITEM_ID = new AtomicInteger(-1);

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemManager.class);

    // Configuration. Loaded from database & updated accordingly.
    public static boolean RECYCLER_ENABLED = true;

    private final TIntObjectMap<Item> items;
    private final TIntObjectHashMap<CrackableReward> crackableRewards;
    private final THashSet<ItemInteraction> interactionsList;
    private final THashMap<String, SoundTrack> soundTracks;
    private final YoutubeManager youtubeManager;
    private final WiredHighscoreManager highscoreManager;
    private final TreeMap<Integer, NewUserGift> newuserGifts;

    public ItemManager() {
        this.items = TCollections.synchronizedMap(new TIntObjectHashMap<>());
        this.crackableRewards = new TIntObjectHashMap<>();
        this.interactionsList = new THashSet<>();
        this.soundTracks = new THashMap<>();
        this.youtubeManager = new YoutubeManager();
        this.highscoreManager = new WiredHighscoreManager();
        this.newuserGifts = new TreeMap<>();
    }

    public void load() {
        Emulator.getPluginManager().fireEvent(new EmulatorLoadItemsManagerEvent());

        long millis = System.currentTimeMillis();

        this.loadItemInteractions();
        this.loadItems();
        this.loadCrackable();
        this.loadSoundTracks();
        this.youtubeManager.load();
        this.highscoreManager.load();
        this.loadNewUserGifts();

        LOGGER.info("Item Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    protected void loadItemInteractions() {
        this.interactionsList.add(new ItemInteraction("default", InteractionDefault.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni", InteractionFurniChest.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_coins", InteractionCoinChest.class));
        this.interactionsList.add(new ItemInteraction(InteractionChestContract.PAYMENT, InteractionChestContract.class));
        this.interactionsList.add(new ItemInteraction(InteractionChestContract.TRADE, InteractionChestContract.class));
        this.interactionsList.add(new ItemInteraction(InteractionChestContract.REWARD, InteractionChestContract.class));
        this.interactionsList.add(new ItemInteraction("room_invisible_tile", InteractionInvisibleFurni.class));
        this.interactionsList.add(new ItemInteraction("conf_invis_control", InteractionInvisControl.class));
        this.interactionsList.add(new ItemInteraction("conf_handitem_block", InteractionHanditemBlock.class));
        this.interactionsList.add(new ItemInteraction("area_hide", InteractionAreaHide.class));
        this.interactionsList.add(new ItemInteraction("gate", InteractionGate.class));
        this.interactionsList.add(new ItemInteraction("guild_furni", InteractionGuildFurni.class));
        this.interactionsList.add(new ItemInteraction("guild_gate", InteractionGuildGate.class));
        this.interactionsList.add(new ItemInteraction("background_toner", InteractionBackgroundToner.class));
        this.interactionsList.add(new ItemInteraction("badge_display", InteractionBadgeDisplay.class));
        this.interactionsList.add(new ItemInteraction("mannequin", InteractionMannequin.class));
        this.interactionsList.add(new ItemInteraction("ads_bg", InteractionRoomAds.class));
        this.interactionsList.add(new ItemInteraction("trophy", InteractionTrophy.class));
        this.interactionsList.add(new ItemInteraction("vendingmachine", InteractionVendingMachine.class));
        this.interactionsList.add(new ItemInteraction("pressureplate", InteractionPressurePlate.class));
        this.interactionsList.add(new ItemInteraction("colorplate", InteractionColorPlate.class));
        this.interactionsList.add(new ItemInteraction("multiheight", InteractionMultiHeight.class));
        this.interactionsList.add(new ItemInteraction("dice", InteractionDice.class));
        this.interactionsList.add(new ItemInteraction("colorwheel", InteractionColorWheel.class));
        this.interactionsList.add(new ItemInteraction("cannon", InteractionCannon.class));
        this.interactionsList.add(new ItemInteraction("teleport", InteractionTeleport.class));
        this.interactionsList.add(new ItemInteraction("teleporttile", InteractionTeleportTile.class));
        this.interactionsList.add(new ItemInteraction("wf_room_linker", InteractionRoomLinker.class));
        this.interactionsList.add(new ItemInteraction("crackable", InteractionCrackable.class));
        this.interactionsList.add(new ItemInteraction("crackable_master", InteractionCrackableMaster.class));
        this.interactionsList.add(new ItemInteraction("nest", InteractionNest.class));
        this.interactionsList.add(new ItemInteraction("pet_drink", InteractionPetDrink.class));
        this.interactionsList.add(new ItemInteraction("pet_food", InteractionPetFood.class));
        this.interactionsList.add(new ItemInteraction("pet_toy", InteractionPetToy.class));
        this.interactionsList.add(new ItemInteraction("pet_tree", InteractionPetTree.class));
        this.interactionsList.add(new ItemInteraction("pet_trampoline", InteractionPetTrampoline.class));
        this.interactionsList.add(new ItemInteraction("breeding_nest", InteractionPetBreedingNest.class));
        this.interactionsList.add(new ItemInteraction("obstacle", InteractionObstacle.class));
        this.interactionsList.add(new ItemInteraction("monsterplant_seed", InteractionMonsterPlantSeed.class));
        this.interactionsList.add(new ItemInteraction("gift", InteractionGift.class));
        this.interactionsList.add(new ItemInteraction("stack_helper", InteractionStackHelper.class));
        this.interactionsList.add(new ItemInteraction("puzzle_box", InteractionPuzzleBox.class));
        this.interactionsList.add(new ItemInteraction("hopper", InteractionHopper.class));
        this.interactionsList.add(new ItemInteraction("costume_hopper", InteractionCostumeHopper.class));
        this.interactionsList.add(new ItemInteraction("effect_gate", InteractionEffectGate.class));
        this.interactionsList.add(new ItemInteraction("club_hopper", InteractionHabboClubHopper.class));
        this.interactionsList.add(new ItemInteraction("club_gate", InteractionHabboClubGate.class));
        this.interactionsList.add(new ItemInteraction("club_teleporttile", InteractionHabboClubTeleportTile.class));
        this.interactionsList.add(new ItemInteraction("onewaygate", InteractionOneWayGate.class));
        this.interactionsList.add(new ItemInteraction("love_lock", InteractionLoveLock.class));
        this.interactionsList.add(new ItemInteraction("clothing", InteractionClothing.class));
        this.interactionsList.add(new ItemInteraction("roller", InteractionRoller.class));
        this.interactionsList.add(new ItemInteraction("postit", InteractionPostIt.class));
        this.interactionsList.add(new ItemInteraction("dimmer", InteractionMoodLight.class));
        this.interactionsList.add(new ItemInteraction("rentable_space", InteractionRentableSpace.class));
        this.interactionsList.add(new ItemInteraction("pyramid", InteractionPyramid.class));
        this.interactionsList.add(new ItemInteraction("musicdisc", InteractionMusicDisc.class));
        this.interactionsList.add(new ItemInteraction("fireworks", InteractionFireworks.class));
        this.interactionsList.add(new ItemInteraction("talking_furni", InteractionTalkingFurniture.class));
        this.interactionsList.add(new ItemInteraction("water_item", InteractionWaterItem.class));
        this.interactionsList.add(new ItemInteraction("water", InteractionWater.class));
        this.interactionsList.add(new ItemInteraction("viking_cotie", InteractionVikingCotie.class));
        this.interactionsList.add(new ItemInteraction("tile_fxprovider_nfs", InteractionTileEffectProvider.class));
        this.interactionsList.add(new ItemInteraction("mutearea", InteractionMuteArea.class));
        this.interactionsList.add(new ItemInteraction("buildarea", InteractionBuildArea.class));
        this.interactionsList.add(new ItemInteraction("information_terminal", InteractionInformationTerminal.class));
        this.interactionsList.add(new ItemInteraction("external_image", InteractionExternalImage.class));
        this.interactionsList.add(new ItemInteraction("youtube", InteractionYoutubeTV.class));
        this.interactionsList.add(new ItemInteraction("jukebox", InteractionJukeBox.class));
        this.interactionsList.add(new ItemInteraction("switch", InteractionSwitch.class));
        this.interactionsList.add(new ItemInteraction("switch_remote_control", InteractionSwitchRemoteControl.class));
        this.interactionsList.add(new ItemInteraction("fx_box", InteractionFXBox.class));
        this.interactionsList.add(new ItemInteraction("blackhole", InteractionBlackHole.class));
        this.interactionsList.add(new ItemInteraction("effect_toggle", InteractionEffectToggle.class));
        this.interactionsList.add(new ItemInteraction("room_o_matic", InteractionRoomOMatic.class));
        this.interactionsList.add(new ItemInteraction("effect_tile", InteractionEffectTile.class));
        this.interactionsList.add(new ItemInteraction("sticky_pole", InteractionStickyPole.class));
        this.interactionsList.add(new ItemInteraction("trap", InteractionTrap.class));
        this.interactionsList.add(new ItemInteraction("tent", InteractionTent.class));
        this.interactionsList.add(new ItemInteraction("gym_equipment", InteractionGymEquipment.class));
        this.interactionsList.add(new ItemInteraction("handitem", InteractionHanditem.class));
        this.interactionsList.add(new ItemInteraction("handitem_tile", InteractionHanditemTile.class));
        this.interactionsList.add(new ItemInteraction("effect_giver", InteractionEffectGiver.class));
        this.interactionsList.add(new ItemInteraction("effect_vendingmachine", InteractionEffectVendingMachine.class));
        this.interactionsList.add(
                new ItemInteraction("effect_vendingmachine_no_sides", InteractionEffectVendingMachineNoSides.class));
        this.interactionsList.add(new ItemInteraction("crackable_monster", InteractionMonsterCrackable.class));
        this.interactionsList.add(new ItemInteraction("snowboard_slope", InteractionSnowboardSlope.class));
        this.interactionsList.add(new ItemInteraction("pressureplate_group", InteractionGroupPressurePlate.class));
        this.interactionsList.add(new ItemInteraction("effect_tile_group", InteractionEffectTile.class));
        this.interactionsList
                .add(new ItemInteraction("crackable_subscription_box", InteractionRedeemableSubscriptionBox.class));
        this.interactionsList.add(new ItemInteraction("random_state", InteractionRandomState.class));
        this.interactionsList
                .add(new ItemInteraction("vendingmachine_no_sides", InteractionNoSidesVendingMachine.class));
        this.interactionsList.add(new ItemInteraction("tile_walkmagic", InteractionTileWalkMagic.class));

        this.interactionsList.add(new ItemInteraction("game_timer", InteractionGameTimer.class));

        this.interactionsList.add(new ItemInteraction("wf_trg_walks_on_furni", WiredTriggerHabboWalkOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_walks_off_furni", WiredTriggerHabboWalkOffFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_enter_room", WiredTriggerHabboEntersRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_says_something", WiredTriggerHabboSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_periodically", WiredTriggerRepeater.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_period_long", WiredTriggerRepeaterLong.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_state_changed", WiredTriggerFurniStateToggled.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_at_given_time", WiredTriggerAtSetTime.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_at_time_long", WiredTriggerAtTimeLong.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_collision", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_starts", WiredTriggerGameStarts.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_ends", WiredTriggerGameEnds.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_bot_reached_stf", WiredTriggerBotReachedFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_bot_reached_avtr", WiredTriggerBotReachedHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_score_achieved", WiredTriggerScoreAchieved.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_performs_action", WiredTriggerUserPerformsAction.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_recv_signal", WiredTriggerReceiveSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_team_win", WiredTriggerTeamWins.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_team_lose", WiredTriggerTeamLoses.class));

        // May/July Wired 2.0 triggers.
        this.interactionsList.add(new ItemInteraction("wf_trg_clock_counter", WiredTriggerClockReachTime.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_furni", WiredTriggerClicksFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_period_short", WiredTriggerRepeaterShort.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_stuff_state", WiredTriggerStuffStateChanged.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_tile", WiredTriggerAvatarClicksTile.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_var_changed", WiredTriggerVariableChanged.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_leave_room", WiredTriggerAvatarLeavesRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_user", WiredTriggerUserClicksUser.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_transaction_complete", WiredTriggerTransactionCompleted.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_transaction_fail", WiredTriggerTransactionFailed.class));

        this.interactionsList.add(new ItemInteraction("wf_act_toggle_state", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_reset_timers", WiredEffectResetTimers.class));
        this.interactionsList.add(new ItemInteraction("wf_act_match_to_sshot", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score", WiredEffectGiveScore.class));
        this.interactionsList.add(new ItemInteraction("wf_act_show_message", WiredEffectWhisper.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_to", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_join_team", WiredEffectJoinTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_act_leave_team", WiredEffectLeaveTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_act_chase", WiredEffectMoveFurniTowards.class));
        this.interactionsList.add(new ItemInteraction("wf_act_flee", WiredEffectMoveFurniAway.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_to_dir", WiredEffectChangeFurniDirection.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score_tm", WiredEffectGiveScoreToTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_act_toggle_to_rnd", WiredEffectToggleRandom.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_furni_to", WiredEffectMoveFurniTo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_reward", WiredEffectGiveReward.class));
        this.interactionsList.add(new ItemInteraction("wf_act_call_stacks", WiredEffectTriggerStacks.class));
        this.interactionsList.add(new ItemInteraction("wf_act_kick_user", WiredEffectKickHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_mute_triggerer", WiredEffectMuteHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_teleport", WiredEffectBotTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_move", WiredEffectBotWalkToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk", WiredEffectBotTalk.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_give_handitem", WiredEffectBotGiveHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_follow_avatar", WiredEffectBotFollowHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_clothes", WiredEffectBotClothes.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk_to_avatar", WiredEffectBotTalkToHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_control_clock", WiredEffectControlClock.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_altitude", WiredEffectSetFurniAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_signal", WiredEffectSendSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_signal_negative", WiredEffectSendSignalNegative.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_var", WiredEffectGiveVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_var", WiredEffectRemoveVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_act_change_var_val", WiredEffectChangeVariableValue.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_signal_neg", WiredEffectSendSignalNegative.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_send_signal", WiredEffectSendSignalNegative.class));
        this.interactionsList.add(new ItemInteraction("wf_act_freeze", WiredEffectFreezeUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_unfreeze", WiredEffectUnfreezeUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_rel_mov", WiredEffectRelativeFurniMove.class));
        this.interactionsList.add(new ItemInteraction("wf_act_furni_to_furni", WiredEffectMoveFurniToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_furni_to_user", WiredEffectMoveFurniToUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_call_stacks", WiredEffectExecuteStacksNegative.class));
        this.interactionsList.add(new ItemInteraction("wf_act_adjust_clock", WiredEffectAdjustCounterTime.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate_user", WiredEffectMoveUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_user_to_furni", WiredEffectMoveUserToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_to_room", WiredEffectTeleportToRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_currency", WiredEffectGiveCurrencyFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_currency_from_chest", WiredEffectGiveCurrencyFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_furni", WiredEffectGiveFurniFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_furni_from_chest", WiredEffectGiveFurniFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_act_init_transaction", WiredEffectInitiateTransaction.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cancel_transaction", WiredEffectCancelTransaction.class));
        this.interactionsList.add(new ItemInteraction("wf_act_log", WiredEffectWriteToLogs.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_log", WiredEffectWriteToLogsNegative.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_achievement", WiredEffectProgressAchievement.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_effect", WiredEffectGiveEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_act_override_height", WiredEffectOverrideHeight.class));
        this.interactionsList.add(new ItemInteraction("wf_act_click_conf", WiredEffectSetClickConfig.class));
        this.interactionsList.add(new ItemInteraction("wf_act_place_furni", WiredEffectPlaceFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_furni", WiredEffectRemoveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_furni_as_group", WiredEffectMoveAsGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_act_progress_reward_track", WiredEffectProgressRewardTrack.class));
        this.interactionsList.add(new ItemInteraction("wf_act_reset_reward_track", WiredEffectResetRewardTrack.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_respect", WiredEffectGiveRespect.class));
        this.interactionsList.add(new ItemInteraction("wf_act_alert", WiredEffectAlert.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_handitem", WiredEffectGiveHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_effect", WiredEffectGiveEffect.class));

        this.interactionsList.add(new ItemInteraction("wf_cnd_has_furni_on", WiredConditionFurniHaveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_furnis_hv_avtrs", WiredConditionFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_stuff_is", WiredConditionFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_in_group", WiredConditionGroupMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_count_in", WiredConditionHabboCount.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wearing_effect", WiredConditionHabboHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wearing_badge", WiredConditionHabboWearsBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_time_less_than", WiredConditionLessTimeElapsed.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_snapshot", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_time_more_than", WiredConditionMoreTimeElapsed.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_furni_on", WiredConditionNotFurniHaveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_hv_avtrs", WiredConditionNotFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_stuff_is", WiredConditionNotFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_user_count", WiredConditionNotHabboCount.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wearing_fx", WiredConditionNotHabboHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wearing_b", WiredConditionNotHabboWearsBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_in_group", WiredConditionNotInGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_in_team", WiredConditionNotInTeam.class));
        this.interactionsList
                .add(new ItemInteraction("wf_cnd_not_match_snap", WiredConditionNotMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_trggrer_on", WiredConditionNotTriggerOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_in_team", WiredConditionTeamMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trggrer_on_frn", WiredConditionTriggerOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_handitem", WiredConditionHabboHasHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_date_rng_active", WiredConditionDateRangeActive.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_movement_validation_legacy", WiredConditionMovementValidation.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_triggerer_match", WiredConditionTriggererMatches.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_triggerer_match", WiredConditionNotTriggererMatches.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_time", WiredConditionTimeMatches.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_date", WiredConditionDateMatches.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_has_handitem", WiredConditionNotHasHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_team_has_rank", WiredConditionTeamIsWinning.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_performs_action", WiredConditionPerformingAction.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_user_performs_action", WiredConditionNotPerformingAction.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_team_has_score", WiredConditionTeamHasScore.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_counter_time_matches", WiredConditionClockTimeMatches.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_altitude", WiredConditionFurniHasAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_dir", WiredConditionUserDirection.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_slc_quantity", WiredConditionInputSourceQuantity.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_valid_moves", WiredConditionCanPerformMove.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_var", WiredConditionHasVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_neg_has_var", WiredConditionNotHasVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_var_val_match", WiredConditionVariableValue.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_var_age_match", WiredConditionVariableAge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_chest_has_items", WiredConditionChestHasItems.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_chest_has_item_type", WiredConditionChestHasItemTypes.class));

        this.interactionsList.add(new ItemInteraction("wf_slc_furni_bytype", WiredSelectorFurniByType.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_picks", WiredSelectorFurniChooser.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_bytype", WiredSelectorUsersByType.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_team", WiredSelectorUsersInTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_onfurni", WiredSelectorFurniOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_signal", WiredSelectorFurniFromSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_area", WiredSelectorFurniInArea.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_onfurni", WiredSelectorUsersOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_byname", WiredSelectorUsersByName.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_area", WiredSelectorUsersInArea.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_handitem", WiredSelectorUsersWithHanditem.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_group", WiredSelectorUsersInGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_altitude", WiredSelectorFurniWithAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_byaction", WiredSelectorUsersByAction.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_signal", WiredSelectorUsersFromSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_neighborhood", WiredSelectorFurniInNeighborhood.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_neighborhood", WiredSelectorUsersInNeighborhood.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_with_var", WiredSelectorFurniWithVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_with_var", WiredSelectorUsersWithVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_remote", WiredSelectorRemote.class));

        this.interactionsList.add(new ItemInteraction("wf_xtra_exec_in_order", WiredAddonExecuteInOrder.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_or_eval", WiredAddonConditionEvaluation.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_random", WiredAddonRandomEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_unseen", WiredAddonUnseenEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_execution_limit", WiredAddonExecutionLimit.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_no_animation", WiredAddonNoMoveAnimation.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_physics", WiredAddonMovementPhysics.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_carry_users", WiredAddonCarryUsers.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_anim_time", WiredAddonAnimationTime.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_furni", WiredAddonFurniSelectorFilter.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_users", WiredAddonUserSelectorFilter.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_furni_by_var", WiredAddonFurniVariableFilter.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_users_by_var", WiredAddonUserVariableFilter.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_text_output_username", WiredAddonUsernamePlaceholder.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_text_output_furni_name", WiredAddonFurniNamePlaceholder.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_lvlup_system", WiredAddonVariableLevelUp.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_text_output_variable", WiredAddonVariablePlaceholder.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_text_input_variable", WiredAddonVariableCapturer.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_text_connector", WiredAddonVariableTextConverter.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_time_util", WiredAddonVariableTimeUtility.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_global_placeholder", WiredAddonGlobalPlaceholder.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_achievement_enabler", WiredAddonAchievementEnabler.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_rotate_to_dir", WiredAddonProjectile.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_curve", WiredAddonJumpStrength.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_scan_chest_furni_by_type", WiredAddonChestItemTypeScanner.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_custom_contract", WiredAddonCustomContract.class));
        this.interactionsList.add(new ItemInteraction("wf_var_furni", WiredVariableFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_var_user", WiredVariableUser.class));
        this.interactionsList.add(new ItemInteraction("wf_var_room", WiredVariableGlobal.class));
        this.interactionsList.add(new ItemInteraction("wf_var_context", WiredVariableContext.class));
        this.interactionsList.add(new ItemInteraction("wf_var_reference", WiredVariableReference.class));
        this.interactionsList.add(new ItemInteraction("wf_var_quest", WiredVariableQuest.class));
        this.interactionsList.add(new ItemInteraction("wf_var_quest_chain", WiredVariableQuestChain.class));
        this.interactionsList.add(new ItemInteraction("wf_var_echo", WiredVariableEcho.class));

        this.interactionsList.add(new ItemInteraction("wf_blob", WiredBlob.class));

        this.interactionsList.add(new ItemInteraction("wf_highscore", InteractionWiredHighscore.class));

        this.interactionsList.add(new ItemInteraction("battlebanzai_tile", InteractionBattleBanzaiTile.class));
        this.interactionsList
                .add(new ItemInteraction("battlebanzai_random_teleport", InteractionBattleBanzaiTeleporter.class));
        this.interactionsList.add(new ItemInteraction("battlebanzai_sphere", InteractionBattleBanzaiSphere.class));
        this.interactionsList.add(new ItemInteraction("battlebanzai_puck", InteractionBattleBanzaiPuck.class));

        this.interactionsList.add(new ItemInteraction("battlebanzai_gate_blue", InteractionBattleBanzaiGateBlue.class));
        this.interactionsList
                .add(new ItemInteraction("battlebanzai_gate_green", InteractionBattleBanzaiGateGreen.class));
        this.interactionsList.add(new ItemInteraction("battlebanzai_gate_red", InteractionBattleBanzaiGateRed.class));
        this.interactionsList
                .add(new ItemInteraction("battlebanzai_gate_yellow", InteractionBattleBanzaiGateYellow.class));

        this.interactionsList
                .add(new ItemInteraction("battlebanzai_counter_blue", InteractionBattleBanzaiScoreboardBlue.class));
        this.interactionsList
                .add(new ItemInteraction("battlebanzai_counter_green", InteractionBattleBanzaiScoreboardGreen.class));
        this.interactionsList
                .add(new ItemInteraction("battlebanzai_counter_red", InteractionBattleBanzaiScoreboardRed.class));
        this.interactionsList
                .add(new ItemInteraction("battlebanzai_counter_yellow", InteractionBattleBanzaiScoreboardYellow.class));

        this.interactionsList.add(new ItemInteraction("freeze_block", InteractionFreezeBlock.class));
        this.interactionsList.add(new ItemInteraction("freeze_tile", InteractionFreezeTile.class));
        this.interactionsList.add(new ItemInteraction("freeze_exit", InteractionFreezeExitTile.class));

        this.interactionsList.add(new ItemInteraction("freeze_gate_blue", InteractionFreezeGateBlue.class));
        this.interactionsList.add(new ItemInteraction("freeze_gate_green", InteractionFreezeGateGreen.class));
        this.interactionsList.add(new ItemInteraction("freeze_gate_red", InteractionFreezeGateRed.class));
        this.interactionsList.add(new ItemInteraction("freeze_gate_yellow", InteractionFreezeGateYellow.class));

        this.interactionsList.add(new ItemInteraction("freeze_counter_blue", InteractionFreezeScoreboardBlue.class));
        this.interactionsList.add(new ItemInteraction("freeze_counter_green", InteractionFreezeScoreboardGreen.class));
        this.interactionsList.add(new ItemInteraction("freeze_counter_red", InteractionFreezeScoreboardRed.class));
        this.interactionsList
                .add(new ItemInteraction("freeze_counter_yellow", InteractionFreezeScoreboardYellow.class));

        this.interactionsList.add(new ItemInteraction("icetag_pole", InteractionIceTagPole.class));
        this.interactionsList.add(new ItemInteraction("icetag_field", InteractionIceTagField.class));

        this.interactionsList.add(new ItemInteraction("bunnyrun_pole", InteractionBunnyrunPole.class));
        this.interactionsList.add(new ItemInteraction("bunnyrun_field", InteractionBunnyrunField.class));

        this.interactionsList.add(new ItemInteraction("rollerskate_field", InteractionRollerskateField.class));

        this.interactionsList.add(new ItemInteraction("football", InteractionFootball.class));
        this.interactionsList.add(new ItemInteraction("football_gate", InteractionFootballGate.class));
        this.interactionsList
                .add(new ItemInteraction("football_counter_blue", InteractionFootballScoreboardBlue.class));
        this.interactionsList
                .add(new ItemInteraction("football_counter_green", InteractionFootballScoreboardGreen.class));
        this.interactionsList.add(new ItemInteraction("football_counter_red", InteractionFootballScoreboardRed.class));
        this.interactionsList
                .add(new ItemInteraction("football_counter_yellow", InteractionFootballScoreboardYellow.class));
        this.interactionsList.add(new ItemInteraction("football_goal_blue", InteractionFootballGoalBlue.class));
        this.interactionsList.add(new ItemInteraction("football_goal_green", InteractionFootballGoalGreen.class));
        this.interactionsList.add(new ItemInteraction("football_goal_red", InteractionFootballGoalRed.class));
        this.interactionsList.add(new ItemInteraction("football_goal_yellow", InteractionFootballGoalYellow.class));

        this.interactionsList.add(new ItemInteraction("snowstorm_tree", null));
        this.interactionsList.add(new ItemInteraction("snowstorm_machine", null));
        this.interactionsList.add(new ItemInteraction("snowstorm_pile", null));

        this.interactionsList.add(new ItemInteraction("vote_counter", InteractionVoteCounter.class));

        this.interactionsList.add(new ItemInteraction("totem_leg", InteractionTotemLegs.class));
        this.interactionsList.add(new ItemInteraction("totem_head", InteractionTotemHead.class));
        this.interactionsList.add(new ItemInteraction("totem_planet", InteractionTotemPlanet.class));
    }

    public void addItemInteraction(ItemInteraction itemInteraction) {
        for (ItemInteraction interaction : this.interactionsList) {
            if (interaction.getType() == itemInteraction.getType() ||
                    interaction.getName().equalsIgnoreCase(itemInteraction.getName()))

                throw new RuntimeException(
                        "Interaction Types must be unique. An class with type: " + interaction.getClass().getName()
                                + " was already added OR the key: " + interaction.getName() + " is already in use.");
        }

        this.interactionsList.add(itemInteraction);
    }

    public ItemInteraction getItemInteraction(Class<? extends HabboItem> type) {
        for (ItemInteraction interaction : this.interactionsList) {
            if (interaction.getType() == type)
                return interaction;
        }

        LOGGER.debug("Can't find interaction class: {}", type.getName());
        return this.getItemInteraction(InteractionDefault.class);
    }

    public ItemInteraction getItemInteraction(String type) {
        for (ItemInteraction interaction : this.interactionsList) {
            if (interaction.getName().equalsIgnoreCase(type))
                return interaction;
        }

        return this.getItemInteraction(InteractionDefault.class);
    }

    public void loadItems() {
        try (
                Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(("SELECT * FROM items_base ORDER BY id DESC"))) {
            while (set.next()) {
                try {
                    // Item proxyItem =
                    int id = set.getInt("id");
                    if (!this.items.containsKey(id))
                        this.items.put(id, new Item(set));
                    else
                        this.items.get(id).update(set);
                } catch (Exception e) {
                    LOGGER.error("Failed to load Item ({})", set.getInt("id"));
                    LOGGER.error("Caught exception", e);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void loadCrackable() {
        this.crackableRewards.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM items_crackable ORDER BY crackable_id, tier");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                try {
                    int crackableId = set.getInt("crackable_id");
                    CrackableReward reward = this.crackableRewards.get(crackableId);
                    if (reward == null) {
                        reward = new CrackableReward(set);
                        this.crackableRewards.put(crackableId, reward);
                    } else {
                        // additional tier/outcome row for an already-loaded crackable
                        reward.addRow(set);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load items_crackable row (crackable_id = {})", set.getInt("crackable_id"));
                    LOGGER.error("Caught exception", e);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    public int getCrackableCount(int itemId) {
        if (this.crackableRewards.containsKey(itemId))
            return this.crackableRewards.get(itemId).count;
        else
            return 0;
    }

    public int calculateCrackState(int count, int max, Item baseItem) {
        return (int) Math.floor((1.0D / ((double) max / (double) count) * baseItem.getStateCount()));
    }

    public CrackableReward getCrackableData(int itemId) {
        return this.crackableRewards.get(itemId);
    }

    /**
     * Rolls the crackable's rewards: one outcome per tier, union of all winners.
     * Returns the list of items_base ids to give (empty if the furni has no crackable config).
     */
    public List<Integer> getCrackableRewards(int itemId) {
        CrackableReward reward = this.crackableRewards.get(itemId);
        if (reward == null) {
            return Collections.emptyList();
        }
        return reward.roll();
    }

    public void loadSoundTracks() {
        this.soundTracks.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM soundtracks");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                this.soundTracks.put(set.getString("code"), new SoundTrack(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public SoundTrack getSoundTrack(String code) {
        return this.soundTracks.get(code);
    }

    public SoundTrack getSoundTrack(int id) {
        for (Map.Entry<String, SoundTrack> entry : this.soundTracks.entrySet()) {
            if (entry.getValue().getId() == id)
                return entry.getValue();
        }

        return null;
    }

    public HabboItem createItem(int habboId, Item item, int limitedStack, int limitedSells, String extraData) {
        return this.createItemInternal(habboId, item, limitedStack, limitedSells, extraData, false);
    }

    private HabboItem createItemInternal(int habboId, Item item, int limitedStack, int limitedSells, String extraData,
            boolean isBuildersClub) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO items (user_id, item_id, extra_data, limited_data, is_builders_club) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, habboId);
            statement.setInt(2, item.getId());
            statement.setString(3, extraData);
            statement.setString(4, limitedStack + ":" + limitedSells);
            statement.setInt(5, isBuildersClub ? 1 : 0);
            statement.execute();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);

                    Class<? extends HabboItem> itemClass = item.getInteractionType().getType();

                    HabboItem habboItem = null;
                    if (itemClass != null) {
                        try {
                            habboItem = itemClass
                                    .getDeclaredConstructor(int.class, int.class, Item.class, String.class, int.class,
                                            int.class)
                                    .newInstance(id, habboId, item, extraData, limitedStack, limitedSells);
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                            habboItem = new InteractionDefault(id, habboId, item, extraData, limitedStack,
                                    limitedSells);
                        }
                    }

                    if (habboItem != null && isBuildersClub) {
                        habboItem.setBuildersClub(true);
                    }

                    return habboItem;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public HabboItem createBuildersClubItem(int habboId, Item item, String extraData) {
        return this.createItemInternal(habboId, item, 0, 0, extraData, true);
    }

    public HabboItem createTemporaryItem(int habboId, Item item, String extraData) {
        if (item == null || item.getInteractionType() == null) return null;
        Class<? extends HabboItem> itemClass = item.getInteractionType().getType();
        if (itemClass == null || InteractionWired.class.isAssignableFrom(itemClass)) return null;
        int id = TEMPORARY_ITEM_ID.getAndDecrement();
        // Temporary Wired copies are deliberately inert. Instantiating the
        // catalogue interaction could execute vending/reward/crackable logic
        // against a non-owned, non-persistent item.
        return new InteractionDefault(id, habboId, item,
                extraData == null ? "0" : extraData, 0, 0);
    }

    public void loadNewUserGifts() {
        this.newuserGifts.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM nux_gifts")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.newuserGifts.put(set.getInt("id"), new NewUserGift(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void addNewUserGift(NewUserGift gift) {
        this.newuserGifts.put(gift.getId(), gift);
    }

    public void removeNewUserGift(NewUserGift gift) {
        this.newuserGifts.remove(gift.getId());
    }

    public NewUserGift getNewUserGift(int id) {
        return this.newuserGifts.get(id);
    }

    public List<NewUserGift> getNewUserGifts() {
        return new ArrayList<>(this.newuserGifts.values());
    }

    public void deleteItem(HabboItem item) {
        if (item == null) {
            return;
        }
        if (Emulator.getGameEnvironment().getChestManager().isChest(item)) {
            com.eu.habbo.habbohotel.items.chests.ChestRepository.Result result =
                    Emulator.getGameEnvironment().getChestManager().deleteChest(item);
            if (result != com.eu.habbo.habbohotel.items.chests.ChestRepository.Result.OK
                    && result != com.eu.habbo.habbohotel.items.chests.ChestRepository.Result.NOT_FOUND) {
                LOGGER.warn("Refusing to delete chest {}: {}", item.getId(), result);
            }
            return;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
            statement.setInt(1, item.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public HabboItem handleRecycle(Habbo habbo, String itemId) {
        String extradata = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-"
                + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-" + Calendar.getInstance().get(Calendar.YEAR);

        HabboItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO items (user_id, item_id, extra_data) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, Emulator.getGameEnvironment().getCatalogManager().ecotronItem.getId());
            statement.setString(3, extradata);
            statement.execute();

            try (ResultSet set = statement.getGeneratedKeys()) {
                try (PreparedStatement preparedStatement = connection
                        .prepareStatement("INSERT INTO items_presents VALUES (?, ?)")) {
                    while (set.next() && item == null) {
                        preparedStatement.setInt(1, set.getInt(1));
                        preparedStatement.setInt(2, Integer.parseInt(itemId));
                        preparedStatement.addBatch();
                        item = new InteractionDefault(set.getInt(1), habbo.getHabboInfo().getId(),
                                Emulator.getGameEnvironment().getCatalogManager().ecotronItem, extradata, 0, 0);
                    }

                    preparedStatement.executeBatch();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return item;
    }

    public HabboItem handleOpenRecycleBox(Habbo habbo, HabboItem box) {
        Emulator.getThreading().run(new QueryDeleteHabboItem(box.getId()));
        HabboItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT * FROM items_presents WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, box.getId());
            try (ResultSet rewardSet = statement.executeQuery()) {
                if (rewardSet.next()) {
                    try (PreparedStatement preparedStatement = connection.prepareStatement(
                            "INSERT INTO items (user_id, item_id) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        preparedStatement.setInt(1, habbo.getHabboInfo().getId());
                        preparedStatement.setInt(2, rewardSet.getInt("base_item_reward"));
                        preparedStatement.execute();

                        try (ResultSet set = preparedStatement.getGeneratedKeys()) {
                            if (set.next()) {
                                try (PreparedStatement request = connection
                                        .prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
                                    request.setInt(1, set.getInt(1));

                                    try (ResultSet resultSet = request.executeQuery()) {
                                        if (resultSet.next()) {
                                            try (PreparedStatement deleteStatement = connection.prepareStatement(
                                                    "DELETE FROM items_presents WHERE item_id = ? LIMIT 1")) {
                                                deleteStatement.setInt(1, box.getId());
                                                deleteStatement.execute();

                                                item = this.loadHabboItem(resultSet);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return item;
    }

    public void insertTeleportPair(int itemOneId, int itemTwoId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("INSERT INTO items_teleports VALUES (?, ?)")) {
            statement.setInt(1, itemOneId);
            statement.setInt(2, itemTwoId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insertHopper(HabboItem hopper) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO items_hoppers VALUES (?, ?)")) {
            statement.setInt(1, hopper.getId());
            statement.setInt(2, hopper.getBaseItem().getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int[] getTargetTeleportRoomId(HabboItem item) {
        int[] a = new int[] {};

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT items.id, items.room_id FROM items_teleports INNER JOIN items ON items_teleports.teleport_one_id = items.id OR items_teleports.teleport_two_id = items.id WHERE items.id != ? AND items.room_id > 0 LIMIT 1")) {
            statement.setInt(1, item.getId());
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    a = new int[] { set.getInt("room_id"), set.getInt("id") };
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return a;
    }

    public HabboItem loadHabboItem(int itemId) {
        HabboItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    item = this.loadHabboItem(set);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return item;
    }

    public HabboItem loadHabboItem(ResultSet set) throws SQLException {
        Item baseItem = this.getItem(set.getInt("item_id"));

        if (baseItem == null)
            return null;

        Class<? extends HabboItem> itemClass = baseItem.getInteractionType().getType();

        if (itemClass != null) {
            try {
                Constructor<?> c = itemClass.getConstructor(ResultSet.class, Item.class);
                c.setAccessible(true);

                return (HabboItem) c.newInstance(set, baseItem);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        return null;
    }

    public HabboItem createGift(String username, Item item, String extraData, int limitedStack, int limitedSells) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);

        int userId = 0;

        if (habbo != null) {
            userId = habbo.getHabboInfo().getId();
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("SELECT id FROM users WHERE username = ?")) {
                statement.setString(1, username);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        userId = set.getInt(1);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        if (userId > 0) {
            return createGift(userId, item, extraData, limitedStack, limitedSells);
        }

        return null;
    }

    public HabboItem createGift(int userId, Item item, String extraData, int limitedStack, int limitedSells) {
        if (userId == 0)
            return null;

        if (extraData.length() > 1000) {
            LOGGER.error("Extradata exceeds maximum length of 1000 characters: {}", extraData);
            extraData = extraData.substring(0, 1000);
        }

        HabboItem gift = this.createItem(userId, item, limitedStack, limitedSells, extraData);

        if (gift != null) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo != null) {
                habbo.getInventory().getItemsComponent().addItem(gift);
                habbo.getClient().sendResponse(new UnseenItemsMessageComposer(gift));
            }
        }

        return gift;
    }

    public Item getItem(int itemId) {
        if (itemId < 0)
            return null;

        return this.items.get(itemId);
    }

    public TIntObjectMap<Item> getItems() {
        return this.items;
    }

    public Item getItem(String itemName) {
        TIntObjectIterator<Item> item = this.items.iterator();

        for (int i = this.items.size(); i-- > 0;) {
            try {
                item.advance();
                if (item.value().getName().equalsIgnoreCase(itemName)) {
                    return item.value();
                }
            } catch (NoSuchElementException e) {
                break;
            }
        }

        return null;
    }

    public YoutubeManager getYoutubeManager() {
        return this.youtubeManager;
    }

    public WiredHighscoreManager getHighscoreManager() {
        return highscoreManager;
    }

    public void dispose() {
        this.items.clear();
        this.highscoreManager.dispose();

        LOGGER.info("Item Manager -> Disposed!");
    }

    public List<String> getInteractionList() {
        List<String> interactions = new ArrayList<>();

        for (ItemInteraction interaction : this.interactionsList) {
            interactions.add(interaction.getName());
        }

        Collections.sort(interactions);
        return interactions;
    }
}
