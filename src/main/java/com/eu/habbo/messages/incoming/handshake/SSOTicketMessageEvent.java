package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionBuildersClub;
import com.eu.habbo.messages.NoAuthMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.Game2AccountGameStatusMessageComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameListMessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HabboBroadcastMessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.MOTDNotificationMessageComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NoobnessLevelMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.InfoFeedEnableMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.AuthenticationOKMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.AvailabilityStatusMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.PingMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.BadgePointLimitsMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.AvatarEffectsMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.CfhTopicsInitMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.ModeratorInitMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.SanctionStatusMessageComposer;
import com.eu.habbo.messages.outgoing.navigator.*;
import com.eu.habbo.messages.outgoing.unknown.BuildersClubSubscriptionStatusMessageComposer;
import com.eu.habbo.messages.outgoing.mysterybox.MysteryBoxKeysMessageComposer;
import com.eu.habbo.messages.outgoing.users.*;
import com.eu.habbo.plugin.events.emulator.SSOAuthenticationEvent;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;

@NoAuthMessage
public class SSOTicketMessageEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSOTicketMessageEvent.class);

    @Override
    public void handle() throws Exception {
        if (!this.client.getChannel().isOpen()) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            return;
        }

        if (!Emulator.isReady)
            return;

        if (Emulator.getConfig().getBoolean("encryption.forced", false) && Emulator.getCrypto().isEnabled()
                && !this.client.isHandshakeFinished()) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            LOGGER.warn("Encryption is forced and TLS Handshake isn't finished! Closed connection...");
            return;
        }

        String sso = this.packet.readString().replace(" ", "");

        if (Emulator.getPluginManager().fireEvent(new SSOAuthenticationEvent(sso)).isCancelled()) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            LOGGER.info("SSO Authentication is cancelled by a plugin. Closed connection...");
            return;
        }

        if (sso.isEmpty()) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            LOGGER.debug("Client is trying to connect without SSO ticket! Closed connection...");
            return;
        }

        if (this.client.getHabbo() == null) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().loadHabbo(sso);
            if (habbo != null) {
                try {
                    habbo.setClient(this.client);
                    this.client.setHabbo(habbo);
                    if (!this.client.getHabbo().connect()) {
                        Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                        return;
                    }

                    if (this.client.getHabbo().getHabboInfo() == null) {
                        Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                        return;
                    }

                    if (this.client.getHabbo().getHabboInfo().getRank() == null) {
                        throw new NullPointerException(
                                habbo.getHabboInfo().getUsername() + " has a NON EXISTING RANK!");
                    }

                    Emulator.getThreading().run(habbo);
                    Emulator.getGameEnvironment().getHabboManager().addHabbo(habbo);
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                    Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                    return;
                }

                if (ClothingValidationManager.VALIDATE_ON_LOGIN) {
                    String validated = ClothingValidationManager.validateLook(this.client.getHabbo());
                    if (!validated.equals(this.client.getHabbo().getHabboInfo().getLook())) {
                        this.client.getHabbo().getHabboInfo().setLook(validated);
                    }
                }

                ArrayList<ServerMessage> messages = new ArrayList<>();

                messages.add(new AuthenticationOKMessageComposer().compose());

                int roomIdToEnter = 0;

                if (!this.client.getHabbo().getHabboStats().nux
                        || Emulator.getConfig().getBoolean("retro.style.homeroom")
                                && this.client.getHabbo().getHabboInfo().getHomeRoom() != 0)
                    roomIdToEnter = this.client.getHabbo().getHabboInfo().getHomeRoom();
                else if (!this.client.getHabbo().getHabboStats().nux
                        || Emulator.getConfig().getBoolean("retro.style.homeroom") && RoomManager.HOME_ROOM_ID > 0)
                    roomIdToEnter = RoomManager.HOME_ROOM_ID;

                messages.add(new NavigatorSettingsMessageComposer(this.client.getHabbo().getHabboInfo().getHomeRoom(),
                        roomIdToEnter).compose());
                messages.add(new AvatarEffectsMessageComposer(habbo,
                        this.client.getHabbo().getInventory().getEffectsComponent().effects.values()).compose());
                messages.add(new FigureSetIdsMessageComposer(this.client.getHabbo()).compose());
                messages.add(new NoobnessLevelMessageComposer(habbo).compose());
                messages.add(new UserRightsMessageComposer(this.client.getHabbo()).compose());
                messages.add(new AvailabilityStatusMessageComposer(true, false, true).compose());
                messages.add(new PingMessageComposer().compose());
                messages.add(
                        new InfoFeedEnableMessageComposer(Emulator.getConfig().getBoolean("bubblealerts.enabled", true))
                                .compose());
                messages.add(new AchievementsScoreMessageComposer(this.client.getHabbo()).compose());
                messages.add(new IsFirstLoginOfDayComposer(true).compose());
                messages.add(new MysteryBoxKeysMessageComposer().compose());
                messages.add(new BuildersClubSubscriptionStatusMessageComposer(this.client.getHabbo()).compose());
                messages.add(new com.eu.habbo.messages.outgoing.catalog.BuildersClubFurniCountMessageComposer(
                        this.client.getHabbo()).compose());
                messages.add(new CfhTopicsInitMessageComposer().compose());
                messages.add(new FavouritesMessageComposer(this.client.getHabbo()).compose());
                messages.add(new GameListMessageComposer().compose());
                messages.add(new Game2AccountGameStatusMessageComposer(3, 100).compose());
                messages.add(new Game2AccountGameStatusMessageComposer(0, 100).compose());

                messages.add(
                        new ScrSendUserInfoMessageComposer(this.client.getHabbo(), SubscriptionHabboClub.HABBO_CLUB,
                                ScrSendUserInfoMessageComposer.RESPONSE_TYPE_LOGIN).compose());

                if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
                    messages.add(new ModeratorInitMessageComposer(this.client.getHabbo()).compose());
                }

                this.client.sendResponses(messages);
                Emulator.getGameEnvironment().getChestManager()
                        .deliverPendingNotifications(habbo);

                // Hardcoded
                // this.client.sendResponse(new ForumsTestComposer());
                this.client.sendResponse(new BadgePointLimitsMessageComposer());

                if (Emulator.getGameEnvironment().getRewardTrackManager() != null
                        && Emulator.getGameEnvironment().getRewardTrackManager().isEnabled()) {
                    Emulator.getGameEnvironment().getRewardTrackManager().sendFullState(this.client.getHabbo(), false);
                }

                ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();

                if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                    THashMap<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap = Emulator
                            .getGameEnvironment().getModToolSanctions().getSanctions(habbo.getHabboInfo().getId());
                    ArrayList<ModToolSanctionItem> modToolSanctionItems = modToolSanctionItemsHashMap
                            .get(habbo.getHabboInfo().getId());

                    if (modToolSanctionItems != null && modToolSanctionItems.size() > 0) {
                        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);

                        if (item.sanctionLevel > 0 && item.probationTimestamp != 0
                                && item.probationTimestamp > Emulator.getIntUnixTimestamp()) {
                            this.client.sendResponse(new SanctionStatusMessageComposer(this.client.getHabbo()));
                        } else if (item.sanctionLevel > 0 && item.probationTimestamp != 0
                                && item.probationTimestamp <= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.updateSanction(item.id, 0);
                        }

                        if (item.tradeLockedUntil > 0 && item.tradeLockedUntil <= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.updateTradeLockedUntil(item.id, 0);
                            habbo.getHabboStats().setAllowTrade(true);
                        } else if (item.tradeLockedUntil > 0
                                && item.tradeLockedUntil > Emulator.getIntUnixTimestamp()) {
                            habbo.getHabboStats().setAllowTrade(false);
                        }

                        if (item.isMuted && item.muteDuration <= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.updateMuteDuration(item.id, 0);
                            habbo.unMute();
                        } else if (item.isMuted && item.muteDuration > Emulator.getIntUnixTimestamp()) {
                            Date muteDuration = new Date((long) item.muteDuration * 1000);
                            long diff = muteDuration.getTime() - Emulator.getDate().getTime();
                            habbo.mute(Math.toIntExact(diff), false);
                        }
                    }
                }

                UserLoginEvent userLoginEvent = new UserLoginEvent(habbo,
                        this.client.getHabbo().getHabboInfo().getIpLogin());
                Emulator.getPluginManager().fireEvent(userLoginEvent);

                if (userLoginEvent.isCancelled()) {
                    Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                    return;
                }

                if (Emulator.getConfig().getBoolean("hotel.welcome.alert.enabled")) {
                    final Habbo finalHabbo = habbo;
                    Emulator.getThreading().run(() -> {
                        if (Emulator.getConfig().getBoolean("hotel.welcome.alert.oldstyle")) {
                            SSOTicketMessageEvent.this.client
                                    .sendResponse(new MOTDNotificationMessageComposer(HabboManager.WELCOME_MESSAGE
                                            .replace("%username%", finalHabbo.getHabboInfo().getUsername())
                                            .replace("%user%", finalHabbo.getHabboInfo().getUsername())
                                            .split("<br/>")));
                        } else {
                            SSOTicketMessageEvent.this.client
                                    .sendResponse(new HabboBroadcastMessageComposer(HabboManager.WELCOME_MESSAGE
                                            .replace("%username%", finalHabbo.getHabboInfo().getUsername())
                                            .replace("%user%", finalHabbo.getHabboInfo().getUsername())));
                        }
                    }, Emulator.getConfig().getInt("hotel.welcome.alert.delay", 5000));
                }

                if (SubscriptionHabboClub.HC_PAYDAY_ENABLED) {
                    SubscriptionHabboClub.processUnclaimed(habbo);
                }

                SubscriptionHabboClub.processClubBadge(habbo);

                Messenger.checkFriendSizeProgress(habbo);

                SubscriptionBuildersClub.checkExpiryWarning(habbo);

                if (!habbo.getHabboStats().hasGottenDefaultSavedSearches) {
                    habbo.getHabboStats().hasGottenDefaultSavedSearches = true;
                    Emulator.getThreading().run(habbo.getHabboStats());

                    habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("official_view", ""));
                    habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("my", ""));
                    habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("favorites", ""));

                    this.client.sendResponse(new NavigatorSavedSearchesMessageComposer(
                            this.client.getHabbo().getHabboInfo().getSavedSearches()));
                }
            } else {
                Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                LOGGER.warn("Someone tried to login with a non-existing SSO token! Closed connection...");
            }
        } else {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
        }
    }
}
