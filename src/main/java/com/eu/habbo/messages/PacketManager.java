package com.eu.habbo.messages;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.achievements.RequestAchievementConfigurationEvent;
import com.eu.habbo.messages.incoming.achievements.GetAchievementsEvent;
import com.eu.habbo.messages.incoming.ambassadors.AmbassadorAlertCommandEvent;
import com.eu.habbo.messages.incoming.ambassadors.AmbassadorVisitCommandEvent;
import com.eu.habbo.messages.incoming.camera.*;
import com.eu.habbo.messages.incoming.catalog.*;
import com.eu.habbo.messages.incoming.catalog.marketplace.*;
import com.eu.habbo.messages.incoming.catalog.recycler.PresentOpenMessageEvent;
import com.eu.habbo.messages.incoming.catalog.recycler.RecycleItemsMessageEvent;
import com.eu.habbo.messages.incoming.catalog.recycler.GetRecyclerStatusMessageEvent;
import com.eu.habbo.messages.incoming.catalog.recycler.RequestRecyclerLogicEvent;
import com.eu.habbo.messages.incoming.crafting.*;
import com.eu.habbo.messages.incoming.events.calendar.OpenCampaignCalendarDoorEvent;
import com.eu.habbo.messages.incoming.events.calendar.OpenCampaignCalendarDoorAsStaffEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.GetOccupiedTilesMessageEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.GetRoomEntryTileMessageEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.UpdateFloorPropertiesMessageEvent;
import com.eu.habbo.messages.incoming.friends.*;
import com.eu.habbo.messages.incoming.games.SnowStormGameUnloadedMessageEvent;
import com.eu.habbo.messages.incoming.gamecenter.*;
import com.eu.habbo.messages.incoming.games.gamehall.GameBoardMoveMessageEvent;
import com.eu.habbo.messages.incoming.games.leaderboards.RequestLeaderboardMessageEvent;
import com.eu.habbo.messages.incoming.games.battleball.*;
import com.eu.habbo.messages.incoming.games.snowstorm.*;
import com.eu.habbo.messages.incoming.games.tickets.BuyTicketsEvent;
import com.eu.habbo.messages.incoming.guardians.ChatReviewGuideDecidesOnOfferMessageEvent;
import com.eu.habbo.messages.incoming.guardians.ChatReviewGuideDetachedMessageEvent;
import com.eu.habbo.messages.incoming.guardians.ChatReviewGuideVoteMessageEvent;
import com.eu.habbo.messages.incoming.guides.*;
import com.eu.habbo.messages.incoming.guilds.*;
import com.eu.habbo.messages.incoming.guilds.forums.*;
import com.eu.habbo.messages.incoming.habbicons.*;
import com.eu.habbo.messages.incoming.handshake.*;
import com.eu.habbo.messages.incoming.helper.GetCfhStatusMessageEvent;
import com.eu.habbo.messages.incoming.helper.RequestTalentTrackEvent;
import com.eu.habbo.messages.incoming.hotelview.*;
import com.eu.habbo.messages.incoming.inventory.GetBadgesEvent;
import com.eu.habbo.messages.incoming.inventory.GetBotInventoryEvent;
import com.eu.habbo.messages.incoming.inventory.RequestFurniInventoryEvent;
import com.eu.habbo.messages.incoming.inventory.GetPetInventoryEvent;
import com.eu.habbo.messages.incoming.modtool.*;
import com.eu.habbo.messages.incoming.navigator.*;
import com.eu.habbo.messages.incoming.polls.AnswerPollEvent;
import com.eu.habbo.messages.incoming.polls.CancelPollEvent;
import com.eu.habbo.messages.incoming.polls.GetPollDataEvent;
import com.eu.habbo.messages.incoming.polls.infobus.VotePollCounterEvent;
import com.eu.habbo.messages.incoming.quests.AcceptQuestEvent;
import com.eu.habbo.messages.incoming.quests.GetQuestsEvent;
import com.eu.habbo.messages.incoming.quests.OpenQuestTrackerEvent;
import com.eu.habbo.messages.incoming.quests.RejectQuestEvent;
import com.eu.habbo.messages.incoming.rooms.*;
import com.eu.habbo.messages.incoming.rooms.bots.RemoveBotFromFlatMessageEvent;
import com.eu.habbo.messages.incoming.rooms.bots.PlaceBotMessageEvent;
import com.eu.habbo.messages.incoming.rooms.bots.CommandBotEvent;
import com.eu.habbo.messages.incoming.rooms.bots.GetBotCommandConfigurationDataEvent;
import com.eu.habbo.messages.incoming.rooms.items.*;
import com.eu.habbo.messages.incoming.rooms.items.jukebox.*;
import com.eu.habbo.messages.incoming.rooms.items.lovelock.FriendFurniConfirmLockMessageEvent;
import com.eu.habbo.messages.incoming.rooms.items.rentablespace.RentableSpaceCancelRentMessageEvent;
import com.eu.habbo.messages.incoming.rooms.items.rentablespace.RentableSpaceRentMessageEvent;
import com.eu.habbo.messages.incoming.rooms.items.youtube.SetYoutubeDisplayPlaylistMessageEvent;
import com.eu.habbo.messages.incoming.rooms.items.youtube.GetYoutubeDisplayStatusMessageEvent;
import com.eu.habbo.messages.incoming.rooms.items.youtube.ControlYoutubeDisplayPlaybackMessageEvent;
import com.eu.habbo.messages.incoming.rooms.pets.*;
import com.eu.habbo.messages.incoming.rooms.promotions.PurchaseRoomAdMessageEvent;
import com.eu.habbo.messages.incoming.rooms.promotions.GetRoomAdPurchaseInfoEvent;
import com.eu.habbo.messages.incoming.rooms.promotions.UpdateRoomPromotionEvent;
import com.eu.habbo.messages.incoming.rooms.users.*;
import com.eu.habbo.messages.incoming.rewardtrack.ClaimRewardTrackRewardMessageEvent;
import com.eu.habbo.messages.incoming.rewardtrack.PurchaseRewardTrackPremiumMessageEvent;
import com.eu.habbo.messages.incoming.trading.*;
import com.eu.habbo.messages.incoming.unknown.GetResolutionAchievementsMessageEvent;
import com.eu.habbo.messages.incoming.unknown.GetBadgePointLimitsEvent;
import com.eu.habbo.messages.incoming.users.*;
import com.eu.habbo.messages.incoming.wired.ApplySnapshotMessageEvent;
import com.eu.habbo.messages.incoming.wired.OpenAckMessageEvent;
import com.eu.habbo.messages.incoming.wired.RequestAllVariablesHashMessageEvent;
import com.eu.habbo.messages.incoming.wired.RequestVariablesDiffMessageEvent;
import com.eu.habbo.messages.incoming.wired.RequestWiredCapabilitiesMessageEvent;
import com.eu.habbo.messages.incoming.wired.UpdateConditionMessageEvent;
import com.eu.habbo.messages.incoming.wired.UpdateActionMessageEvent;
import com.eu.habbo.messages.incoming.wired.UpdateAddonMessageEvent;
import com.eu.habbo.messages.incoming.wired.UpdateSelectorMessageEvent;
import com.eu.habbo.messages.incoming.wired.UpdateTriggerMessageEvent;
import com.eu.habbo.messages.incoming.wired.UpdateVariableMessageEvent;
import com.eu.habbo.messages.incoming.wired.WiredClickUserMessageEvent;
import com.eu.habbo.messages.incoming.wired.WiredMenuMessageEvent;
import com.eu.habbo.messages.incoming.wired.chests.ChestTransactionLogMessageEvent;
import com.eu.habbo.messages.incoming.wired.chests.*;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PacketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketManager.class);

    private static final List<Integer> logList = new ArrayList<>();
    public static boolean DEBUG_SHOW_PACKETS = false;
    public static boolean MULTI_THREADED_PACKET_HANDLING = false;
    private final THashMap<Integer, Class<? extends MessageHandler>> incoming;
    private final THashMap<Integer, List<ICallable>> callables;
    private final PacketNames names;

    public PacketManager() throws Exception {
        this.incoming = new THashMap<>();
        this.callables = new THashMap<>();
        this.names = new PacketNames();
        this.names.initialize();

        this.registerHandshake();
        this.registerCatalog();
        this.registerEvent();
        this.registerFriends();
        this.registerNavigator();
        this.registerUsers();
        this.registerHotelview();
        this.registerInventory();
        this.registerRooms();
        this.registerPolls();
        this.registerUnknown();
        this.registerModTool();
        this.registerTrading();
        this.registerGuilds();
        this.registerPets();
        this.registerWired();
        this.registerAchievements();
        this.registerFloorPlanEditor();
        this.registerAmbassadors();
        this.registerGuides();
        this.registerCrafting();
        this.registerCamera();
        this.registerGameCenter();
        this.registerHotelTickets();
        this.registerBattleBall();
        this.registerSnowStorm();
        this.registerRewardTrack();
        this.registerHabbicons();
        this.registerQuests();
    }

    public PacketNames getNames() {
        return names;
    }

    @EventHandler
    public static void onConfigurationUpdated(EmulatorConfigUpdatedEvent event) {
        logList.clear();

        for (String s : Emulator.getConfig().getValue("debug.show.headers").split(";")) {
            try {
                logList.add(Integer.valueOf(s));
            } catch (NumberFormatException e) {

            }
        }
    }

    public void registerHandler(Integer header, Class<? extends MessageHandler> handler) throws Exception {
        if (header < 0)
            return;

        if (this.incoming.containsKey(header)) {
            throw new Exception("Header already registered. Failed to register " + handler.getName() + " with header " + header);
        }

        this.incoming.putIfAbsent(header, handler);
    }

    private void registerBattleBall() throws Exception {
        this.registerHandler(Incoming.BattleBallGetInstanceListEvent, BattleBallGetInstanceListEvent.class);
        this.registerHandler(Incoming.BattleBallSetGameParametersEvent, BattleBallSetGameParametersEvent.class);
        this.registerHandler(Incoming.BattleBallDeleteEvent, BattleBallDeleteEvent.class);
        this.registerHandler(Incoming.BattleBallInitiateJoinEvent, BattleBallInitiateJoinEvent.class);
        this.registerHandler(Incoming.BattleBallSetJoinParametersEvent, BattleBallSetJoinParametersEvent.class);
        this.registerHandler(Incoming.BattleBallLeaveEvent, BattleBallLeaveEvent.class);
        this.registerHandler(Incoming.BattleBallKickEvent, BattleBallKickEvent.class);
        this.registerHandler(Incoming.BattleBallWatchEvent, BattleBallWatchEvent.class);
        this.registerHandler(Incoming.BattleBallStartEvent, BattleBallStartEvent.class);
        this.registerHandler(Incoming.BattleBallGameEvent, BattleBallGameEvent.class);
        this.registerHandler(Incoming.BattleBallRequestFullStatusEvent, BattleBallRequestFullStatusEvent.class);
        this.registerHandler(Incoming.BattleBallAddBotEvent, BattleBallAddBotEvent.class);
        this.registerHandler(Incoming.BattleBallRemoveBotEvent, BattleBallRemoveBotEvent.class);
    }

    private void registerHotelTickets() throws Exception {
        this.registerHandler(Incoming.BuyTicketsEvent, BuyTicketsEvent.class);
    }

    private void registerSnowStorm() throws Exception {
        this.registerHandler(Incoming.SnowStormGetLoungeEvent, SnowStormGetLoungeEvent.class);
        this.registerHandler(Incoming.SnowStormSetGameParametersEvent, SnowStormSetGameParametersEvent.class);
        this.registerHandler(Incoming.SnowStormDeleteEvent, SnowStormDeleteEvent.class);
        this.registerHandler(Incoming.SnowStormInitiateJoinEvent, SnowStormInitiateJoinEvent.class);
        this.registerHandler(Incoming.SnowStormSetJoinParametersEvent, SnowStormSetJoinParametersEvent.class);
        this.registerHandler(Incoming.SnowStormLeaveEvent, SnowStormLeaveEvent.class);
        this.registerHandler(Incoming.SnowStormKickEvent, SnowStormKickEvent.class);
        this.registerHandler(Incoming.SnowStormWatchEvent, SnowStormWatchEvent.class);
        this.registerHandler(Incoming.SnowStormStartEvent, SnowStormStartEvent.class);
        this.registerHandler(Incoming.SnowStormGameEvent, SnowStormGameEvent.class);
        this.registerHandler(Incoming.SnowStormRequestFullStatusEvent, SnowStormRequestFullStatusEvent.class);
        this.registerHandler(Incoming.SnowStormRequestFinishedGameEvent, SnowStormRequestFinishedGameEvent.class);
        this.registerHandler(Incoming.SnowStormAddBotEvent, SnowStormAddBotEvent.class);
        this.registerHandler(Incoming.SnowStormRemoveBotEvent, SnowStormRemoveBotEvent.class);
    }

    public void registerCallable(Integer header, ICallable callable) {
        this.callables.putIfAbsent(header, new ArrayList<>());
        this.callables.get(header).add(callable);
    }

    public void unregisterCallables(Integer header, ICallable callable) {
        if (this.callables.containsKey(header)) {
            this.callables.get(header).remove(callable);
        }
    }

    public void unregisterCallables(Integer header) {
        if (this.callables.containsKey(header)) {
            this.callables.clear();
        }
    }

    public void handlePacket(GameClient client, ClientMessage packet) {
        if (client == null || Emulator.isShuttingDown)
            return;

        try {
            if (this.isRegistered(packet.getMessageId())) {
                Class<? extends MessageHandler> handlerClass = this.incoming.get(packet.getMessageId());

                if (handlerClass == null) throw new Exception("Unknown message " + packet.getMessageId());

                if (client.getHabbo() == null && !handlerClass.isAnnotationPresent(NoAuthMessage.class)) {
                    if (DEBUG_SHOW_PACKETS) {
                        LOGGER.warn("Client packet {} requires an authenticated session.", packet.getMessageId());
                    }

                    return;
                }

                final MessageHandler handler = handlerClass.getDeclaredConstructor().newInstance();

                if (handler.getRatelimit() > 0) {
                    Object ratelimitKey =
                            handler.getRatelimitKey(packet.getMessageId());
                    if (client.messageTimestamps.containsKey(ratelimitKey) && System.currentTimeMillis() - client.messageTimestamps.get(ratelimitKey) < handler.getRatelimit()) {
                        if (PacketManager.DEBUG_SHOW_PACKETS) {
                            LOGGER.warn("Client packet {} was ratelimited.", packet.getMessageId());
                        }

                        return;
                    } else {
                        client.messageTimestamps.put(ratelimitKey, System.currentTimeMillis());
                    }
                }

                if (logList.contains(packet.getMessageId()) && client.getHabbo() != null) {
                    LOGGER.info("User {} sent packet {} with body {}", client.getHabbo().getHabboInfo().getUsername(), packet.getMessageId(), packet.getMessageBody());
                }

                handler.client = client;
                handler.packet = packet;

                if (this.callables.containsKey(packet.getMessageId())) {
                    for (ICallable callable : this.callables.get(packet.getMessageId())) {
                        callable.call(handler);
                    }
                }

                if (!handler.isCancelled) {
                    handler.handle();
                }
            }
        } catch (MalformedPacketException e) {
            LOGGER.warn("Disconnecting client for malformed packet {}: {}", packet.getMessageId(), e.getMessage());
            if (client.getChannel() != null) {
                client.getChannel().close();
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    boolean isRegistered(int header) {
        return this.incoming.containsKey(header);
    }

    private void registerAmbassadors() throws Exception {
        this.registerHandler(Incoming.AmbassadorAlertCommandEvent, AmbassadorAlertCommandEvent.class);
        this.registerHandler(Incoming.AmbassadorVisitCommandEvent, AmbassadorVisitCommandEvent.class);
    }

    private void registerRewardTrack() throws Exception {
        this.registerHandler(Incoming.ClaimRewardTrackRewardMessageEvent, ClaimRewardTrackRewardMessageEvent.class);
        this.registerHandler(Incoming.PurchaseRewardTrackPremiumMessageEvent, PurchaseRewardTrackPremiumMessageEvent.class);
    }

    private void registerHabbicons() throws Exception {
        this.registerHandler(Incoming.RequestHabbiconShopDataMessageEvent, RequestHabbiconShopDataMessageEvent.class);
        this.registerHandler(Incoming.RequestHabbiconInfoMessageEvent, RequestHabbiconInfoMessageEvent.class);
        this.registerHandler(Incoming.BuyHabbiconCollectionMessageEvent, BuyHabbiconCollectionMessageEvent.class);
        this.registerHandler(Incoming.BuyHabbiconMessageEvent, BuyHabbiconMessageEvent.class);
        this.registerHandler(Incoming.ClaimHabbiconRewardMessageEvent, ClaimHabbiconRewardMessageEvent.class);
        this.registerHandler(Incoming.FavouriteHabbiconMessageEvent, FavouriteHabbiconMessageEvent.class);
        this.registerHandler(Incoming.UnfavouriteHabbiconMessageEvent, UnfavouriteHabbiconMessageEvent.class);
        this.registerHandler(Incoming.UseHabbiconInRoomMessageEvent, UseHabbiconInRoomMessageEvent.class);
        this.registerHandler(Incoming.SendHabbiconInstantMessageEvent, SendHabbiconInstantMessageEvent.class);
    }

    private void registerCatalog() throws Exception {
        this.registerHandler(Incoming.RequestRecylerLogicEvent, RequestRecyclerLogicEvent.class);
        this.registerHandler(Incoming.GetBundleDiscountRulesetEvent, GetBundleDiscountRulesetEvent.class);
        this.registerHandler(Incoming.GetGiftWrappingConfigurationEvent, GetGiftWrappingConfigurationEvent.class);
        this.registerHandler(Incoming.GetMarketplaceConfigEvent, RequestMarketplaceConfigEvent.class);
        this.registerHandler(Incoming.GetCatalogIndexEvent, GetCatalogIndexEvent.class);
        this.registerHandler(Incoming.BuildersClubQueryFurniCountMessageEvent, BuildersClubQueryFurniCountMessageEvent.class);
        this.registerHandler(Incoming.BuildersClubPlaceRoomItemMessageEvent, BuildersClubPlaceRoomItemMessageEvent.class);
        this.registerHandler(Incoming.BuildersClubPlaceWallItemMessageEvent, BuildersClubPlaceWallItemMessageEvent.class);
        this.registerHandler(Incoming.GetCatalogPageEvent, GetCatalogPageEvent.class);
        this.registerHandler(Incoming.PurchaseFromCatalogAsGiftEvent, PurchaseFromCatalogAsGiftEvent.class);
        this.registerHandler(Incoming.PurchaseFromCatalogEvent, PurchaseFromCatalogEvent.class);
        this.registerHandler(Incoming.RedeemVoucherMessageEvent, RedeemVoucherMessageEvent.class);
        this.registerHandler(Incoming.GetRecyclerStatusMessageEvent, GetRecyclerStatusMessageEvent.class);
        this.registerHandler(Incoming.RecycleItemsMessageEvent, RecycleItemsMessageEvent.class);
        this.registerHandler(Incoming.PresentOpenMessageEvent, PresentOpenMessageEvent.class);
        this.registerHandler(Incoming.GetMarketplaceOwnOffersMessageEvent, GetMarketplaceOwnOffersMessageEvent.class);
        this.registerHandler(Incoming.CancelMarketplaceOfferMessageEvent, CancelMarketplaceOfferMessageEvent.class);
        this.registerHandler(Incoming.GetMarketplaceOffersMessageEvent, GetMarketplaceOffersMessageEvent.class);
        this.registerHandler(Incoming.GetMarketplaceItemStatsEvent, GetMarketplaceItemStatsEvent.class);
        this.registerHandler(Incoming.BuyMarketplaceOfferMessageEvent, BuyMarketplaceOfferMessageEvent.class);
        this.registerHandler(Incoming.GetMarketplaceCanMakeOfferEvent, GetMarketplaceCanMakeOfferEvent.class);
        this.registerHandler(Incoming.MakeOfferMessageEvent, MakeOfferMessageEvent.class);
        this.registerHandler(Incoming.RedeemMarketplaceOfferCreditsMessageEvent, RedeemMarketplaceOfferCreditsMessageEvent.class);
        this.registerHandler(Incoming.GetSellablePetPalettesEvent, GetSellablePetPalettesEvent.class);
        this.registerHandler(Incoming.ApproveNameMessageEvent, ApproveNameMessageEvent.class);
        this.registerHandler(Incoming.GetClubDataEvent, RequestClubDataEvent.class);
        this.registerHandler(Incoming.GetClubGiftInfoEvent, GetClubGiftInfoEvent.class);
        this.registerHandler(Incoming.GetProductOfferEvent, GetProductOfferEvent.class);
        this.registerHandler(Incoming.PurchaseTargetedOfferEvent, PurchaseTargetedOfferEvent.class);
        this.registerHandler(Incoming.SetTargetedOfferStateEvent, SetTargetedOfferStateEvent.class);
        this.registerHandler(Incoming.SelectClubGiftEvent, SelectClubGiftEvent.class);
        this.registerHandler(Incoming.ScrGetKickbackInfoMessageEvent, ScrGetKickbackInfoMessageEvent.class);
        this.registerHandler(Incoming.GetHabboClubExtendOfferMessageEvent, GetHabboClubExtendOfferMessageEvent.class);
        this.registerHandler(Incoming.PurchaseVipMembershipExtensionEvent, PurchaseVipMembershipExtensionEvent.class);
    }

    private void registerEvent() throws Exception {
        this.registerHandler(Incoming.OpenCampaignCalendarDoorAsStaffEvent, OpenCampaignCalendarDoorAsStaffEvent.class);
        this.registerHandler(Incoming.OpenCampaignCalendarDoorEvent, OpenCampaignCalendarDoorEvent.class);
    }

    private void registerHandshake() throws Exception {
        this.registerHandler(Incoming.ClientHelloMessageEvent, ClientHelloMessageEvent.class);
        this.registerHandler(Incoming.InitDiffieHandshake, InitDiffieHandshakeEvent.class);
        this.registerHandler(Incoming.CompleteDiffieHandshake, CompleteDiffieHandshakeEvent.class);
        this.registerHandler(Incoming.SSOTicketMessageEvent, SSOTicketMessageEvent.class);
        this.registerHandler(Incoming.UniqueIDMessageEvent, UniqueIDMessageEvent.class);
        this.registerHandler(Incoming.GetIgnoredUsersMessageEvent, GetIgnoredUsersMessageEvent.class);
        this.registerHandler(Incoming.LatencyPingRequestMessageEvent, LatencyPingRequestMessageEvent.class);
    }

    private void registerFriends() throws Exception {
        this.registerHandler(Incoming.GetMOTDMessageEvent, GetMOTDMessageEvent.class);
        this.registerHandler(Incoming.SetRelationshipStatusMessageEvent, SetRelationshipStatusMessageEvent.class);
        this.registerHandler(Incoming.FriendListUpdateMessageEvent, FriendListUpdateMessageEvent.class);
        this.registerHandler(Incoming.RemoveFriendMessageEvent, RemoveFriendMessageEvent.class);
        this.registerHandler(Incoming.HabboSearchMessageEvent, HabboSearchMessageEvent.class);
        this.registerHandler(Incoming.RequestFriendMessageEvent, RequestFriendMessageEvent.class);
        this.registerHandler(Incoming.AcceptFriendRequest, AcceptFriendRequestEvent.class);
        this.registerHandler(Incoming.DeclineFriendRequest, DeclineFriendRequestEvent.class);
        this.registerHandler(Incoming.SendMsgMessageEvent, SendMsgMessageEvent.class);
        this.registerHandler(Incoming.RequestFriendRequestEvent, RequestFriendRequestsEvent.class);
        this.registerHandler(Incoming.VisitUserMessageEvent, VisitUserMessageEvent.class);
        this.registerHandler(Incoming.MessengerInitMessageEvent, MessengerInitMessageEvent.class);
        this.registerHandler(Incoming.FindNewFriendsMessageEvent, FindNewFriendsMessageEvent.class);
        this.registerHandler(Incoming.SendRoomInviteMessageEvent, SendRoomInviteMessageEvent.class);
    }

    private void registerUsers() throws Exception {
        this.registerHandler(Incoming.InfoRetrieveMessageEvent, InfoRetrieveMessageEvent.class);
        this.registerHandler(Incoming.GetCreditsInfoEvent, GetCreditsInfoEvent.class);
        this.registerHandler(Incoming.ScrGetUserInfoMessageEvent, ScrGetUserInfoMessageEvent.class);
        this.registerHandler(Incoming.GetSoundSettingsEvent, GetSoundSettingsEvent.class);
        this.registerHandler(Incoming.GetTalentTrackLevelMessageEvent, GetTalentTrackLevelMessageEvent.class);
        this.registerHandler(Incoming.GetExtendedProfileMessageEvent, GetExtendedProfileMessageEvent.class);
        this.registerHandler(Incoming.GetRelationshipStatusInfoMessageEvent, GetRelationshipStatusInfoMessageEvent.class);
        this.registerHandler(Incoming.GetWardrobeMessageEvent, GetWardrobeMessageEvent.class);
        this.registerHandler(Incoming.SaveWardrobeOutfitMessageEvent, SaveWardrobeOutfitMessageEvent.class);
        this.registerHandler(Incoming.ChangeMottoMessageEvent, ChangeMottoMessageEvent.class);
        this.registerHandler(Incoming.UpdateFigureDataMessageEvent, UpdateFigureDataMessageEvent.class);
        this.registerHandler(Incoming.SetActivatedBadgesEvent, SetActivatedBadgesEvent.class);
        this.registerHandler(Incoming.GetSelectedBadgesMessageEvent, GetSelectedBadgesMessageEvent.class);
        this.registerHandler(Incoming.SetSoundSettingsEvent, SetSoundSettingsEvent.class);
        this.registerHandler(Incoming.SetRoomCameraPreferencesMessageEvent, SetRoomCameraPreferencesMessageEvent.class);
        this.registerHandler(Incoming.SetIgnoreRoomInvitesMessageEvent, SetIgnoreRoomInvitesMessageEvent.class);
        this.registerHandler(Incoming.SetChatPreferencesMessageEvent, SetChatPreferencesMessageEvent.class);
        this.registerHandler(Incoming.AvatarEffectActivatedEvent, AvatarEffectActivatedEvent.class);
        this.registerHandler(Incoming.AvatarEffectSelectedEvent, AvatarEffectSelectedEvent.class);
        this.registerHandler(Incoming.EventLogMessageEvent, EventLogMessageEvent.class);
        this.registerHandler(Incoming.NewUserExperienceScriptProceedEvent, NewUserExperienceScriptProceedEvent.class);
        this.registerHandler(Incoming.NewUserExperienceGetGiftsMessageEvent, NewUserExperienceGetGiftsMessageEvent.class);
        this.registerHandler(Incoming.CheckUserNameMessageEvent, CheckUserNameMessageEvent.class);
        this.registerHandler(Incoming.ChangeUserNameMessageEvent, ChangeUserNameMessageEvent.class);
        this.registerHandler(Incoming.SetChatStylePreferenceEvent, SetChatStylePreferenceEvent.class);
        this.registerHandler(Incoming.SetUIFlagsMessageEvent, SetUIFlagsMessageEvent.class);
    }

    private void registerNavigator() throws Exception {
        this.registerHandler(Incoming.GetUserFlatCatsMessageEvent, GetUserFlatCatsMessageEvent.class);
        this.registerHandler(Incoming.GetOfficialRoomsMessageEvent, GetOfficialRoomsMessageEvent.class);
        this.registerHandler(Incoming.MyRecommendedRoomsMessageEvent, MyRecommendedRoomsMessageEvent.class);
        this.registerHandler(Incoming.PopularRoomsSearchMessageEvent, PopularRoomsSearchMessageEvent.class);
        this.registerHandler(Incoming.RoomsWithHighestScoreSearchMessageEvent, RoomsWithHighestScoreSearchMessageEvent.class);
        this.registerHandler(Incoming.MyRoomsSearchMessageEvent, MyRoomsSearchMessageEvent.class);
        this.registerHandler(Incoming.CanCreateRoomMessageEvent, CanCreateRoomMessageEvent.class);
        this.registerHandler(Incoming.GetUnreadForumsCountMessageEvent, GetUnreadForumsCountMessageEvent.class);
        this.registerHandler(Incoming.CreateFlatMessageEvent, CreateFlatMessageEvent.class);
        this.registerHandler(Incoming.GetPopularRoomTagsMessageEvent, GetPopularRoomTagsMessageEvent.class);
        this.registerHandler(Incoming.SearchRoomsByTagEvent, SearchRoomsByTagEvent.class);
        this.registerHandler(Incoming.RoomTextSearchMessageEvent, RoomTextSearchMessageEvent.class);
        this.registerHandler(Incoming.RoomsWhereMyFriendsAreSearchMessageEvent, RoomsWhereMyFriendsAreSearchMessageEvent.class);
        this.registerHandler(Incoming.MyFriendsRoomsSearchMessageEvent, MyFriendsRoomsSearchMessageEvent.class);
        this.registerHandler(Incoming.MyRoomRightsSearchMessageEvent, MyRoomRightsSearchMessageEvent.class);
        this.registerHandler(Incoming.MyGuildBasesSearchMessageEvent, MyGuildBasesSearchMessageEvent.class);
        this.registerHandler(Incoming.SearchRoomsMyFavoriteEvent, SearchRoomsMyFavouriteEvent.class);
        this.registerHandler(Incoming.MyRoomHistorySearchMessageEvent, MyRoomHistorySearchMessageEvent.class);
        this.registerHandler(Incoming.NewNavigatorInitEvent, NewNavigatorInitEvent.class);
        this.registerHandler(Incoming.NewNavigatorSearchEvent, NewNavigatorSearchEvent.class);
        this.registerHandler(Incoming.ForwardToSomeRoomMessageEvent, ForwardToSomeRoomMessageEvent.class);
        this.registerHandler(Incoming.GetUserEventCatsMessageEvent, GetUserEventCatsMessageEvent.class);
        this.registerHandler(Incoming.SetNewNavigatorWindowPreferencesMessageEvent, SetNewNavigatorWindowPreferencesMessageEvent.class);
        this.registerHandler(Incoming.DeleteRoomMessageEvent, DeleteRoomMessageEvent.class);
        this.registerHandler(Incoming.NavigatorSetSearchCodeViewModeMessageEvent, NavigatorSetSearchCodeViewModeMessageEvent.class);
        this.registerHandler(Incoming.NavigatorAddCollapsedCategoryMessageEvent, NavigatorAddCollapsedCategoryMessageEvent.class);
        this.registerHandler(Incoming.NavigatorRemoveCollapsedCategoryMessageEvent, NavigatorRemoveCollapsedCategoryMessageEvent.class);
        this.registerHandler(Incoming.NavigatorAddSavedSearchEvent, NavigatorAddSavedSearchEvent.class);
        this.registerHandler(Incoming.NavigatorDeleteSavedSearchEvent, NavigatorDeleteSavedSearchEvent.class);
    }

    private void registerHotelview() throws Exception {
        this.registerHandler(Incoming.QuitMessageEvent, QuitMessageEvent.class);
        this.registerHandler(Incoming.GetBonusRareInfoMessageEvent, GetBonusRareInfoMessageEvent.class);
        this.registerHandler(Incoming.GetPromoArticlesEvent, GetPromoArticlesEvent.class);
        this.registerHandler(Incoming.GetCurrentTimingCodeMessageEvent, GetCurrentTimingCodeMessageEvent.class);
        this.registerHandler(Incoming.HotelViewRequestBadgeRewardEvent, HotelViewRequestBadgeRewardEvent.class);
        this.registerHandler(Incoming.HotelViewClaimBadgeRewardEvent, HotelViewClaimBadgeRewardEvent.class);
        this.registerHandler(Incoming.GetLimitedOfferAppearingNextEvent, GetLimitedOfferAppearingNextEvent.class);
        this.registerHandler(Incoming.GetSecondsUntilMessageEvent, GetSecondsUntilMessageEvent.class);
    }

    private void registerInventory() throws Exception {
        this.registerHandler(Incoming.GetBadgesEvent, GetBadgesEvent.class);
        this.registerHandler(Incoming.GetBotInventoryEvent, GetBotInventoryEvent.class);
        this.registerHandler(Incoming.RequestFurniInventoryEvent, RequestFurniInventoryEvent.class);
        this.registerHandler(Incoming.HotelViewInventoryEvent, RequestFurniInventoryEvent.class);
        this.registerHandler(Incoming.GetPetInventoryEvent, GetPetInventoryEvent.class);
    }

    void registerRooms() throws Exception {
        this.registerHandler(Incoming.OpenFlatConnectionMessageEvent, OpenFlatConnectionMessageEvent.class);
        this.registerHandler(Incoming.GetFurnitureAliasesMessageEvent, GetRoomEntryDataMessageEvent.class);
        this.registerHandler(Incoming.GetRoomEntryDataMessageEvent, GetRoomEntryDataMessageEvent.class);
        this.registerHandler(Incoming.RateFlatMessageEvent, RateFlatMessageEvent.class);
        this.registerHandler(Incoming.GetGuestRoomMessageEvent, GetGuestRoomMessageEvent.class);
        this.registerHandler(Incoming.SaveRoomSettingsMessageEvent, SaveRoomSettingsMessageEvent.class);
        this.registerHandler(Incoming.PlaceObjectMessageEvent, PlaceObjectMessageEvent.class);
        this.registerHandler(Incoming.MoveObjectMessageEvent, MoveObjectMessageEvent.class);
        this.registerHandler(Incoming.MoveWallItemMessageEvent, MoveWallItemMessageEvent.class);
        this.registerHandler(Incoming.PickupObjectMessageEvent, PickupObjectMessageEvent.class);
        this.registerHandler(Incoming.RequestRoomPropertySetEvent, RequestRoomPropertySetEvent.class);
        this.registerHandler(Incoming.StartTypingMessageEvent, StartTypingMessageEvent.class);
        this.registerHandler(Incoming.CancelTypingMessageEvent, CancelTypingMessageEvent.class);
        this.registerHandler(Incoming.UseFurnitureMessageEvent, UseFurnitureMessageEvent.class);
        this.registerHandler(Incoming.ClickFurniMessageEvent, ClickFurniMessageEvent.class);
        this.registerHandler(Incoming.ToggleAreaHideMessageEvent, ToggleAreaHideMessageEvent.class);
        this.registerHandler(Incoming.SaveAreaHideMessageEvent, SaveAreaHideMessageEvent.class);
        this.registerHandler(Incoming.UseWallItemMessageEvent, UseWallItemMessageEvent.class);
        this.registerHandler(Incoming.SetRoomBackgroundColorDataEvent, SetRoomBackgroundColorDataEvent.class);
        this.registerHandler(Incoming.SetMannequinNameEvent, SetMannequinNameEvent.class);
        this.registerHandler(Incoming.SetMannequinFigureEvent, SetMannequinFigureEvent.class);
        this.registerHandler(Incoming.SetClothingChangeDataMessageEvent, SetClothingChangeDataMessageEvent.class);
        this.registerHandler(Incoming.SetObjectDataMessageEvent, SetObjectDataMessageEvent.class);
        this.registerHandler(Incoming.GetRoomSettingsMessageEvent, GetRoomSettingsMessageEvent.class);
        this.registerHandler(Incoming.RoomDimmerGetPresetsMessageEvent, RoomDimmerGetPresetsMessageEvent.class);
        this.registerHandler(Incoming.RoomDimmerChangeStateMessageEvent, RoomDimmerChangeStateMessageEvent.class);
        this.registerHandler(Incoming.DropCarryItemMessageEvent, DropCarryItemMessageEvent.class);
        this.registerHandler(Incoming.LookToMessageEvent, LookToMessageEvent.class);
        this.registerHandler(Incoming.ChatMessageEvent, ChatMessageEvent.class);
        this.registerHandler(Incoming.ShoutMessageEvent, ShoutMessageEvent.class);
        this.registerHandler(Incoming.WhisperMessageEvent, WhisperMessageEvent.class);
        this.registerHandler(Incoming.AvatarExpressionMessageEvent, AvatarExpressionMessageEvent.class);
        this.registerHandler(Incoming.ChangePostureMessageEvent, ChangePostureMessageEvent.class);
        this.registerHandler(Incoming.DanceMessageEvent, DanceMessageEvent.class);
        this.registerHandler(Incoming.SignMessageEvent, SignMessageEvent.class);
        this.registerHandler(Incoming.MoveAvatarMessageEvent, MoveAvatarMessageEvent.class);
        this.registerHandler(Incoming.RespectUserMessageEvent, RespectUserMessageEvent.class);
        this.registerHandler(Incoming.AssignRightsMessageEvent, AssignRightsMessageEvent.class);
        this.registerHandler(Incoming.RemoveOwnRoomRightsRoomMessageEvent, RemoveOwnRoomRightsRoomMessageEvent.class);
        this.registerHandler(Incoming.GetFlatControllersMessageEvent, GetFlatControllersMessageEvent.class);
        this.registerHandler(Incoming.RemoveAllRightsMessageEvent, RemoveAllRightsMessageEvent.class);
        this.registerHandler(Incoming.RemoveRightsMessageEvent, RemoveRightsMessageEvent.class);
        this.registerHandler(Incoming.PlaceBotMessageEvent, PlaceBotMessageEvent.class);
        this.registerHandler(Incoming.RemoveBotFromFlatMessageEvent, RemoveBotFromFlatMessageEvent.class);
        this.registerHandler(Incoming.CommandBotEvent, CommandBotEvent.class);
        this.registerHandler(Incoming.GetBotCommandConfigurationDataEvent, GetBotCommandConfigurationDataEvent.class);
        this.registerHandler(Incoming.ThrowDiceMessageEvent, ThrowDiceMessageEvent.class);
        this.registerHandler(Incoming.DiceOffMessageEvent, DiceOffMessageEvent.class);
        this.registerHandler(Incoming.SpinWheelOfFortuneMessageEvent, SpinWheelOfFortuneMessageEvent.class);
        this.registerHandler(Incoming.CreditFurniRedeemMessageEvent, CreditFurniRedeemMessageEvent.class);
        this.registerHandler(Incoming.PlacePetMessageEvent, PlacePetMessageEvent.class);
        this.registerHandler(Incoming.RoomUserKickMessageEvent, RoomUserKickMessageEvent.class);
        this.registerHandler(Incoming.SetCustomStackingHeightEvent, SetCustomStackingHeightEvent.class);
        this.registerHandler(Incoming.EnterOneWayDoorMessageEvent, EnterOneWayDoorMessageEvent.class);
        this.registerHandler(Incoming.LetUserInMessageEvent, LetUserInMessageEvent.class);
        this.registerHandler(Incoming.CustomizeAvatarWithFurniMessageEvent, CustomizeAvatarWithFurniMessageEvent.class);
        this.registerHandler(Incoming.PlacePostItMessageEvent, PlacePostItMessageEvent.class);
        this.registerHandler(Incoming.GetItemDataMessageEvent, GetItemDataMessageEvent.class);
        this.registerHandler(Incoming.SetItemDataMessageEvent, SetItemDataMessageEvent.class);
        this.registerHandler(Incoming.RemoveItemMessageEvent, RemoveItemMessageEvent.class);
        this.registerHandler(Incoming.RoomDimmerSavePresetMessageEvent, RoomDimmerSavePresetMessageEvent.class);
        this.registerHandler(Incoming.RentableSpaceRentMessageEvent, RentableSpaceRentMessageEvent.class);
        this.registerHandler(Incoming.RentableSpaceCancelRentMessageEvent, RentableSpaceCancelRentMessageEvent.class);
        this.registerHandler(Incoming.UpdateHomeRoomMessageEvent, UpdateHomeRoomMessageEvent.class);
        this.registerHandler(Incoming.PassCarryItemMessageEvent, PassCarryItemMessageEvent.class);
        this.registerHandler(Incoming.MuteAllInRoomEvent, MuteAllInRoomEvent.class);
        this.registerHandler(Incoming.GetCustomRoomFilterMessageEvent, GetCustomRoomFilterMessageEvent.class);
        this.registerHandler(Incoming.UpdateRoomFilterMessageEvent, UpdateRoomFilterMessageEvent.class);
        this.registerHandler(Incoming.ToggleStaffPickMessageEvent, ToggleStaffPickMessageEvent.class);
        this.registerHandler(Incoming.TogglePublicRoomMessageEvent, TogglePublicRoomMessageEvent.class);
        this.registerHandler(Incoming.TryBusMessageEvent, TryBusMessageEvent.class);
        this.registerHandler(Incoming.ChangeWorldMessageEvent, ChangeWorldMessageEvent.class);
        this.registerHandler(Incoming.GameBoardMoveMessageEvent, GameBoardMoveMessageEvent.class);
        this.registerHandler(Incoming.RequestLeaderboardMessageEvent, RequestLeaderboardMessageEvent.class);
        this.registerHandler(Incoming.RoomRequestBannedUsersEvent, RoomRequestBannedUsersEvent.class);
        this.registerHandler(Incoming.GetOfficialSongIdMessageEvent, GetOfficialSongIdMessageEvent.class);
        this.registerHandler(Incoming.GetSongInfoMessageEvent, GetSongInfoMessageEvent.class);
        this.registerHandler(Incoming.AddJukeboxDiskEvent, AddJukeboxDiskEvent.class);
        this.registerHandler(Incoming.RemoveJukeboxDiskEvent, RemoveJukeboxDiskEvent.class);
        this.registerHandler(Incoming.GetNowPlayingMessageEvent, GetNowPlayingMessageEvent.class);
        this.registerHandler(Incoming.GetUserSongDisksMessageEvent, GetUserSongDisksMessageEvent.class);
        this.registerHandler(Incoming.GetJukeboxPlayListMessageEvent, GetJukeboxPlayListMessageEvent.class);
        this.registerHandler(Incoming.GetSoundMachinePlayListMessageEvent, GetSoundMachinePlayListMessageEvent.class);
        this.registerHandler(Incoming.AddSpamWallPostItMessageEvent, AddSpamWallPostItMessageEvent.class);
        this.registerHandler(Incoming.GetRoomAdPurchaseInfoEvent, GetRoomAdPurchaseInfoEvent.class);
        this.registerHandler(Incoming.PurchaseRoomAdMessageEvent, PurchaseRoomAdMessageEvent.class);
        this.registerHandler(Incoming.EditRoomPromotionMessageEvent, UpdateRoomPromotionEvent.class);
        this.registerHandler(Incoming.IgnoreUserMessageEvent, IgnoreUserMessageEvent.class);
        this.registerHandler(Incoming.UnignoreUserMessageEvent, UnignoreUserMessageEvent.class);
        this.registerHandler(Incoming.RoomUserMuteMessageEvent, RoomUserMuteMessageEvent.class);
        this.registerHandler(Incoming.BanUserWithDurationMessageEvent, BanUserWithDurationMessageEvent.class);
        this.registerHandler(Incoming.UnbanUserFromRoomMessageEvent, UnbanUserFromRoomMessageEvent.class);
        this.registerHandler(Incoming.GetUserTagsMessageEvent, GetUserTagsMessageEvent.class);
        this.registerHandler(Incoming.GetYoutubeDisplayStatusMessageEvent, GetYoutubeDisplayStatusMessageEvent.class);
        this.registerHandler(Incoming.ControlYoutubeDisplayPlaybackMessageEvent, ControlYoutubeDisplayPlaybackMessageEvent.class);
        this.registerHandler(Incoming.SetYoutubeDisplayPlaylistMessageEvent, SetYoutubeDisplayPlaylistMessageEvent.class);
        this.registerHandler(Incoming.AddFavouriteRoomMessageEvent, AddFavouriteRoomMessageEvent.class);
        this.registerHandler(Incoming.FriendFurniConfirmLockMessageEvent, FriendFurniConfirmLockMessageEvent.class);
        this.registerHandler(Incoming.DeleteFavouriteRoomMessageEvent, DeleteFavouriteRoomMessageEvent.class);
        this.registerHandler(Incoming.SetRandomStateMessageEvent, SetRandomStateMessageEvent.class);
    }

    void registerPolls() throws Exception {
        this.registerHandler(Incoming.CancelPollEvent, CancelPollEvent.class);
        this.registerHandler(Incoming.GetPollDataEvent, GetPollDataEvent.class);
        this.registerHandler(Incoming.AnswerPollEvent, AnswerPollEvent.class);
        this.registerHandler(Incoming.VotePollCounterEvent, VotePollCounterEvent.class);
    }

    void registerModTool() throws Exception {
        this.registerHandler(Incoming.GetModeratorRoomInfoMessageEvent, GetModeratorRoomInfoMessageEvent.class);
        this.registerHandler(Incoming.GetRoomChatlogMessageEvent, GetRoomChatlogMessageEvent.class);
        this.registerHandler(Incoming.GetModeratorUserInfoMessageEvent, GetModeratorUserInfoMessageEvent.class);
        this.registerHandler(Incoming.PickIssuesMessageEvent, PickIssuesMessageEvent.class);
        this.registerHandler(Incoming.CloseIssuesMessageEvent, CloseIssuesMessageEvent.class);
        this.registerHandler(Incoming.ReleaseIssuesMessageEvent, ReleaseIssuesMessageEvent.class);
        this.registerHandler(Incoming.ModMessageMessageEvent, ModMessageMessageEvent.class);
        this.registerHandler(Incoming.ModToolWarnEvent, ModToolWarnEvent.class);
        this.registerHandler(Incoming.ModKickMessageEvent, ModKickMessageEvent.class);
        this.registerHandler(Incoming.ModeratorActionMessageEvent, ModeratorActionMessageEvent.class);
        this.registerHandler(Incoming.ModerateRoomMessageEvent, ModerateRoomMessageEvent.class);
        this.registerHandler(Incoming.GetRoomVisitsMessageEvent, GetRoomVisitsMessageEvent.class);
        this.registerHandler(Incoming.GetCfhChatlogMessageEvent, GetCfhChatlogMessageEvent.class);
        this.registerHandler(Incoming.ModToolRequestRoomUserChatlogEvent, ModToolRequestRoomUserChatlogEvent.class);
        this.registerHandler(Incoming.GetUserChatlogMessageEvent, GetUserChatlogMessageEvent.class);
        this.registerHandler(Incoming.ModAlertMessageEvent, ModAlertMessageEvent.class);
        this.registerHandler(Incoming.ModMuteMessageEvent, ModMuteMessageEvent.class);
        this.registerHandler(Incoming.ModBanMessageEvent, ModBanMessageEvent.class);
        this.registerHandler(Incoming.ModTradingLockMessageEvent, ModTradingLockMessageEvent.class);
        this.registerHandler(Incoming.ModToolSanctionEvent, ModToolSanctionEvent.class);
        this.registerHandler(Incoming.CloseIssueDefaultActionMessageEvent, CloseIssueDefaultActionMessageEvent.class);

        this.registerHandler(Incoming.GetPendingCallsForHelpMessageEvent, GetPendingCallsForHelpMessageEvent.class);
        this.registerHandler(Incoming.GetGuideReportingStatusMessageEvent, GetGuideReportingStatusMessageEvent.class);
        this.registerHandler(Incoming.ChatReviewSessionCreateMessageEvent, ChatReviewSessionCreateMessageEvent.class);
        this.registerHandler(Incoming.CallForHelpMessageEvent, CallForHelpMessageEvent.class);
        this.registerHandler(Incoming.CallForHelpFromIMMessageEvent, CallForHelpFromIMMessageEvent.class);
        this.registerHandler(Incoming.CallForHelpFromForumThreadMessageEvent, CallForHelpFromForumThreadMessageEvent.class);
        this.registerHandler(Incoming.CallForHelpFromForumMessageMessageEvent, CallForHelpFromForumMessageMessageEvent.class);
        this.registerHandler(Incoming.CallForHelpFromPhotoMessageEvent, CallForHelpFromPhotoMessageEvent.class);
    }

    void registerTrading() throws Exception {
        this.registerHandler(Incoming.OpenTradingEvent, OpenTradingEvent.class);
        this.registerHandler(Incoming.AddItemToTradeEvent, AddItemToTradeEvent.class);
        this.registerHandler(Incoming.AddItemsToTradeEvent, AddItemsToTradeEvent.class);
        this.registerHandler(Incoming.RemoveItemFromTradeEvent, RemoveItemFromTradeEvent.class);
        this.registerHandler(Incoming.AcceptTradingEvent, AcceptTradingEvent.class);
        this.registerHandler(Incoming.UnacceptTradingEvent, UnacceptTradingEvent.class);
        this.registerHandler(Incoming.ConfirmAcceptTradingEvent, ConfirmAcceptTradingEvent.class);
        this.registerHandler(Incoming.CloseTradingEvent, CloseTradingEvent.class);
        this.registerHandler(Incoming.ConfirmDeclineTradingEvent, ConfirmDeclineTradingEvent.class);
    }

    void registerGuilds() throws Exception {
        this.registerHandler(Incoming.GetGuildCreationInfoMessageEvent, GetGuildCreationInfoMessageEvent.class);
        this.registerHandler(Incoming.GetGuildEditorDataMessageEvent, GetGuildEditorDataMessageEvent.class);
        this.registerHandler(Incoming.CreateGuildMessageEvent, CreateGuildMessageEvent.class);
        this.registerHandler(Incoming.GetHabboGroupDetailsMessageEvent, GetHabboGroupDetailsMessageEvent.class);
        this.registerHandler(Incoming.GetGuildEditInfoMessageEvent, GetGuildEditInfoMessageEvent.class);
        this.registerHandler(Incoming.GetGuildMembersMessageEvent, GetGuildMembersMessageEvent.class);
        this.registerHandler(Incoming.JoinHabboGroupMessageEvent, JoinHabboGroupMessageEvent.class);
        this.registerHandler(Incoming.UpdateGuildIdentityMessageEvent, UpdateGuildIdentityMessageEvent.class);
        this.registerHandler(Incoming.UpdateGuildBadgeMessageEvent, UpdateGuildBadgeMessageEvent.class);
        this.registerHandler(Incoming.UpdateGuildColorsMessageEvent, UpdateGuildColorsMessageEvent.class);
        this.registerHandler(Incoming.RemoveAdminRightsFromMemberMessageEvent, RemoveAdminRightsFromMemberMessageEvent.class);
        this.registerHandler(Incoming.KickMemberMessageEvent, KickMemberMessageEvent.class);
        this.registerHandler(Incoming.UpdateGuildSettingsMessageEvent, UpdateGuildSettingsMessageEvent.class);
        this.registerHandler(Incoming.ApproveMembershipRequestMessageEvent, ApproveMembershipRequestMessageEvent.class);
        this.registerHandler(Incoming.RejectMembershipRequestMessageEvent, RejectMembershipRequestMessageEvent.class);
        this.registerHandler(Incoming.AddAdminRightsToMemberMessageEvent, AddAdminRightsToMemberMessageEvent.class);
        this.registerHandler(Incoming.SelectFavouriteHabboGroupMessageEvent, SelectFavouriteHabboGroupMessageEvent.class);
        this.registerHandler(Incoming.GetGuildMembershipsMessageEvent, GetGuildMembershipsMessageEvent.class);
        this.registerHandler(Incoming.GetGuildFurniContextMenuInfoMessageEvent, GetGuildFurniContextMenuInfoMessageEvent.class);
        this.registerHandler(Incoming.GetMemberGuildItemCountMessageEvent, GetMemberGuildItemCountMessageEvent.class);
        this.registerHandler(Incoming.DeselectFavouriteHabboGroupMessageEvent, DeselectFavouriteHabboGroupMessageEvent.class);
        this.registerHandler(Incoming.DeactivateGuildMessageEvent, DeactivateGuildMessageEvent.class);
        this.registerHandler(Incoming.GetForumsListMessageEvent, GetForumsListMessageEvent.class);
        this.registerHandler(Incoming.GetThreadsMessageEvent, GetThreadsMessageEvent.class);
        this.registerHandler(Incoming.GetForumStatsMessageEvent, GetForumStatsMessageEvent.class);
        this.registerHandler(Incoming.PostMessageMessageEvent, PostMessageMessageEvent.class);
        this.registerHandler(Incoming.UpdateForumSettingsMessageEvent, UpdateForumSettingsMessageEvent.class);
        this.registerHandler(Incoming.GetMessagesMessageEvent, GetMessagesMessageEvent.class);
        this.registerHandler(Incoming.ModerateMessageMessageEvent, ModerateMessageMessageEvent.class);
        this.registerHandler(Incoming.ModerateThreadMessageEvent, ModerateThreadMessageEvent.class);
        this.registerHandler(Incoming.UpdateThreadMessageEvent, UpdateThreadMessageEvent.class);
        this.registerHandler(Incoming.GuildForumMarkAsReadEvent, GuildForumMarkAsReadEvent.class);
        this.registerHandler(Incoming.GetHabboGroupBadgesMessageEvent, GetHabboGroupBadgesMessageEvent.class);

//        this.registerHandler(Incoming.GetForumStatsMessageEvent,              ModerateMessageMessageEvent.class);
//        this.registerHandler(Incoming.GetForumStatsMessageEvent,              ModerateThreadMessageEvent.class);
//        this.registerHandler(Incoming.GetForumStatsMessageEvent,              PostMessageMessageEvent.class);
//        this.registerHandler(Incoming.GetForumStatsMessageEvent,              GetThreadsMessageEvent.class);
//        this.registerHandler(Incoming.GetForumStatsMessageEvent,              GetMessagesMessageEvent.class);
//        this.registerHandler(Incoming.GetForumStatsMessageEvent,              UpdateForumSettingsMessageEvent.class);
    }

    void registerPets() throws Exception {
        this.registerHandler(Incoming.GetPetInfoMessageEvent, GetPetInfoMessageEvent.class);
        this.registerHandler(Incoming.RemovePetFromFlatMessageEvent, RemovePetFromFlatMessageEvent.class);
        this.registerHandler(Incoming.RespectPetMessageEvent, RespectPetMessageEvent.class);
        this.registerHandler(Incoming.GetPetCommandsMessageEvent, GetPetCommandsMessageEvent.class);
        this.registerHandler(Incoming.CustomizePetWithFurniEvent, CustomizePetWithFurniEvent.class);
        this.registerHandler(Incoming.HorseRideSettingsEvent, PetRideSettingsEvent.class);
        this.registerHandler(Incoming.HorseRideEvent, PetRideEvent.class);
        this.registerHandler(Incoming.RemoveSaddleFromPetMessageEvent, RemoveSaddleFromPetMessageEvent.class);
        this.registerHandler(Incoming.TogglePetBreedingPermissionMessageEvent, TogglePetBreedingPermissionMessageEvent.class);
        this.registerHandler(Incoming.CompostPlantMessageEvent, CompostPlantMessageEvent.class);
        this.registerHandler(Incoming.BreedPetsMessageEvent, BreedPetsMessageEvent.class);
        this.registerHandler(Incoming.MovePetMessageEvent, MovePetMessageEvent.class);
        this.registerHandler(Incoming.OpenPetPackageMessageEvent, OpenPetPackageMessageEvent.class);
        this.registerHandler(Incoming.CancelPetBreedingEvent, CancelPetBreedingEvent.class);
        this.registerHandler(Incoming.ConfirmPetBreedingEvent, ConfirmPetBreedingEvent.class);
    }

    void registerWired() throws Exception {
        this.registerHandler(Incoming.RequestWiredCapabilitiesMessageEvent, RequestWiredCapabilitiesMessageEvent.class);
        this.registerHandler(Incoming.RequestAllVariablesHashMessageEvent, RequestAllVariablesHashMessageEvent.class);
        this.registerHandler(Incoming.RequestVariablesDiffMessageEvent, RequestVariablesDiffMessageEvent.class);
        this.registerHandler(Incoming.UpdateTriggerMessageEvent, UpdateTriggerMessageEvent.class);
        this.registerHandler(Incoming.UpdateActionMessageEvent, UpdateActionMessageEvent.class);
        this.registerHandler(Incoming.UpdateConditionMessageEvent, UpdateConditionMessageEvent.class);
        this.registerHandler(Incoming.UpdateSelectorMessageEvent, UpdateSelectorMessageEvent.class);
        this.registerHandler(Incoming.UpdateAddonMessageEvent, UpdateAddonMessageEvent.class);
        this.registerHandler(Incoming.UpdateVariableMessageEvent, UpdateVariableMessageEvent.class);
        this.registerHandler(Incoming.ApplySnapshotMessageEvent, ApplySnapshotMessageEvent.class);
        this.registerHandler(Incoming.OpenAckMessageEvent, OpenAckMessageEvent.class);
        this.registerHandler(Incoming.WiredClickUserMessageEvent, WiredClickUserMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestVariableHoldersMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuClearErrorsMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuUpdatePreferencesMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestRoomStatsMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuUpdateRoomSettingsMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuInspectObjectMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestErrorsMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuReloadOrRollbackMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestRoomSettingsMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuModifyVariableMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestLogsMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestUserVariablesMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuMutatePermanentVariableMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.WiredMenuRequestPermanentVariablesMessageEvent, WiredMenuMessageEvent.class);
        this.registerHandler(Incoming.ChestOpenMessageEvent, ChestOpenMessageEvent.class);
        this.registerHandler(Incoming.ChestSetRoomLocksMessageEvent, ChestSetRoomLocksMessageEvent.class);
        this.registerHandler(Incoming.ChestSaveSettingsMessageEvent, ChestSaveSettingsMessageEvent.class);
        this.registerHandler(Incoming.ChestUpgradeMessageEvent, ChestUpgradeMessageEvent.class);
        this.registerHandler(Incoming.ChestSaveNotificationsMessageEvent, ChestSaveNotificationsMessageEvent.class);
        this.registerHandler(Incoming.ChestSaveSafetyMessageEvent, ChestSaveSafetyMessageEvent.class);
        this.registerHandler(Incoming.ChestWithdrawFurniMessageEvent, ChestWithdrawFurniMessageEvent.class);
        this.registerHandler(Incoming.ChestWithdrawAllMessageEvent, ChestWithdrawAllMessageEvent.class);
        this.registerHandler(Incoming.ChestWithdrawCoinsMessageEvent, ChestWithdrawCoinsMessageEvent.class);
        this.registerHandler(Incoming.ChestCloseMessageEvent, ChestCloseMessageEvent.class);
        this.registerHandler(Incoming.ChestStartDepositMessageEvent, ChestStartDepositMessageEvent.class);
        this.registerHandler(Incoming.WiredTradeCancelMessageEvent, WiredTradeCancelMessageEvent.class);
        this.registerHandler(Incoming.WiredTradeAcceptMessageEvent, WiredTradeAcceptMessageEvent.class);
        this.registerHandler(Incoming.WiredTradeItemsMessageEvent, WiredTradeItemsMessageEvent.class);
        this.registerHandler(Incoming.ChestContractContentsMessageEvent, ChestContractContentsMessageEvent.class);
        this.registerHandler(Incoming.ChestContractSaveMessageEvent, ChestContractSaveMessageEvent.class);
        this.registerHandler(Incoming.ChestTransactionLogsMessageEvent, ChestTransactionLogMessageEvent.class);
        this.registerHandler(Incoming.ChestRoomTransactionLogsMessageEvent, ChestTransactionLogMessageEvent.class);
        this.registerHandler(Incoming.ChestTransactionDetailsMessageEvent, ChestTransactionLogMessageEvent.class);
    }

    void registerUnknown() throws Exception {
        // Accepted and discarded: the client sends these regardless of what this server implements.
        this.registerHandler(Incoming.LatencyPingReportMessageEvent, LatencyPingReportMessageEvent.class);
        this.registerHandler(Incoming.SnowStormGameUnloadedMessageEvent, SnowStormGameUnloadedMessageEvent.class);
        this.registerHandler(Incoming.GetResolutionAchievementsMessageEvent, GetResolutionAchievementsMessageEvent.class);
        this.registerHandler(Incoming.RequestTalenTrackEvent, RequestTalentTrackEvent.class);
        this.registerHandler(Incoming.GetBadgePointLimitsEvent, GetBadgePointLimitsEvent.class);
        this.registerHandler(Incoming.GetCfhStatusMessageEvent, GetCfhStatusMessageEvent.class);
    }

    void registerFloorPlanEditor() throws Exception {
        this.registerHandler(Incoming.UpdateFloorPropertiesMessageEvent, UpdateFloorPropertiesMessageEvent.class);
        this.registerHandler(Incoming.GetOccupiedTilesMessageEvent, GetOccupiedTilesMessageEvent.class);
        this.registerHandler(Incoming.GetRoomEntryTileMessageEvent, GetRoomEntryTileMessageEvent.class);
    }

    void registerAchievements() throws Exception {
        this.registerHandler(Incoming.GetAchievementsEvent, GetAchievementsEvent.class);
        this.registerHandler(Incoming.RequestAchievementConfigurationEvent, RequestAchievementConfigurationEvent.class);
    }

    void registerGuides() throws Exception {
        this.registerHandler(Incoming.GuideSessionOnDutyUpdateMessageEvent, GuideSessionOnDutyUpdateMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionCreateMessageEvent, GuideSessionCreateMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionIsTypingMessageEvent, GuideSessionIsTypingMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionReportMessageEvent, GuideSessionReportMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionFeedbackMessageEvent, GuideSessionFeedbackMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionMessageMessageEvent, GuideSessionMessageMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionRequesterCancelsMessageEvent, GuideSessionRequesterCancelsMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionGuideDecidesMessageEvent, GuideSessionGuideDecidesMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionInviteRequesterMessageEvent, GuideSessionInviteRequesterMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionGetRequesterRoomMessageEvent, GuideSessionGetRequesterRoomMessageEvent.class);
        this.registerHandler(Incoming.GuideSessionResolvedMessageEvent, GuideSessionResolvedMessageEvent.class);

        this.registerHandler(Incoming.ChatReviewGuideDetachedMessageEvent, ChatReviewGuideDetachedMessageEvent.class);
        this.registerHandler(Incoming.ChatReviewGuideDecidesOnOfferMessageEvent, ChatReviewGuideDecidesOnOfferMessageEvent.class);
        this.registerHandler(Incoming.ChatReviewGuideVoteMessageEvent, ChatReviewGuideVoteMessageEvent.class);
    }

    void registerCrafting() throws Exception {
        this.registerHandler(Incoming.GetCraftingRecipeEvent, GetCraftingRecipeEvent.class);
        this.registerHandler(Incoming.GetCraftableProductsEvent, GetCraftableProductsEvent.class);
        this.registerHandler(Incoming.CraftEvent, CraftEvent.class);
        this.registerHandler(Incoming.CraftSecretEvent, CraftSecretEvent.class);
        this.registerHandler(Incoming.GetCraftingRecipesAvailableEvent, GetCraftingRecipesAvailableEvent.class);
    }

    void registerCamera() throws Exception {
        this.registerHandler(Incoming.RenderRoomMessageEvent, RenderRoomMessageEvent.class);
        this.registerHandler(Incoming.RequestCameraConfigurationEvent, RequestCameraConfigurationEvent.class);
        this.registerHandler(Incoming.PurchasePhotoMessageEvent, PurchasePhotoMessageEvent.class);
        this.registerHandler(Incoming.RenderRoomThumbnailMessageEvent, RenderRoomThumbnailMessageEvent.class);
        this.registerHandler(Incoming.PublishPhotoMessageEvent, PublishPhotoMessageEvent.class);
    }

    void registerGameCenter() throws Exception {
        this.registerHandler(Incoming.GetGameListMessageEvent, GetGameListMessageEvent.class);
        this.registerHandler(Incoming.GetGameAchievementsMessageEvent, GetGameAchievementsMessageEvent.class);
        this.registerHandler(Incoming.GetGameStatusMessageEvent, GetGameStatusMessageEvent.class);
        this.registerHandler(Incoming.GetUserGameAchievementsMessageEvent, GetUserGameAchievementsMessageEvent.class);
        this.registerHandler(Incoming.JoinQueueMessageEvent, JoinQueueMessageEvent.class);
        this.registerHandler(Incoming.GetWeeklyCompetitiveFriendsLeaderboardEvent, GetWeeklyCompetitiveFriendsLeaderboardEvent.class);
        this.registerHandler(Incoming.Game2GetWeeklyLeaderboardEvent, Game2GetWeeklyLeaderboardEvent.class);
        this.registerHandler(Incoming.GetWeeklyGameRewardWinnersEvent, GetWeeklyGameRewardWinnersEvent.class);
        this.registerHandler(Incoming.GameUnloadedMessageEvent, GameUnloadedMessageEvent.class);
        this.registerHandler(Incoming.GetWeeklyGameRewardEvent, GetWeeklyGameRewardEvent.class);
        this.registerHandler(Incoming.Game2GetAccountGameStatusMessageEvent, Game2GetAccountGameStatusMessageEvent.class);
    }

    void registerQuests() throws Exception {
        this.registerHandler(Incoming.GetQuestsEvent, GetQuestsEvent.class);
        this.registerHandler(Incoming.AcceptQuestEvent, AcceptQuestEvent.class);
        this.registerHandler(Incoming.RejectQuestEvent, RejectQuestEvent.class);
        this.registerHandler(Incoming.OpenQuestTrackerEvent, OpenQuestTrackerEvent.class);
    }
}
