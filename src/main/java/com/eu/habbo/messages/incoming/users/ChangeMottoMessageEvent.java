package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.UserChangeMessageComposer;
import com.eu.habbo.plugin.events.users.UserSavedMottoEvent;

public class ChangeMottoMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String motto = Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString(), this.client.getHabbo());
        String oldMotto = this.client.getHabbo().getHabboInfo().getMotto();
        boolean changed = oldMotto == null || !oldMotto.equals(motto);
        UserSavedMottoEvent event = new UserSavedMottoEvent(this.client.getHabbo(), this.client.getHabbo().getHabboInfo().getMotto(), motto);
        Emulator.getPluginManager().fireEvent(event);
        motto = event.newMotto;
        changed = oldMotto == null || !oldMotto.equals(motto);
        
        if(motto.length() <= Emulator.getConfig().getInt("motto.max_length", 38)) {
            this.client.getHabbo().getHabboInfo().setMotto(motto);
            this.client.getHabbo().getHabboInfo().run();
            if (changed && Emulator.getGameEnvironment().getRewardTrackManager() != null) {
                Emulator.getGameEnvironment().getRewardTrackManager().progress(this.client.getHabbo(), "change_motto");
            }
        }

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new UserChangeMessageComposer(this.client.getHabbo()).compose());
        } else {
            this.client.sendResponse(new UserChangeMessageComposer(this.client.getHabbo()));
        }

        // Friends' lists show the motto, and nothing else refreshes it for them.
        if (changed && this.client.getHabbo().getMessenger() != null) {
            this.client.getHabbo().getMessenger().connectionChanged(this.client.getHabbo(), true,
                    this.client.getHabbo().getHabboInfo().getCurrentRoom() != null);
        }

        AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("Motto"));
    }
}
