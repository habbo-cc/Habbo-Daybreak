package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.TargetedOfferMessageComposer;
import com.eu.habbo.messages.outgoing.events.calendar.CampaignCalendarDataMessageComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.InClientLinkMessageComposer;
import com.eu.habbo.messages.outgoing.users.AccountPreferencesMessageComposer;
import com.eu.habbo.messages.outgoing.users.PerkAllowancesMessageComposer;
import com.eu.habbo.messages.outgoing.users.PurchasableChatStylesMessageComposer;
import com.eu.habbo.messages.outgoing.users.UserObjectMessageComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;

public class InfoRetrieveMessageEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfoRetrieveMessageEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() != null) {
            //this.client.sendResponse(new TestComposer());

            //this.client.sendResponse(new UserObjectMessageComposer(this.client.getHabbo()));
            //this.client.sendResponse(new CloseConnectionMessageComposer());
            //this.client.sendResponse(new NavigatorSettingsMessageComposer());
            //this.client.sendResponse(new UserRightsMessageComposer(this.client.getHabbo()));

            //this.client.sendResponse(new CreditBalanceMessageComposer(this.client.getHabbo()));
            //this.client.sendResponse(new ActivityPointsMessageComposer(this.client.getHabbo()));
            //this.client.sendResponse(new FavouritesMessageComposer());

            //this.client.sendResponse(new AchievementsScoreMessageComposer(this.client.getHabbo()));
            //this.client.sendResponse(new FigureSetIdsMessageComposer());
            //this.client.sendResponse(new HabboBroadcastMessageComposer(Emulator.getTexts().getValue("hotel.alert.message.welcome").replace("%user%", this.client.getHabbo().getHabboInfo().getUsername()), this.client.getHabbo()));


            //

            ArrayList<ServerMessage> messages = new ArrayList<>();


            messages.add(new UserObjectMessageComposer(this.client.getHabbo()).compose());
            messages.add(new PerkAllowancesMessageComposer(this.client.getHabbo()).compose());

            messages.add(new AccountPreferencesMessageComposer(this.client.getHabbo()).compose());
            messages.add(new PurchasableChatStylesMessageComposer(Emulator.getGameEnvironment().getCatalogManager().getOwnedChatStyles(this.client.getHabbo())).compose());
            if (Emulator.getGameEnvironment().getRewardTrackManager() != null) {
                messages.add(new com.eu.habbo.messages.outgoing.rewardtrack.RewardTracksMessageComposer(
                        !Emulator.getGameEnvironment().getRewardTrackManager().isEnabled(),
                        Emulator.getGameEnvironment().getRewardTrackManager().getTracks(),
                        Emulator.getGameEnvironment().getRewardTrackManager().loadUserStates(this.client.getHabbo()),
                        false
                ).compose());
            }


//
//

//
//
//


            this.client.sendResponses(messages);
            this.handleSessionBootstrap();


        } else {
            LOGGER.debug("Attempted to request user data where Habbo was null.");
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
        }
    }

    private void handleSessionBootstrap() throws Exception {
        if (!this.client.getHabbo().getHabboStats().getAchievementProgress().containsKey(Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login"))) {
            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login"));
        } else {
            long daysBetween = DAYS.between(new Date((long) this.client.getHabbo().getHabboInfo().getLastOnline() * 1000L).toInstant(), new Date().toInstant());
            Date lastLogin = new Date(this.client.getHabbo().getHabboInfo().getLastOnline());

            if (daysBetween == 1) {
                if (this.client.getHabbo().getHabboStats().getAchievementProgress().get(Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login")) == this.client.getHabbo().getHabboStats().loginStreak) {
                    AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login"));
                }
                this.client.getHabbo().getHabboStats().loginStreak++;
            } else if (daysBetween < 1) {
                if (((lastLogin.getTime() / 1000) - Emulator.getIntUnixTimestamp()) > 86400) {
                    this.client.getHabbo().getHabboStats().loginStreak = 0;
                }
            }
        }

        if (!this.client.getHabbo().getHabboStats().getAchievementProgress().containsKey(Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration"))) {
            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration"), 0);
        } else {
            int daysRegistered = ((Emulator.getIntUnixTimestamp() - this.client.getHabbo().getHabboInfo().getAccountCreated()) / 86400);
            int days = this.client.getHabbo().getHabboStats().getAchievementProgress(
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration")
            );

            if (daysRegistered - days > 0) {
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration"), daysRegistered - days);
            }
        }

        if (!this.client.getHabbo().getHabboStats().getAchievementProgress().containsKey(Emulator.getGameEnvironment().getAchievementManager().getAchievement("TraderPass"))) {
            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("TraderPass"));
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement achievementQueueStatement = connection.prepareStatement("SELECT * FROM users_achievements_queue WHERE user_id = ?")) {
            achievementQueueStatement.setInt(1, this.client.getHabbo().getHabboInfo().getId());

            try (ResultSet achievementSet = achievementQueueStatement.executeQuery()) {
                while (achievementSet.next()) {
                    AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementSet.getInt("achievement_id")), achievementSet.getInt("amount"));
                }
            }

            try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM users_achievements_queue WHERE user_id = ?")) {
                deleteStatement.setInt(1, this.client.getHabbo().getHabboInfo().getId());
                deleteStatement.execute();
            }
        }

        if (Emulator.getConfig().getBoolean("hotel.calendar.enabled")) {
            CalendarCampaign campaign = Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(Emulator.getConfig().getValue("hotel.calendar.default"));
            if (campaign != null) {
                long daysBetween = DAYS.between(new Timestamp(campaign.getStartTimestamp() * 1000L).toInstant(), new Date().toInstant());
                if (daysBetween >= 0) {
                    this.client.sendResponse(new CampaignCalendarDataMessageComposer(campaign.getName(), campaign.getImage(), campaign.getTotalDays(), (int) daysBetween, this.client.getHabbo().getHabboStats().calendarRewardsClaimed, campaign.getLockExpired()));
                    this.client.sendResponse(new InClientLinkMessageComposer("openView/calendar"));
                }
            }
        }

        if (TargetOffer.ACTIVE_TARGET_OFFER_ID > 0) {
            TargetOffer offer = Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(TargetOffer.ACTIVE_TARGET_OFFER_ID);

            if (offer != null) {
                this.client.sendResponse(new TargetedOfferMessageComposer(this.client.getHabbo(), offer));
            }
        }

        this.client.getHabbo().getHabboInfo().setLastOnline(Emulator.getIntUnixTimestamp());
    }
}
